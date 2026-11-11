# Tracer: design-decisions

One entry per finding: `YYYY-MM-DD · actor · <text>`.

Decisions recorded in decisions/ (DM records) and ADR-0070; this file logs the reasoning taken during implementation.

---

2026-11-11 · claude · Fixtures live in a test-fixtures source set of `:packs` (operator decision A); deployable reads them through `testFixtures(project(":packs"))`, and ExamplePacks copies them out of the jar to a temp directory once per JVM.

2026-11-11 · claude · The open-packs validation job is a follow-up in open-packs (#188), not part of this mission (operator decision A).

2026-11-11 · claude · The `policy` kind stays in the whole-pack expected-red list, linked to #476, instead of a production change inside WP02: the gap is in the writer, outside this mission's test-only scope. Flagged to the operator.

2026-11-11 · claude · WP04 lowered three non-vacuity thresholds to the example-pack scale (guidance ≥5, command ≥10, delegation ≥1); each still has a failing control. For review to judge.
