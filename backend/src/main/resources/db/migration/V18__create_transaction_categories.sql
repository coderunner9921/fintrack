
-- V17: Add categories while preserving historical transaction labels.

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- NULL owner means this is a shared system category.
    user_id UUID
        REFERENCES users(id) ON DELETE RESTRICT,

    name VARCHAR(80) NOT NULL,

    category_type VARCHAR(20) NOT NULL DEFAULT 'BOTH',

    is_system BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_category_type
        CHECK (category_type IN ('INCOME', 'EXPENSE', 'BOTH')),

    CONSTRAINT chk_category_name
        CHECK (length(trim(name)) > 0),

    CONSTRAINT chk_category_ownership
        CHECK (
            (is_system = TRUE AND user_id IS NULL)
            OR
            (is_system = FALSE AND user_id IS NOT NULL)
        )
);

-- System categories are shared by all users.
CREATE UNIQUE INDEX uq_system_categories_name
    ON categories (lower(name))
    WHERE is_system = TRUE;

-- Custom category names must be unique per user, ignoring case.
CREATE UNIQUE INDEX uq_user_categories_name
    ON categories (user_id, lower(name))
    WHERE is_system = FALSE;

-- Seed shared defaults. BOTH keeps these usable during the initial rollout.
INSERT INTO categories (name, category_type, is_system)
VALUES
    ('Food', 'BOTH', TRUE),
    ('Transport', 'BOTH', TRUE),
    ('Bills', 'BOTH', TRUE),
    ('Salary', 'BOTH', TRUE),
    ('Shopping', 'BOTH', TRUE),
    ('Healthcare', 'BOTH', TRUE),
    ('Entertainment', 'BOTH', TRUE),
    ('Housing', 'BOTH', TRUE),
    ('Education', 'BOTH', TRUE),
    ('Travel', 'BOTH', TRUE),
    ('Other', 'BOTH', TRUE);

-- Create user-owned categories for historical labels that do not match
-- a shared default. Group case-insensitively to avoid duplicate names.
INSERT INTO categories (user_id, name, category_type, is_system)
SELECT
    historical.user_id,
    historical.name,
    'BOTH',
    FALSE
FROM (
    SELECT DISTINCT ON (t.user_id, lower(trim(t.category)))
        t.user_id,
        COALESCE(NULLIF(trim(t.category), ''), 'Uncategorized') AS name
    FROM transactions t
    ORDER BY t.user_id, lower(trim(t.category)), t.category
) historical
WHERE NOT EXISTS (
    SELECT 1
    FROM categories system_category
    WHERE system_category.is_system = TRUE
      AND lower(system_category.name) = lower(historical.name)
);

-- Add a nullable relationship first, then backfill it.
ALTER TABLE transactions
    ADD COLUMN category_id UUID;

-- Existing labels matching a shared default use that system category.
UPDATE transactions t
SET category_id = c.id
FROM categories c
WHERE c.is_system = TRUE
  AND lower(c.name) = lower(trim(t.category));

-- Remaining historical labels use the user's own category.
UPDATE transactions t
SET category_id = c.id
FROM categories c
WHERE c.is_system = FALSE
  AND c.user_id = t.user_id
  AND lower(c.name) = lower(
      COALESCE(NULLIF(trim(t.category), ''), 'Uncategorized')
  )
  AND t.category_id IS NULL;

-- Every existing transaction must have a category relationship.
ALTER TABLE transactions
    ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_category
    FOREIGN KEY (category_id)
    REFERENCES categories(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_transactions_category_date
    ON transactions (category_id, transaction_date DESC);
