-- Add request idempotency support to transactions.
-- Existing transactions remain valid with a NULL key.

ALTER TABLE transactions
ADD COLUMN idempotency_key VARCHAR(100);

-- Each user can use a given idempotency key only once.
-- PostgreSQL allows multiple NULL values with this unique index.

CREATE UNIQUE INDEX uq_transactions_user_idempotency_key
ON transactions (user_id, idempotency_key);