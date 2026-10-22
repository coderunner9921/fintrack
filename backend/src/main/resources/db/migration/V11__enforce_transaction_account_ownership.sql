-- Ensure an account ID and its owner form a valid reference.
ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_id_user_id
    UNIQUE (id, user_id);

-- A transaction may reference only accounts owned by its user.
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_source_account_owner
    FOREIGN KEY (source_account_id, user_id)
    REFERENCES accounts (id, user_id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_destination_account_owner
    FOREIGN KEY (destination_account_id, user_id)
    REFERENCES accounts (id, user_id);