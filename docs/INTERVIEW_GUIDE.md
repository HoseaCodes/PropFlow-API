# PropFlow — Interview Preparation Guide

**This document is for me, not for reviewers.** It exists so I can defend every
decision in this repository in my own words.

Two rules for using it:

1. **Do not memorise the answers.** Understand the reasoning. An interviewer will
   ask a follow-up, and a recited answer collapses on the first one.
2. **Never say "the tool wrote it."** Say what the decision was and why. If I
   cannot explain a thing in this repository, that thing should not be in this
   repository.

### The honest framing of how this was built

This will come up, and the honest answer is a strength if delivered well:

> "I audited my own project and found 37 issues — the API was completely
> unauthenticated, a database password was committed, and the README advertised
> JWT authentication that didn't exist. I used an AI agent heavily to work
> through the remediation, but I set the priorities, made the architectural
> calls, and verified everything by running it. Some of the most important bugs
> came out of that verification rather than the code review — I'll give you an
> example if you want one."

Then have the example ready (see Q24). What sells this is that I found real bugs
*by insisting on running things*, not by trusting output.

---

## Contents

- [Architecture & design](#architecture--design)
- [Security](#security)
- [Data & PostgreSQL](#data--postgresql)
- [Testing](#testing)
- [Scale, operations, failure](#scale-operations-failure)
- [Process & judgement](#process--judgement)
- [Questions I should be ready to be stumped by](#questions-i-should-be-ready-to-be-stumped-by)

---

## Architecture & design

### Q1. Why did you structure the application this way?

**Short answer.** Standard layered architecture — controller, service,
repository, model — with two boundaries I enforce strictly: controllers never
touch repositories, and JPA entities never cross the HTTP boundary.

**Deeper.** The layering itself is unremarkable; what matters is *why* those two
rules are non-negotiable here.

Controllers can't touch repositories because the **transaction boundary is at
the service method**. A controller reaching past the service would execute
outside any transaction, so a read-then-write would span two of them.

Entities can't cross the HTTP boundary because that single decision was the root
cause of three separate defects in the original code. `User` implements
`UserDetails`, so it *must* expose `getPassword()`, so Jackson published every
account's BCrypt hash on `GET /api/users` — unauthenticated. And accepting
entities as request bodies gave me mass assignment: `PUT /api/users/{id}` bound
the entity, set the id from the path, and saved it, so any caller could rewrite
any account's credentials.

One consequence worth naming: my **services return DTOs, not entities**. That's
slightly unusual and it's forced by `open-in-view=false`. The persistence
context closes when the service method returns, so anything the response needs
must be materialised before that boundary. Mapping in the controller works right
up until someone touches a lazy collection, then throws at runtime. Mapping in
the service makes the boundary explicit and impossible to get wrong. The cost is
that services know about response types — real coupling, accepted deliberately.

**Files:** `controller/`, `service/`, `dto/`, `docs/ARCHITECTURE.md`

**Follow-ups:** *Why not put mapping in a dedicated mapper layer?* — At this
size it'd be indirection without benefit; I'd add one if mapping grew logic.
*Why not MapStruct?* — Hand-written mapping keeps "what crosses the boundary"
visible in reviewable code. Deliberately omitting a field is the point, and
generated code hides that decision.

---

### Q2. Where are your transaction boundaries, and why there?

**Short answer.** At the service method. Class-level
`@Transactional(readOnly = true)`, with writes overriding it.

**Deeper.** The service method is the unit of work — one business operation that
should entirely succeed or entirely fail.

- **Repository is too small.** An operation that reads then writes would span
  two transactions, so a concurrent change between them is lost.
- **Controller is the wrong layer.** Transaction scope would be tied to HTTP,
  and any non-HTTP caller — a scheduled job, a CLI — would get none.

`readOnly = true` isn't documentation. It lets Hibernate skip dirty-checking on
loaded entities and signals intent to the JDBC driver, which matters on a
read-heavy reporting workload.

The related decision is **how updates are written**. The original
`updateTransaction` set an id on a client-supplied object and called `save()`,
which replaced the whole row — every field the request omitted became null,
silently wiping `approvedBy`, `refund`, and `tags`. Now I load the managed
entity inside the transaction and mutate it, letting dirty-checking issue the
`UPDATE`. There's a test asserting `createdAt` and `userId` survive.

**Files:** `service/PropertyService.java`, `service/TransactionService.java`

**Follow-ups:** *What isolation level?* — `READ_COMMITTED`, the PostgreSQL
default. I prevent lost updates with optimistic locking rather than by raising
isolation, because no locks are held and readers are never blocked. *What if a
service calls another service?* — Spring's default propagation `REQUIRED` joins
the existing transaction, which is what I'd want; I'd only reach for
`REQUIRES_NEW` for something like audit logging that must survive a rollback.

---

### Q3. Why a monolith?

**Short answer.** One bounded context, one team, one transactional model.
Microservices solve organisational and scaling problems this system doesn't
have.

**Deeper.** Splitting would trade in-process calls for network calls that fail
partially, and ACID transactions for eventual consistency and compensating
actions — **in a financial ledger**, where "the transaction was recorded but the
property update was lost" is exactly the failure that must not happen.

The judgement I want credit for isn't "monoliths are good." It's matching
architecture to constraints and knowing which constraints would change the
answer: separate teams needing independent deploys, one capability with a load
profile the rest doesn't share, or a component whose failure must not take the
rest down. None of those is true here.

I applied the same test to everything else and wrote down the rejections —
Kafka (no async integration, no second consumer), Redis (no measured cache need;
a cache without a measured hit rate is an invalidation-bug surface for imaginary
gain), Kubernetes, CQRS, GraphQL, OpenTelemetry.

**Files:** `docs/adr/ADR-005-modular-monolith.md`

**Follow-ups:** *When would you split this?* — When bookings needed independent
scaling from the ledger, or a separate team owned them. *Isn't OpenTelemetry
standard now?* — With one service and no collector there's nothing to correlate
across. I documented how I'd introduce it in `OPERATIONS.md`; Micrometer's
`Observation` API already sits underneath, so spans would attach to existing
instrumentation points.

---

## Security

### Q4. How is authentication implemented?

**Short answer.** Stateless JWT, HS256. `POST /api/auth/signin` verifies
credentials via `AuthenticationManager` and returns a signed token; a
`OncePerRequestFilter` validates it on every subsequent request.

**Deeper.** Walk the flow:

1. `AuthenticationManager.authenticate` delegates to
   `DaoAuthenticationProvider`, which loads the user and compares the submitted
   password against the stored BCrypt hash.
2. `JwtService.generateToken` mints a token with subject, issued-at, expiry, and
   an authorities claim, signed with an HMAC key.
3. On each request, `JwtAuthenticationFilter` extracts the bearer token,
   verifies **signature and expiry and subject**, loads the principal, and
   populates the `SecurityContext`.
4. Authorization rules then evaluate against that context.

What was there before is worth describing, because the contrast is the point:
sign-in verified credentials, wrote the result into `SecurityContextHolder` —
which is thread-local and cleared at end of request — and returned the string
"User signed in successfully". The client got nothing it could present later.
There was no JWT library on the classpath at all.

**Files:** `security/JwtService.java`, `security/JwtAuthenticationFilter.java`,
`config/SecurityConfig.java`

---

### Q5. Authentication vs. authorization — and how does that show up here?

**Short answer.** Authentication is *who are you*; authorization is *what may
you do*. In this codebase they're separate layers, and authorization is itself
two layers.

**Deeper.** Roles say what *kind* of account you are (`USER`/`ADMIN`). Ownership
says which *rows* you may touch. Roles alone are insufficient and that's the
important part — with role checks only, every authenticated user could read
every other user's financial records. That's the more damaging failure of the
two, because it's silent.

I can't take much credit for noticing that in the abstract; I noticed it because
after implementing JWT I wrote the README's "Known Limitations" section
honestly, and "any authenticated user can read any property" looked as bad
written down as it was.

---

### Q6. Why JWT? What are its weaknesses?

**Short answer.** Chosen for stateless horizontal scaling and clean cross-origin
use. The dominant weakness is that **a token can't be revoked before it
expires**.

**Deeper.** The value: the token carries its own proof of authenticity, verified
with a key only the server holds, so no session store lookup is needed and any
instance can serve any request — no sticky sessions, no shared session state.

The cost, stated without hedging: signing out, changing a password, or
discovering a leak does not invalidate an issued token. Every mitigation is
imperfect:

- **Short lifetime** (one hour here) bounds the window; doesn't close it.
- **A denylist** works but reintroduces exactly the server-side state that
  motivated JWT. If I needed it, that would mean JWT was the wrong choice.
- **What I actually implemented:** the filter reloads the principal from the
  database on every request. So a deleted or role-changed account stops working
  *immediately*, even though the token is still cryptographically valid. That
  costs one query per request and gives up some statelessness. There's an
  integration test asserting it.

I'd also say what I *didn't* do: no refresh tokens, so clients re-authenticate
hourly. A refresh flow is the right next step because it would let access tokens
live for minutes instead of an hour.

**Files:** `docs/adr/ADR-002-jwt-authentication.md`, `security/JwtService.java`

**Follow-ups:** *HS256 or RS256?* — HS256, because one service both mints and
verifies. RS256 earns its keep when verifiers must not be able to mint —
multiple services, or a public key given to clients. *Where do you store the
token client-side?* — Honest answer: that's a client decision I haven't made
here. `localStorage` is XSS-exposed; an httpOnly cookie is safer against XSS but
reintroduces CSRF. I'd take the cookie and re-enable CSRF protection.

---

### Q7. How do you prevent one user from accessing another's data?

**This is the question I most want to be asked.**

**Short answer.** Authorization is part of the query, not a check after loading.

```java
propertyRepository.findByIdAndOwner(id, caller)
Specification.allOf(scopedTo(caller), hasId(id))
```

**Deeper.** The naive version is load-then-check:

```java
Property p = repo.findById(id);
if (!p.getOwner().equals(caller)) throw new ForbiddenException();
```

That works only while every call site remembers it. Forget one and it leaks. A
**scoped query fails closed** — a forgotten scope produces an empty result, not
a breach. Given that the central pattern in this codebase's original defects was
"control applied correctly in one place, bypassed in another," designing so the
safe path is the *only* path mattered more than adding another guard.

Three details I'd volunteer:

1. **404, not 403.** A 403 confirms the id exists, so an attacker can walk the
   id space and learn which records are real. From outside, "doesn't exist" and
   "isn't yours" must be indistinguishable. 403 is only for role failures, which
   reveal nothing about data.
2. **Writes are scoped too.** Creating a transaction resolves its property
   through the owner-scoped lookup. Otherwise a user who couldn't *read* another
   account's property could still file transactions against it — writing into
   books they can't see. That's a separate check and easy to miss.
3. **This required a schema change first.** There was no ownership edge to query
   on: transactions referenced users through a `VARCHAR` column with no foreign
   key, while `users.id` is a `BIGINT`. You can't scope by an owner that isn't
   modelled.

**Files:** `service/PropertyService.java`, `service/TransactionService.java`,
`ResourceOwnershipIT.java` (15 tests), `V7__transaction_relationships.sql`

**Follow-ups:** *Why not `@PreAuthorize`?* — Method security evaluates *after*
the method resolves its arguments, so I'd still be loading the row first. Query
scoping is stronger. *Row-level security in PostgreSQL?* — Genuinely stronger,
since it binds any connection. I didn't use it because it requires per-request
session variables and a connection-pool strategy that complicates things at this
scale. Good answer to have ready.

---

### Q8. How are secrets managed?

**Short answer.** Environment variables. `JWT_SECRET` has **no default** and the
application refuses to start without it.

**Deeper.** A default signing key is a forgery oracle — anyone who reads the
source can mint a token for any account. Failing to start is correct; an app
that boots with a known key is worse than one that doesn't boot.

I should disclose the incident rather than wait to be asked: **this repository
had a live PostgreSQL password committed**, present in `HEAD` and seven commits
of a public repo. My audit found it. I rotated the credential, moved config to
environment variables, and documented it in `SECURITY.md`. I deliberately did
*not* rewrite git history — once rotated the value is dead, and a `filter-repo`
rewrite breaks every clone and fork for no real gain. Recording the incident is
a more useful signal than a scrubbed history.

For production I'd use AWS Secrets Manager or Vault with rotation. Environment
variables are visible in `/proc`, in `docker inspect`, and in crash dumps.

**Follow-ups:** *How do you rotate the JWT secret without logging everyone out?*
— Good question, and currently I can't. You'd support two keys during a
transition: sign with the new, accept either, retire the old after max token
lifetime.

---

### Q9. What security vulnerabilities did you find?

Lead with the two that show the most, then the list.

**The best one — an unresolved placeholder became the signing key.**
`propflow.jwt.secret=${JWT_SECRET}` does *not* fail when the variable is unset.
Spring's `@ConfigurationProperties` binder resolves placeholders with
`ignoreUnresolvablePlaceholders=true`, so it binds the **literal string**
`${JWT_SECRET}` and the app starts with that as its HMAC key — a publicly known
secret shared by every deployment that forgot the variable, any of which could
forge a token for any user of the others. My "no default" guarantee was holding
purely by accident: that literal is 13 characters and failed my 32-byte minimum.
A longer variable name would have sailed through. I found it because I refused
to write a CI assertion I hadn't actually run.

**Second best — a gitignored file inside a Docker image.** `docker compose up`
produced an unhealthy container. Cause: a stale, untracked, *gitignored*
`application-prod.properties` on my machine got copied into the build context
and packaged into the jar. **`.gitignore` doesn't keep a file out of a Docker
build context** — separate mechanisms. A clean clone built fine; only my image
was poisoned. That's exactly how a local file with real credentials ends up in a
published image.

**The rest, from the audit:** entirely unauthenticated API
(`requestMatchers("/api/**").permitAll()` with every controller under `/api`);
committed database password; plaintext passwords via a second signup path that
bypassed the encoder in the controller; BCrypt hashes returned in API responses;
mass assignment plus IDOR on `PUT /api/users/{id}`; `logging.level.root=DEBUG`
shipping to production; error responses echoing raw SQL constraint text.

**Files:** `docs/ENGINEERING_AUDIT.md`, `docs/SECURITY.md`

---

## Data & PostgreSQL

### Q10. Why PostgreSQL?

**Short answer.** The data is highly relational and it's money. Both point the
same way.

**Deeper.** The invariants that matter — every transaction belongs to a real
property owned by a real user — are relational, and a document store can only
enforce them advisorily in application code. This project already lived that
failure: transactions referenced users through an unvalidated string and the
model drifted.

Over MySQL specifically, PostgreSQL wins on features this workload actually
reaches for: functional indexes (used for case-insensitive uniqueness),
`NUMERIC` arithmetic, richer constraints, and `EXCLUDE USING gist` for
booking-overlap prevention — an invariant MySQL can't express declaratively.

**Files:** `docs/adr/ADR-001-postgresql.md`

---

### Q11. Which indexes exist and why?

**This is where I can be most concrete.** Five indexes beyond the primary keys,
each tied to a query.

| Index | Query it serves |
|---|---|
| `ix_properties_owner_id` | Every property read — all are owner-scoped |
| `ix_transactions_user_id_date (user_id, date DESC)` | Owner-scoped listing, newest first |
| `ix_transactions_property_id_date` | Per-property statements |
| `ix_transaction_tags/warranties_transaction_id` | Collection loads and parent-delete checks |
| `ix_bookings_property_id` | Booking lookups |

**The composite column order is the whole answer.** Every transaction read is:

```sql
WHERE user_id = ? ORDER BY date DESC
```

With `user_id` leading, PostgreSQL seeks straight to that user's slice of the
index. Because `date DESC` is second, rows *within that slice are already in the
requested order* — so the `ORDER BY` is satisfied by the index itself, with no
separate sort step, and a `LIMIT` can stop after one page instead of sorting the
user's entire history.

Reversed to `(date, user_id)` it'd be nearly useless: an equality predicate on a
trailing column can't drive a seek, so the scan reads across every user's rows.

**What I deliberately didn't add:** `(user_id, property_id)`. The leading column
of the first index already narrows to the user, and filtering that much smaller
set by property is cheap. Adding an index per column combination is how a table
gets slower to write and no faster to read.

**The tradeoff:** every index costs storage and is maintained inside every
`INSERT`/`UPDATE`/`DELETE`, in the same transaction. Worth it for read-heavy
financial reporting; I'd re-make that judgement for a write-heavy ingest table.

Also worth saying: **PostgreSQL doesn't index foreign keys automatically.** An
unindexed FK means a sequential scan on every child lookup *and* on every parent
`DELETE`, which has to check for referencing rows.

**Files:** `V7__transaction_relationships.sql`,
`SchemaMigrationIT.transactionIndexColumnOrderSupportsScopedListing`

**Follow-ups:** *How would you know if an index is unused?* —
`pg_stat_user_indexes.idx_scan`. *How do you verify one is being used?* —
`EXPLAIN ANALYZE`; I'd want to see an Index Scan and no Sort node. Honest
caveat: **I have not run `EXPLAIN` against a large dataset here** — my reasoning
is from the access patterns and index structure, not from measured plans. Say
that rather than imply otherwise.

---

### Q12. How do migrations work?

**Short answer.** Flyway, seven versioned SQL migrations applied at startup, with
`ddl-auto=validate` as a second gate.

**Deeper.** The project used `ddl-auto=update`, which fails on four counts: it
only ever *adds* (never drops a column or narrows a type, so a rename silently
orphans data); it produces nothing reviewable in a PR; the resulting schema
depends on the *history of versions a database has seen* rather than current
code, so environments legitimately diverge; and it can't express data migration
at all.

Two details I'd volunteer:

**The V1 baseline deliberately preserves the model's known defects** — `DOUBLE
PRECISION` money, missing foreign keys — each annotated with its audit finding.
Silently fixing them in the baseline would hide the work; as separate migrations
they're real, reviewable schema evolution.

**Migrations refuse to destroy data.** V6 and V7 raise an exception if existing
rows can't be mapped, rather than guessing an owner or discarding financial
records. Deciding who owns an existing property is a business decision, not
something a migration may invent.

`ddl-auto=validate` means Hibernate verifies entities against the migrated
schema and fails startup on drift, naming the exact column. I proved that by
dropping a column and watching it refuse to boot.

**Files:** `src/main/resources/db/migration/`,
`docs/adr/ADR-003-flyway-migrations.md`

**Follow-ups:** *How do you roll back?* — I don't, and that's deliberate: I
write a new forward migration. Down-migrations are rarely tested and often can't
restore dropped data. *Migrations at startup with many instances?* — Flyway takes
a lock so they don't race, but at scale I'd run it as a separate pre-deploy step.

---

### Q13. How would you prevent double-booking?

Not implemented — but I have a real answer, and it's the best remaining feature.

**Application-level checking cannot guarantee this.** "Select overlapping
bookings, and insert if none" is check-then-act: two concurrent requests both
see zero overlaps and both insert. Serialising with a lock works but hurts
throughput.

**PostgreSQL can express it declaratively:**

```sql
ALTER TABLE bookings ADD CONSTRAINT no_overlapping_bookings
  EXCLUDE USING gist (
    property_id WITH =,
    tstzrange(check_in, check_out) WITH &&
  );
```

The database rejects any insert whose date range overlaps an existing one for
the same property — enforced at write time, so it survives concurrency without
application locking. That's the same principle as everywhere else here: the
database is the only layer that binds every writer.

---

## Testing

### Q14. Why Testcontainers instead of H2?

**Short answer.** An in-memory database in "PostgreSQL compatibility mode" isn't
PostgreSQL, so a passing test isn't evidence about the deployed database.

**Deeper.** H2 diverges on type coercion, constraint and index semantics,
sequences, `NUMERIC` precision, upsert syntax, and JSON. The divergence clusters
exactly where this project's correctness lives. Concretely, four tests would be
meaningless or impossible:

- Case-insensitive uniqueness relies on a **functional unique index** on
  `lower(email)`.
- `SchemaMigrationIT` asserts PostgreSQL `CHECK` and `ON DELETE RESTRICT`
  behaviour via raw SQL.
- The money test sums `NUMERIC` **in the database** and asserts `0.10 + 0.20`
  equals exactly `0.30`.
- The index test reads `pg_indexes` to assert column order.

**A design detail worth mentioning:** the container starts in a `static`
initialiser, not via the `@Testcontainers` extension, which starts and stops one
per test *class*. Started once per JVM it serves the whole run — the first
integration class pays ~10s, the next runs in **0.097s**.

**The costs, stated:** Docker becomes a hard prerequisite; integration tests take
seconds not milliseconds. And there's a real risk of over-reliance — because
these are pleasant to write, they can crowd out faster unit tests for logic that
needs no database. The Surefire/Failsafe split is partly a guard against that.

**Files:** `AbstractIntegrationTest.java`, `docs/adr/ADR-004-testcontainers.md`

---

### Q15. Which integration tests are most valuable?

Ranked by what they'd catch that nothing else would:

1. **`ResourceOwnershipIT`** — 15 tests proving one user can't reach another's
   data by read, list, search, update, delete, or by writing against their
   property. A data-leak regression is the worst thing that could silently
   happen here.
2. **`SchemaMigrationIT`** — asserts the *database* enforces its invariants,
   using raw SQL that bypasses the application. A constraint that exists only in
   Java protects nothing from a repair script or a second writer.
3. **The search-filter tests** — the regression test for a bug where a
   fully-built `Specification` was silently discarded.
4. **The query-count test** — an N+1 is completely invisible in a response body.
   The only way to regression-test it is counting statements, via Hibernate's
   `Statistics`.

**Follow-up:** *What's not covered?* — No load or performance testing. No
concurrency test that actually races two requests (the optimistic-locking path
is reasoned about, not exercised under real contention). No `Booking` coverage,
since there's no API. No security scanning or pen testing.

---

### Q16. How do you know your tests are any good?

Better answer than a coverage number, which I deliberately don't quote.

Three things I'd point to:

**Every bug fix has a test that fails without the fix.** The discarded
`Specification`, the destructive update, the unresolved JWT placeholder.

**I found and fixed a test that passed for the wrong reason.** A `CHECK`
constraint test inserted rows with non-existent foreign key values. Once real
FKs were added, a *foreign-key* violation satisfied the same
`assertThrows(DataIntegrityViolationException)`. It kept passing while testing
nothing. It now seeds valid parents and includes a positive control proving the
same insert succeeds with a valid enum value.

**I don't mock repositories in tests about data behaviour.** The discarded
`Specification` bug was invisible to mocks — the mock returns its stub whether or
not the spec was applied.

I don't quote coverage because a high number obtained by testing getters is a
negative signal, and I haven't measured it.

---

## Scale, operations, failure

### Q17. What happens if PostgreSQL becomes unavailable?

**Short answer.** Requests fail with 500 and a correlation id; readiness goes
`DOWN`, liveness stays `UP`. Traffic stops routing; processes survive; recovery
is automatic.

**Deeper — and this is the part worth getting right.** The database is in the
**readiness** group and deliberately **not** liveness:

- **Readiness fails** → load balancer stops routing. When the database returns,
  the next probe passes and instances rejoin with warm JVMs and established
  pools.
- **If the database were in liveness** → the orchestrator would kill the entire
  fleet into a restart loop. Restarting cannot fix a database. Worse, on
  recovery every instance cold-starts simultaneously — JIT-cold, pools empty —
  and hammers the just-recovered database. A recoverable dependency failure
  becomes a self-inflicted thundering herd.

**The weakness I'd volunteer:** no circuit breaker, so every request during the
outage waits the full connection timeout before failing, occupying Tomcat
threads. I'd add a short connection timeout plus Resilience4j so failures are
fast rather than slow.

**Files:** `application.properties` (health groups), `ActuatorIT.java`

---

### Q18. What does the health endpoint actually tell you?

**Short answer.** That the process is up and Spring's `DataSourceHealthIndicator`
got a connection from the pool and ran a validation query. That's all.

**Deeper.** It does *not* tell you queries are fast, the pool has headroom,
migrations succeeded, or disk has space. **A green health check means "not
obviously broken," never "working correctly."**

I'd also mention the access decision: anonymous callers get only `UP`/`DOWN`.
Component detail — which check failed, plus the database vendor and version —
requires an `ADMIN` token. A load balancer can't hold a credential and only
needs the status; an anonymous attacker shouldn't learn the topology. Endpoints
that dump configuration or memory (`env`, `heapdump`, `configprops`) aren't
merely protected, they're **removed from the exposure list**, with a test
asserting they 404.

---

### Q19. What would you monitor in production?

Ranked by how directly each predicts user-visible failure:

1. **5xx rate** — the service's own fault, the earliest honest signal.
2. **p99 latency** — averages hide the tail; p99 is what the user with a large
   transaction history experiences.
3. **`hikaricp_connections_pending`** — sustained non-zero means requests are
   queueing for a connection. First thing to break under load.
4. **`hikaricp_connections_acquire_seconds`** — rises before pending does, so
   it's the leading indicator.
5. **Readiness flapping** — an instance oscillating in and out is worse than one
   cleanly out.
6. **Heap after GC** — a rising post-GC floor means a leak.
7. **401 rate on `/api/auth/signin`** — spikes mean credential stuffing. With no
   rate limiting, this is currently the only detection.

**Not** CPU or raw memory. Those are causes, not symptoms; alerting on them
pages for conditions no user noticed.

---

### Q20. How would this behave at 10x traffic? What breaks first?

**Order, with reasoning — and I should be explicit that I have not load-tested
this, so this is analysis, not measurement.**

1. **The connection pool.** Hikari defaults to 10. Every request touches the
   database, so concurrency beyond ~10 in-flight operations queues. Symptom:
   pending rises, latency climbs, CPU stays low — which is diagnostic, because
   it rules out compute.
2. **Free-text search.** `LIKE '%term%'` can't use a B-tree index, so those
   queries are sequential scans degrading linearly with table size.
3. **Unindexed sort fields.** Sorting by `amount` has no supporting index, so
   PostgreSQL sorts the user's whole filtered set before paging.
4. **The heap**, last. No large payloads; pages capped at 100.

**Scaling:** the application is genuinely stateless — no session state, no
sticky routing, no local caching — so adding instances behind a load balancer
works. But that multiplies pool demand against one PostgreSQL, so pool sizing
and PgBouncer become the real constraint. Then read replicas for reporting, and
a covering index or materialised view for aggregates.

**The honest framing:** the application isn't the bottleneck; the single
database is.

---

### Q21. Are any operations non-idempotent? Any race conditions?

Good question to answer without defensiveness.

**Non-idempotent:** `POST`. Retrying `POST /api/transactions` after a timeout
creates a second transaction. There's no idempotency-key mechanism. **For a
financial ledger that's a genuine gap**, and the fix is an `Idempotency-Key`
header with a short-lived record of processed keys. `GET`, `PUT`, and `DELETE`
are idempotent.

**Races, and how each is handled:**

- **Concurrent updates to the same row** — optimistic locking via `@Version` on
  all three mutable entities. Second writer's `UPDATE` matches zero rows →
  `OptimisticLockingFailureException` → **409**.
- **Duplicate registration** — the service's `existsBy` check is check-then-act
  and *is* racy; two concurrent registrations can both pass it. The **functional
  unique index** is what actually enforces it; the loser gets
  `DataIntegrityViolationException` → 409. The application check exists only to
  produce a friendly message in the common case.
- **Double-booking** — not implemented; see Q13.

The pattern worth naming: **application checks are for good error messages;
database constraints are for correctness.**

---

### Q22. How would you deploy this to AWS?

**Short answer.** ECS Fargate behind an ALB, RDS PostgreSQL, secrets in Secrets
Manager.

**Deeper.**

- **Compute:** ECS Fargate — the app is a stateless container and I don't want
  to operate Kubernetes for one service. ALB target group health checks point at
  `/actuator/health/readiness`; ECS container health at `/liveness`.
- **Database:** RDS PostgreSQL, Multi-AZ, in private subnets, reachable only
  from the app security group. Automated backups **with a tested restore** —
  untested backups aren't backups.
- **Secrets:** Secrets Manager, injected as ECS task secrets rather than
  environment variables in a task definition, with rotation for the database
  credential.
- **Migrations:** as a one-off ECS task in the deploy pipeline, *before* the new
  task set starts, rather than at instance startup — so instances don't race and
  a bad migration fails the deploy instead of a container.
- **Deploys:** rolling with health-check gating; ECS won't shift traffic until
  readiness passes.
- **Observability:** CloudWatch Logs for structured JSON, AMP or a managed
  Prometheus scraping `/actuator/prometheus` from inside the VPC.
- **TLS** terminated at the ALB with ACM.

**Follow-ups:** *Why not Lambda?* — JVM cold starts, and a connection pool
doesn't fit a per-invocation model. *Zero downtime with a schema change?* —
Expand/contract: add the new column, deploy code writing both, backfill, deploy
code reading the new one, then drop the old in a later release.

---

## Process & judgement

### Q23. What would you change before putting this in production?

Ordered by risk:

1. **Rate limiting and lockout on authentication.** The most significant gap.
   Credential stuffing is unmitigated, and because BCrypt is intentionally
   expensive, a flood of sign-in attempts is also a CPU denial-of-service
   vector.
2. **Secret management** with rotation, replacing environment variables.
3. **Refresh tokens**, so access tokens live minutes rather than an hour.
4. **An append-only audit log** for financial mutations. Constraints protect the
   data; nothing records who changed what.
5. **Idempotency keys** on `POST`.
6. **Circuit breaker and tuned timeouts** on database access.
7. **Load testing** — everything I've said about what breaks first is reasoning
   from access patterns, not measurement.
8. **Dependency and container scanning**, with a policy for acting on findings.
9. **`java.time` migration** off `java.util.Date`.

---

### Q24. Tell me about a bug you found in your own code.

**Use the JWT placeholder bug.** It's the best story because it shows a habit,
not just a fix.

> "I was adding a CI step asserting the container refuses to start without
> `JWT_SECRET`. Before committing the assertion I ran it, because I don't write
> assertions I haven't seen pass. The error message wasn't what I expected — it
> said the secret must be at least 32 bytes and it 'got 13'.
>
> Thirteen is the length of the literal string `${JWT_SECRET}`. Spring's
> `@ConfigurationProperties` binder resolves placeholders with
> `ignoreUnresolvablePlaceholders=true`, so an unset variable doesn't fail — it
> binds the raw placeholder text. The application would have started with
> `${JWT_SECRET}` as its HMAC signing key. Every deployment that forgot the
> variable would share one publicly known secret, and any of them could forge a
> token for any user of the others.
>
> My 'no default signing key' guarantee was holding by pure accident, because
> that literal happens to be under the minimum length. A longer variable name
> would have gone straight through. I added an explicit unresolved-placeholder
> check and a regression test using a name long enough to clear the length
> check.
>
> The lesson I took: if I'd written the CI assertion from what I *assumed* the
> message was, I'd have shipped the bug *and* a green pipeline telling me it was
> fixed."

**Backup story:** the gitignored file in the Docker image (Q9).

---

### Q25. How did you use AI on this, and how do you know it's right?

Answer directly; evasion is the only wrong move.

> "Heavily, and under review. I ran the audit first and used it to set
> priorities, then worked through them in small, individually verified commits.
> I made the architectural decisions — Testcontainers over H2, monolith over
> microservices, ownership scoping in the query — and each is written up as an
> ADR with its downsides.
>
> How I know it's right: I ran everything. `mvn verify` from a clean clone with
> no database, the Docker image, the Compose stack, the actual HTTP endpoints
> with curl. The most valuable bugs came out of that verification, not out of
> reading code — the JWT placeholder, the gitignored file in the image, an
> exception handler that turned 405 into 500, a test that passed for the wrong
> reason once real foreign keys existed.
>
> I also wrote `AGENTS.md`, which encodes the constraints an agent must follow
> here. Every rule in it cites the specific defect that motivated it, so it can
> be judged rather than obeyed."

**Follow-up:** *How do you review code you didn't write?* — Same as any code
review, plus a bias toward running it. My rule is that if I can't explain
something in the repository, it doesn't stay in the repository — which is partly
why this guide exists.

---

## Questions I should be ready to be stumped by

Rehearse these; the good answer to several is "I don't know, here's how I'd find
out."

- **"Show me the `EXPLAIN` output for your indexed query."** I haven't run it at
  scale. Say so, then explain what I'd expect: Index Scan on
  `ix_transactions_user_id_date`, no Sort node, and a `LIMIT` short-circuiting.
- **"What's your p99 latency?"** Unknown — no load testing. Don't invent a
  number.
- **"Why `@ElementCollection` rather than a proper entity for tags?"** No
  identity or lifecycle of their own and never queried independently. If tags
  needed to be shared or renamed globally, they'd become an entity.
- **"Your service returns DTOs — isn't that a layering violation?"** It's a real
  tradeoff. Forced by `open-in-view=false`. The alternative fails at runtime the
  first time someone touches a lazy field.
- **"Why is `property_name` denormalised?"** Point-in-time snapshot so renaming
  a property doesn't rewrite past statements. Intentional, and commented.
- **"What happens if two migrations are written on separate branches with the
  same version?"** Flyway fails on the duplicate. Real teams use timestamp-based
  versions.
- **"Is `docker compose up` actually tested?"** I ran it — healthy in ~25s, 401
  anonymous, 201 authenticated. It's not in CI; CI builds the image and asserts
  the fail-closed behaviour.
- **"Your CI badge — has that workflow ever run?"** Be honest: at the time of
  writing it hadn't run remotely. The command it runs was verified locally.
