# WP01 review feedback (cycle 1) — reviewer-renata / claude-reviewer

Build: `./gradlew --rerun-tasks :missions:check :deployable:test --tests '*HexagonalArchitectureTest*'` is green.
Domain is faithful to data-model.md and ADR-0044 §2/§3/§7; wiring (settings, charter-reports `serviceModules`,
`SLICES`, `DOMAIN_RINGS`, `MISSIONS_DOMAIN_IS_PURE`, slice-crossing bite, `ENTRYPOINT_STAYS_IN_ITS_SLICE`) matches the
`:profiles` precedent; `@NullMarked`, Javadoc, no suppressions. The `api(project(":missions"))` line in
`service/packs/build.gradle.kts` (landed in 095c056) is a known closeout item and is not raised here.

The WP prompt requires "one accepted/refused pair per invariant (... blank fields ...)". Three guarded invariants have
no test at all: deleting their rule leaves every test green.

**Issue 1 (medium)**: `MissionType` schema-version-not-blank is untested.
`service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionType.java:33-34` guards it;
`MissionTypeTest.blankTextsAreRefused` (MissionTypeTest.java:201-209) covers title, description and content only.
Fix: add `research().schemaVersion(" ").build()` refused, beside the existing accepted neighbour.

**Issue 2 (medium)**: `MissionStep` title-not-blank and description-not-blank are untested.
`service/missions/src/main/java/ai/speckitty/charter/missions/domain/MissionStep.java:44-45,79-80` guards them; no test
builds a step with a blank title or a present-but-blank description. Fix: add a refused twin for each (title `" "`,
description `Optional.of("")`) and an accepted neighbour with a non-blank description.

**Issue 3 (low)**: `MissionTypeTest.stepIdsAreUniqueAcrossBothLists` (MissionTypeTest.java:137-146) has refused twins
only; its accepted neighbour lives in another test. Add, in the same test, the distinct-id case
(`research().onDemandStep(step("retrospect")).build()` does not throw) so the pair reads together.

Anti-pattern checklist: 1 dead code — PASS (domain consumed by WP03 binding per plan; `api` edge from `:packs` in place);
2 synthetic fixture — PASS; 3 silent empty return — PASS; 4 FR coverage — FAIL for FR-006's guarded fields as above
(FR-005 PASS); 5 frozen surface — PASS; 6 locked decision — PASS (no file paths beyond the data-model's
`templatePath`/`pathPattern`; references restricted to the six guidance kinds); 7 shared-file ownership — PASS
(packs build line noted as closeout); 8 production fragility — PASS (ConstraintBreachException is the fail-loud guard).
