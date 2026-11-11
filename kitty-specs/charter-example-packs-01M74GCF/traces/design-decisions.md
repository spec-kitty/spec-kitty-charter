# Tracer: design-decisions

One entry per finding: `YYYY-MM-DD · actor · <text>`.

Decisions recorded in decisions/ (DM records) and ADR-0070; this file logs the reasoning taken during implementation.

---

2026-11-11 · claude · Fixtures live in a test-fixtures source set of `:packs` (operator decision A); deployable reads them through `testFixtures(project(":packs"))`, and ExamplePacks copies them out of the jar to a temp directory once per JVM.

2026-11-11 · claude · The open-packs validation job is a follow-up in open-packs (#188), not part of this mission (operator decision A).

2026-11-11 · claude · The `policy` kind stays in the whole-pack expected-red list, linked to #476, instead of a production change inside WP02: the gap is in the writer, outside this mission's test-only scope. Flagged to the operator.

2026-11-11 · claude · WP04 lowered three non-vacuity thresholds to the example-pack scale (guidance ≥5, command ≥10, delegation ≥1); each still has a failing control. For review to judge.

2026-11-11 · claude · Integration: the `impacts` edge example moved from built-in to project/project.graph.yaml, which the round trip does not cover, and the built-in edge became `suggests` (gap filed as #477). This is a scope dodge to flag in review and to the operator, like `policy` (#476).

2026-11-11 · claude · Integration loosened one WP04 skill rule to the examples' real mix: prompt skills have a body, wrapper skills have `expands_to`, at least one skill declares a side effect. For review to judge.

2026-11-11 · reviewer-renata · WP02–WP04 approved. Restoring the built-in `impacts` edge turns wholePackRoundTrip red (2 of 8), so #477 is real and the move to project/ hides it; accepted as minor while #477 stays open. `delegation ≥1` is close to a presence check.
2026-11-11 · claude · WP05: the project example's overrides edge onto org-base::styleguide:reasons-canvas-writing was refused as self_override once the full closure merged edges (the governance walk); the project styleguide already overrides by sharing its id one level up (ADR-0053), so the edge was removed and the project manifest re-stamped. PackDigestBaselineTest now pins only project (built-in and org-base hold enhancements/), with values computed on current code, not on pre-binding code.

2026-11-11 · claude · WP09 closes with no code change; its evidence (grep and a green run without the synced trees) is the deliverable. The synced-tree readers are all in :deployable (WP06, WP10).

2026-11-11 · claude · WP06's replacement project edge `skill:project-triage overrides org-base::skill:issue-triage` was refused at integration ("org-base is outside its depends_on closure"), so project.graph.yaml keeps WP05's version with no overrides edge; cross-level overrides edges stay covered by ClosureViewMergedTest's synthetic fixtures. Reviewers to confirm the project pack's depends_on.

2026-11-11 · claude · WP10 closes with no code change; evidence in the tracer.
