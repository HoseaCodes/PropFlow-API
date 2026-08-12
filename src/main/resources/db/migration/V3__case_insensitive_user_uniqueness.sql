-- V3: Make email and username uniqueness case-insensitive.
--
-- V1 created plain UNIQUE constraints on users.email and users.username. Those
-- are case-SENSITIVE, so "User@example.com" and "user@example.com" are two
-- distinct values and both can be registered. That is not what anyone means by
-- "email must be unique": the same person would end up with two accounts, and
-- either could be used to sign in.
--
-- Checking case-insensitively in application code is not sufficient. Two
-- concurrent registrations can both pass the check before either commits --
-- check-then-act is racy by construction. Only a database constraint evaluated
-- at write time actually enforces the invariant.

-- Guard against data that would violate the new constraint. On an empty or
-- clean database this is a no-op; if it does raise, the duplicates must be
-- reconciled by hand rather than silently discarded, because deciding which of
-- two real accounts to keep is a business decision, not a migration's call.
DO $$
DECLARE
    duplicate_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT lower(email) FROM users GROUP BY lower(email) HAVING COUNT(*) > 1
        UNION ALL
        SELECT lower(username) FROM users GROUP BY lower(username) HAVING COUNT(*) > 1
    ) AS duplicates;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION
            'Cannot apply V3: % case-insensitive duplicate email/username group(s) exist. Reconcile them before migrating.',
            duplicate_count;
    END IF;
END $$;

-- Replace the case-sensitive constraints with functional unique indexes.
-- Keeping both would be redundant: a unique index on lower(email) already
-- rejects everything UNIQUE(email) would, and every extra index costs storage
-- and write time on each INSERT and UPDATE.
ALTER TABLE users DROP CONSTRAINT uq_users_email;
ALTER TABLE users DROP CONSTRAINT uq_users_username;

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));

-- Note for query planning: these indexes are only usable by a predicate of the
-- same shape. WHERE lower(email) = lower(?) can use uq_users_email_lower;
-- WHERE email = ? cannot. Repository lookups that need to match this index must
-- be written accordingly.
