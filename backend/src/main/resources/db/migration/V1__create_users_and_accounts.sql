
-- V1: Initial users and accounts schema

CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    opening_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT chk_accounts_type
        CHECK (account_type IN (
            'CASH',
            'SAVINGS',
            'CURRENT',
            'CREDIT_CARD',
            'INVESTMENT',
            'OTHER'
        )),

    CONSTRAINT chk_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_accounts_user_id
    ON accounts(user_id);
