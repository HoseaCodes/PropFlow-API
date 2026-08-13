# ADR-005: A single deployable, not microservices

**Status:** Accepted · **Date:** 2026-08

## Context

This is a portfolio project intended to demonstrate senior engineering
judgement. There is a real temptation to reach for distributed architecture
because it *looks* senior.

## Decision

One Spring Boot application, one PostgreSQL database, organised into clear
internal layers with enforced boundaries.

## Rationale

Microservices solve organisational and scaling problems that this system does
not have: independent deployment by separate teams, independent scaling of parts
with different load profiles, and fault isolation between genuinely independent
capabilities. PropFlow has one bounded context, one team, and one coherent
transactional model in which properties, transactions, and users are constantly
joined.

Splitting it would trade in-process method calls for network calls that can fail
partially, and ACID transactions for eventual consistency and compensating
actions — in a **financial ledger**, where "the transaction was recorded but the
property update was lost" is precisely the failure that must not happen.

The same reasoning rejects the rest of the resume-driven stack:

| Technology | Why not |
|---|---|
| **Kafka** | No asynchronous integration, no stream, no second consumer. Pure ceremony. |
| **Redis** | No measured cache need. A cache without a measured hit rate adds an invalidation-bug surface for imaginary gain. |
| **Kubernetes** | A container image and Compose demonstrate the same portability without operational theatre. |
| **CQRS / event sourcing** | Read and write models are the same shape. Complexity in search of a problem. |
| **GraphQL** | Clients are not query-shape-constrained. Adds an N+1 surface this project just finished removing. |
| **MapStruct** | Mapping is trivial at this size, and hand-written mapping keeps "what crosses the boundary" visible in reviewable code. Deliberately omitting a field is the point. |
| **OpenTelemetry** | One service, no collector, nothing to correlate across. Documented in `OPERATIONS.md` as how it *would* be introduced. |

## Consequences

**Good.** One process to run, debug, and reason about. Real ACID transactions
across the whole domain. A reviewer can read the system in an afternoon. No
distributed-systems failure modes to handle, so none are handled badly.

**Bad.** Everything scales together — a hot reporting endpoint means scaling the
whole application, not one component. A single database is the scaling ceiling.
A single deployable means one release cadence. Internal boundaries are enforced
by convention and review rather than by the network, so they can erode without a
compiler noticing.

**Honest framing:** this project is small enough that a monolith is not merely
acceptable, it is correct. The judgement being demonstrated is *not* "monoliths
are good" — it is matching architecture to actual constraints, and being able to
say which constraints would change the answer. Those are: separate teams needing
independent deploys, one capability with a load profile the rest does not share,
or a component whose failure must not take the rest down.
