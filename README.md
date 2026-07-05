# CoreIssuer
> Java 8 / Spring Boot card-issuing payment engine with double-entry ledger,
> idempotent APIs, fraud rules engine, and a legacy Struts admin module.

## Demo (gif)
(Demo to be added)

## Why this exists
This project is engineered to hit every line of the i2c Associate Software Engineer Job Description.
Every requirement in the JD has at least one place in this project where it is visibly exercised.

## Architecture
```text
┌─────────────────────────────────────────────────────────────────────┐
│                          CoreIssuer System                          │
│                                                                     │
│  ┌──────────────────┐         ┌─────────────────────┐               │
│  │  admin-struts    │         │      core-api       │               │
│  │  (Struts 2)      │  HTTP   │  (Spring Boot REST) │  ← merchants  │
│  │  port 8081       │ ──────► │  port 8080          │               │
│  │  - freeze card   │         │  - provision card   │               │
│  │  - close card    │         │  - authorize        │               │
│  │  - view ledger   │         │  - capture          │               │
│  └──────────────────┘         │  - reverse / refund │               │
│                               │  - webhooks         │               │
│                               └─────────┬───────────┘               │
│                                         │                           │
│                          ┌──────────────▼──────────────┐            │
│                          │      MySQL 8 (3NF)          │            │
│                          │  cards, accounts,           │            │
│                          │  transactions, ledger_entry │            │
│                          │  idempotency_record         │            │
│                          └─────────────────────────────┘            │
│                                                                     │
│   In-memory:                                                        │
│   - ConcurrentHashMap<String, CachedIdempotentResponse> ← idempotency│
│   - Map<String, ArrayDeque<Instant>>            ← velocity window   │
└─────────────────────────────────────────────────────────────────────┘
```

## Quickstart

```bash
# 1. Configure secrets (compose refuses to start without them)
cp .env.example .env
#    edit .env: set COREISSUER_PEPPER, ADMIN_USER, ADMIN_PASSWORD

# 2. Build and start MySQL + both services (multi-stage Docker build,
#    no local JDK required)
docker compose up --build -d

# 3. Provision a card
curl -s -X POST localhost:8080/api/v1/cards \
  -H 'Content-Type: application/json' \
  -d '{"tier":"PREMIUM","initialBalance":"500.00","currency":"USD"}'

# 4. Authorize a $42 charge (Idempotency-Key is required)
curl -s -X POST localhost:8080/api/v1/transactions/authorize \
  -H 'Idempotency-Key: 4a7f9b1e-0b2a-4c8a-91f4-1f3e2c4d5e6f' \
  -H 'Content-Type: application/json' \
  -d '{"cardId":"<from step 3>","merchantId":"1","mcc":"5411","amount":"42.00","currency":"USD"}'

# 5. Replay step 4 — identical response, no double charge.
#    Same key + different body → 409 conflict.

# 6. Capture the hold
curl -s -X POST localhost:8080/api/v1/transactions/<txn-id>/capture

# 7. Run reconciliation (admin endpoints require basic auth)
curl -s -u "$ADMIN_USER:$ADMIN_PASSWORD" localhost:8080/api/v1/admin/ledger/reconcile
```

Admin UI (Struts 2): http://localhost:8081/cards
Swagger UI: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/actuator/health

To run locally without Docker you need JDK 8+ (any modern JDK works — main
code is compiled with `--release 8`), a MySQL 8 instance, and the environment
variables from `.env.example` exported.

## API tour
- `POST /api/v1/cards` — provision a card (+ cardholder account)
- `GET  /api/v1/cards/{id}` — card details; never exposes the PAN
- `POST /api/v1/transactions/authorize` — requires `Idempotency-Key` header
- `POST /api/v1/transactions/{id}/capture`
- `POST /api/v1/transactions/{id}/reverse` — releases the full hold, fee included
- `POST /api/v1/transactions/{id}/refund`
- `GET  /api/v1/admin/ledger/reconcile` — HTTP Basic (`ADMIN_USER`/`ADMIN_PASSWORD`)

Error envelope: `{"error": "<code>", "detail": "<optional>"}` with proper
status codes — 404 unknown resources, 409 state-machine or idempotency
conflicts, 400 validation failures.

## Design decisions
- **Double-entry ledger** — every movement writes a balanced D/C pair per
  transaction; the nightly reconciliation job proves `SUM(D) = SUM(C)` for
  every transaction.
- **Fees as first-class postings** — the fee is held with the authorization,
  posted to `acc-fee-revenue` at capture, and released back to the cardholder
  on reversal.
- **Pessimistic locking on the balance check** — `SELECT ... FOR UPDATE`
  (via `@Lock(PESSIMISTIC_WRITE)`) prevents two parallel authorizations from
  double-spending; `@Version` on account is the optimistic second line.
- **Idempotency: ConcurrentHashMap + durable table** — lock-free reads and an
  atomic `putIfAbsent` in-flight marker in memory (TTL-evicted), backed by the
  `idempotency_record` table across restarts. Key reuse with a different
  payload hash returns 409.
- **Strategy + Chain of Responsibility** — domestic/international fee routing
  by merchant country; fraud checks (amount ceiling, MCC block, velocity
  sliding window) short-circuit on first block.
- **PAN/CVV never stored raw** — HMAC-SHA256 with a peppered key; PAN and CVV
  are generated with `SecureRandom`.

## Module map
- `common`: Shared JPA entities and models.
- `core-api`: Spring Boot REST engine.
- `admin-struts`: Legacy Struts 2 web app (prototype-scoped actions, POST +
  session-token forms for state changes).

## Tests
```bash
./mvnw clean verify          # unit tests + Testcontainers ITs (needs Docker)
./mvnw clean verify -DskipITs  # unit tests only
```
- Unit: fraud checks, reconciliation, full `TransactionService` state machine
  and ledger assertions (Mockito).
- Integration: `TransactionFlowIT` boots the app against a real MySQL 8
  container, covers authorize→capture→refund and the concurrent-authorize
  pessimistic-locking test.
- Coverage: JaCoCo report at `core-api/target/site/jacoco/index.html`.

## Known limitations (deliberate scope cuts)
- Merchant-facing endpoints are unauthenticated; production deployments put
  them behind gateway auth (API keys / mTLS). Admin endpoints require basic auth.
- Merchant → settlement-account mapping is a naming convention
  (`acc-merchant-<merchantId>`), pending a real mapping table.
- Single-node idempotency in-flight marker (in-memory); multi-node needs a
  shared store or DB-level `INSERT ... ON DUPLICATE KEY`.
- Spring Boot 2.7 is the last Java-8-compatible line and is past OSS EOL;
  kept intentionally to honor the Java 8 requirement.

## Roadmap
- BNPL extension
- Informix profile
- Kafka backbone

## License
MIT License
