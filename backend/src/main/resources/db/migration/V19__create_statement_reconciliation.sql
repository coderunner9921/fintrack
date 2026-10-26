
CREATE TABLE reconciliation_imports (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    total_rows INTEGER NOT NULL DEFAULT 0,
    matched_rows INTEGER NOT NULL DEFAULT 0,
    unmatched_rows INTEGER NOT NULL DEFAULT 0,
    duplicate_rows INTEGER NOT NULL DEFAULT 0,
    mismatched_rows INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_reconciliation_imports_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_reconciliation_imports_account_owner
        FOREIGN KEY (account_id, user_id)
        REFERENCES accounts(id, user_id),

    CONSTRAINT chk_reconciliation_import_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_reconciliation_import_counts
        CHECK (
            total_rows >= 0
            AND matched_rows >= 0
            AND unmatched_rows >= 0
            AND duplicate_rows >= 0
            AND mismatched_rows >= 0
        )
);

CREATE INDEX idx_reconciliation_imports_user_created
    ON reconciliation_imports (user_id, created_at DESC);

CREATE INDEX idx_reconciliation_imports_account
    ON reconciliation_imports (account_id);


CREATE TABLE statement_rows (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    statement_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    matched_transaction_id UUID,
    duplicate_of_row_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_statement_rows_import
        FOREIGN KEY (import_id)
        REFERENCES reconciliation_imports(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_statement_rows_transaction
        FOREIGN KEY (matched_transaction_id)
        REFERENCES transactions(id),

    CONSTRAINT fk_statement_rows_duplicate
        FOREIGN KEY (duplicate_of_row_id)
        REFERENCES statement_rows(id),

    CONSTRAINT uq_statement_rows_import_row
        UNIQUE (import_id, row_number),

    CONSTRAINT chk_statement_rows_row_number
        CHECK (row_number > 0),

    CONSTRAINT chk_statement_rows_amount
        CHECK (amount <> 0),

    CONSTRAINT chk_statement_rows_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_statement_rows_status
        CHECK (
            status IN (
                'PENDING',
                'MATCHED',
                'UNMATCHED',
                'DUPLICATE',
                'MISMATCHED'
            )
        ),

    CONSTRAINT chk_statement_rows_match
        CHECK (
            status = 'MATCHED'
            OR matched_transaction_id IS NULL
        ),

    CONSTRAINT chk_statement_rows_duplicate
        CHECK (
            status = 'DUPLICATE'
            OR duplicate_of_row_id IS NULL
        )
);

CREATE INDEX idx_statement_rows_import_status
    ON statement_rows (import_id, status);

CREATE INDEX idx_statement_rows_matched_transaction
    ON statement_rows (matched_transaction_id)
    WHERE matched_transaction_id IS NOT NULL;
