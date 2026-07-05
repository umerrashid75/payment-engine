-- Fee charged at authorization. Capture needs it to post fee revenue and
-- reverse needs it to release the full hold (amount + fee).
ALTER TABLE transaction
  ADD COLUMN fee_amount DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER amount;
