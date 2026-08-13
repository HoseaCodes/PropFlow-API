# ADR-001: PostgreSQL as the relational database

**Status:** Accepted · **Date:** 2026-08

## Context

PropFlow stores property records and a financial ledger: income and expenses,
tax categorisation, refunds, warranties. The access patterns are
report-shaped — filter a user's transactions by date range, category, amount,
and status, then aggregate.

Two properties dominate the choice. The data is **highly relational** (a
transaction belongs to a property belongs to a user), and it is **money**, which
must be exact and must not be lost.

## Options considered

**MongoDB.** Attractive early: the `Transaction` document with nested tax
details, warranties, and metadata maps naturally to a document. Rejected because
the invariants that matter here are relational. A document store cannot enforce
"every transaction references a real property" — that check moves into
application code, where it is advisory. This project has already demonstrated
what happens when an invariant lives only in application code: transactions
referenced users through an unvalidated string, and the model drifted.

**MySQL.** A reasonable choice. PostgreSQL wins on the features this workload
will actually reach for: partial and functional indexes (used in V3 for
case-insensitive uniqueness), `NUMERIC` arithmetic, richer constraint support,
and `EXCLUDE USING gist` for booking-overlap prevention when `Booking` is
implemented — an invariant MySQL cannot express declaratively.

**SQLite.** Fine for a single-writer local tool; not for a concurrently-accessed
service.

## Decision

PostgreSQL 15.

## Consequences

**Good.** Foreign keys, `CHECK` constraints, and unique indexes enforce the
domain's invariants at the only layer every writer must pass through. `NUMERIC`
gives exact decimal money. Functional indexes made case-insensitive uniqueness a
schema change rather than an application convention. A clear upgrade path exists
for the two known performance limits: `tsvector` + GIN for free-text search, and
exclusion constraints for booking overlap.

**Bad.** Requires a running server — Docker is a prerequisite for local
development and for the test suite, where an embedded database would need
nothing. Schema changes require migrations, which is discipline the document-store
alternative would not have imposed. A single instance is the scaling ceiling;
read replicas and connection pooling become necessary before the application
itself does.

**Neutral.** Committing to PostgreSQL specifically — rather than "some SQL
database" — is what makes [ADR-004](./ADR-004-testcontainers.md) follow: if the
dialect and constraint semantics are being relied on, the tests must run against
the real thing.
