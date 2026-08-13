# ADR-006: `BigDecimal` over `NUMERIC` for monetary values

**Status:** Accepted · **Date:** 2026-08

## Context

`Transaction.amount`, `TaxDetails.taxAmount`, and `RefundInfo.refundAmount` were
Java `Double`, mapped to PostgreSQL `DOUBLE PRECISION`. `Property.basePrice` was
already `BigDecimal`, so the codebase contradicted itself.

This is a financial ledger whose primary purpose is producing income and expense
totals for tax reporting.

## The problem

`double` is IEEE-754 binary floating point. It cannot represent most decimal
fractions exactly — 0.1 has no finite binary expansion, exactly as 1/3 has no
finite decimal one. So:

```java
0.1 + 0.2 == 0.30000000000000004   // true
```

Individually invisible. Summed across a year of transactions the error
accumulates, and the totals do not reconcile against the statements they exist
to match. In a tax report, a figure that is nearly right is wrong.

## Options considered

**Store cents as `BIGINT`.** Exact, fast, and common in payment systems.
Rejected because every read and write needs scaling, which is a conversion
someone eventually forgets, and it does not generalise to tax rates or currency
with more than two decimal places.

**Keep `double`, round on display.** Rejected: rounding at the edge does not
undo error already accumulated in the sum. It hides the symptom.

**`BigDecimal` over `NUMERIC(19,2)`.** Chosen.

## Decision

`BigDecimal` in Java, `NUMERIC(19,2)` in PostgreSQL. Migration `V5` converts
with `ROUND(column::numeric, 2)`.

Scale 2 covers currency to the cent. Precision 19 leaves ample headroom while
staying within the range a 64-bit integer count of cents could hold.

`V5` also adds `CHECK (transaction_amount > 0)`: direction is carried by the
`type` column (`INCOME`/`EXPENSE`), not by the sign, so a zero or negative
amount is meaningless. Request validation enforces the same rule with
`@DecimalMin("0.01")` and `@Digits(fraction = 2)` — an over-precise value is
rejected with a 400 rather than silently rounded, because a silently rounded
price is a wrong price the client never learns about.

**Migration consequence, stated in the migration itself:** existing values are
rounded to two decimal places. A stored `10.005` — which a float may actually
hold as `10.004999999999999` — becomes `10.00`. Any value that was genuinely
currency is unaffected, having at most two decimal places by construction. No
rows are deleted, no column dropped.

## Consequences

**Good.** Money is exact. An integration test proves it end to end: two
transactions of `0.10` and `0.20`, summed by PostgreSQL, equal exactly `0.30`.
Comparisons use `compareTo` rather than `equals`, since `BigDecimal.equals`
considers scale — `1234.56` and `1234.5600` are equal in value but not by
`equals`, a trap worth knowing.

**Bad.** `BigDecimal` arithmetic is slower than primitive `double` and more
verbose (no operators). Irrelevant here — these values are summed and reported,
not used in tight numeric loops. `NUMERIC` is also slower than a float type in
PostgreSQL, on a workload nowhere near being arithmetic-bound.

**Neutral.** Division would require an explicit `RoundingMode`, which is
`BigDecimal` forcing a decision that `double` would have made silently and
wrongly.
