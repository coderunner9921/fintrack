-- Every reversal must reference its original transaction.
-- Ordinary transactions must not have a reversal link.
ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_reversal_link
    CHECK (
        (
            transaction_type = 'REVERSAL'
            AND reversal_of_transaction_id IS NOT NULL
        )
        OR
        (
            transaction_type <> 'REVERSAL'
            AND reversal_of_transaction_id IS NULL
        )
    );

-- A transaction cannot reference itself as its original.
ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_not_self_reversal
    CHECK (
        reversal_of_transaction_id IS NULL
        OR reversal_of_transaction_id <> id
    );
