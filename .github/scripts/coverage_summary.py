#!/usr/bin/env python3
"""Render the merged JaCoCo report as a GitHub Actions job summary.

Kept as a file rather than inlined in the workflow: an indented heredoc inside
a YAML `run:` block never terminates, because bash requires the delimiter at
column zero. As a script it is also runnable locally, which is how it was
verified.
"""
import collections
import csv
import os
import pathlib
import sys

REPORT = pathlib.Path("target/site/jacoco/jacoco.csv")

COUNTERS = (
    "INSTRUCTION_MISSED", "INSTRUCTION_COVERED",
    "BRANCH_MISSED", "BRANCH_COVERED",
    "LINE_MISSED", "LINE_COVERED",
)


def percentage(covered: int, missed: int) -> float:
    total = covered + missed
    return 100 * covered / total if total else 0.0


def main() -> int:
    if not REPORT.exists():
        print("No coverage report found at", REPORT)
        return 0

    rows = list(csv.DictReader(REPORT.open()))
    totals = collections.Counter()
    for row in rows:
        for counter in COUNTERS:
            totals[counter] += int(row[counter])

    by_package = collections.defaultdict(lambda: [0, 0])
    for row in rows:
        entry = by_package[row["PACKAGE"].replace("com/hoseacodes/propflow", "…")]
        entry[0] += int(row["LINE_COVERED"])
        entry[1] += int(row["LINE_MISSED"])

    lines = [
        "## Coverage — unit and integration, merged",
        "",
        "| Metric | Covered |",
        "|---|---|",
        f"| Lines | {percentage(totals['LINE_COVERED'], totals['LINE_MISSED']):.1f}% |",
        f"| Instructions | {percentage(totals['INSTRUCTION_COVERED'], totals['INSTRUCTION_MISSED']):.1f}% |",
        f"| Branches | {percentage(totals['BRANCH_COVERED'], totals['BRANCH_MISSED']):.1f}% |",
        "",
        "<details><summary>By package</summary>",
        "",
        "| Package | Lines |",
        "|---|---|",
    ]
    for package, (covered, missed) in sorted(
        by_package.items(), key=lambda kv: -percentage(*kv[1])
    ):
        lines.append(f"| `{package}` | {percentage(covered, missed):.1f}% ({covered}/{covered + missed}) |")

    lines += [
        "",
        "</details>",
        "",
        "_DTOs, config classes, and the entry point are excluded. Their accessors are "
        "compiler-generated, so counting them inflates the figure without describing "
        "any tested behaviour. There is deliberately no build-failing threshold: a "
        "coverage gate reliably produces tests written to satisfy the gate._",
    ]

    summary = "\n".join(lines)
    print(summary)

    target = os.getenv("GITHUB_STEP_SUMMARY")
    if target:
        with open(target, "a", encoding="utf-8") as handle:
            handle.write(summary + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
