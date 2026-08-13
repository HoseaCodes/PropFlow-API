---
search:
  exclude: true
---
# PropFlow API — Engineering Audit

> ## ⚠️ HISTORICAL DOCUMENT
>
> **This audit describes the repository as it was at commit `f771d7d`, before
> remediation. It is not a description of the current state.** It is kept
> unedited because the findings, and the git history of fixing them, are part of
> what this repository is meant to demonstrate.
>
> The classification below — **prototype** — was accurate then and is not now.
>
> | Then (`f771d7d`) | Now |
> |---|---|
> | Entire API anonymously reachable | JWT authentication, default-deny, row-level ownership |
> | No JWT despite the README claiming it | Implemented, 13 unit tests on signing and verification |
> | Live DB password committed | Rotated; all config from the environment |
> | Plaintext passwords via `POST /api/users` | Single BCrypt registration path; endpoint removed |
> | BCrypt hashes in API responses | Response DTOs with no password component |
> | `mvn test` failed on a clean clone | `mvn verify` passes: 178 tests, Testcontainers |
> | `ddl-auto=update`, no migrations | 7 Flyway migrations, `ddl-auto=validate` |
> | Search silently ignored every filter | Fixed, with regression tests |
> | `DOUBLE PRECISION` money | `NUMERIC(19,2)` |
> | No foreign keys on transactions | Real FKs with `ON DELETE RESTRICT` + composite indexes |
> | No CI, no health endpoint, broken Swagger | GitHub Actions, Actuator probes, working OpenAPI |
>
> Current state: [`README.md`](https://github.com/HoseaCodes/PropFlow-API#readme) ·
> Remediation plan: [`PORTFOLIO_HARDENING_PLAN.md`](./PORTFOLIO_HARDENING_PLAN.md) ·
> Remaining gaps: [`SECURITY.md`](./SECURITY.md)

**Audit date:** 2026-08-07
**Commit audited:** `f771d7d` (`master`)
**Auditor perspective:** Senior/Staff backend engineer reviewing the repository as a hiring signal for a Senior Full-Stack Product Engineer role.
**Scope:** Entire repository — build, packaging, security, API design, persistence, configuration, containerization, tests, and documentation.

This audit is **read-only**. No application code was modified. Every finding below was verified against the source at the commit named above; each cites the file and line involved. Where a claim could be verified by executing the build, it was.

---

## Executive Summary

### Classification: **Prototype**

Not "portfolio application," and specifically not "production-oriented." I want to be direct about this, because the gap between the repository's self-description and its actual behavior is currently the single biggest risk to you.

The project has the *shape* of a layered Spring Boot service — there is a controller package, a service package, a repository package, a model package, a `SecurityConfig`, a `GlobalExceptionHandler`. Someone skimming the file tree would read that as competent structure. But the load-bearing behaviors that those names imply are largely absent or inert:

- **The API has no authentication.** `SecurityConfig` permits `/api/**` unauthenticated, and every controller in the application is mounted under `/api`. There is no endpoint in this application that requires a credential.
- **There is no JWT implementation.** The README advertises "JWT Authentication." There is no JWT library on the classpath — I verified this against the resolved dependency tree. No token is minted, signed, parsed, or validated anywhere in the codebase.
- **A live database password is committed to the repository** and is present across seven commits of history in a public GitHub repo.
- **`mvn test` fails on a clean clone.** I ran it. The single test is a `@SpringBootTest` that needs a real PostgreSQL on `localhost:5432` with hardcoded credentials.
- **A fresh clone cannot build at all**, because `.gitignore` excludes `mvnw`, `mvnw.cmd`, and `.mvn/`. The Maven wrapper the README tells you to run is not in the repository.
- **The transaction search feature does not filter.** A 90-line JPA `Specification` is constructed and then discarded; the repository call ignores it entirely.

The honest framing: this is a working prototype that was iterated toward a deploy target (Heroku, then Render, judging by commit history) and picked up configuration debt along the way. That is an extremely normal way for a side project to end up. It is not a criticism of your ability. But it is *not yet* evidence of the production judgment the target role screens for, and several findings would actively count against you if a senior engineer opened the repository cold.

### The good news

The remediation path is unusually favorable. The domain model is genuinely interesting — property management with income/expense transactions, tax deductibility, warranties, refunds, and recurring-charge frequency is a **richer, more defensible domain than the CRUD apps most candidates submit**. `TransactionCategory` (`TransactionCategory.java:44-66`) already encodes real business rules about which categories are valid for which transaction type. That is a domain invariant worth building around, and it gives us authentic material for validation logic, integration tests, and interview conversation.

The work ahead is mostly *replacing inert scaffolding with real implementations* rather than redesigning the application. The layering is already correct in outline. We are filling it in, not tearing it down.

### What changes the classification

Fixing the CRITICAL and HIGH findings moves this to a credible **production-oriented portfolio application** — a project that demonstrably understands authentication, migrations, testing, and operational concerns, while being honest that it is not carrying production traffic. That is the correct target, and it is achievable. We should not claim "production-ready," and I will not write that phrase into any document in this repository.

---

## Strengths

These are real and worth preserving. I list them first because the risk section is long, and it would be easy to lose sight of what already works.

**1. Correct layered package structure.**
`controller` → `service` → `repository` → `model` with no inversions. Controllers do not touch repositories directly. This is the right skeleton, and a surprising number of candidate projects get it wrong.

**2. A domain model with actual business semantics.**
`TransactionCategory` (`TransactionCategory.java`) is not an enum of strings — it carries `displayName`, classifies itself as income vs. expense, and exposes `isValidForType(type, category)` to validate the pairing. `TaxDetails`, `RefundInfo`, and `Warranty` are modeled as `@Embeddable` value objects rather than being flattened into the parent table or over-normalized into separate entities. That is a deliberate, defensible modeling choice and exactly the kind of thing to discuss in an interview.

**3. `spring.jpa.open-in-view=false` is explicitly set.**
`application.properties:18`. Most Spring Boot projects leave the default (`true`) in place and never learn what it does. Disabling it prevents the persistence context from being held open across view rendering, which surfaces lazy-loading bugs at the service boundary where they belong instead of hiding them behind an open session. Setting this deliberately is a genuine signal of JPA understanding.

**4. Passwords are BCrypt-hashed on the primary signup path.**
`SecurityConfig.java:42-45` registers a `BCryptPasswordEncoder`, and `AuthController.saveUser` (`AuthController.java:65`) encodes before persisting. The mechanism is correct — the problem (finding **C3**) is that a second, unprotected user-creation path bypasses it.

**5. Optimistic locking is present on `User`.**
`User.java:33-35` uses `@Version`. Concurrency control is not something most portfolio projects consider at all. It is applied inconsistently (finding **M13**), but the awareness is there.

**6. Explicit transaction isolation level.**
`application.properties:17` sets `READ_COMMITTED`. Again — deliberate, and most projects never touch it.

**7. Reasonable `.gitignore` hygiene for secrets, and it worked.**
`.env`, `*.pem`, `*.key`, `*.sql`, and `application-*.properties` are all ignored. I verified that `.env` and the local `seed.sql` (which contains the same database password) are **untracked and absent from git history**. That discipline prevented a much worse leak. The gap is that the *main* `application.properties` was excluded from that pattern.

---

## Weaknesses

Grouped by what a senior reviewer would notice, in the order they would notice it.

### Within 60 seconds of opening the repo
- README advertises JWT authentication, `/bookings` endpoints, and `/expenses` endpoints. None exist.
- README states Spring Boot 3.2.0; `pom.xml:8` says 3.4.0.
- README links `LICENSE.md`; the file does not exist.
- README lists a support email (`support@PropFlow.api`) and a Slack channel for a solo portfolio project.
- `git clone && ./mvnw test` fails twice over — no wrapper, then no database.

### Within 5 minutes
- `SecurityConfig` permits the entire API surface unauthenticated.
- A database password sits in plain sight in `application.properties`.
- `logging.level.root=DEBUG` — every dependency logging at DEBUG, in the file that ships.
- No DTOs. JPA entities are the wire format, and `User` (which implements `UserDetails` and exposes `getPassword()`) is returned directly from two endpoints.
- Zero validation annotations in the entire codebase.

### On closer reading
- `GlobalExceptionHandler` handles exactly one exception type and uses `System.out.println`.
- `searchTransactions` builds a filter and throws it away.
- No migrations; `ddl-auto=update` is the schema strategy.
- `Transaction` references properties and users by bare scalar columns with no foreign keys, and `userId` is a `String` while `User.id` is a `Long`.
- Money is `Double` on `Transaction.amount` but `BigDecimal` on `Property.basePrice`.
- Four dead entities (`Booking`, `Expense`, `CleaningChecklist`) with no repository, service, or controller.
- `springdoc-openapi-ui:1.7.0` is a Spring Boot 2.x artifact on a Spring Boot 3.4 application — the OpenAPI UI cannot work.

---

## Risk Register

Severity definitions used here:

| Severity | Meaning |
|---|---|
| **CRITICAL** | Exploitable security flaw, credential exposure, or a claim in the README that is factually contradicted by the code. Would end a technical review. |
| **HIGH** | A senior reviewer will notice it, and it will materially lower their assessment. Includes broken core features and "cannot run this" problems. |
| **MEDIUM** | Correctness, performance, or design weakness that invites hard questions you currently cannot answer well. |
| **LOW** | Polish, consistency, and hygiene. Cheap to fix; cumulatively affects the impression of care. |

**Complexity** is my estimate of implementation effort: **S** ≈ under an hour, **M** ≈ a few hours, **L** ≈ a day or more.

### Summary table

| ID | Severity | Issue | Complexity | Portfolio value |
|---|---|---|---|---|
| C1 | CRITICAL | Entire API is unauthenticated | M | Very high |
| C2 | CRITICAL | Database password committed to public git history | S + rotation | Very high |
| C3 | CRITICAL | Plaintext password storage via `POST /api/users` | S | Very high |
| C4 | CRITICAL | README claims JWT auth; no JWT exists anywhere | L | Very high |
| C5 | CRITICAL | Password hash serialized in API responses | M | Very high |
| H1 | HIGH | Mass assignment + IDOR on user endpoints | M | High |
| H2 | HIGH | Transaction search silently ignores all filters | S | High |
| H3 | HIGH | `mvn test` fails on clean clone; no wrapper committed | S | High |
| H4 | HIGH | No database migrations (`ddl-auto=update`) | M | Very high |
| H5 | HIGH | No input validation anywhere | M | High |
| H6 | HIGH | JPA entities used as API contract | L | High |
| H7 | HIGH | Exception handling is a stub; leaks internals | M | High |
| H8 | HIGH | Root-level DEBUG logging + SQL logging | S | Medium |
| H9 | HIGH | OpenAPI dependency incompatible with Spring Boot 3 | S | Medium |
| H10 | HIGH | No referential integrity between transactions and properties/users | M | High |
| H11 | HIGH | Docker healthcheck references a nonexistent endpoint | S | Medium |
| M1 | MEDIUM | Three EAGER `@ElementCollection`s → N+1 | M | High |
| M2 | MEDIUM | No indexes on foreign-key or date columns | S | Very high |
| M3 | MEDIUM | Four conflicting CORS configurations | S | Medium |
| M4 | MEDIUM | Money stored as `Double` | M | High |
| M5 | MEDIUM | Incorrect HTTP status codes throughout | S | Medium |
| M6 | MEDIUM | Not-found modeled as `RuntimeException` → HTTP 500 | S | Medium |
| M7 | MEDIUM | No pagination on collection endpoints | S | Medium |
| M8 | MEDIUM | Missing/incorrect transaction boundaries | S | High |
| M9 | MEDIUM | `updateTransaction` full-replace destroys audit fields | S | High |
| M10 | MEDIUM | `pom.xml` dependency hygiene problems | S | Low |
| M11 | MEDIUM | Dead entities; README advertises their endpoints | S | Medium |
| M12 | MEDIUM | `java.util.Date` instead of `java.time` | M | Low |
| M13 | MEDIUM | Optimistic locking applied inconsistently | S | Medium |
| L1 | LOW | Multiple unsupported README claims | S | High |
| L2 | LOW | `com.airbnb` package name implies Airbnb, Inc. affiliation | M | Medium |
| L3 | LOW | Stale deploy tooling (Heroku, personal Docker Hub push) | S | Low |
| L4 | LOW | Broken `.vscode/launch.json` main class | S | Low |
| L5 | LOW | Inconsistent dependency injection style | S | Low |
| L6 | LOW | No `.env.example` | S | Medium |
| L7 | LOW | `adminer` container port mapping is wrong | S | Low |

---

## CRITICAL Findings

### C1 — The entire API is unauthenticated

**Issue.**
`SecurityConfig.java:26-29`:

```java
.authorizeRequests(authz -> authz
    .requestMatchers("/api/**").permitAll()
    .anyRequest().authenticated()
);
```

Every controller in this application is mounted under `/api`: `/api/auth`, `/api/users`, `/api/properties`, `/api/transactions`. The `permitAll()` rule matches all of them. The `anyRequest().authenticated()` clause that follows protects only paths that no controller serves.

The practical consequence is that an anonymous caller can:
- `GET /api/users` — enumerate every user account, including their BCrypt password hashes and email addresses (see **C5**)
- `POST /api/users` — create an account with a plaintext password (see **C3**)
- `PUT /api/users/{id}` — overwrite any user's credentials (see **H1**)
- `DELETE /api/users/{id}` — delete any account
- Full CRUD on every property and every financial transaction belonging to every user

**Why it matters.**
This is the finding that ends the review. The role explicitly values Spring Security and authorization; a `SecurityConfig` that disables security across the whole application is the strongest possible counter-signal. It also means the project has *no* authorization model to discuss — there is no ownership check anywhere, so any authenticated user (if there were authentication) could still read and modify every other user's financial data.

There is a secondary tell: `authorizeRequests` and the `.and()`-chained builder style are deprecated in Spring Security 6. An interviewer who knows the framework will read this as configuration copied from a Spring Security 5 tutorial without understanding what it does.

**Files.** `src/main/java/com/airbnb/config/SecurityConfig.java:22-31`

**Recommended solution.**
Rewrite the filter chain using the Spring Security 6 lambda DSL. Permit only what genuinely must be public — `POST /api/auth/**`, the OpenAPI endpoints, and the actuator health endpoint — and require authentication for everything else. Set the session policy to `STATELESS` once token authentication exists (**C4**). Then add a real authorization layer: every transaction and property must be owned by a user, and reads/writes must be scoped to the authenticated principal. That ownership check is the difference between "I configured Spring Security" and "I understand authorization," and it is worth doing carefully.

**Complexity.** M (the filter chain is small; the ownership model is the real work — see **H10**)

**Materially improves portfolio?** **Yes — this is the highest-value fix in the repository.**

---

### C2 — Live database password committed to public git history

**Issue.**
`application.properties:13` assigns `spring.datasource.password` a literal, non-placeholder credential. (The value is deliberately not reproduced in this document — it is already public in the file and in history, and republishing it here would only widen the exposure.)

This file is tracked. I confirmed the credential is present in `HEAD` and that the file has been modified across seven commits (`8610b81`, `f1152d8`, `4fda225`, `4f336d4`, `f67c9d2`, `bd09c67`, `db0a147`). The same password also appears in the local `seed.sql` as the `CREATE ROLE` password — that file is untracked and *not* in history, which is fortunate, but it confirms the credential is real rather than a placeholder.

The repository is public: `https://github.com/HoseaCodes/PropFlow-API.git`.

**Why it matters.**
Two separate problems. First, the operational one: a real credential is public and must be treated as compromised. Second, and for your purposes more important: **a reviewer evaluating you for security awareness will find this in under two minutes.** Committed credentials are one of the first things experienced engineers grep for. Finding one immediately reframes every other security claim in the repository.

Removing it from `HEAD` is necessary but not sufficient — it remains in history and in any fork or clone.

**Files.** `src/main/resources/application.properties:13`; git history for that path.

**Recommended solution.**
1. **Rotate the credential first.** Change the password on the actual PostgreSQL role before touching the repository. Anything already public should be assumed captured.
2. Replace the literal with an environment-variable reference and a development-safe default that is obviously not a secret, e.g. `${DB_PASSWORD:postgres}` pointed at a local Docker Compose database.
3. Add `.env.example` with placeholders only (**L6**).
4. Decide on history: rewriting with `git filter-repo` is the thorough option but breaks existing clones and forks. Given that the credential will be rotated and this is a portfolio repository, my recommendation is to rotate, fix forward, and — optionally — note in `docs/SECURITY.md` that the credential was rotated. Being able to explain that tradeoff out loud is itself a good interview answer.

**Complexity.** S to fix forward; rotation is an external action you must perform.

**Materially improves portfolio?** **Yes — and it must be done before you send this link to anyone.**

---

### C3 — `POST /api/users` stores passwords in plaintext

**Issue.**
There are two paths that create users, and they behave differently.

`AuthController.saveUser` (`AuthController.java:54-77`) does the right thing — it checks for a duplicate email, encodes the password, and saves.

`UserController.createUser` (`UserController.java:34-38`) delegates to `UserService.createUser` (`UserService.java:23-25`):

```java
public User createUser(User user) {
    return userRepository.save(user);
}
```

No encoding. The raw password string from the request body is persisted directly to the `password` column. Because of **C1**, this endpoint is publicly reachable.

`UserService.updateUser` (`UserService.java:38-44`) has the same defect on the update path.

**Why it matters.**
Plaintext credential storage is a textbook OWASP failure, and its presence *alongside* a correct BCrypt implementation is arguably worse than its presence alone: it shows the mechanism was understood but not applied consistently. It also corrupts authentication — a user created via `POST /api/users` can never log in, because `AuthenticationManager` will compare the submitted password against a BCrypt hash that isn't one.

This is the architectural lesson worth internalizing: **when a security control lives in a controller instead of in the domain, a second entry point will eventually bypass it.** Password encoding belongs in one place that every write path must traverse.

**Files.** `src/main/java/com/airbnb/service/UserService.java:23-25`, `:38-44`; `src/main/java/com/airbnb/controller/UserController.java:34-38`, `:56-61`

**Recommended solution.**
Consolidate registration into a single `UserService.register(...)` method that owns encoding and uniqueness checking, and have `AuthController` call it. Remove `POST /api/users` and `PUT /api/users/{id}` as general-purpose entry points, or restrict them to an administrative role with an explicit password-change flow that requires the current password. Add a unit test asserting that a persisted user's password is not equal to the submitted plaintext and that it verifies against the encoder.

**Complexity.** S

**Materially improves portfolio?** **Yes.**

---

### C4 — README claims JWT authentication; no JWT implementation exists

**Issue.**
`README.md:41` lists "JWT Authentication" under the tech stack. `README.md:98-100` documents `JWT_SECRET` and `JWT_EXPIRATION` environment variables.

I resolved the full dependency tree. **There is no JWT library on the classpath** — no `io.jsonwebtoken:jjwt`, no `com.nimbusds:nimbus-jose-jwt`, no `spring-boot-starter-oauth2-resource-server`. There is no filter, no token provider, no token parser, and no `jwt` reference in any Java source file. The only traces are commented-out properties (`application.properties:19-20`).

What actually happens on `POST /api/auth/signin` (`AuthController.java:41-52`):

```java
Authentication authentication = authenticationManager.authenticate(...);
SecurityContextHolder.getContext().setAuthentication(authentication);
return ResponseEntity.ok("User signed in successfully");
```

The credentials are genuinely verified — that part works. But the resulting `Authentication` is written into the `SecurityContextHolder`, which is **thread-local and cleared at the end of the request**. No `SecurityContextRepository` persists it, no session cookie is established as a deliberate mechanism, and no token is returned to the caller. The client receives a plain string with nothing to present on the next request. The authentication result is discarded microseconds after it is computed.

**Why it matters.**
This is the most damaging finding for your candidacy, above even **C1**, because it is not a bug — it is a **documented capability that does not exist**. A reviewer who checks it (and for a role that lists authentication as a core competency, they will) now has to question every other claim in the README. Credibility is the entire currency of a portfolio repository.

The role also specifically lists "AI-assisted engineering with strong human review." Shipping an unsupported capability claim reads as the failure mode of *unreviewed* AI assistance. Fixing this and being able to explain exactly what was wrong is a strong inversion of that signal.

**Files.** `README.md:41`, `:98-100`; `src/main/java/com/airbnb/controller/AuthController.java:41-52`; `pom.xml` (missing dependency); `src/main/resources/application.properties:19-20`

**Recommended solution.**
Implement it properly, since the role values it. Add a JWT library, mint a signed token on successful authentication, and add a `OncePerRequestFilter` that parses and validates the `Authorization: Bearer` header ahead of `UsernamePasswordAuthenticationFilter`. Sign with HMAC-SHA256 using a secret supplied via environment variable — with **no hardcoded fallback**; the application should refuse to start without it, because a default signing key is a forgery oracle. Set a short expiry, validate signature *and* expiry *and* subject on every request, and never log token contents.

Then document the honest limitations in `docs/SECURITY.md`: stateless JWTs cannot be revoked before expiry, which is the classic tradeoff and a question you will be asked.

**Complexity.** L (this is the single largest piece of implementation work identified)

**Materially improves portfolio?** **Yes — highest value after C1.**

---

### C5 — BCrypt password hashes are returned in API responses

**Issue.**
`User` implements `UserDetails`, which requires a public `getPassword()` (`User.java:52-55`). Jackson serializes public getters by default, so `password` appears in the JSON body of every response that returns a `User`. So do `accountNonExpired`, `accountNonLocked`, `credentialsNonExpired`, `enabled`, and `authorities` — the `UserDetails` surface leaks into the API contract.

Affected endpoints:
- `AuthController.java:72` — `POST /api/auth/signup` returns the saved `User`
- `UserController.java:42-45` — `GET /api/users` returns **every** user
- `UserController.java:49-53` — `GET /api/users/{id}`
- `UserController.java:57-61` — `PUT /api/users/{id}`

Combined with **C1**, an anonymous request to `GET /api/users` dumps the full credential table.

**Why it matters.**
Exposed hashes enable offline brute-force attacks with no rate limiting and no detection. BCrypt is deliberately slow, which helps, but strong hashing is a mitigation for breach — not a license to publish the hashes.

The deeper lesson is the one that matters for the interview: **this is what happens when a persistence entity is used as an API contract.** `User` has three jobs here — database mapping, Spring Security principal, and JSON response body — and the security-principal job requires exposing a getter that the response-body job must never expose. Those requirements are in direct conflict, and there is no way to satisfy both with one class. This finding is the concrete argument for **H6**.

**Files.** `src/main/java/com/airbnb/model/User.java:52-55`; `src/main/java/com/airbnb/controller/AuthController.java:72`; `src/main/java/com/airbnb/controller/UserController.java:42-61`

**Recommended solution.**
Introduce a `UserResponse` DTO exposing only `id`, `email`, `firstName`, `lastName`, `username`. Never return the entity. As defense in depth, annotate the field `@JsonIgnore` / `@JsonProperty(access = WRITE_ONLY)` so that a future careless endpoint cannot leak it either — but the DTO is the real fix; the annotation is the seatbelt. Add an API test asserting the response body contains no `password` key.

**Complexity.** M (part of the broader DTO work in **H6**)

**Materially improves portfolio?** **Yes.**

---

## HIGH Findings

### H1 — Mass assignment and IDOR on user endpoints

**Issue.** `UserService.updateUser` (`UserService.java:38-44`) binds the request body straight onto a `User` and saves it:

```java
if (userRepository.existsById(id)) {
    user.setId(id);
    return userRepository.save(user);
}
```

Two distinct flaws. **Mass assignment:** the caller controls every field, including `password`, `email`, `username`, and `version`. **IDOR (Insecure Direct Object Reference):** `id` comes from the path with no check that the caller owns that record — and per **C1** there is no caller identity at all. There is also a silent data-loss bug: `save()` on a detached entity with unset fields writes `null` over them, so a partial update wipes columns the client did not send.

**Why it matters.** Any anonymous request can take over any account by rewriting its credentials. Mass assignment is a well-known vulnerability class that DTOs exist specifically to prevent, so this reinforces **H6**.

**Files.** `src/main/java/com/airbnb/service/UserService.java:38-44`; `src/main/java/com/airbnb/controller/UserController.java:56-61`

**Solution.** Accept a narrow `UpdateUserRequest` DTO containing only mutable non-sensitive fields. Load the existing entity, copy permitted fields onto it, save within a transaction. Enforce that the path `id` matches the authenticated principal (or that the principal holds an admin role). Handle password changes through a separate endpoint requiring the current password.

**Complexity.** M · **Portfolio value:** High

---

### H2 — Transaction search silently ignores every filter

**Issue.** `TransactionService.searchTransactions` (`TransactionService.java:66-165`) builds a 78-line `Specification<Transaction>` covering date ranges, amount ranges, type, category, property, user, status, payment method, recurrence, vendor, tax/warranty/refund presence, approval status, overdue detection, and a multi-field search term.

Then, at line 164:

```java
return repository.findAll(pageable);
```

The variable `spec` is never referenced again. The root cause is at `TransactionRepository.java:13` — the interface extends `JpaRepository` but **not** `JpaSpecificationExecutor<Transaction>`, so the `findAll(spec, pageable)` overload does not exist. The code compiles because `findAll(Pageable)` is a valid alternative signature.

**Why it matters.** `POST /api/transactions/search` returns the first page of *all* transactions regardless of what the client sends. It looks like it works: the response is a well-formed `Page`, the pagination and sorting are real. It is silently, completely wrong. Combined with **C1**, it is also an unauthenticated dump of every transaction in the system.

For the interview this is a gift, because it is exactly the kind of bug integration tests catch and unit tests with mocked repositories do not — a mock would happily return the stubbed list either way. It is a concrete argument for **Phase 3**'s database-backed testing strategy.

**Files.** `src/main/java/com/airbnb/service/TransactionService.java:66-165`; `src/main/java/com/airbnb/repository/TransactionRepository.java:13`

**Solution.** Extend `JpaSpecificationExecutor<Transaction>`, call `repository.findAll(spec, pageable)`, and decompose the monolithic lambda into small composable `Specification` factory methods. Whitelist the `sortBy` field — an unvalidated sort property is both an error vector and an information leak. Add an integration test that seeds known rows and asserts the filters actually narrow the result set.

**Complexity.** S to fix, M to test properly · **Portfolio value:** High

---

### H3 — The project cannot be built or tested from a clean clone

**Issue.** Two independent blockers, both verified by execution.

*Missing Maven wrapper.* `.gitignore:132-134` ignores `mvnw.cmd`, `mvnw`, and `.mvn`. I confirmed via `git ls-files` that none are tracked. The README instructs `./mvnw clean package` (`README.md:137`) — a command that cannot run on a fresh clone.

*Tests require a live database.* I ran `./mvnw test`. Result:

```
[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed for user "airbnb"
[INFO] BUILD FAILURE
```

The only test is `@SpringBootTest` `contextLoads()`, which starts the full application context and therefore needs PostgreSQL on `localhost:5432` with the committed credentials.

**Why it matters.** A reviewer's first action is to clone and build. Right now that fails twice. It also means no CI pipeline can be added until this is resolved (**Phase 10**), and the one existing test proves nothing about behavior — it only asserts that Spring can wire the beans, and it cannot even do that without a database.

**Files.** `.gitignore:132-134`; `src/test/java/com/airbnb/AirbnbPropertyManagementApplicationTests.java`

**Solution.** Remove the wrapper entries from `.gitignore` and commit `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/`. Then make the test suite self-sufficient with Testcontainers, which starts a real PostgreSQL in Docker per test run. This is worth doing over H2 specifically because H2 does not reproduce PostgreSQL's actual behavior around types, constraints, sequences, and SQL dialect — testing against a database you do not deploy on gives false confidence. That is a strong, defensible answer to a question you will be asked.

**Complexity.** S for the wrapper, M for Testcontainers · **Portfolio value:** High

---

### H4 — No database migrations; schema is managed by `ddl-auto=update`

**Issue.** `application.properties:14` sets `spring.jpa.hibernate.ddl-auto=update`. There is no Flyway or Liquibase dependency. Schema is derived from entity annotations at startup.

**Why it matters.** `ddl-auto=update` is a development convenience that is genuinely dangerous beyond it:

- **It only adds.** It never drops a column, never narrows a type, never removes a constraint. Renaming a field silently creates a new column and orphans the old one with its data.
- **It is not reviewable.** There is no artifact in the pull request showing what will change. You discover the schema change by diffing the running database.
- **It is not reproducible.** The resulting schema depends on the *sequence* of versions the database has seen, not on the current code. Two environments that ran different deploy histories can legitimately diverge.
- **It cannot express data migration.** Splitting `name` into `firstName`/`lastName` requires backfill logic that annotations cannot express.

There is already evidence of this friction in the repository: the untracked `seed.sql` contains hand-written `ALTER TABLE users ADD COLUMN version` and `CREATE INDEX` statements — schema changes that live outside version control because there was no migration mechanism to hold them.

**Files.** `src/main/resources/application.properties:14`; `pom.xml`

**Solution.** Adopt Flyway. Generate a baseline `V1__initial_schema.sql` from the current entity model, then add incremental versioned migrations for indexes (**M2**), foreign keys (**H10**), and constraints. Set `ddl-auto=validate` so Hibernate verifies that entities match the migrated schema and fails fast on drift — this catches entity/schema mismatches at startup rather than at the first query. Flyway runs automatically on boot, so the developer experience stays "start Postgres, start app."

**Complexity.** M · **Portfolio value:** **Very high** — migration discipline is explicitly named in the role requirements, and `ddl-auto=update` in a repository claiming production awareness is a contradiction a reviewer will notice.

---

### H5 — No input validation anywhere in the application

**Issue.** `spring-boot-starter-validation` is on the classpath (`pom.xml:91-94`) and never used. There is not a single `@Valid`, `@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Positive`, or `@Pattern` annotation in the codebase. Every `@RequestBody` is bound unvalidated.

The nearest thing to validation is `TransactionService.validateTransaction` (`TransactionService.java:167-180`), which hand-checks four fields and throws `IllegalArgumentException` — which no handler catches, so it surfaces as HTTP 500 rather than 400.

Concrete consequences:
- `POST /api/auth/signup` accepts an empty username, an empty password, and a malformed email
- `POST /api/properties` accepts a negative `basePrice`, zero `maxGuests`, and a null `name`
- `POST /api/transactions` accepts any `description` length despite the column being bounded
- **The `TransactionCategory.isValidForType` business rule (`TransactionCategory.java:64-67`) is never called** — an `INCOME` transaction can be created with category `MORTGAGE`, and nothing objects

**Why it matters.** The role names "API validation and error handling" explicitly. Beyond that, invalid data reaching the persistence layer produces constraint-violation stack traces surfaced as 500s (**H7**), which both leaks schema details and misrepresents a client error as a server error.

The last bullet is the most interesting one: the domain already *has* a validity rule, written correctly, and nothing enforces it. That is a great thing to fix, because it demonstrates connecting domain invariants to the API boundary rather than just sprinkling annotations.

**Files.** All controllers; all `@RequestBody` types; `TransactionService.java:167-180`; `TransactionCategory.java:64-67`

**Solution.** Put Jakarta Bean Validation constraints on request DTOs (**H6**), add `@Valid` to every `@RequestBody`, and handle `MethodArgumentNotValidException` centrally (**H7**) to return a structured 400 with per-field messages. Enforce cross-field rules like `isValidForType` as a custom class-level constraint or an explicit service-layer domain check that throws a typed exception — and unit-test the boundary cases.

**Complexity.** M · **Portfolio value:** High

---

### H6 — JPA entities are used directly as the API contract

**Issue.** No DTO layer exists. Entities are accepted as request bodies and returned as response bodies throughout: `Property` (`PropertyController.java:37,43`), `Transaction` (`TransactionController.java:44,51`), `User` (`AuthController.java:55`, `UserController.java:35,57`). `SignInRequest` is the only request-shaped class, and it lives in the `model` package alongside entities.

**Why it matters.** This single decision is the root cause of several findings above, which is what makes it worth fixing properly rather than patching symptoms:

- **C5** (hash leakage) — the entity must expose `getPassword()` for Spring Security, so the API exposes it too
- **H1** (mass assignment) — the entity has no notion of which fields a client may set
- **H5** (validation) — persistence constraints and API constraints are genuinely different concerns; `@Column(nullable=false)` is a schema rule, `@NotBlank` is a request rule, and conflating them means you cannot have one without the other

There is also a coupling problem: the database schema becomes the public API. Renaming a column is a breaking API change. Adding an internal audit field publishes it. The entity cannot evolve independently of its consumers.

**Files.** All controllers; `src/main/java/com/airbnb/model/`

**Solution.** Introduce `dto/request` and `dto/response` packages. Java `record` types are ideal — immutable, concise, and they make the read-only nature of a response obvious. Map explicitly in the service layer; I'd avoid MapStruct or ModelMapper here, because hand-written mapping is trivial at this size and keeps the "what crosses the boundary" decision visible in code a reviewer can read. Deliberately omitting a field is the point.

**Complexity.** L (touches every endpoint) · **Portfolio value:** High — this is the "entity/API boundary" judgment the role screens for.

---

### H7 — Exception handling is a stub that leaks internals

**Issue.** `GlobalExceptionHandler` (`GlobalExceptionHandler.java`) is 25 lines and handles exactly one type — `IOException`:

```java
System.out.println("An error occurred: " + ex);
return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("An error occurred: " + ex.getMessage());
```

Problems, in order of severity:
- **`System.out.println` in a Spring application.** Bypasses SLF4J entirely — no level, no timestamp, no logger name, no correlation, invisible to any log aggregator.
- **`ex.getMessage()` returned to the client.** For persistence exceptions this includes SQL fragments, table names, column names, and constraint names.
- **Everything else is unhandled.** `IllegalArgumentException` from `validateTransaction`, `RuntimeException("Property not found")` from `PropertyService.java:20`, `DataIntegrityViolationException` from unique-constraint collisions, `MethodArgumentNotValidException`, `HttpMessageNotReadableException` from malformed JSON — all fall through to Spring's default handler and become HTTP 500 with a generic body.
- **Error responses are bare strings**, not JSON. A client that parses `application/json` on success gets `text/plain` on failure.

`AuthController.java:73-76` compounds this with a catch-all that returns `"Signup failed: " + e.getMessage()` — this will echo raw PostgreSQL constraint-violation text to unauthenticated callers.

**Why it matters.** "Production-minded error handling" is a named requirement. Error responses are part of the API contract, and inconsistent, unparseable, information-leaking errors are one of the clearest maturity signals a reviewer reads.

**Files.** `src/main/java/com/airbnb/exception/GlobalExceptionHandler.java`; `src/main/java/com/airbnb/controller/AuthController.java:73-76`

**Solution.** Build a consistent error model (`timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors` where applicable) — Spring Boot 3's `ProblemDetail` (RFC 7807) is the idiomatic choice and signals current framework knowledge. Add a typed exception hierarchy (`ResourceNotFoundException`, `DuplicateResourceException`, `BusinessRuleViolationException`) and map each to the right status. Log the full exception server-side with a generated correlation ID; return the ID to the client but never the stack trace or the raw message. That split — full detail in logs, safe summary to the caller — is exactly the production instinct worth demonstrating.

**Complexity.** M · **Portfolio value:** High

---

### H8 — Root-level DEBUG logging and SQL logging enabled

**Issue.** `application.properties:22-26`:

```properties
logging.level.com.airbnb=DEBUG
logging.level.root=DEBUG
logging.level.org.springframework=DEBUG
logging.level.org.springframework.security=DEBUG
logging.level.org.springframework.web=DEBUG
```

Plus `spring.jpa.show-sql=true` (line 15). The now-untracked `application-prod.properties` carried the identical block, so this was the production configuration too.

**Why it matters.**
- `logging.level.root=DEBUG` sets DEBUG for *every* library — Hibernate, Hikari, Tomcat, Jackson. Volume is enormous and the signal-to-noise ratio for real diagnostics is near zero.
- `org.springframework.security` at DEBUG logs authentication flow details, including principal names and filter-chain decisions.
- `org.springframework.web` at DEBUG can log request details including headers — which is where an `Authorization` header would appear once **C4** is implemented. This is a sensitive-data-in-logs risk waiting to activate.
- `show-sql=true` writes every statement to stdout, unformatted, with no timing — it is a development toggle, not observability. Real query diagnostics come from `p6spy`, Hibernate statistics, or `pg_stat_statements`.
- `PropertyController.java:38` logs the entire `Property` object on create. Harmless for `Property`; the same pattern applied to a request containing credentials would not be.

**Files.** `src/main/resources/application.properties:15,22-26`; `src/main/java/com/airbnb/controller/PropertyController.java:38`

**Solution.** Default root to `INFO` and the application package to `INFO`, with DEBUG enabled only under a `dev` profile. Disable `show-sql`. Once JWT exists, explicitly document and verify that tokens are never logged. Consider structured JSON logging for the container profile so that logs are machine-parseable — that ties directly into **Phase 8**.

**Complexity.** S · **Portfolio value:** Medium (but it prevents a sensitive-logging finding later, which is High)

---

### H9 — OpenAPI dependency is incompatible with Spring Boot 3

**Issue.** `pom.xml:62-66` declares `org.springdoc:springdoc-openapi-ui:1.7.0`. I confirmed via the dependency tree that it resolves and pulls `swagger-core:2.2.9`.

The springdoc **1.x** line targets Spring Boot 2.x and the `javax.*` servlet namespace. This application is Spring Boot **3.4.0** (`pom.xml:8`), which uses `jakarta.*`. Spring Boot 3 requires `springdoc-openapi-starter-webmvc-ui` **2.x**. The 1.x autoconfiguration will not activate against Boot 3, so `/swagger-ui.html` and `/v3/api-docs` do not serve.

**Why it matters.** The dependency is dead weight that looks like a feature. A reviewer who tries the Swagger UI finds nothing. It also suggests dependencies were added without verifying they work — and it is an unnecessary transitive-dependency surface.

**Files.** `pom.xml:62-66`

**Solution.** Replace with `springdoc-openapi-starter-webmvc-ui:2.7.0` (or the current 2.x). Verify `/swagger-ui.html` actually loads. Once JWT exists, add a security scheme definition so the UI can send a bearer token — an OpenAPI spec that documents auth and lets a reviewer exercise the API in-browser is a genuinely good first impression. Permit the OpenAPI paths in the security config (**C1**).

**Complexity.** S · **Portfolio value:** Medium

---

### H10 — No referential integrity between transactions and properties/users

**Issue.** `Transaction` references its related entities with bare scalar columns (`Transaction.java:24-31`):

```java
@Column(nullable = false)
private String userId;

@Column(nullable = false)
private Long propertyId;

@Column(nullable = false)
private String propertyName;
```

No `@ManyToOne`, no `@JoinColumn`, no foreign key. Three separate problems:

1. **No referential integrity.** A transaction can reference property `99999` that does not exist. Deleting a property orphans its transactions silently. The database cannot protect the invariant because no constraint expresses it.
2. **Type mismatch.** `userId` is a `String`; `User.id` is a `Long` (`User.java:24`). These cannot be joined without a cast, and nothing prevents `userId = "not-a-number"`.
3. **Denormalized `propertyName`.** Copied at write time and never reconciled. Rename a property and every historical transaction shows the stale name. (This *can* be a legitimate pattern — capturing a name at transaction time for audit purposes is defensible — but if that is the intent it needs a comment saying so. As written it reads as accidental duplication.)

Meanwhile `Booking`, `Expense`, and `CleaningChecklist` *do* use proper `@ManyToOne` associations, so the model is internally inconsistent about its own conventions.

**Why it matters.** "Relational data modeling" and "auditability" are named requirements. Without foreign keys, the database cannot enforce the domain's core invariant — that a transaction belongs to a real property owned by a real user. This is also the prerequisite for authorization (**C1**): you cannot scope a query to "transactions the caller owns" without a reliable ownership edge.

**Files.** `src/main/java/com/airbnb/model/transactions/Transaction.java:24-31`; `src/main/java/com/airbnb/repository/TransactionRepository.java`

**Solution.** Model the relationships as `@ManyToOne(fetch = FetchType.LAZY)` to `Property` and `User` with real `@JoinColumn` foreign keys, added via a Flyway migration (**H4**) that includes a data-cleanup step for any rows that would violate the new constraint. Choose and document the `ON DELETE` behavior deliberately — for financial records, `RESTRICT` (refuse to delete a property with transactions) is almost certainly correct, and explaining *why* you chose it over `CASCADE` is a strong interview answer. Keep `propertyName` only if it is an intentional point-in-time snapshot, and comment it as such.

**Complexity.** M · **Portfolio value:** High

---

### H11 — Docker healthcheck targets an endpoint that does not exist

**Issue.** `docker-compose.yml:29-34`:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
```

Three independent failures:
1. **Actuator is not a dependency.** I verified against the resolved tree — `spring-boot-starter-actuator` is absent. `/actuator/health` returns 404.
2. **Wrong port.** The app is pinned to 8081 by `Dockerfile:10` (`-Dserver.port=8081`) and the service maps `8081:8081`. The healthcheck probes 8080.
3. **`curl` is not installed** in `openjdk:17-jdk-slim`.

The container is therefore permanently `unhealthy`, and `restart: unless-stopped` combined with a failing healthcheck is a configuration that looks like resilience while providing none.

Related container issues in the same file: `adminer` maps `8082:8082` but Adminer listens on 8080 internally, so it is unreachable; `pgadmin` uses hardcoded `admin@admin.com`/`admin` credentials; the compose file passes `SPRING_DATASOURCE_*` variables that `application.properties` ignores, because lines 31-37 are commented out and line 11-13 hardcode localhost — **so the containerized app will try to reach PostgreSQL at `localhost:5432` inside its own container and fail to start.**

That last point deserves emphasis: `docker-compose up` does not currently produce a working application.

**Why it matters.** Docker and cloud-readiness are named requirements. A healthcheck that cannot pass is worse than no healthcheck — in an orchestrator it would cause an endless restart loop. And a compose file that does not start the app is the second thing a reviewer will try after `mvn test`.

**Files.** `docker-compose.yml:26,29-34,72-84`; `Dockerfile:9-11`; `src/main/resources/application.properties:11-13`

**Solution.** Add `spring-boot-starter-actuator`, expose `/actuator/health` with liveness and readiness groups, and point the healthcheck at the correct port. Use Spring Boot 3.4's built-in `HEALTHCHECK` support or install `curl` in the runtime image. Make the datasource read from environment variables so compose can inject the `db` hostname. Move to a non-root user in the Dockerfile, use a JRE rather than a full JDK base image, and add `.dockerignore` so the build context excludes `target/` and `.git/`. Then actually run `docker compose up` and verify.

**Complexity.** S–M · **Portfolio value:** Medium (High if the reviewer tries it, which they will)

---

## MEDIUM Findings

### M1 — Three EAGER `@ElementCollection`s cause N+1 queries
`Transaction.java:89,97,137` mark `tags`, `warranties`, and `metadata` as `FetchType.EAGER`. Each is a separate collection table. Loading N transactions issues 1 query for the transactions plus up to 3N queries for the collections. `GET /api/transactions` (`TransactionController.java:22`) loads the entire table with no pagination, so this compounds with **M7**. Two eager `List` collections on one entity also risks Hibernate's `MultipleBagFetchException` if anyone later adds a join fetch.
**Solution.** Switch to `FetchType.LAZY` (the correct default for collections), then use `@EntityGraph` or an explicit `join fetch` on the specific queries that genuinely need them. Verify with SQL-count assertions in an integration test — Hibernate's `Statistics` API makes "assert this endpoint issues ≤ 2 queries" a real, meaningful test, and that is a memorable thing to show an interviewer.
**Complexity:** M · **Portfolio value:** High (N+1 is a near-certain interview question)

### M2 — No indexes on foreign-key or date columns
The only indexes are the implicit unique constraints on `users.email` and `users.username` (`User.java:25,30`). No entity declares `@Index`. But the application's actual query patterns are clear from the repository interface (`TransactionRepository.java:14-16`): `findByUserId`, `findByPropertyId`, `findByUserIdAndPropertyId`, plus date-range and sorted-by-date queries.
Every one of those is currently a sequential scan. At small row counts PostgreSQL's planner correctly prefers a seq scan anyway, so nothing appears wrong — the degradation is gradual and only shows up under volume.
**Solution.** Add indexes via Flyway migration (**H4**), and **only** the ones matching observed access patterns:
- `transactions (user_id, date DESC)` — supports `findByUserId` and the default date-sorted listing. Column order matters: the equality predicate must come first so the index can seek, with `date` second to satisfy the sort without a separate sort step.
- `transactions (property_id, date DESC)` — same reasoning for `findByPropertyId`.
- `transactions (user_id, property_id)` is *not* needed separately; the first index's leading column already serves it, and PostgreSQL can filter the rest.
The tradeoff to state explicitly: each index adds storage and slows every `INSERT`/`UPDATE`/`DELETE` because the index must be maintained. For a read-heavy financial-reporting workload that trade is clearly worth it; for a write-heavy ingest table it might not be. Being able to articulate *why these two and not six* is the actual signal.
**Complexity:** S · **Portfolio value:** **Very high** — "which indexes exist and why" is a guaranteed interview question, and a well-reasoned short list beats a long one.

### M3 — Four conflicting CORS configurations
`WebConfig.java:16-20` allows only `https://prop-flow-ui.vercel.app` with credentials. `AuthController.java:24` declares `@CrossOrigin(origins = "*")`. `PropertyController.java:16` declares `http://localhost:4200`. `TransactionController.java:15` declares both the Vercel and localhost origins. Controller-level `@CrossOrigin` overrides the global registry, so the effective policy differs per endpoint with no single place to read it. `origins = "*"` on the auth controller is the most permissive and sits on the most sensitive endpoint.
**Solution.** Delete every `@CrossOrigin` annotation. Define one `CorsConfigurationSource` bean wired into the Spring Security filter chain (important: with Spring Security present, CORS must be handled there so preflight requests are not rejected before reaching MVC). Drive allowed origins from configuration so dev and prod differ by property, not by code.
**Complexity:** S · **Portfolio value:** Medium

### M4 — Money stored as `Double`
`Transaction.amount` is `Double` (`Transaction.java:53`), as are `TaxDetails.taxAmount` and `RefundInfo.refundAmount`. `Property.basePrice`, `Booking.totalPrice`, and `Expense.amount` correctly use `BigDecimal`.
IEEE-754 binary floating point cannot represent most decimal fractions exactly. `0.1 + 0.2 == 0.30000000000000004`. Summing thousands of transactions for a tax report accumulates error, and the resulting figures will not reconcile.
**Solution.** Migrate to `BigDecimal` mapped to `NUMERIC(19,2)` (or `NUMERIC(19,4)` if fractional cents matter for tax computation). This requires a Flyway migration and careful handling of existing rows. Also note the validation gap: `validateTransaction` (`TransactionService.java:174`) rejects `amount <= 0`, but for a model where `type` distinguishes `INCOME` from `EXPENSE`, that rule needs to be stated deliberately rather than assumed.
**Complexity:** M · **Portfolio value:** High — "why `BigDecimal` for money" is a classic question with a crisp answer.

### M5 — Incorrect HTTP status codes
`POST /api/properties` (`PropertyController.java:36-40`) and `POST /api/transactions` (`TransactionController.java:43-46`) return **200** instead of **201 Created**, and neither sets a `Location` header. `DELETE` on both returns **200** with an empty body instead of **204 No Content** (`PropertyController.java:52`, `TransactionController.java:58`). `UserController` gets both right (`:37` uses 201, `:67` uses 204) — so the API is internally inconsistent about its own conventions. `POST /api/transactions/search` (`TransactionController.java:61`) uses POST for a read operation, making it non-cacheable and non-bookmarkable.
**Solution.** 201 with `Location` on create, 204 on delete, consistently. For search, `GET` with query parameters is the more RESTful choice; if the filter set is genuinely too large for a URL, `POST /search` is a defensible pragmatic exception — but it should be a documented decision, not an accident.
**Complexity:** S · **Portfolio value:** Medium

### M6 — Not-found conditions produce HTTP 500
`PropertyService.java:19-21` throws `RuntimeException("Property not found")`; `TransactionService.java:54` throws `RuntimeException("Transaction not found with id: " + id)`. Neither is handled, so both surface as **500 Internal Server Error** when the correct answer is **404 Not Found**. Meanwhile `TransactionController.java:28-31` handles the same condition correctly via `Optional`, and `UserService.updateUser` signals it by returning `null` — three different conventions for one concept.
`RuntimeException` is also too generic to catch selectively without catching unrelated failures.
**Solution.** A typed `ResourceNotFoundException` mapped to 404 in the exception handler (**H7**). Pick one convention and apply it everywhere.
**Complexity:** S · **Portfolio value:** Medium

### M7 — No pagination on collection endpoints
`GET /api/properties` (`PropertyController.java:24`), `GET /api/transactions` (`TransactionController.java:21`), `GET /api/users` (`UserController.java:41`), `GET /api/transactions/user/{userId}`, and `GET /api/transactions/property/{propertyId}` all return unbounded `List`s via `findAll()`. Response size and memory footprint grow linearly with the table. Combined with **M1**'s eager collections, `GET /api/transactions` is the most expensive request in the application by a wide margin.
**Solution.** Return `Page<T>` with `Pageable` parameters and a sane default and maximum page size. The infrastructure already exists — `searchTransactions` uses `PageRequest` correctly.
**Complexity:** S · **Portfolio value:** Medium

### M8 — Missing and incorrect transaction boundaries
`PropertyService` has no `@Transactional` at all. `updateProperty` (`PropertyService.java:27-43`) performs a read-modify-write across two repository calls with no surrounding transaction, so the read and the write occur in separate auto-commit transactions — a concurrent update between them is lost with no optimistic-locking check to detect it (`Property` has no `@Version`). `UserService` likewise has none, and `createUser`/`updateUser` have the same read-then-write pattern.
`TransactionService` applies `@Transactional` to writes correctly (`:45,51,61`) but leaves reads without `@Transactional(readOnly = true)`, which would let Hibernate skip dirty-checking and signal read-intent to the driver.
**Solution.** `@Transactional` on all service write methods, `@Transactional(readOnly = true)` on reads (class-level default plus method-level override is the tidy idiom). Add `@Version` to `Property` and `Transaction` for optimistic locking. Be able to explain *why* the transaction boundary belongs at the service layer and not the controller or repository — that is the question behind the question.
**Complexity:** S · **Portfolio value:** High

### M9 — `updateTransaction` destroys audit fields and permits lost updates
`TransactionService.java:51-59` sets the ID on a client-supplied object and calls `save()`, replacing the entire row. Any field the client omits becomes `null`. `createdAt` is `updatable = false` (`Transaction.java:131`) so it survives, but every other unsent field — `approvedBy`, `approvedDate`, `refund`, `tags`, `metadata` — is silently wiped. There is no optimistic-locking check, so two concurrent updates result in a lost update. The `existsById`-then-`save` sequence is also a check-then-act race: the row can be deleted between the two calls.
**Solution.** Load the managed entity, copy only the fields the update DTO permits, and let JPA dirty-checking issue the `UPDATE` inside one transaction. Add `@Version`. This is a good concrete example of why PUT-with-full-entity is a risky default and why explicit field mapping (**H6**) pays off.
**Complexity:** S · **Portfolio value:** High

### M10 — `pom.xml` hygiene
- `javax.persistence:javax.persistence-api:2.2` (`pom.xml:95-99`) sits on the classpath alongside `jakarta.persistence-api:3.1.0` (confirmed in the dependency tree). Two generations of the same API. The code correctly imports `jakarta.*`, so this is dead weight and a source of confusing IDE autocompletion.
- `spring-boot-starter-data-jpa` is declared **twice** (`pom.xml:33-36` and `:79-82`).
- `HikariCP` version is pinned to `5.0.1` (`pom.xml:52-56`), overriding the version the Spring Boot BOM manages. Manual overrides of BOM-managed versions are how subtle incompatibilities get introduced; there is no stated reason for it.
- `com.h2database:h2` is at `runtime` scope, not `test` (`pom.xml:84-88`), so it ships in the production jar. The comment says "for testing."
- No `jacoco` or any coverage tooling — relevant since we should not claim a coverage number we do not measure.
**Solution.** Remove the `javax.persistence` dependency, the duplicate JPA starter, and the Hikari version pin. Move H2 to `test` scope or remove it entirely in favor of Testcontainers (**H3**).
**Complexity:** S · **Portfolio value:** Low individually, but dependency hygiene is something reviewers scan for.

### M11 — Dead entities and phantom endpoints
`Booking.java`, `Expense.java`, and `CleaningChecklist.java` are full `@Entity` classes with `@ManyToOne` relationships. None has a repository, service, or controller. `ddl-auto=update` creates their tables regardless, so the schema carries three unused tables.
`README.md:122-130` documents `GET /bookings`, `POST /bookings`, `PUT /bookings/{id}`, `GET /expenses`, `POST /expenses`, and `GET /expenses/summary`. **None of these routes exist** — I enumerated every mapping in the codebase.
There is also conceptual overlap: `Expense` and `Transaction` model the same thing, with `Transaction` being the more developed of the two.
**Solution.** Decide deliberately. Either implement `Booking` (it is the most interesting remaining domain object — date-range overlap validation for double-booking is genuinely good interview material and a natural place to demonstrate a database-level exclusion constraint) or remove the dead entities and correct the README. Merge `Expense` into `Transaction`. Either way, the README must describe only what exists.
**Complexity:** S to remove, L to implement Booking properly · **Portfolio value:** Medium

### M12 — `java.util.Date` instead of `java.time`
`Transaction` uses `java.util.Date` for `date`, `dueDate`, `paidAt`, `createdAt`, `updatedAt`, `approvedDate` (`Transaction.java:111-135`), as do `Warranty` and `RefundInfo`. `Booking` and `Expense` correctly use `LocalDateTime`/`LocalDate`. `java.util.Date` is mutable, has no timezone semantics, and has been effectively deprecated since Java 8. The mutability matters here: `@Data` generates a getter returning the internal `Date` reference, which a caller can mutate.
For financial records, timezone semantics are not academic — "which day did this transaction occur" affects which tax year it lands in. `Instant` for timestamps and `LocalDate` for business dates is the right split.
**Solution.** Migrate to `java.time`, with a Flyway migration aligning column types (`TIMESTAMPTZ` for instants, `DATE` for business dates).
**Complexity:** M · **Portfolio value:** Low-Medium

### M13 — Optimistic locking applied inconsistently
`User` has `@Version` (`User.java:33-35`); `Property`, `Transaction`, `Booking`, and `Expense` do not. The financial entities are the ones most in need of it. Worse, `AuthController.java:68` explicitly nulls the version before save — with a `nullable = false` column, that is fighting the mechanism rather than using it, and the accompanying comment ("Set version to null to ensure JPA handles versioning") suggests the semantics were not clear at the time.
**Solution.** Add `@Version` to `Property` and `Transaction`. Remove the manual version manipulation — for a new entity, a null ID is what tells JPA it is transient. Map `OptimisticLockingFailureException` to HTTP **409 Conflict** in the exception handler, which is a nice, concrete demonstration of concurrency awareness surfacing correctly through the API.
**Complexity:** S · **Portfolio value:** Medium

---

## LOW Findings

### L1 — README claims not supported by the code
Consolidated list of every unsupported statement I found:

| README claim | Location | Reality |
|---|---|---|
| "Spring Boot 3.2.0" | `:36` | `pom.xml:8` → 3.4.0 |
| "JWT Authentication" | `:41` | No JWT anywhere (**C4**) |
| `JWT_SECRET`, `JWT_EXPIRATION` env vars | `:98-100` | Unused |
| `/bookings` endpoints (3) | `:122-125` | Do not exist |
| `/expenses` endpoints (3) | `:127-130` | Do not exist |
| "Photo management" | `:11` | No implementation |
| "Guest communication logs" | `:16` | No implementation |
| "Break-even analysis", "Financial projections" | `:22-23` | No implementation |
| "Cleaning schedule", "Service provider management", "Quality tracking" | `:27-30` | `CleaningChecklist` entity only, no API |
| `LICENSE.md` | `:195` | File does not exist |
| `support@PropFlow.api`, Slack channel | `:199` | Fabricated for a solo project |
| Angular frontend setup + `ng test` | `:43-48,158-161` | No frontend in this repository |
| "access your application at http://localhost:8081" | `:149` | Compose app cannot start (**H11**) |

**Why it matters.** This is LOW only in implementation difficulty. In *reviewer impact* it is High — accumulated unsupported claims are the fastest way to lose technical credibility, and this list is long enough that a careful reader will start assuming nothing in the README is true.
**Solution.** Full rewrite (**Phase 13**), documenting only what exists.
**Complexity:** S · **Portfolio value:** **High**

### L2 — `com.airbnb` package name implies affiliation with Airbnb, Inc.
The root package is `com.airbnb` and the artifact is `airbnb-property-management`. `com.airbnb` is Airbnb, Inc.'s reverse-DNS namespace. For a public repository presented as professional work, this reads as either a misunderstanding of Java package conventions or an implied association with a company you do not work for. The product is called PropFlow; the package should reflect that.
**Solution.** Rename to something you control, e.g. `com.hoseacodes.propflow`. This is a mechanical IDE refactor but touches every file and the `@ComponentScan`/`@EntityScan`/`@EnableJpaRepositories` declarations in `AirbnbPropertyManagementApplication.java:8-10`. Worth doing, but it should be a single isolated commit so it does not obscure substantive changes in the diff.
**Complexity:** M (mechanical but wide) · **Portfolio value:** Medium

### L3 — Stale deployment tooling
`Procfile` targets Heroku with a hardcoded jar name that breaks on any version bump. `.herokuignore` excludes `docs/` — which will exclude the documentation we are about to write. A `heroku` git remote is still configured. `docker_build.sh` builds and **pushes** to the personal Docker Hub account `hoseacodes` on every run, and calls `docker system prune -f` at the end (which removes unrelated dangling images and build cache on the developer's machine — a destructive side effect in a build script). Commit history shows the project migrated Heroku → Render, and artifacts from both remain.
**Solution.** Remove the Heroku artifacts. Either delete `docker_build.sh` in favor of a CI-driven build (**Phase 10**) or strip the push and prune steps from it.
**Complexity:** S · **Portfolio value:** Low

### L4 — `.vscode/launch.json` references a nonexistent main class
`.vscode/launch.json:8` specifies `com.airbnb.property_management.AirbnbPropertyManagementApplication`. The actual class is `com.airbnb.AirbnbPropertyManagementApplication`. The same phantom package appears in `scanBasePackages` (`AirbnbPropertyManagementApplication.java:8`) — Spring tolerates scanning a package that does not exist, so it is silently inert. The file is untracked, so it affects only your local setup, but the phantom package in the tracked source is worth cleaning.
**Complexity:** S · **Portfolio value:** Low

### L5 — Inconsistent dependency injection style
`PropertyService`, `TransactionService`, `CustomUserDetailsService`, `SecurityConfig`, `PropertyController`, and `TransactionController` use field injection (`@Autowired` on a field). `UserService`, `UserController`, and `AuthController` use constructor injection. Constructor injection is the Spring team's documented recommendation — it permits `final` fields, makes dependencies explicit and unmockable-by-reflection, and fails fast on missing beans. Field injection also makes the class harder to unit test without a Spring context, which matters directly for **Phase 3**. `AuthController.java:27` additionally leaves `authenticationManager` non-final for no reason.
**Solution.** Constructor injection throughout; with Lombok already present, `@RequiredArgsConstructor` on the class plus `private final` fields is the concise form.
**Complexity:** S · **Portfolio value:** Low individually — but it directly enables cleaner unit tests.

### L6 — No `.env.example`
`.env` is correctly gitignored and correctly untracked. But there is no committed template, so a new developer cannot know which variables are required. `.vscode/launch.json` references `.env` via `envFile`, so the application will not launch as configured without it, and nothing documents its contents.
**Solution.** Commit `.env.example` with every required key and placeholder values only. Reference it from the README setup steps.
**Complexity:** S · **Portfolio value:** Medium (directly improves the clone-and-run experience)

### L7 — `adminer` container port mapping is wrong
`docker-compose.yml:82-83` maps `8082:8082`, but the Adminer image listens on port 8080 internally. The service is unreachable. Should be `8082:8080`.
**Complexity:** S · **Portfolio value:** Low

---

## Cross-Cutting Observations

**Absent entirely:** database migrations, meaningful tests, CI/CD, actuator/health endpoints, metrics, structured logging, API documentation that works, DTOs, authorization, rate limiting, `.dockerignore`, `.env.example`, `LICENSE`, and any architecture documentation. Several of these are named requirements for the target role.

**Dependency security:** no dependency scanning is configured, and no CVE audit was performed as part of this review. Spring Boot 3.4.0 was current as of late 2024; a version check and a scanning step belong in the CI work (**Phase 10**). I would rather add that than assert a security posture I have not verified.

**A pattern worth naming.** Several findings share one root cause: **a mechanism was implemented correctly in one place and then bypassed elsewhere.** BCrypt is correct in `AuthController` and absent in `UserService` (**C3**). `@Transactional` is correct in `TransactionService` and absent in `PropertyService` (**M8**). `@Version` is correct on `User` and absent on the financial entities (**M13**). 201/204 status codes are correct in `UserController` and wrong in the other two (**M5**). `@ManyToOne` is correct on `Booking`/`Expense` and absent on `Transaction` (**H10**).

That pattern is diagnostic. It says the knowledge is present but the enforcement is not — the codebase has no mechanism that makes the correct thing the default. That is precisely what tests, a shared base configuration, and consistent boundaries provide, and it is the strongest argument for the work in Phases 3–6. It is also, honestly, a good thing to be able to say out loud in an interview: *"I audited my own project, found that I'd applied the right patterns inconsistently, and added the tests and structure that make consistency enforceable."*

---

## Highest-Value Improvements — Recommended Order

Ranked by reviewer impact per unit of effort. This is the input to the Phase 2 plan.

### Do these before sharing the repository link with anyone

1. **Rotate the leaked database credential and remove it from configuration** (**C2**) — external action plus a small code change. Nothing else matters if this is still live.
2. **Delete or correct every unsupported README claim** (**C4**, **L1**) — a few hours. Right now the README actively damages your credibility, and fixing it costs almost nothing. Even before the code improves, an honest README is strictly better than an inflated one.
3. **Commit the Maven wrapper** (**H3**) — five minutes. Removes the "cannot even build this" first impression.

### The core of the work — this is what changes the classification

4. **Implement real authentication and authorization** (**C1**, **C3**, **C4**, **C5**, **H1**) — JWT issuance and validation, a correct Spring Security 6 filter chain, single-path password encoding, and per-user ownership checks on properties and transactions. This is the largest single body of work and by far the highest-value: it converts the repository's biggest liability into its strongest demonstration, and it maps directly onto the role's Spring Security / authentication / authorization requirements.
5. **Establish the testing foundation with Testcontainers** (**H3**, **H2**) — this is the credibility multiplier. Tests are the evidence that everything else in the repository actually works, and choosing Testcontainers over H2 is a defensible decision you can explain. It also catches bugs like **H2** that mocked tests cannot.
6. **Adopt Flyway migrations** (**H4**) — named in the role requirements, and a prerequisite for the schema fixes in **H10**, **M2**, and **M4**.
7. **Introduce DTOs and centralized error handling** (**H5**, **H6**, **H7**) — closes the mass-assignment and hash-leakage findings at the root, and produces the consistent API error model the role asks for.

### Strong differentiators once the foundation is sound

8. **Fix the data model: foreign keys, targeted indexes, `BigDecimal` money** (**H10**, **M2**, **M4**) — this is where "relational data modeling" and "indexing and query performance" become demonstrable rather than asserted. The index reasoning in **M2** is genuinely good interview material.
9. **Fix the N+1 and add pagination** (**M1**, **M7**) — with a query-count integration test as the proof.
10. **Observability and a working Docker/CI story** (**H8**, **H11**, Phases 8 & 10) — actuator health endpoints, sane logging, a compose file that actually starts, and a green CI badge backed by tests that genuinely run.

### Lower priority

11. Correct HTTP semantics and API consistency (**M5**, **M6**, **M13**)
12. Transaction boundaries and the update-semantics fix (**M8**, **M9**)
13. Package rename off `com.airbnb` (**L2**), dependency cleanup (**M10**), stale tooling removal (**L3**)
14. Documentation set — `ARCHITECTURE.md`, `SECURITY.md`, `OPERATIONS.md`, ADRs, `AGENTS.md`, `INTERVIEW_GUIDE.md` (Phases 12–17)

### A note on sequencing

Items 4–7 are interdependent and should be done roughly in that order: DTOs are easier once authentication establishes who the caller is; migrations should exist before schema changes; and **tests should come early enough to protect the refactors that follow.** I would actually start the testing foundation (5) in parallel with authentication (4), writing the API tests for auth as the auth is built — that way the test suite grows with the feature rather than being retrofitted, and you get a much more natural story about test-driven work.

---

## Verification Notes

Everything asserted in this audit was checked directly:

- **Build:** `./mvnw compile` — **succeeds**.
- **Tests:** `./mvnw test` — **fails**. `Tests run: 1, Errors: 1`, caused by `PSQLException: FATAL: password authentication failed for user "airbnb"`.
- **Dependency tree:** resolved and inspected. Confirmed *present*: `springdoc-openapi-ui:1.7.0`, `jakarta.persistence-api:3.1.0`, `javax.persistence-api:2.2`, `hibernate-core:6.6.2.Final`. Confirmed *absent*: any JWT library, `spring-boot-starter-actuator`, Flyway, Liquibase, Testcontainers.
- **Secrets:** `git ls-files` confirms `.env`, `seed.sql`, and `application-prod.properties` are **untracked**; `git log` confirms they are **absent from history**. `application.properties` **is** tracked and **does** contain a live credential in `HEAD` and across seven commits.
- **Routes:** every `@RequestMapping`/`@*Mapping` in the codebase was enumerated and compared against the README's documented endpoints.
- **Build tooling:** `git ls-files` confirms `mvnw`, `mvnw.cmd`, and `.mvn/` are untracked, and `.gitignore` in `HEAD` explicitly excludes them.

No application code, configuration, or documentation outside this file was modified during the audit.
