# PropFlow API — Operations

How to run, observe, and troubleshoot this service.

> **Scope.** This is a portfolio project. It is not deployed anywhere, serves no
> real users, and has never carried production traffic. Everything below
> describes what the code actually does today, plus explicitly-labelled notes on
> what a real deployment would additionally need. Nothing here is a claim of
> production readiness.

---

## Health and readiness

| Endpoint | Auth | Purpose |
|---|---|---|
| `GET /actuator/health` | public | Aggregate status |
| `GET /actuator/health/liveness` | public | Process-level health |
| `GET /actuator/health/readiness` | public | Can this instance serve traffic? |
| `GET /actuator/prometheus` | `ADMIN` | Metrics scrape |
| `GET /actuator/info` | `ADMIN` | Build information |

### Liveness vs. readiness — the distinction that matters

These answer different questions, and conflating them causes outages rather
than preventing them.

**Liveness** — *is this process broken beyond recovery?* If it fails, the
orchestrator kills and restarts the container. Configured to include
`livenessState` only.

**Readiness** — *can this instance serve traffic right now?* If it fails, the
load balancer stops routing to it, but the process is left running. Configured
to include `readinessState` **and** the database check.

The database is deliberately in readiness and **not** liveness. Consider a
PostgreSQL outage:

- **Correct (current) behaviour:** every instance reports not-ready, traffic
  stops being routed, the processes stay alive. When the database returns, the
  next probe succeeds and instances rejoin the pool with warm JVMs and
  established connection pools.
- **If the database were in liveness:** every instance would report dead, the
  orchestrator would kill the entire fleet, and they would enter a restart loop.
  Restarting cannot fix a database. Worse, when the database recovers, the whole
  fleet cold-starts simultaneously — JIT-cold, connection pools empty, all
  hammering the just-recovered database at once. A recoverable dependency
  failure becomes a self-inflicted thundering herd.

What `/actuator/health` actually tells you: that the process is up, and that
Spring's `DataSourceHealthIndicator` obtained a connection from the pool and ran
a validation query. It does **not** tell you that queries are fast, that the
connection pool has headroom, that migrations succeeded, or that the disk has
space. Treat a green health check as "not obviously broken," never as "working
correctly."

### Detail is authenticated

Anonymous callers receive only `{"status":"UP"}`. Component breakdown — which
check failed, plus the database vendor and version that come with it — requires
an `ADMIN` token. A load balancer needs the status; an anonymous attacker should
not learn the topology.

---

## Metrics

`/actuator/prometheus` exposes the standard Micrometer registry: JVM memory and
GC, HikariCP pool statistics, HTTP request counts and latency distributions by
URI and status, and Tomcat thread pool usage. Every series is tagged
`application=propflow-api`.

### What I would actually alert on

Ranked by how directly each predicts user-visible failure:

| Signal | Metric | Why |
|---|---|---|
| Error rate | `http_server_requests_seconds_count{status=~"5.."}` | 5xx is the service's own fault. A rising rate is the earliest honest signal something is broken. |
| Latency (p99) | `http_server_requests_seconds` | Averages hide the tail. p99 is what a user with a large transaction history experiences. |
| Connection pool exhaustion | `hikaricp_connections_pending` | Sustained non-zero means requests are queueing for a connection. This is the first thing to break under load — see below. |
| Connection acquisition time | `hikaricp_connections_acquire_seconds` | Rising before pending does, so it is the leading indicator. |
| Readiness flapping | probe state | An instance oscillating in and out of the pool is worse than one cleanly out. |
| Heap after GC | `jvm_memory_used_bytes{area="heap"}` | Steadily rising post-GC floor indicates a leak. |
| Authentication failures | `http_server_requests_seconds_count{uri="/api/auth/signin",status="401"}` | A spike is credential stuffing. There is no rate limiting yet, so this is the only current detection. |

**Not** alerting on CPU or raw memory. Those are causes, not symptoms; alerting
on them produces pages for conditions no user ever noticed.

---

## Logging

Logback via SLF4J, `INFO` at root, `DEBUG` for `com.hoseacodes.propflow` under
the `dev` profile only.

### What is deliberately never logged

- **JWTs.** `JwtService` logs the *class name* of a parse failure, never the
  token. A token in a log file is a working credential for anyone who can read
  that file.
- **Passwords**, in any form, at any level.
- **`org.springframework.web` at DEBUG** in shared profiles — it can log request
  headers, which carry the `Authorization` header.
- **Attempted usernames on failed sign-in.** The original code logged
  `"User not found: {username}"` at ERROR, turning the log into a record of
  probed accounts and a noise source under credential stuffing.
- **SQL.** `show-sql` is off. It writes unformatted statements with no timings,
  which is not observability. Use Hibernate statistics or `pg_stat_statements`.

### Correlation IDs

Unexpected exceptions are logged at `ERROR` with a generated UUID, and the same
id is returned to the client in the `correlationId` field of the RFC 7807 body.
A user can quote it in a support request and an engineer can find the exact log
line — without the response ever carrying a stack trace, SQL fragment, or
internal class name.

```
grep "<correlation-id>" application.log
```

Expected failures — 404s, validation errors — are **not** logged at all. They
are normal API traffic, and logging them turns routine client mistakes into
alert noise.

### Not implemented

Structured JSON logging. Currently plain text, which is fine for local
development and `docker compose logs` but awkward for a log aggregator. Adding
`logstash-logback-encoder` under a container profile would be the change.

---

## Failure modes

Analysed honestly, including the ones that are not handled well.

### PostgreSQL becomes unavailable

**What happens.** HikariCP fails to hand out connections. Requests in flight
fail; the exception handler catches them and returns 500 with a correlation id
and no internal detail. `/actuator/health/readiness` goes `DOWN`;
`/actuator/health/liveness` stays `UP`. Traffic stops being routed; the process
survives.

**Recovery.** Automatic. Hikari re-establishes connections, readiness returns
`UP`, the instance rejoins.

**Weakness.** No circuit breaker, so every request during the outage waits the
full connection timeout before failing. Under sustained load this occupies
Tomcat threads and slows even requests that need no database. A real deployment
would add a short connection timeout plus a circuit breaker (Resilience4j) so
failures are fast rather than slow.

### Migration fails on startup

**What happens.** Flyway aborts and the application does not start. The
container exits and, under `restart: unless-stopped`, restarts into the same
failure.

**Why that is correct.** A partially-migrated schema serving traffic is worse
than an instance that is down. `ddl-auto=validate` provides the second gate: if
entities do not match the migrated schema, startup fails with the exact missing
column named.

**Operationally:** check `flyway_schema_history` for the failed version. Flyway
wraps each migration in a transaction where PostgreSQL permits it, so a failed
migration generally rolls back cleanly.

### Concurrent updates to the same record

**What happens.** `users`, `properties`, and `transactions` all carry `@Version`.
The second writer's `UPDATE` matches zero rows, Hibernate raises
`OptimisticLockingFailureException`, and the API returns **409** telling the
client to re-read and retry.

**Why optimistic rather than pessimistic.** No locks are held, so readers are
never blocked. Conflicts are detected at write time rather than prevented at
read time — the right trade when conflicts are rare, which they are when each
user edits their own records.

### Duplicate registration under concurrency

**What happens.** Two simultaneous registrations for the same email both pass
the service-layer `existsBy` check before either commits. That check is
check-then-act and inherently racy. The unique index on `lower(email)` is what
actually enforces the invariant: one insert succeeds, the other raises
`DataIntegrityViolationException`, and the handler returns 409.

**The point:** the application check exists to produce a friendly message in the
common case. The database constraint is the guarantee.

### Retried requests

`GET`, `PUT`, and `DELETE` are idempotent. **`POST` is not** — retrying
`POST /api/transactions` after a timeout creates a second transaction. There is
no idempotency-key mechanism. For a financial ledger this is a genuine gap, and
the fix would be an `Idempotency-Key` header with a short-lived record of
processed keys.

### Deleting a user or property with dependents

`ON DELETE RESTRICT` refuses the delete; the API returns 409. Deliberate:
removing an account must not silently take its financial history with it.

### Token compromise

A stolen JWT is valid until it expires (default one hour). It **cannot be
revoked** — that is inherent to stateless tokens. Partial mitigation: the
authentication filter reloads the principal from the database on every request,
so deleting or disabling the account stops it working immediately even though
the token remains cryptographically valid.

### Credential stuffing

**Not mitigated.** There is no rate limiting on `/api/auth/signin`. BCrypt's
cost factor makes each attempt expensive for the server as well as the attacker,
which is itself a denial-of-service vector. This is a known gap; see
[`SECURITY.md`](./SECURITY.md).

---

## What breaks first under load

In order, with reasoning rather than benchmarks — **no load testing has been
performed, and no throughput numbers are claimed anywhere in this repository.**

1. **The connection pool.** Hikari defaults to 10 connections. With every
   request touching the database, concurrency beyond ~10 in-flight database
   operations queues. Symptom: `hikaricp_connections_pending` rises, latency
   climbs while CPU stays low.
2. **Free-text search.** `LIKE '%term%'` cannot use a B-tree index, so
   `searchTerm` queries are sequential scans that degrade linearly with table
   size. Upgrade path: `tsvector` + GIN index.
3. **Unindexed sort fields.** Sorting by an allowed field other than `date`
   (e.g. `amount`) has no supporting index, so PostgreSQL sorts the user's whole
   filtered set before applying the page.
4. **The JVM heap**, last. The API streams no large payloads and pagination is
   capped at 100 items.

### At 10x traffic

The application itself is stateless and horizontally scalable — no session
state, no sticky routing, no local caching — so adding instances is the obvious
first move. But that multiplies connection-pool demand against a single
PostgreSQL instance, so pool sizing and a connection pooler (PgBouncer) become
the real constraint. Beyond that: read replicas for reporting queries, and a
covering index or materialised view for aggregate reports.

---

## Troubleshooting

**Application will not start**

```
propflow.jwt.secret is not configured
```
`JWT_SECRET` is unset. Generate one: `openssl rand -base64 48`. By design there
is no fallback — a default signing key would let anyone with the source forge
tokens.

```
Schema-validation: missing column [x] in table [y]
```
An entity and the migrated schema disagree. Either a migration is missing, or
the database predates a migration. For a development database:
`docker compose down -v` (**deletes the volume**).

```
FATAL: password authentication failed
```
`DB_*` does not match the database. Note that PostgreSQL ignores
`POSTGRES_USER`/`POSTGRES_PASSWORD` when the data volume already exists — those
only apply when initialising an empty data directory. Recreate with
`docker compose down -v`.

```
Bind for 0.0.0.0:5432 failed: port is already allocated
```
Another PostgreSQL owns the port. Set `DB_HOST_PORT` in `.env` and update the
port in `DB_URL` to match.

**Every request returns 401** — check the header is `Authorization: Bearer
<token>`, and that the token has not expired (default one hour).

**A resource returns 404 that should exist** — reads are ownership-scoped.
Another user's resource returns 404 rather than 403 by design, so that response
codes cannot be used to discover which ids exist. Verify with
`GET /api/users/me` that the token belongs to the account you expect.

**Container is unhealthy** — the healthcheck probes
`/actuator/health/readiness`, which includes the database. Check
`docker compose logs db` first; the app is usually a symptom.

---

## What a real deployment would add

Listed because their absence is a deliberate scoping decision, not an oversight.

- **Distributed tracing.** OpenTelemetry is **not** implemented, and adding it
  here would be box-ticking: with one service and no collector there is nothing
  to correlate across. It earns its place at the point there is a second service
  or an external dependency worth tracing through. The introduction would be the
  `opentelemetry-spring-boot-starter` plus an OTLP endpoint; Micrometer's
  `Observation` API already sits underneath the metrics here, so spans would
  attach to the same instrumentation points rather than requiring new ones.
- **Structured JSON logs** shipped to an aggregator, with the correlation id as
  an indexed field.
- **Secret management** — AWS Secrets Manager or Vault instead of environment
  variables, with rotation.
- **Rate limiting** on authentication.
- **Circuit breaker and timeouts** on database access.
- **Idempotency keys** for `POST`.
- **Backups and a tested restore procedure.** Untested backups are not backups.
- **Alerting** on the signals listed above, routed to someone on call.
- **Log retention and PII review** — transactions carry vendor names and
  descriptions that may be personal data.
