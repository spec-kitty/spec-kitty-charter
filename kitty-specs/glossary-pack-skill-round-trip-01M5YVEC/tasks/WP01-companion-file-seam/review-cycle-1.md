---
affected_files: []
cycle_number: 1
mission_slug: glossary-pack-skill-round-trip-01M5YVEC
reproduction_command:
reviewed_at: '2026-10-27T16:32:02Z'
reviewer_agent: claude
wp_id: WP01
---

# WP01 review feedback (reviewer-renata) — changes requested

**Issue 1 (BLOCKING) — `.yaml` rule is bypassable; a companion can overwrite or delete `pack.yaml` / an artifact file.**
`Companions.resolve` (Companions.java:107) tests `.yaml` on the *declared string*, but the file used is `base.resolve(relative).normalize()` (line 120). `Path.of("pack.yaml/")` drops the trailing slash and `x.tactic.yaml/.` normalizes to `x.tactic.yaml` (verified in jshell: `/pack/pack.yaml`, `/pack/x.tactic.yaml`). So:
- `write` of a *replaced* artifact (no "taken" check) declaring `pack.yaml/` writes companion text over `pack.yaml` or another artifact's YAML;
- `removeDropped` will `Files.delete` such a file when the path is dropped — violating "deleting an old companion must never delete a `.yaml`";
- read accepts an artifact YAML as a companion.
Fix: apply the `.yaml` (case-ignored) test to the resolved file's name (`file.getFileName()`), and/or refuse declared paths with a trailing separator or `.`/empty segments. Add tests for `pack.yaml/`, `x.tactic.yaml/.` on read, write and remove.

**Issue 2 (HIGH) — the "named by two artifacts" case is untested (vacuous on Linux).**
`unreadableCompanionsAreViolations` uses `shared.md` vs `SHARED.md`; on a case-sensitive FS `SHARED.md` does not exist, so `second` is excluded as `missing`, not by the claim check. Deleting the claim logic keeps the test green. Use the same-case path (or `./shared.md`, `sub/../shared.md`) for a real collision, and assert the reason `named by two artifacts`.

**Issue 3 (MEDIUM) — store violation text is never asserted.**
`reasons()` calls `Companions.read` directly, so nothing checks that `readKind` records `PackElementViolation(file, "companion", "<path>: <why>")` (a broken impl that drops the artifact silently passes). The DoD says every refusal asserts the exact reason. The javadoc points to `SkillYamlTest`, which does not exist yet. Expose the violations for the fixture (e.g. a package-private `readKind`-based helper, or assert through `readOne`/a violations-returning overload) and assert `"<path>: <why>"` for each case, including the collision.

**Issue 4 (MEDIUM) — partial write / ordering in `write` (FilesystemPackStore.java:406-416).**
Companions are validated and written in one loop, so with two companions the first is written before the second is refused ("nothing is written" holds only for one companion). `removeDropped` runs before the artifact write, so a failed artifact write leaves the old file naming a deleted companion. Validate all companions (content, rule, taken, containment, duplicate claims within the DSO) before any write, and remove dropped companions after the artifact file is written.

**Issue 5 (LOW)** — `hasCompanions()` must be overridden alongside `companions()` or orphans are never removed; document the coupling on `companions` (or derive it). Missing tests: symlinked *parent directory* pointing outside the pack; positive control on an existing bound kind's file text (T004 last bullet). Fixture `fileToken` "fixture" vs kind TACTIC means a created `n.fixture.yaml` is not re-locatable; note or align.

Checklist: dead code PASS (seam; consumers in WP04/05 by design); synthetic fixture PASS; silent empty return PASS; FR-005 coverage FAIL (Issue 2/3); frozen surface PASS; locked decision FAIL (Issue 1: companion may be a YAML file); shared-file ownership N/A; fragility PASS. Out-of-map `Companions.java` is acceptable (single path rule, recorded). Style/javadoc match surroundings. CompanionFilesTest 8/8 green.
