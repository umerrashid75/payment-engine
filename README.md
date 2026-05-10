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
│   - ConcurrentHashMap<String, CachedResponse>  ← idempotency        │
│   - Map<UUID, ArrayDeque<Instant>>             ← velocity window    │
└─────────────────────────────────────────────────────────────────────┘
```

## Quickstart (docker compose up)
(Instructions to be added)

## API tour
- POST /api/v1/cards
- POST /api/v1/transactions/authorize
- POST /api/v1/transactions/{id}/capture
- POST /api/v1/transactions/{id}/refund
- GET /api/v1/admin/ledger/reconcile

## Design decisions
- Why double-entry ledger
- Why pessimistic locking on the balance check
- Why ConcurrentHashMap for idempotency cache
- Why Strategy + Chain of Responsibility

## Module map
- `common`: Shared JPA entities and models.
- `core-api`: Spring Boot REST engine.
- `admin-struts`: Legacy Struts 2 web app.

## Tests
(To be added)

## Roadmap
- BNPL extension
- Informix profile
- Kafka backbone

## License
MIT License
