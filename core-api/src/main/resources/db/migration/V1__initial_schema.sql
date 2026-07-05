-- All money columns are DECIMAL(19,4). Never DOUBLE.
-- String keys/codes are VARCHAR to match the JPA mappings (Hibernate
-- schema validation runs with ddl-auto: validate).

CREATE TABLE account (
  id              VARCHAR(36) PRIMARY KEY,
  type            VARCHAR(32) NOT NULL,      -- CARDHOLDER, MERCHANT, FEE_REVENUE, NETWORK_SETTLEMENT (18 chars)
  currency        VARCHAR(3) NOT NULL,       -- ISO-4217
  available_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
  ledger_balance  DECIMAL(19,4) NOT NULL DEFAULT 0,  -- holds + posted
  version         BIGINT NOT NULL DEFAULT 0, -- optimistic locking @Version
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE card (
  id              VARCHAR(36) PRIMARY KEY,
  account_id      VARCHAR(36) NOT NULL,
  pan_last_four   VARCHAR(4)  NOT NULL,
  pan_hash        VARCHAR(64) NOT NULL,      -- HMAC-SHA256(PAN, pepper), hex
  cvv_hash        VARCHAR(64) NOT NULL,
  expiry_month    INT NOT NULL,
  expiry_year     INT NOT NULL,
  tier            VARCHAR(16) NOT NULL,      -- STANDARD, PREMIUM
  status          VARCHAR(16) NOT NULL,      -- ACTIVE, FROZEN, CLOSED
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_card_account FOREIGN KEY (account_id) REFERENCES account (id)
);
CREATE INDEX idx_card_account ON card(account_id);

CREATE TABLE transaction (
  id              VARCHAR(36) PRIMARY KEY,
  card_id         VARCHAR(36) NOT NULL,
  merchant_id     VARCHAR(64) NOT NULL,
  mcc             VARCHAR(4),
  amount          DECIMAL(19,4) NOT NULL,
  currency        VARCHAR(3) NOT NULL,
  status          VARCHAR(16) NOT NULL,      -- AUTHORIZED, CAPTURED, REVERSED, REFUNDED, DECLINED
  decline_reason  VARCHAR(64),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_txn_card FOREIGN KEY (card_id) REFERENCES card (id)
);
CREATE INDEX idx_txn_card_created ON transaction(card_id, created_at);

CREATE TABLE ledger_entry (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id  VARCHAR(36) NOT NULL,
  account_id      VARCHAR(36) NOT NULL,
  direction       VARCHAR(1) NOT NULL,       -- 'D' debit / 'C' credit
  amount          DECIMAL(19,4) NOT NULL,
  posted_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_le_txn FOREIGN KEY (transaction_id) REFERENCES transaction (id),
  CONSTRAINT fk_le_acc FOREIGN KEY (account_id) REFERENCES account (id)
);
CREATE INDEX idx_le_txn ON ledger_entry(transaction_id);

CREATE TABLE idempotency_record (
  idempotency_key  VARCHAR(80) PRIMARY KEY,
  endpoint         VARCHAR(80) NOT NULL,
  request_hash     VARCHAR(64) NOT NULL,
  response_status  INT NOT NULL,
  response_body    JSON NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE webhook_delivery (
  id               VARCHAR(36) PRIMARY KEY,
  event_type       VARCHAR(40) NOT NULL,
  payload          JSON NOT NULL,
  target_url       VARCHAR(255) NOT NULL,
  status           VARCHAR(16) NOT NULL,    -- PENDING, SUCCEEDED, FAILED
  attempt_count    INT NOT NULL DEFAULT 0,
  next_attempt_at  TIMESTAMP,
  last_error       VARCHAR(255),
  created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
