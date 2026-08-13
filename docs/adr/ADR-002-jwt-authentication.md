# ADR-002: Stateless JWT authentication

**Status:** Accepted, with known limitations · **Date:** 2026-08

## Context

The API had no working authentication. Sign-in verified credentials, wrote the
result into `SecurityContextHolder` — thread-local, cleared at end of request —
and returned a plain string. The client received nothing it could present on a
subsequent call, and every endpoint was anonymously reachable.

Requirements: a browser SPA on a different origin; the API must scale
horizontally without sticky sessions; a reviewer should be able to authenticate
from Swagger UI or curl without cookie handling.

## Options considered

**Server-side sessions (`JSESSIONID`).** Genuinely simpler and revocation is
trivial — delete the session. Rejected because sessions are server state: either
instances share a session store (Redis, adding infrastructure and a failure
mode) or requests are pinned to instances with sticky routing, which undermines
horizontal scaling. Cookies also reintroduce CSRF, and a cross-origin SPA makes
cookie handling awkward.

**OAuth2 / OIDC with an external provider.** The right answer for a real product
— delegating credential handling to Auth0 or Cognito removes a whole class of
risk. Rejected here for two reasons: it makes the repository undemonstrable
without an account and secrets a reviewer does not have, and the point of this
project is to *show* the authentication mechanics rather than delegate them.

**Opaque tokens with server-side lookup.** Revocable, but every request becomes
a store lookup — session state under a different name.

## Decision

Stateless JWT, HS256, signed with a secret from `JWT_SECRET` (no default;
startup fails without it). Access tokens only, one-hour default lifetime.

Symmetric HS256 rather than asymmetric RS256 because there is exactly one
service, which both mints and verifies. RS256 earns its keep when verifiers must
not be able to mint — multiple services, or a public key distributed to clients.

## Consequences

**Good.** No server-side session state, so any instance serves any request.
Bearer tokens are trivially usable from curl, Swagger UI, and a cross-origin SPA
without cookie machinery, and because no ambient credential is attached by the
browser, CSRF protection is not needed (documented in `SecurityConfig` at the
point of the decision).

**Bad — and this is the real cost.** **A stateless token cannot be revoked
before it expires.** Signing out, changing a password, or discovering a leak
does not invalidate an issued token. The mitigations are all imperfect:

- Short lifetime (one hour) bounds the exposure window but does not close it.
- A revocation denylist would work but reintroduces exactly the server-side
  state that motivated JWT — the honest position is that adopting it would mean
  the original choice was wrong.
- **Partial mitigation implemented:** the authentication filter reloads the
  principal from the database on every request, so a deleted or modified account
  stops authenticating immediately even though its token remains
  cryptographically valid. This costs one query per request and gives up some
  statelessness. An integration test asserts it.

Also bad: no refresh flow, so clients re-authenticate hourly; and the role claim
inside the token is informational only — authorities are re-derived from the
database, because trusting the claim would let a revoked role outlive its
revocation.

**Revisit if** sign-out must invalidate immediately, or a second service needs
to verify tokens without being able to mint them (move to RS256), or session
count needs to be observable.
