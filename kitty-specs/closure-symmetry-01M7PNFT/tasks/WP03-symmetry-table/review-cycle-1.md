---
affected_files: []
cycle_number: 1
mission_slug: closure-symmetry-01M7PNFT
reproduction_command: spec-kitty agent tasks move-task WP03 --to approved --mission closure-symmetry-01M7PNFT --agent claude-reviewer
reviewed_at: '2026-11-18T09:39:08Z'
reviewer_agent: claude-reviewer
wp_id: WP03
---

Approved by claude-reviewer: approval:WP03

# WP03 review: approved (independent reviewer claude-reviewer, session_019C2Tk5VeSiEfU1VoQEpCNv)

Reviewed at 302791e (f517b4f, with the c6b8c59 split) against the WP03 prompt, FR-006, SC-001, User Story 3, the spec edge cases and research D5 and plan-review findings 3 and 8.

## Verified
- `ClosureSymmetryTest` (:pack-validation) has 17 rows over a copy of the built-in example pack (plan-review finding 8). Each row first proves its repaired control on every path (DIRECTIVE_041), then judges the broken tree on five paths: validate mode, load, PUT /packs verdict (finding 3), whole-pack write, and per-file commit or remove.
- Rows 1-11 are the closure cases; row 1 asserts the full cascade (asset -> guard -> lifecycle), and row 11 is T016's delete (refused, not cascaded). Rows 12-17 are the Executable and Lifecycle invariants; on the write paths the value is `Unbuildable` (D5).
- Local run: 17/17 green. The implementer reports non-vacuity: with the judge removed, all 11 closure rows fail; with the rule blanked, they fail at validate mode.

## Deviations accepted
- T017 (fold the per-case load tests) is not done. FR-002/SC-002 ("existing load tests pass unchanged") conflicts with FR-006's "folded in". Analysis finding F1 resolves it toward the ratchet, and the PR body says so. Accepted.
- Validate mode words a missing named file "names <path> ...", where the load has "<path> ...". The table matches them by #554's `sameFault` (same file, ends with the load's words) and does not reword the reader. Accepted.

## Non-blocking findings (LOW)
1. Row 10 deletes the type's template, so on disk the reader's named-file check (`...: missing`) fires before the closure on validate and load. The closure's own wording (`is not a template of the type`) appears only on the write paths. The spec edge case "a top-level template, not one of the type's own" exists only in an in-memory aggregate (a `Template` with the type's id and no mission type). It is covered for the whole-pack write by `MissionTypeBindingTest`, but there is no per-file commit row. Consider a `Commit` variant of row 10 that writes that top-level template.
2. Nit: row 10's inline comment is wrapped mid-sentence ("the two write\n// paths").
