-- V4: Add an optimistic locking version column to properties.
--
-- Without it, two concurrent updates to the same property both succeed and the
-- later write silently discards the earlier one -- a lost update. The users
-- table already had a version column; the financial and property tables, which
-- matter more, did not.
--
-- How it works: Hibernate includes the version in the UPDATE predicate
-- (WHERE id = ? AND version = ?) and increments it. If another transaction
-- committed first, zero rows match, Hibernate detects the mismatch and raises
-- OptimisticLockingFailureException, which the API surfaces as 409 Conflict.
-- No locks are held, so readers are never blocked -- the conflict is detected
-- at write time rather than prevented at read time.

ALTER TABLE properties
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
