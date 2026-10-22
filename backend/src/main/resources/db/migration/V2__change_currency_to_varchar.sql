-- V2: Align currency column type with the JPA entity

ALTER TABLE accounts
ALTER COLUMN currency TYPE VARCHAR(3);