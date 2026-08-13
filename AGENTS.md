# AGENTS.md — instructions for AI coding agents

Read this before changing anything in this repository.

These are the constraints that keep the codebase coherent. Most exist because
the thing they forbid **already happened here** and had to be undone — each rule
below cites the defect that motivated it, so it can be judged rather than
obeyed. The full history is in [`docs/ENGINEERING_AUDIT.md`](docs/ENGINEERING_AUDIT.md).

**The human author is accountable for everything merged.** An agent's job is to
make review easy: small changes, stated reasoning, tests that would fail without
the change, and explicit flags where it is unsure. Volume is not progress.

---

## Before you finish a change

1. Understand the domain behaviour you are touching. Read the surrounding code
   and the relevant `docs/` page first.
2. Preserve the architectural boundaries below.
3. Add or update tests. A behaviour change with no test change is incomplete.
4. Never weaken authentication or authorization to make something pass.
5. Never introduce a secret, default credential, or signing key.
6. Add a migration when the schema changes. Never edit an applied one.
7. Run `./mvnw verify` and report the actual result.
8. Explain any significant tradeoff in the commit message.
9. **Flag uncertainty instead of inventing behaviour.** "I could not determine
   X" is a useful answer. A plausible guess presented as fact is not.

---

## Architecture and boundaries

Layers: `controller → service → repository → model`, with `dto`, `security`,
`config`, and `exception` alongside. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

**Controllers must not touch repositories.** The transaction boundary is at the
service; reaching past it executes outside any transaction.

**Controllers must not contain business logic.** Bind, delegate, map, choose a
status code.

**Entities must never cross the HTTP boundary, in either direction.** Use
`dto/request` and `dto/response` records.
*Why:* `User` implements `UserDetails` and must expose `getPassword()`, so
returning it published every account's BCrypt hash. Accepting entities allowed
mass assignment — `PUT /api/users/{id}` bound the entity and saved it under the
path id with no ownership check.

**Services return DTOs, not entities.** `spring.jpa.open-in-view=false`, so the
persistence context closes at the service boundary. Anything the response needs
must be materialised before then. Mapping in a controller works until someone
touches a lazy collection, then fails at runtime.

**Do not enable `open-in-view`.** It would hide this class of bug rather than fix it.

**Repositories contain data access, not business rules.**

---

## Security — the non-negotiables

**Never weaken an authorization rule to make a test pass.** If a test fails on
authorization, the test or the caller is wrong until proven otherwise.

**Authorization belongs in the query, not after the load.**

```java
propertyRepository.findByIdAndOwner(id, caller)   // yes
Specification.allOf(scopedTo(caller), hasId(id))  // yes

Property p = repo.findById(id);                   // no
if (!p.getOwner().equals(caller)) throw ...;      // no
```
*Why:* a post-hoc check protects only the call sites that remember it. A scoped
query fails closed — a forgotten scope yields an empty result, not a leak.

**Return 404, never 403, for another user's resource.** 403 confirms the id
exists and enables enumeration. 403 is only for role failures.

**Scope writes too, not just reads.** Creating a transaction resolves its
property through the owner-scoped lookup. Without that, a user could write into
books they cannot read.

**Never add a default, fallback, or example value for `JWT_SECRET`.** The
application must refuse to start without it.
*Why:* a default signing key is a forgery oracle. This bit once already —
`${JWT_SECRET}` with the variable unset binds the *literal string* (Spring
ignores unresolvable placeholders in `@ConfigurationProperties`) and the app
started with that as its HMAC key. `JwtProperties` now rejects unresolved
placeholders; do not remove that check.

**Never log** tokens, passwords, `Authorization` headers, or attempted usernames
on failed sign-in. Do not set `org.springframework.web` to DEBUG in a shared
profile — it can log request headers.

**Never return `ex.getMessage()` to a client** for persistence or internal
exceptions. Log the detail with a correlation id; return the id.

**Roles are assigned server-side.** No request record may carry a `role` field.

**Keep the security config default-deny.** `anyRequest().authenticated()` stays
last. Adding a `permitAll()` requires a stated reason.

**If cookie-based auth is ever added, re-enable CSRF.** It is disabled because
the API is stateless and holds no ambient credential — not for convenience.

---

## Database and migrations

**Every schema change needs a new versioned migration** in
`src/main/resources/db/migration`. `ddl-auto=validate`; Hibernate never alters
the schema.

**Never edit an applied migration.** The checksum is the guarantee. Correct a
mistake with a new migration.

**Never switch to `ddl-auto=update`,** including "temporarily."

**A migration that could destroy data must refuse to run instead.** `V6` and
`V7` raise an exception when existing rows cannot be mapped, rather than
guessing an owner or discarding financial records. Follow that pattern and state
the consequence in a comment at the top.

**Money is `BigDecimal` over `NUMERIC(19,2)`.** Never `double` or `float`.
Compare with `compareTo`, not `equals` — scale differs.

**Enforce invariants in the database, not only in Java.** Foreign keys, `CHECK`
constraints, unique indexes. Application checks are advisory; they do not bind a
repair script or a second writer.

**Justify every index you add** — the query it supports, the column order, and
the write cost. Do not add one speculatively. Note that PostgreSQL does not
index foreign keys automatically.

**Collections are `LAZY`.** They were `EAGER`, making a listing `1 + 3N`
queries. If you need children in a response, either use a summary DTO that
omits them or fetch deliberately — and add a query-count assertion.

**Transaction boundaries are at the service method.** Class default
`@Transactional(readOnly = true)`; writes override.

**Update by mutating a managed entity,** not by `save()`-ing a detached object
built from the request — that writes nulls over every unsent field.

---

## Testing

**Two tiers, split by name.** `*Test` = unit, Surefire, no Spring, no Docker.
`*IT` = integration, Failsafe, real PostgreSQL via Testcontainers.

**Extend `AbstractIntegrationTest` for integration tests.** Do not create a
second container or a divergent context configuration — that forks the Spring
context and costs a full startup.

**Do not replace Testcontainers with H2.** See
[ADR-004](docs/adr/ADR-004-testcontainers.md). Several tests assert PostgreSQL
behaviour directly and would become meaningless.

**Do not mock the repository in a test whose purpose is data behaviour.** The
broken transaction search — a `Specification` built and then discarded — was
invisible to mocks, which return the stub either way.

**A bug fix needs a test that fails without the fix.**

**Do not write tests that pass for the wrong reason.** A real example from this
repository: a `CHECK`-constraint test inserted rows with non-existent foreign
keys, so once real FKs were added a *foreign-key* violation satisfied the same
`assertThrows`. It kept passing while testing nothing. Seed valid parent rows,
and add a positive control showing the same operation succeeds when it should.

**Clean up children before parents** — `resetDatabase()` exists for this;
`ON DELETE RESTRICT` will reject the wrong order.

**Never delete, skip, or `@Disabled` a failing test to get a green build.**
Report the failure.

**Do not pad the suite.** Tests for getters and generated code are noise. Test
logic, boundaries, and failure modes.

---

## Secrets and configuration

**Never commit a credential**, including in a comment, a test fixture, or an
example. This repository has already leaked a live database password —
[`docs/SECURITY.md`](docs/SECURITY.md) documents it.

**Configuration comes from the environment.** Defaults are permitted only when
plainly non-secret (`propflow`/`propflow` against a throwaway local container).
A default that could be mistaken for a real credential is not acceptable.

**`.env` is never tracked.** Update `.env.example` with placeholders when adding
a variable.

**`.gitignore` does not keep a file out of the Docker build context.** They are
separate mechanisms. Adding a local config or secret file means updating
**both** `.gitignore` and `.dockerignore`.

---

## Errors and API conventions

**All errors are RFC 7807 `ProblemDetail`.** No bare strings, no stack traces,
no SQL, no internal class names.

| Situation | Status |
|---|---|
| Created | `201` + `Location` |
| Deleted | `204` |
| Missing or not yours | `404` |
| Invalid field | `400` with per-field `errors` |
| Valid fields, invalid combination | `422` |
| Uniqueness or version conflict | `409` |
| No token | `401` |
| Wrong role | `403` |

**Do not add a broad `@ExceptionHandler(Exception.class)` ahead of specific
ones.** `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so
Spring MVC's own exceptions keep their correct statuses. An earlier catch-all
turned a 405 into a 500 and logged it as an unhandled bug.

**Collection endpoints are paginated,** returning `PagedResponse`, never
`PageImpl` and never an unbounded `List`.

**Whitelist any user-supplied sort field.** It becomes a Criteria attribute path.

---

## Documentation

**Never document a capability that does not exist.** The original README
advertised JWT authentication with no JWT library on the classpath, plus
endpoints that were never implemented. Accumulated false claims destroy trust in
everything else.

**Update the README when behaviour changes** — endpoints, status codes,
configuration, limitations.

**Keep the Known Limitations sections honest.** Removing a limitation requires
having actually fixed it.

**Never describe this project as production-ready.** It is not deployed and
serves no users. Do not add uptime, scale, performance, or usage claims.

**Do not invent benchmarks.** No performance numbers unless measured, with the
method stated.

---

## Backwards compatibility

There are no external consumers with a compatibility contract, so a breaking
change is permitted **when it fixes a real defect** — two endpoints were removed
outright for being insecure. But:

- Say so explicitly in the commit message, with the reason.
- Update the README in the same commit.
- Do not break something merely because a different shape is tidier.

---

## Commits

Conventional prefixes: `feat`, `fix`, `security`, `db`, `test`, `docs`, `build`,
`ci`, `chore`, `refactor`.

The body should explain **why**, not restate the diff. State the tradeoff,
what was verified, and how. Report test results accurately — never claim a
passing build without running it.

**Do not push to a remote** unless explicitly asked.

---

## When to stop and ask

- The change requires weakening a security control.
- The correct behaviour is genuinely ambiguous and the options differ materially.
- A fix needs a schema change that could lose data.
- A test fails and the cause is not understood. Do not delete it.
- The task implies adding significant infrastructure (a broker, a cache, a
  second datastore). See [ADR-005](docs/adr/ADR-005-modular-monolith.md); the
  bar is a measured need, not a plausible one.
