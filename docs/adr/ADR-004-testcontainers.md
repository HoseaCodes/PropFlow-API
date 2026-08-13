# ADR-004: Testcontainers over H2 for integration tests

**Status:** Accepted · **Date:** 2026-08

## Context

The project had one test: a `@SpringBootTest` `contextLoads()` that required a
live PostgreSQL with credentials hardcoded in `application.properties`. It
failed on a clean clone. There was no automated evidence that anything in the
application worked.

Whatever replaced it had to run from `git clone` with no manual setup, and had
to be trustworthy enough that a green run means something.

## Options considered

**H2 in PostgreSQL compatibility mode.** Fast, in-memory, no Docker. Rejected
because *compatibility mode is not compatibility*. H2 diverges from PostgreSQL
on type coercion, constraint and index semantics, sequence behaviour, `NUMERIC`
precision, upsert syntax, and JSON support. A test passing against H2 is not
evidence about the database this application deploys on — and the divergence
clusters exactly where this project's correctness lives.

Concretely, these tests would be meaningless or impossible under H2:

- `V3` creates functional unique indexes on `lower(email)`. H2's support differs,
  so the case-insensitive uniqueness test would prove nothing about production.
- `SchemaMigrationIT` asserts PostgreSQL `CHECK` constraint and `ON DELETE
  RESTRICT` behaviour by issuing raw SQL.
- The money test sums `NUMERIC` in the database and asserts `0.10 + 0.20 = 0.30`
  exactly — a claim about PostgreSQL's arithmetic.
- The index-column-order test reads `pg_indexes`.

**A shared CI database service container.** Real PostgreSQL, but the developer
experience diverges from CI, state leaks between runs, and parallel runs
conflict.

**A developer-managed local PostgreSQL.** The status quo. It is what made
`mvn test` fail on a clean clone.

## Decision

Testcontainers with `postgres:15-alpine`, matching the Compose and production
image. Wired via `@ServiceConnection`, so no test knows the randomly assigned
port.

The container is started in a **static initialiser**, not by the
`@Testcontainers` JUnit extension. The extension starts and stops a static
container per test *class*; started once per JVM, one container serves the whole
run and Ryuk reaps it at exit. Measured effect: the first integration class pays
~10s for container start plus context boot, and the next runs in **0.097s**,
reusing both.

Tests are split by naming — `*Test` under Surefire, `*IT` under Failsafe — so
`mvn test` stays Docker-free and sub-second while `mvn verify` is the full gate.

## Consequences

**Good.** `git clone && ./mvnw verify` passes with only a JDK and Docker;
verified against a clean clone with no database running. Flyway migrates a
virgin database every run, so migration correctness is continuously proven.
Tests can assert database-level behaviour — constraints, index definitions,
`NUMERIC` arithmetic — that no in-memory substitute could support. The same
mechanism runs locally and on the GitHub runner, with no CI-specific setup.

**Bad.** Docker becomes a hard prerequisite; a contributor without it cannot run
the integration suite. Integration tests take seconds rather than milliseconds,
and the first pays container startup. Docker image pulls make the first run on a
cold machine slower still. There is a real risk of over-reliance: because
integration tests are pleasant to write here, they can crowd out faster unit
tests for logic that needs no database — the split by naming is partly a guard
against that.

**Neutral.** Pinning `postgres:15-alpine` means test and production versions
must be bumped together, which is a small cost and the correct coupling.
