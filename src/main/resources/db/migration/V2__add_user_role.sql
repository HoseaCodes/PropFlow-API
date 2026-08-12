-- V2: Add a role column to users.
--
-- Every account previously carried a hardcoded ROLE_USER authority returned
-- from User.getAuthorities(), so there was no way to distinguish an
-- administrator from a standard account. Persisting the role makes that
-- distinction real and auditable.

-- NOT NULL with a DEFAULT is safe on an existing table: since PostgreSQL 11 a
-- default is stored as metadata rather than rewriting every row, so this is a
-- fast catalog-only operation regardless of table size. Existing accounts
-- become USER, which is the least-privileged option.
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Keeps the column self-describing and rejects values the application would
-- never produce. Without this, a data-repair script could write 'admin' in
-- lower case and silently create an account that matches no authority check.
ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- V1 created users.version as NOT NULL DEFAULT 0. That default is retained
-- deliberately: rows inserted outside JPA still participate in optimistic
-- locking rather than failing on a null version.
