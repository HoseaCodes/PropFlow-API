# PropFlow API

Engineering documentation for **PropFlow** — a REST API for managing short-term
rental properties and their financial ledger.

[Source on GitHub :fontawesome-brands-github:](https://github.com/HoseaCodes/PropFlow-API){ .md-button .md-button--primary }
[API reference](api/index.html){ .md-button }

!!! warning "Status: portfolio project — not production-ready, not deployed"
    This serves no real users and carries no real data. Every capability
    documented here is implemented and covered by tests, but the
    [security limitations](SECURITY.md#known-limitations) are real and worth
    reading before drawing conclusions.

## Start here

<div class="grid cards" markdown>

-   :material-sitemap: **[Architecture](ARCHITECTURE.md)**

    Layers and the boundary rules that are load-bearing, domain model, data and
    transaction architecture, deployment shape.

-   :material-shield-lock: **[Security](SECURITY.md)**

    Authentication and authorization models, secret management, a disclosed
    credential incident, and the full limitations list.

-   :material-heart-pulse: **[Operations](OPERATIONS.md)**

    Health probes, metrics worth alerting on, failure modes, troubleshooting,
    and what breaks first under load.

-   :material-file-document-multiple: **[Decision records](adr/README.md)**

    Six decisions where a competent engineer could reasonably have chosen
    otherwise — each stating its downsides.

</div>

## What this project demonstrates

| | |
|---|---|
| **Authorization that fails closed** | Ownership enforced *inside the query*, not checked after loading |
| **Migrations** | 7 Flyway migrations, `ddl-auto=validate`, migrations that refuse to destroy data |
| **Integration testing** | 110 integration tests against real PostgreSQL via Testcontainers — no mocked repositories |
| **Relational modelling** | Real foreign keys, `CHECK` constraints, functional unique indexes, `ON DELETE RESTRICT` |
| **Query performance** | Composite indexes chosen from actual access patterns; N+1 removed and pinned by a query-count test |
| **Observability** | Actuator with liveness and readiness deliberately separated |

## The honest part

This repository began as a prototype with serious defects: an entirely
unauthenticated API, a database password committed to a public repo, plaintext
passwords on one code path, and a README advertising JWT authentication that did
not exist.

Rather than quietly fixing them, the [audit that found them](ENGINEERING_AUDIT.md)
is published alongside the [plan](PORTFOLIO_HARDENING_PLAN.md) that addressed
them. The remediation process is offered as evidence alongside the result.
