-- V5: Convert monetary columns from DOUBLE PRECISION to NUMERIC(19,2).
--
-- DOUBLE PRECISION is IEEE-754 binary floating point, which cannot represent
-- most decimal fractions exactly. 0.1 + 0.2 evaluates to 0.30000000000000004.
-- Individually the error is invisible; summed across a year of transactions for
-- a tax report it is not, and the totals will not reconcile against the
-- statements they are supposed to match.
--
-- NUMERIC is arbitrary-precision decimal: exact for the values money actually
-- takes. It is slower to compute with than a float, which is irrelevant here --
-- these columns are summed and reported, not used in tight numeric loops.
--
-- Scale 2 covers currency to the cent. Precision 19 leaves ample headroom while
-- staying within the range of a 64-bit integer count of cents.
--
-- MIGRATION CONSEQUENCE, stated explicitly:
-- Existing DOUBLE values are rounded to 2 decimal places. A stored 10.005 --
-- which a float may actually hold as 10.004999999999999 -- becomes 10.00.
-- Any value that was already a real currency amount is unaffected, because such
-- values have at most 2 decimal places by construction. Values with more
-- precision than a cent were not meaningful money to begin with. No rows are
-- deleted and no column is dropped.

ALTER TABLE transactions
    ALTER COLUMN transaction_amount TYPE NUMERIC(19, 2)
        USING ROUND(transaction_amount::numeric, 2);

ALTER TABLE transactions
    ALTER COLUMN tax_amount TYPE NUMERIC(19, 2)
        USING ROUND(tax_amount::numeric, 2);

ALTER TABLE transactions
    ALTER COLUMN refund_amount TYPE NUMERIC(19, 2)
        USING ROUND(refund_amount::numeric, 2);

-- An amount of zero or less is not a transaction; direction is carried by the
-- type column (INCOME or EXPENSE), not by the sign of the amount. Enforcing it
-- here means the rule holds for writers that bypass the application.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_amount_positive CHECK (transaction_amount > 0);

-- Optimistic locking, matching users and properties. Two concurrent updates to
-- the same transaction would otherwise both succeed with the later silently
-- discarding the earlier -- unacceptable for a financial record.
ALTER TABLE transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
