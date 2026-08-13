# PropFlow API — Security

The authentication and authorization model, what it deliberately does not do,
and what would have to change before this carried real data.

> **This is a portfolio project.** It is not deployed, serves no real users, and
> holds no real data. Everything documented here is implemented and covered by
> tests, but "implemented correctly" is not the same as "production-hardened."
> The [Known limitations](#known-limitations) section is not boilerplate — read it.

---

## Authentication

**Model:** stateless bearer tokens (JWT), HS256.

```
POST /api/auth/signup   -> 201, no credentials in the response
POST /api/auth/signin   -> 200 { accessToken, tokenType, expiresIn, user }
GET  /api/...           -> Authorization: Bearer <accessToken>
```

### Password storage

BCrypt at the default cost factor of 10, via `BCryptPasswordEncoder`.

BCrypt is deliberately slow and salts each hash individually. The salt means a
stolen hash cannot be attacked with precomputed rainbow tables; the cost factor
means each guess is expensive, so brute force scales badly for the attacker. The
cost is symmetric — each increment doubles the work for this application too —
which is why sign-in is itself a denial-of-service consideration (see
[limitations](#known-limitations)).

Hashing happens in exactly one place: `UserService.register`. That is a
structural decision, not a stylistic one. Encoding previously lived in a
controller, and a second controller (`POST /api/users`) bypassed it entirely and
stored raw passwords — producing accounts that could never sign in. **A security
control enforced at a call site will eventually be missed at another call site.**
Registration now has one entry point that every write path must traverse.

### Token issuance and validation

`JwtService` mints tokens with subject, issued-at, expiry, and an authorities
claim. Validation happens in `JwtAuthenticationFilter`, registered before
`UsernamePasswordAuthenticationFilter`.

Every request with a bearer token has its **signature, expiry, and subject**
verified. `Jwts.parser().verifyWith(key)` refuses unsigned tokens outright, so
the classic `alg: none` substitution fails. Unit tests cover a token signed with
a different key, a tampered payload, an expired token, an unsigned token, and
arbitrary garbage.

**The authorities claim in the token is never trusted for authorization.** The
filter reloads the principal from the database on every request and derives
authorities from the stored role. Trusting the claim would mean a role revoked
in the database stays effective until the token expires.

That database read is a deliberate trade: it gives up some of JWT's
statelessness (one query per authenticated request) in exchange for account
changes — deletion, role change — taking effect immediately. An integration test
asserts that a deleted user's still-valid, still-unexpired token stops working.

### Signing secret

Supplied via `JWT_SECRET`. **There is no default and no fallback.** The
application refuses to start without it, because a default signing key is a
forgery oracle: anyone who has read the source can mint a valid token for any
account.

Minimum 32 bytes, enforced at startup (HS256 requires a 256-bit key per RFC
7518).

> **A real bug this caught.** `propflow.jwt.secret=${JWT_SECRET}` in
> `application.properties` does *not* fail when the variable is unset. Spring's
> `@ConfigurationProperties` binder resolves placeholders with
> `ignoreUnresolvablePlaceholders=true`, so it binds the **literal string**
> `${JWT_SECRET}` and the application starts with that as its HMAC key — a
> publicly-known secret shared by every deployment that forgot the variable.
> It was caught only because that literal is 13 characters and failed the length
> check; a longer variable name would have passed. `JwtProperties` now rejects
> any value that still looks like an unresolved placeholder, with a regression
> test for the long-name case.

### Account enumeration

Sign-in returns an identical response for an unknown username and a wrong
password — byte-for-byte identical apart from the timestamp, asserted by test.
`DaoAuthenticationProvider` runs with `hideUserNotFoundExceptions(true)`, so it
compares against a dummy hash for an unknown user and the two paths take
comparable time. `CustomUserDetailsService` no longer logs attempted usernames.

---

## Authorization

Two independent layers. Both are required; neither is sufficient alone.

### 1. Role-based access

`USER` and `ADMIN`, persisted on the user row and enforced in the filter chain.

```java
.requestMatchers(PUBLIC_PATHS).permitAll()
.requestMatchers("/actuator/**").hasRole("ADMIN")
.requestMatchers("/api/users/me").authenticated()
.requestMatchers("/api/users/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

**Default deny.** Anything not explicitly listed requires authentication, so a
newly added controller is protected unless someone deliberately opens it. The
original configuration was the inverse — `requestMatchers("/api/**").permitAll()`
with every controller mounted under `/api` — which made the entire API,
including full CRUD over accounts and financial records, anonymously reachable.

Roles are assigned server-side at registration. `SignUpRequest` has no `role`
component, so there is nothing for a client to bind; a payload containing
`"role":"ADMIN"` is simply ignored, and a test asserts it.

### 2. Row-level ownership

Roles say *what kind of account you are*. Ownership says *which rows you may
touch*. Enforcing only roles would leave every authenticated user able to read
every other user's financial records — the more damaging of the two failures.

Ownership is enforced **inside the query**, never as a check after loading:

```java
propertyRepository.findByIdAndOwner(id, caller)          // properties
Specification.allOf(scopedTo(caller), hasId(id))          // transactions
```

A check performed after loading protects only the call sites that remember it;
forget one and it leaks. A scoped query **fails closed** — a missing scope shows
up as an empty result, not a breach. This required a schema change first: there
was no ownership edge to query on, because transactions referenced users through
a `VARCHAR` column with no foreign key.

Writes are scoped too. Creating a transaction resolves its property through the
owner-scoped lookup, so a caller cannot file records against a property they do
not own — writing into books they cannot read.

`ADMIN` bypasses ownership, implemented in exactly one place per service rather
than as a conditional at each call site.

### 404, not 403

A resource belonging to another account returns **404**. A 403 would confirm the
id exists, letting an attacker walk the id space and learn which records are
real. From outside, "does not exist" and "is not yours" are indistinguishable.

403 is reserved for role failures, which reveal nothing about data.

---

## Input handling

**Request DTOs, never entities.** Java `record` types with Bean Validation
constraints. The entity is not the API contract, which closes three problems at
once:

- **Mass assignment** — a client can only set fields the record declares. The
  old `PUT /api/users/{id}` bound the `User` entity directly and saved it under
  the path id with no ownership check: mass assignment and IDOR in four lines.
  It was removed rather than patched.
- **Credential leakage** — `User` implements `UserDetails`, so it must expose
  `getPassword()`, so Jackson published every account's BCrypt hash.
  `UserResponse` simply has no password component.
- **Unwritable server-managed fields** — `createdAt`, `version`, `userId`, and
  `propertyName` are absent from request types. On an auditable financial
  record, those are precisely the fields a client must not control.

**SQL injection** is not a live risk: all access is through JPA/Criteria with
bound parameters, and there is no string-concatenated SQL anywhere. `LIKE`
wildcards in search input are escaped — that is a *correctness* fix (a user
searching for `%` means the character), not an injection defence.

**Sort fields are whitelisted.** `sortBy` becomes a Criteria attribute path; an
unvalidated value is a 500 at best and a way to probe the internal model at
worst.

---

## Transport and browser concerns

**CORS** is configured once, as a `CorsConfigurationSource` inside the security
filter chain — not via `WebMvcConfigurer`, because preflight `OPTIONS` must be
handled before authorization or the browser's preflight is rejected with a 401.
Origins come from configuration; `*` is rejected at startup. This replaced four
conflicting declarations, one of which allowed every origin on the
authentication endpoints.

`allowCredentials` is **false**. In CORS terms "credentials" means cookies and
TLS client certificates, which this API does not use — the bearer token travels
in an explicit `Authorization` header the browser never attaches automatically.

**CSRF is disabled**, and that is a conclusion rather than a convenience. CSRF
defends against a browser automatically attaching *ambient* credentials to a
cross-site request. This API is stateless, holds no session, and sets no cookie,
so there is no ambient credential to abuse. **If cookie-based authentication is
ever added, CSRF protection must be re-enabled** — the comment in
`SecurityConfig` says so at the point of the decision.

**TLS** is not terminated by the application. It is assumed to be handled by a
load balancer or ingress. Without it, bearer tokens travel in clear text.

---

## Secrets

| Secret | Source | Default |
|---|---|---|
| `JWT_SECRET` | environment | **none — startup fails** |
| `DB_PASSWORD` | environment | `propflow` (local container only) |

No credential is stored in any tracked file. `.env` is gitignored;
`.env.example` contains placeholders only. `.dockerignore` keeps `.env` out of
the build context — the Dockerfile does `COPY . .`, so without it the file
landed in a build layer.

### Disclosed incident

**A live PostgreSQL password was committed to this public repository** in
`application.properties`, present in `HEAD` and across seven commits. It was
found by the audit that started this work
([`ENGINEERING_AUDIT.md`](./ENGINEERING_AUDIT.md), finding C2).

Remediation:
1. The credential was rotated on the database.
2. Configuration moved to environment variables with non-secret local defaults.
3. `.env.example`, `.dockerignore`, and a `.gitignore` review followed.

**Git history was deliberately not rewritten.** Once rotated, the value is dead;
a `filter-repo` rewrite breaks every existing clone and fork and requires a
force-push, which is a poor trade for a credential that no longer works.
Recording the incident here is a more useful signal than a silently scrubbed
history.

---

## Known limitations

Stated plainly. Several are things a reviewer would otherwise find, and finding
them undisclosed is worse than reading them here.

**Tokens cannot be revoked before expiry.** Inherent to stateless JWT. A stolen
token is valid for its remaining lifetime (default one hour). Mitigations in
place: short lifetime, and the per-request database reload that stops a deleted
or modified account immediately. A true revocation list would reintroduce the
server-side state JWT was chosen to avoid — which is the actual tradeoff, and
the honest answer is that this project accepts the exposure window rather than
pretending otherwise.

**No refresh tokens.** Clients re-authenticate when the token expires. A refresh
flow would allow a much shorter access-token lifetime, which is the real reason
to add one.

**No rate limiting on `/api/auth/signin`.** Credential stuffing is unmitigated,
and because BCrypt is intentionally expensive, a flood of sign-in attempts is
also a CPU denial-of-service vector. This is the most significant gap on the
list.

**No account lockout, no MFA, no password-complexity policy beyond length,
no breached-password check.**

**No password change or reset flow.** Removed alongside the vulnerable
`PUT /api/users/{id}`; a correct implementation requires the current password
and would invalidate outstanding tokens, which the current model cannot do.

**No audit log.** Entities carry `createdAt`/`updatedAt` and financial records
are protected by `ON DELETE RESTRICT`, but there is no immutable record of who
changed what. For a system holding financial data this is a genuine gap.

**No email verification.** Any address can be registered without proving control.

**Dependency scanning is not configured**, and no CVE audit has been performed
as part of this work. Spring Boot 3.4.0 was current as of late 2024; a real
project would run Dependabot or OWASP Dependency-Check in CI and treat that as
one signal rather than comprehensive assurance.

**No penetration testing.** Nothing here has been validated by an external
security review.

---

## What would change before production

Roughly in priority order:

1. Rate limiting and lockout on authentication endpoints.
2. Secret management (AWS Secrets Manager / Vault) with rotation, replacing
   environment variables.
3. A refresh-token flow, allowing access-token lifetimes measured in minutes.
4. An append-only audit log for financial mutations.
5. Dependency and container scanning in CI, with a policy for acting on findings.
6. TLS enforced end to end, including between the load balancer and the service.
7. Password reset with proof of email control, plus token invalidation on
   password change.
8. Security headers (HSTS, CSP) at the edge.
9. A review of what constitutes personal data in transaction descriptions and
   vendor names, with retention rules to match.
