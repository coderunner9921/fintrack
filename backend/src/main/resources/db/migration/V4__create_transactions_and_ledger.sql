
-- V3: Create transaction and double-entry ledger tables

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL
        REFERENCES users(id) ON DELETE RESTRICT,

    transaction_type VARCHAR(20) NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(80) NOT NULL,

    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_date DATE NOT NULL,

    source_account_id UUID
        REFERENCES accounts(id) ON DELETE RESTRICT,

    destination_account_id UUID
        REFERENCES accounts(id) ON DELETE RESTRICT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transaction_type
        CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER')),

    CONSTRAINT chk_transaction_amount
        CHECK (amount > 0),

    CONSTRAINT chk_transaction_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_transaction_accounts
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
        )
);

CREATE INDEX idx_transactions_user_date
    ON transactions(user_id, transaction_date DESC);

CREATE INDEX idx_transactions_source_account
    ON transactions(source_account_id);

CREATE INDEX idx_transactions_destination_account
    ON transactions(destination_account_id);


CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,

    transaction_id UUID NOT NULL
        REFERENCES transactions(id) ON DELETE RESTRICT,

    account_id UUID
        REFERENCES accounts(id) ON DELETE RESTRICT,

    system_account_code VARCHAR(30),

    entry_side VARCHAR(10) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ledger_entry_side
        CHECK (entry_side IN ('DEBIT', 'CREDIT')),

    CONSTRAINT chk_ledger_entry_amount
        CHECK (amount > 0),

    CONSTRAINT chk_ledger_account_reference
        CHECK (
            (account_id IS NOT NULL AND system_account_code IS NULL)
            OR
            (account_id IS NULL AND system_account_code IS NOT NULL)
        ),

    CONSTRAINT chk_ledger_system_account
        CHECK (
            system_account_code IS NULL
            OR system_account_code IN (
                'INCOME',
                'EXPENSE',
                'EQUITY',
                'OPENING_BALANCE'
            )
        )
);

CREATE INDEX idx_ledger_entries_transaction
    ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_entries_account
    ON ledger_entries(account_id);
