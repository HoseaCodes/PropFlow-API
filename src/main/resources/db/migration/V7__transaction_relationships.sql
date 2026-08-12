-- V7: Turn the transaction's scalar references into real foreign keys.
--
-- transactions.user_id was VARCHAR(255) while users.id is BIGINT, and neither
-- user_id nor property_id had a foreign key. Three consequences:
--   1. A transaction could reference property 99999, or user 'banana'. Nothing
--      rejected it.
--   2. Deleting a property silently orphaned its financial records.
--   3. The two columns could not be joined without a cast, and no index could
--      help a join that casts.
--
-- The database is the only place this invariant can actually be enforced.
-- Application-level checks are advisory: they do not bind a data-repair script,
-- a background job, or a second service.

-- ---------------------------------------------------------------------------
-- user_id: VARCHAR -> BIGINT
-- ---------------------------------------------------------------------------
-- Converted via a new column rather than ALTER TYPE ... USING, so that
-- unconvertible rows can be detected and reported before anything is dropped.
ALTER TABLE transactions
    ADD COLUMN user_id_bigint BIGINT;

-- Only convert values that are both numeric and an existing user.
UPDATE transactions t
SET user_id_bigint = CAST(t.user_id AS BIGINT)
WHERE t.user_id ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM users u WHERE u.id = CAST(t.user_id AS BIGINT));

DO $$
DECLARE
    unconverted INTEGER;
BEGIN
    SELECT COUNT(*) INTO unconverted FROM transactions WHERE user_id_bigint IS NULL;

    IF unconverted > 0 THEN
        RAISE EXCEPTION
            'Cannot apply V7: % transaction(s) have a user_id that is not a numeric id of an existing user. Reconcile them before migrating -- these are financial records and must not be reassigned or discarded automatically. For a throwaway development database: docker compose down -v',
            unconverted;
    END IF;
END $$;

ALTER TABLE transactions DROP COLUMN user_id;
ALTER TABLE transactions RENAME COLUMN user_id_bigint TO user_id;
ALTER TABLE transactions ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------
-- property_id: add the missing foreign key
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    orphans INTEGER;
BEGIN
    SELECT COUNT(*) INTO orphans
    FROM transactions t
    WHERE NOT EXISTS (SELECT 1 FROM properties p WHERE p.id = t.property_id);

    IF orphans > 0 THEN
        RAISE EXCEPTION
            'Cannot apply V7: % transaction(s) reference a property that does not exist. Reconcile before migrating.',
            orphans;
    END IF;
END $$;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------
-- Indexes
-- ---------------------------------------------------------------------------
-- Both are composite, and the column order is the whole point.
--
-- Every transaction read is now scoped to the caller and sorted newest-first:
--     WHERE user_id = ? ORDER BY date DESC
--
-- With user_id leading, PostgreSQL seeks straight to that user's slice of the
-- index. Because date is the second column and stored descending, the rows in
-- that slice are ALREADY in the requested order, so the planner can satisfy the
-- ORDER BY and the LIMIT from the index itself -- no separate sort step, and it
-- can stop after reading one page instead of sorting the user's entire history.
--
-- Reversing the order to (date, user_id) would be close to useless here: an
-- equality predicate on the second column cannot be used for seeking, so the
-- scan would read across every user's rows.
--
-- Tradeoff: two indexes cost storage and are maintained inside every INSERT,
-- UPDATE, and DELETE on transactions. For a read-heavy financial reporting
-- workload that is clearly worth it. On a write-heavy ingest table it might not
-- be, and that judgement should be re-made rather than assumed.
CREATE INDEX ix_transactions_user_id_date ON transactions (user_id, date DESC);
CREATE INDEX ix_transactions_property_id_date ON transactions (property_id, date DESC);

-- Deliberately NOT created: an index on (user_id, property_id). The leading
-- column of ix_transactions_user_id_date already narrows to the user, and
-- filtering that much smaller result by property is cheap. Adding indexes for
-- every column combination is how a table ends up slower to write and no faster
-- to read.
