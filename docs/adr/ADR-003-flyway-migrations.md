# ADR-003: Flyway migrations with `ddl-auto=validate`

**Status:** Accepted · **Date:** 2026-08

## Context

Schema was managed by `spring.jpa.hibernate.ddl-auto=update`. Hibernate inferred
the schema from entity annotations at startup and altered the database to match.

The symptom that made this urgent: an untracked `seed.sql` in the repository
held hand-written `ALTER TABLE` and `CREATE INDEX` statements — schema changes
living outside version control, because no mechanism existed to hold them.

## Options considered

**Keep `ddl-auto=update`.** Zero effort. Rejected on four counts:

- **It only adds.** Never drops a column, narrows a type, or removes a
  constraint. Renaming a field silently creates a new column and orphans the old
  one, with its data.
- **It is not reviewable.** No artifact appears in a pull request. The schema
  change is discovered by diffing a running database.
- **It is not reproducible.** The resulting schema depends on the *sequence of
  versions a database has seen*, not on current code, so two environments with
  different deploy histories legitimately diverge.
- **It cannot express data migration.** Splitting `name` into `firstName`/
  `lastName` needs backfill logic that annotations cannot describe.

**Liquibase.** Equivalent in capability. Flyway chosen for plain SQL: the
migration is the exact statement PostgreSQL will run, which matters when using
dialect-specific features (functional indexes, `USING` clauses on type changes)
that an abstraction layer obscures. Liquibase's database-agnostic changelogs buy
portability this project does not need, having already committed to PostgreSQL.

**`ddl-auto=none` with manually applied SQL.** Reviewable, but nothing records
what has been applied where.

## Decision

Flyway for schema, `ddl-auto=validate` for verification. Hibernate never
modifies the schema.

The baseline `V1` was hand-authored from the DDL Hibernate actually generated,
rather than copied from it — auto-generated constraint names like
`fkc9d3qi2jq9yls5ob3xq86d1yg` cannot be referenced by a later migration or
understood in an error message.

`V1` deliberately **preserves** the model's known defects (`DOUBLE PRECISION`
money, missing foreign keys), each annotated with its audit finding, so the
corrections land as real reviewable migrations instead of being hidden in the
baseline.

`baseline-on-migrate` is off: on a non-empty schema with no history table,
Flyway should fail loudly rather than assume the existing schema matches `V1`.

## Consequences

**Good.** Schema changes are reviewed like code. `validate` turns entity/schema
drift into a startup failure naming the exact missing column, rather than a
runtime error much later. Every test run applies all migrations to a virgin
database, so "the migrations work from nothing" is continuously proven.
Migrations can carry data-safety guards — `V6` and `V7` refuse to run if
existing rows cannot be mapped, rather than guessing an owner or discarding
financial records.

**Bad.** Every schema change now needs a migration file, including in
development, which is friction that `ddl-auto=update` did not impose. An applied
migration must never be edited — the checksum is the guarantee — so a mistake is
corrected by a new migration, not a fix to the old one. Developers with an
existing `ddl-auto` database must recreate it.

**Neutral.** Startup is marginally slower, which is invisible next to JVM start.
