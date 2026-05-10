-- Seed Data for Testing and Demo

-- 1. Merchant Account
INSERT INTO account (id, type, currency, available_balance, ledger_balance, version)
VALUES ('acc-merchant-1', 'MERCHANT', 'USD', 0.00, 0.00, 0);

-- 2. Fee Revenue Account
INSERT INTO account (id, type, currency, available_balance, ledger_balance, version)
VALUES ('acc-fee-revenue', 'FEE_REVENUE', 'USD', 0.00, 0.00, 0);

-- 3. Network Settlement Account
INSERT INTO account (id, type, currency, available_balance, ledger_balance, version)
VALUES ('acc-network-settle', 'NETWORK_SETTLEMENT', 'USD', 0.00, 0.00, 0);

-- Note: Cardholder accounts and cards will be created via the API's provision endpoint.
