# Architecture Decision Records

Short records of decisions that were genuinely contested — where a competent
engineer could reasonably have chosen otherwise, and where the reasoning is
worth preserving after the context is forgotten.

Deliberately **not** recorded: choices with no real alternative (using Spring
Boot in a Spring Boot project), or with an obvious answer (BCrypt for passwords).
An ADR set padded with non-decisions makes the real ones harder to find.

| ADR | Decision | Status |
|---|---|---|
| [001](./ADR-001-postgresql.md) | PostgreSQL as the relational database | Accepted |
| [002](./ADR-002-jwt-authentication.md) | Stateless JWT authentication | Accepted, with known limits |
| [003](./ADR-003-flyway-migrations.md) | Flyway migrations, `ddl-auto=validate` | Accepted |
| [004](./ADR-004-testcontainers.md) | Testcontainers over H2 for integration tests | Accepted |
| [005](./ADR-005-modular-monolith.md) | Single deployable, not microservices | Accepted |
| [006](./ADR-006-numeric-money.md) | `BigDecimal` / `NUMERIC` for money | Accepted |

Format: context, options considered, decision, consequences — including the bad
ones. An ADR that lists no downsides is marketing, not a record.
