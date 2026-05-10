# CoreIssuer — Payment Engine Build Spec

> A stand-alone Java/Spring Boot project engineered to hit every line of the i2c
> Associate Software Engineer JD. Treat this file as a build prompt: follow it
> top to bottom and you will end up with a resume-grade, interview-defensible
> repo.

**Author:** Muhammad Umer Rashid
**Target role:** Associate Software Engineer, i2c Inc.
**Repo name:** `core-issuer` (place under `github.com/umerrashid75/core-issuer`)
**Working directory:** `C:\BuizelCodes\javai2c`

---

## 1. Why this project (JD ↔ feature map)

Every requirement in the JD has at least one place in this project where it is
*visibly* exercised. When an interviewer asks "where did you use X?" you can
point at a file.

| JD requirement | Where it shows up |
| --- | --- |
| Java 8 | Whole project compiled to `--release 8`. Optional, Streams, lambdas, `BigDecimal` everywhere. |
| Java EE / Spring Boot | Spring Boot 2.7.x (last line that supports Java 8) with Spring Web, Spring Data JPA, Spring Validation, Spring Security. |
| Struts | One legacy admin module (`admin-struts/`) built with Apache Struts 2 to manage card lifecycle (freeze / close). Demonstrates "we maintain legacy + new." |
| Maven | Multi-module Maven build (`core-issuer` parent → `core-api`, `admin-struts`, `common`). |
| Git | Repo committed with conventional commits, feature branches, PR-style merges, signed tags for milestones. |
| MySQL (and Informix-friendly) | Default profile: MySQL 8 via Spring Data JPA. SQL kept ANSI-compatible; an `informix` profile swaps the dialect to demonstrate awareness. |
| OOP analysis & design | Service layer is interface-first; DI via constructor injection; clear domain → service → repository → controller layering. |
| Data structures (LinkedList, HashMap, etc.) | `ConcurrentHashMap` idempotency cache; `ArrayDeque<Instant>` (LinkedList-style) velocity window; `TreeMap<LocalDate, Money>` settlement bucket. All real, not theatrical. |
| Algorithms | Luhn check digit on PAN generation; sliding-window velocity counter; double-entry reconciliation pass; exponential-backoff webhook retry. |
| Design patterns | Factory, Strategy, Chain of Responsibility, Builder, Observer (Spring events), Specification, Adapter — labelled in code. |
| Databases | 3rd Normal Form schema, primary/foreign keys, indexes, pessimistic row locks via `@Lock(LockModeType.PESSIMISTIC_WRITE)`. |
| Programming fundamentals | Idempotency, transactions, ACID, exception hierarchy, validation, logging with MDC correlation IDs. |
| Stand-alone services | `core-api` and `admin-struts` are independently bootable JARs. Each has its own `application.yml` and starts on its own port. |
| Implementation of APIs | `/api/v1/cards`, `/api/v1/transactions/*`, `/api/v1/admin/*`, `/api/v1/webhooks` — fully OpenAPI-documented. |
| Front-end / back-end collaboration | OpenAPI 3 spec + Swagger UI shipped with the API; a tiny React page (or just Swagger UI) drives a live demo. |
| Troubleshoot/debug existing code | Add `LEGACY_BUGS.md` documenting one or two intentional bugs in the legacy Struts module that you fixed — proof you can read and improve other people's code. |
| SDLC | `CONTRIBUTING.md`, branch protection rules, GitHub Actions CI (build + test + checkstyle + SpotBugs), CHANGELOG. |

If any JD line cannot be answered with a file path in this repo, the project is
not done.

---

## 2. Architecture at a glance

```
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
│   - ConcurrentHashMap<String, CachedResponse>  ← idempotency        │
│   - Map<UUID, ArrayDeque<Instant>>             ← velocity window    │
└─────────────────────────────────────────────────────────────────────┘
```

Two stand-alone Spring Boot processes share a database. That alone satisfies
the JD's "Stand-Alone Services" bullet without going into Spring Cloud
overkill.

---

## 3. Tech stack (pinned versions)

```
Java                17 source, --release 8 target  (or just Java 8 throughout if simpler)
Spring Boot         2.7.18         (last 2.7 line — supports Java 8)
Spring Data JPA     bundled
Hibernate           5.6.x
Struts              2.5.30         (admin-struts module only)
MySQL               8.0
HikariCP            bundled
MapStruct           1.5.5.Final
Lombok              1.18.30
springdoc-openapi   1.7.0          (Swagger UI for Spring Boot 2)
JUnit               5.10
Mockito             5.x
AssertJ             3.24
Testcontainers      1.19           (MySQL container for integration tests)
Flyway              9.x            (DB migrations under db/migration)
Maven               3.9
Docker / Compose    for local DB and demo
```

Justify every dependency in `README.md`. Don't add any you can't explain.

---

## 4. Database schema (3NF, with locking notes)

```sql
-- All money columns are DECIMAL(19,4). Never DOUBLE.

CREATE TABLE card (
  id              CHAR(36) PRIMARY KEY,
  account_id      CHAR(36) NOT NULL,
  pan_last_four   CHAR(4)  NOT NULL,
  pan_hash        CHAR(64) NOT NULL,         -- SHA-256(PAN+pepper)
  cvv_hash        CHAR(64) NOT NULL,
  expiry_month    TINYINT  NOT NULL,
  expiry_year     SMALLINT NOT NULL,
  tier            VARCHAR(16) NOT NULL,      -- STANDARD, PREMIUM
  status          VARCHAR(16) NOT NULL,      -- ACTIVE, FROZEN, CLOSED
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES account (id)
);
CREATE INDEX idx_card_account ON card(account_id);

CREATE TABLE account (
  id              CHAR(36) PRIMARY KEY,
  type            VARCHAR(16) NOT NULL,      -- CARDHOLDER, MERCHANT, FEE_REVENUE, NETWORK_SETTLEMENT
  currency        CHAR(3) NOT NULL,          -- ISO-4217
  available_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
  ledger_balance    DECIMAL(19,4) NOT NULL DEFAULT 0,  -- holds + posted
  version         BIGINT NOT NULL DEFAULT 0, -- optimistic locking @Version
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transaction (
  id              CHAR(36) PRIMARY KEY,
  card_id         CHAR(36) NOT NULL,
  merchant_id     VARCHAR(64) NOT NULL,
  mcc             CHAR(4),
  amount          DECIMAL(19,4) NOT NULL,
  currency        CHAR(3) NOT NULL,
  status          VARCHAR(16) NOT NULL,      -- AUTHORIZED, CAPTURED, REVERSED, REFUNDED, DECLINED
  decline_reason  VARCHAR(64),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_txn_card FOREIGN KEY (card_id) REFERENCES card (id)
);
CREATE INDEX idx_txn_card_created ON transaction(card_id, created_at);

CREATE TABLE ledger_entry (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id  CHAR(36) NOT NULL,
  account_id      CHAR(36) NOT NULL,
  direction       CHAR(1) NOT NULL,          -- 'D' debit / 'C' credit
  amount          DECIMAL(19,4) NOT NULL,
  posted_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_le_txn FOREIGN KEY (transaction_id) REFERENCES transaction (id),
  CONSTRAINT fk_le_acc FOREIGN KEY (account_id) REFERENCES account (id)
);
CREATE INDEX idx_le_txn ON ledger_entry(transaction_id);

CREATE TABLE idempotency_record (
  idempotency_key  VARCHAR(80) PRIMARY KEY,
  endpoint         VARCHAR(80) NOT NULL,
  request_hash     CHAR(64) NOT NULL,
  response_status  INT NOT NULL,
  response_body    JSON NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhook_delivery (
  id               CHAR(36) PRIMARY KEY,
  event_type       VARCHAR(40) NOT NULL,
  payload          JSON NOT NULL,
  target_url       VARCHAR(255) NOT NULL,
  status           VARCHAR(16) NOT NULL,    -- PENDING, SUCCEEDED, FAILED
  attempt_count    INT NOT NULL DEFAULT 0,
  next_attempt_at  TIMESTAMP,
  last_error       VARCHAR(255),
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Invariant:** for every transaction, `SUM(ledger_entry.amount WHERE direction='D') = SUM(ledger_entry.amount WHERE direction='C')`. The reconciliation job verifies this nightly and writes the result to a report file.

**Locking:**
- Cardholder account row reads use `@Lock(LockModeType.PESSIMISTIC_WRITE)` inside the authorize transaction — prevents two parallel authorizations both seeing the same balance and double-spending.
- `account.version` provides optimistic locking as a second line of defence on long-running transactions.
- Idempotency-key insertion uses `INSERT ... ON DUPLICATE KEY UPDATE` semantics so two parallel calls with the same key never both succeed.

---

## 5. API specification

All requests/responses are JSON. All money values are strings to avoid float
imprecision on the wire (the JSON parser decodes them as `BigDecimal`).

```
POST   /api/v1/cards                            # provision card
GET    /api/v1/cards/{id}                       # fetch card + balance
GET    /api/v1/cards/{id}/transactions          # list transactions

POST   /api/v1/transactions/authorize           # Idempotency-Key required
POST   /api/v1/transactions/{id}/capture        # finalize a hold
POST   /api/v1/transactions/{id}/reverse        # release a hold
POST   /api/v1/transactions/{id}/refund         # refund a captured txn

POST   /api/v1/admin/cards/{id}/freeze
POST   /api/v1/admin/cards/{id}/close
GET    /api/v1/admin/ledger/reconcile           # run reconciliation, return report

POST   /api/v1/webhooks/test                    # for the demo
```

Sample authorize request:

```http
POST /api/v1/transactions/authorize HTTP/1.1
Idempotency-Key: 4a7f9b1e-0b2a-4c8a-91f4-1f3e2c4d5e6f
Content-Type: application/json

{
  "cardId": "c1d4...",
  "merchantId": "ACME-SHOP",
  "mcc": "5411",
  "amount": "42.00",
  "currency": "USD"
}
```

Sample success response:

```json
{
  "transactionId": "8a2e...",
  "status": "AUTHORIZED",
  "amount": "42.00",
  "currency": "USD",
  "availableBalanceAfter": "458.00"
}
```

Sample decline (velocity rule fires):

```json
{
  "transactionId": "9b1d...",
  "status": "DECLINED",
  "declineReason": "VELOCITY_LIMIT_EXCEEDED"
}
```

OpenAPI 3 spec at `/v3/api-docs`. Swagger UI at `/swagger-ui.html`.

---

## 6. Design patterns — labelled, with file paths

| Pattern | File | Why |
| --- | --- | --- |
| **Factory** | `card/CardFactory.java` | `create(tier)` returns a configured `Card` per `CardTier` (`STANDARD`, `PREMIUM`) with different limits and fee rules. |
| **Strategy** | `auth/AuthorizationStrategy.java` + `DomesticAuthStrategy`, `InternationalAuthStrategy` | Selected by `currency` and `merchant.country`. Each computes fees + applies its own rule overrides. |
| **Chain of Responsibility** | `fraud/FraudCheck.java` + `VelocityCheck`, `MccBlockCheck`, `AmountCeilingCheck` | Each link returns `PASS` or `BLOCK(reason)`; chain short-circuits on first block. New rules drop in without modifying existing ones. |
| **Builder** | `dto/AuthorizeRequest.Builder` | Keeps test setup readable; immutable DTOs. |
| **Observer (Spring events)** | `event/TransactionAuthorizedEvent` + listeners | Webhook scheduler and metrics counter both subscribe; loose coupling. |
| **Specification** | `repository/TransactionSpecifications.java` | Composable predicates for the admin transaction search endpoint. |
| **Adapter** | `webhook/HttpWebhookAdapter` | Swappable transport — could become Kafka/SNS later without touching event publishers. |
| **Singleton (managed by Spring)** | All `@Service`, `@Repository`, `@Component` | Default Spring scope. |

In every file that implements a pattern, put a top-of-class Javadoc tag:

```java
/**
 * Pattern: Strategy
 * Selected by AuthorizationStrategyResolver based on currency and merchant country.
 */
```

When the interviewer greps your repo for "Pattern: Strategy" they will land on
the right file. This is the move.

---

## 7. Data-structure choices — labelled, with reasons

| Structure | File | Reason it has to be that one |
| --- | --- | --- |
| `ConcurrentHashMap<String, CachedResponse>` | `idempotency/IdempotencyCache.java` | Lock-free reads, atomic `putIfAbsent`. Default cache before DB record check. |
| `ArrayDeque<Instant>` per card | `fraud/VelocityWindow.java` | LinkedList-shaped. O(1) append / poll-from-head. Sliding-window discards entries older than 60s on each access. |
| `TreeMap<LocalDate, BigDecimal>` | `settlement/SettlementBuckets.java` | Sorted by date for the nightly settlement report. |
| `EnumMap<CardStatus, Set<CardStatus>>` | `card/CardStateMachine.java` | Allowed-transition table, EnumMap is the canonical Java structure for an enum-keyed lookup. |
| `LinkedHashMap` | `audit/AuditTrail.java` | Preserves insertion order for the audit log dumps. |

Don't pick a data structure to hit the JD checkbox; pick the one that's
genuinely correct, then label the choice in a Javadoc on the field. The
interviewer can ask you why a `HashMap` would have been wrong — and you can
answer.

---

## 8. Stand-alone services

Two independently bootable JARs share the database:

1. **`core-api`** — Spring Boot REST. Runs on `:8080`. Owns provisioning,
   authorization, capture, reversal, refund, webhooks, reconciliation.
2. **`admin-struts`** — Apache Struts 2 web app on `:8081`. JSP-driven UI for
   freezing/closing cards and viewing ledger entries. Uses the same JPA
   entities (shared `common` module).

Both modules are built as separate Maven artifacts and started by separate
`java -jar` commands. Run together via `docker compose up`.

The Struts module is intentionally minimal — its job is to satisfy the "Struts"
JD line and to give you something to talk about under "I've worked with legacy
frameworks." Keep it small.

---

## 9. Phased build plan

Eleven phases, each shippable on its own. Tag each phase with a Git tag so the
history reads like a series of milestones.

### Phase 0 — Repo bootstrap (1 evening)
- `git init`, GitHub repo, `README.md` skeleton, `.gitignore`, `.editorconfig`.
- Maven multi-module `pom.xml` (parent + `common` + `core-api` + `admin-struts`).
- GitHub Actions: build + test on PR.
- License: MIT.
- Tag `v0.1.0`.

### Phase 1 — Domain & schema (1 day)
- Flyway migrations for the schema in §4.
- JPA entities for `Card`, `Account`, `Transaction`, `LedgerEntry`,
  `IdempotencyRecord`, `WebhookDelivery`.
- Repositories with the `@Lock` annotation on the cardholder-balance read.
- A seed migration inserts a test cardholder + merchant + fee_revenue +
  settlement account.
- Tag `v0.2.0`.

### Phase 2 — Card provisioning (½ day)
- `POST /api/v1/cards` with `CardFactory` (Factory pattern).
- Luhn check digit + PAN/CVV/expiry generation.
- Hash PAN + CVV at rest with SHA-256 + pepper.
- `GET /api/v1/cards/{id}` returns card details + balance, never the raw PAN.
- Unit tests for the factory and Luhn.
- Tag `v0.3.0`.

### Phase 3 — Authorize (1.5 days)
- `POST /api/v1/transactions/authorize`.
- Strategy pattern: Domestic vs International, selected by currency.
- Pessimistic-write lock on the cardholder account during balance check.
- Double-entry ledger writes: debit cardholder, credit `NETWORK_SETTLEMENT`
  hold account.
- Decline reasons: insufficient funds, frozen card, expired card.
- Unit + integration tests.
- Tag `v0.4.0`.

### Phase 4 — Idempotency (½ day)
- `Idempotency-Key` header required on `authorize`, `capture`, `reverse`,
  `refund`.
- `ConcurrentHashMap` short-circuit cache, plus `idempotency_record` table for
  durability across restarts.
- Conflict detection: if the same key arrives with a different request hash,
  return 409.
- Tag `v0.5.0`.

### Phase 5 — Fraud & velocity (1 day)
- Chain of Responsibility: `VelocityCheck`, `MccBlockCheck`,
  `AmountCeilingCheck`.
- Velocity window: per-card `ArrayDeque<Instant>` keeping the last 60s of
  authorizations. Threshold: configurable, default `>3 in 60s` declines as
  `VELOCITY_LIMIT_EXCEEDED`.
- Each link is a `@Component`; the chain is wired by Spring config.
- Tag `v0.6.0`.

### Phase 6 — Capture, reverse, refund (1 day)
- `POST /capture` finalizes the hold: moves funds from settlement-hold to
  merchant account; ledger writes mirror.
- `POST /reverse` releases an unmatured hold (auth without capture).
- `POST /refund` against a captured transaction; creates an inverse ledger
  pair.
- Each transition is a state-machine guard (`EnumMap` allowed transitions).
- Tag `v0.7.0`.

### Phase 7 — Webhooks (½ day)
- Spring `ApplicationEventPublisher` fires
  `TransactionAuthorizedEvent`, `TransactionCapturedEvent`, etc.
- A `@Async` listener writes a `webhook_delivery` row.
- A `@Scheduled` worker picks up `PENDING` deliveries, POSTs to the merchant
  URL, retries with exponential backoff (1s, 5s, 30s, 5m, 1h), gives up after
  6 attempts and marks `FAILED`.
- Tag `v0.8.0`.

### Phase 8 — Admin Struts module (1 day)
- New Maven module `admin-struts/` with Struts 2.
- JSP pages: list cards, freeze/close, view ledger for a card.
- Calls into the same `CardService` from the `common` module.
- Bootable on `:8081`.
- Tag `v0.9.0`.

### Phase 9 — Reconciliation & settlement (½ day)
- Nightly `@Scheduled` job: for every transaction in the day's window, verify
  `SUM(D) = SUM(C)` and write the report to `reports/recon-<date>.txt`.
- Settlement bucketing: `TreeMap<LocalDate, BigDecimal>` per merchant.
- Endpoint `GET /api/v1/admin/ledger/reconcile` triggers it on demand and
  returns the report.
- Tag `v0.10.0`.

### Phase 10 — Tests, ops, polish (1 day)
- JUnit 5 + Mockito for service-layer logic; AssertJ assertions.
- Testcontainers MySQL for integration tests covering pessimistic locking
  (spawn 8 threads authorizing $1 each on a card with $5; expect exactly 5
  approvals, 3 declines, balance = 0).
- Spring Boot Actuator + Micrometer + Prometheus endpoint.
- MDC correlation IDs in logs (one per HTTP request).
- README with diagrams, demo cURLs, quickstart, design-decision log.
- Tag `v1.0.0`.

### Phase 11 — Bonus (optional, big resume win)
- BNPL extension: `POST /api/v1/loans` to split a captured transaction into
  installments. New tables, new state machine, new tests.
- Or: `informix` Maven profile with the IBM Informix Hibernate dialect, README
  section showing how to swap.

---

## 10. Testing strategy

Two test source sets in `core-api`:

`src/test/java/.../unit` — pure JUnit + Mockito. Targets:
- `CardFactory` returns the correct tier limits.
- Strategy resolver picks Domestic for USD/US, International otherwise.
- Velocity chain blocks at the configured threshold.
- Luhn check digit is correct for known fixtures.
- State machine rejects invalid transitions.

`src/test/java/.../integration` — Spring Boot test + Testcontainers MySQL.
Targets:
- **The headline test:** 8 concurrent authorize calls on a card with balance
  5.00 USD, each for 1.00 USD. Assert exactly 5 succeed, 3 are declined for
  insufficient funds, and the final available balance is 0. Run it ten times
  to flush out flakes.
- Idempotency: two parallel calls with the same key produce one DB insert.
- Reconciliation: after 100 random-walk transactions, debits equal credits.
- Webhook retry: configure target URL to 500 four times then 200; assert the
  delivery row reaches `SUCCEEDED` after exactly 5 attempts.

CI runs both on every PR. Coverage target ≥ 80% lines on the service layer.

---

## 11. Demo script

Recruiters will skim the README. The README's first section after the badges
is "Try it in 60 seconds":

```bash
# 1. Start MySQL + the two services
docker compose up -d

# 2. Provision a Premium card
curl -s -X POST localhost:8080/api/v1/cards \
  -H 'Content-Type: application/json' \
  -d '{"tier":"PREMIUM","initialBalance":"500.00","currency":"USD"}' | jq

# 3. Authorize a $42 charge (idempotent)
curl -s -X POST localhost:8080/api/v1/transactions/authorize \
  -H 'Idempotency-Key: 4a7f9b1e-0b2a-4c8a-91f4-1f3e2c4d5e6f' \
  -H 'Content-Type: application/json' \
  -d '{"cardId":"<from step 2>","merchantId":"ACME","mcc":"5411","amount":"42.00","currency":"USD"}' | jq

# 4. Replay the same call — same response, no double-charge
# 5. Capture the hold
# 6. Run reconciliation
curl -s -X GET localhost:8080/api/v1/admin/ledger/reconcile | jq
```

Record a 60-second screen capture. Embed it in the README as a `.gif`. That
gif is what gets you past the resume screen.

---

## 12. README outline (commit this exact structure)

```
# CoreIssuer
> Java 8 / Spring Boot card-issuing payment engine with double-entry ledger,
> idempotent APIs, fraud rules engine, and a legacy Struts admin module.

## Demo (gif)
## Why this exists
## Architecture (diagram)
## Quickstart (docker compose up)
## API tour (cards → auth → capture → refund → reconcile)
## Design decisions
  - Why double-entry ledger
  - Why pessimistic locking on the balance check
  - Why ConcurrentHashMap for idempotency cache
  - Why Strategy + Chain of Responsibility
## Module map
## Tests
## Roadmap (BNPL, Informix profile, Kafka backbone)
## License
```

Pin the repo on your GitHub profile. Add a one-line description: *"Card-issuing
payment engine in Java/Spring Boot — double-entry ledger, idempotent APIs,
fraud rules, Struts admin module."*

---

## 13. Resume bullets you write afterwards

Drop these into your resume's Projects section, replacing whichever of your
current projects is weakest. Keep three bullets, varied verbs.

> **CoreIssuer: Card-Issuing Payment Engine**
> *Java 8, Spring Boot, Spring Data JPA, MySQL, Struts 2, Maven, JUnit 5, Testcontainers*
>
> - Engineered a stand-alone payment processor in Spring Boot with a double-entry MySQL ledger, BigDecimal arithmetic throughout, and pessimistic row-level locking; verified no overdraft under concurrent authorizations with an 8-thread Testcontainers integration test.
> - Implemented idempotent authorize / capture / reverse / refund APIs using a `ConcurrentHashMap` short-circuit cache plus a durable `idempotency_record` table; built a fraud rules engine via Chain of Responsibility (velocity, MCC blocks, ceilings) using a per-card `ArrayDeque` sliding window.
> - Added a Struts 2 admin module sharing JPA entities with the Spring Boot core, OpenAPI/Swagger docs, exponential-backoff webhook delivery, and a nightly reconciliation job that proves every transaction's debits and credits reconcile to zero.

---

## 14. Interview prep checklist

When you walk into the i2c interview, be ready to answer:

- *Walk me through what happens between merchant `authorize` and merchant
  `capture`.* — auth places a hold, capture finalizes; describe the ledger
  entries at each step.
- *What stops two parallel authorize calls from double-spending the balance?*
  — pessimistic-write lock on the account row inside one transaction.
- *Why HashMap and not TreeMap / LinkedHashMap?* — answer for each place you
  used a Map; the answer is different each time.
- *What's the difference between `@Transactional` propagation REQUIRED and
  REQUIRES_NEW?* — webhook persistence uses REQUIRES_NEW so the outer
  transaction can roll back without losing the audit row.
- *Why BigDecimal and not double?* — float representation can't hold 0.10
  exactly; cumulative rounding errors are unacceptable for money.
- *How does your idempotency handle network retries vs replay attacks?* — same
  request body hash with same key returns cached; different body hash returns
  409.
- *Walk me through your Strategy and Factory implementations.* — open the
  files, point at the `Pattern: Strategy` Javadoc tags, explain the resolver.
- *If you had to scale this 100x, what's the first bottleneck?* — the row-lock
  on balance becomes contention; partition by card-id, or move to optimistic
  lock + retry, or precompute available balance via Kafka.

If you can answer those eight cleanly, you will outperform every other 0–1 YOE
candidate in the i2c pipeline.

---

## 15. Delivery checklist

Before declaring done:

- [ ] All 11 phases tagged in Git
- [ ] Both JARs run via `docker compose up`
- [ ] Swagger UI loads at `/swagger-ui.html`
- [ ] 80%+ test coverage on service layer
- [ ] The 8-thread concurrent-authorize integration test passes 10 runs in a row
- [ ] Reconciliation job reports 0 breaks on the seed data
- [ ] README has a demo gif
- [ ] LinkedIn project, GitHub pinned, resume bullet updated
- [ ] One Loom video walkthrough (5 min) linked in README

Ship this and your application to i2c stops being one of fifty and starts
being one of three.
