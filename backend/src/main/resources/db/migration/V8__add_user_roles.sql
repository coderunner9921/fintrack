-- Add a default role for all existing and new users.
ALTER TABLE users
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Restrict roles to supported values.
ALTER TABLE users
ADD CONSTRAINT chk_users_role
CHECK (role IN ('USER', 'ADMIN'));
