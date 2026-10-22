-- Allow reversal transactions while preserving the existing transaction types.
ALTER TABLE transactions
    DROP CONSTRAINT chk_transaction_type;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_type
    CHECK (
        transaction_type IN (
            'INCOME',
            'EXPENSE',
            'TRANSFER',
            'REVERSAL'
        )
    );

-- A reversal retains the original account references.
-- Income/expense reversals use one account; transfer reversals use two.
ALTER TABLE transactions
    DROP CONSTRAINT chk_transaction_accounts;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_accounts
    CHECK (
        (
            transaction_type = 'INCOME'
            AND source_account_id IS NULL
            AND destination_account_id IS NOT NULL
        )
        OR
        (
            transaction_type = 'EXPENSE'
            AND source_account_id IS NOT NULL
            AND destination_account_id IS NULL
        )
        OR
        (
            transaction_type = 'TRANSFER'
            AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id
        )
        OR
        (
            transaction_type = 'REVERSAL'
            AND (
                (
                    source_account_id IS NULL
                    AND destination_account_id IS NOT NULL
                )
                OR
                (
                    source_account_id IS NOT NULL
                    AND destination_account_id IS NULL
                )
                OR
                (
                    source_account_id IS NOT NULL
                    AND destination_account_id IS NOT NULL
                    AND source_account_id <> destination_account_id
                )
            )
        )
    );

-- Link each reversal to its original transaction.
ALTER TABLE transactions
    ADD COLUMN reversal_of_transaction_id UUID;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_reversal_original
    FOREIGN KEY (reversal_of_transaction_id)
    REFERENCES transactions(id)
    ON DELETE RESTRICT;

-- A non-null original transaction ID can appear only once.
CREATE UNIQUE INDEX uq_transactions_reversal_of
    ON transactions(reversal_of_transaction_id)
    WHERE reversal_of_transaction_id IS NOT NULL;
