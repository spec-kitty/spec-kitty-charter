---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T11:32:36Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP11
---

# WP11 review, cycle 1: changes requested (reviewer-renata)

The registration, relationship-write and read surfaces are correct. Each has a failing fixture and a passing twin, and
two planted mutations prove there is no fake green:

- Mutation (i), `inactivatableKind` always empty: 10 unit tests fail, and 4 of the 7 scenarios fail. Those 4 are the
  two registration refusals, the relationship refusal and the read refusal. Every twin stays green.
- Mutation (ii), load hook removed: the load unit test fails, and the 3 load-side scenarios fail (the two registration
  refusals and the 500 read). The relationship-write refusal stays green, so it is independent of the hook.

Gates on the lane tip:

- `:packs:test` passes: 4732 tests, 0 failures.
- `CharterContractSuite` passes: 745 tests, 0 failures, including all 7 scenarios of override-source-kind.
- `:contracts:check -PcontractBaseline=none` passes. The run used `-PcontractBaseline=none` because this worktree is a
  shallow clone. The changelog fragments, refusal examples and lint all pass.
- The style gates pass: Spotless, Checkstyle, javadoc and Error Prone compile.

Only the source kind is judged. A qualified source is judged like a bare one. Activatable-to-activatable edges and
other relations are unchanged.

One MEDIUM blocks approval.

## Issue 1 [MEDIUM]: validate mode does not report the fault, so a pack passes validate and fails registration

`service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java:171-197` (`PackPolicy.judge`).

Validate mode already reports every other load violation code from the graph documents it reads:

- `PackPolicy.judge` runs the graph rules (`graph(name, read.value())`, l.185).
- It runs the R-OVR and R-REL edge rules through `stepEnhancementFindings` (l.194). R-OVR is the precedent the WP
  prompt names, and it is reported in validate mode as `PROMPT_FRAGMENT_INVALID`.
- It runs the prompt slot rules (`promptSlotFindings`).

Each of the three existing load violation codes (`prompt_fragment_invalid`, `prompt_slot_malformed`,
`prompt_slot_not_empty`) has a `PackFindingCode` and a validate-mode finding. ADR-0042 decision 7 says that validate
mode agrees with the load. Its known gap covers only breaks that "only the domain sees", which need a domain value built.
This rule is a pure judgment over the edges of graph documents that validate mode already parses.

Leaving it out makes `overrides_source_not_activatable` the only load violation that validate mode passes.

Fix it as follows:

- Add a `PackFindingCode` (for example `OVERRIDES_SOURCE_NOT_ACTIVATABLE`, BLOCKING) and map it to the violation token.
  Make `OverrideSourceRule.CODE` and the finding code one source of truth, as `violationKind` does for
  `PROMPT_FRAGMENT_INVALID`.
- In `PackPolicy.judge`, judge the edges of each readable graph document with `OverrideSourceRule.judge` and add one
  blocking finding per fault at its file.
- Write the failing test first. Use a `PackPolicyTest` or `PackValidationTest` fixture with
  `template:t -overrides-> directive:D`, and a passing twin with `directive:E`.
- Record the edit as `out-of-map: .../PackPolicy.java` (and `PackFindingCode.java`). Neither file is a slice-B file.

## Issue 2 [LOW]: correct the out-of-map record for FilesystemPackStore and tell the run lead

The commit `d503ca443` records `FilesystemPackStore.java` as "the load seam outside DomainRepositoryService (slice B, PR
#501)". This does not break C-006: the WP prompt's C-006 list does not name `FilesystemPackStore`. However,
`FilesystemPackStore.java` is in the `owned_files` of slice B's WP06 (`swap-window-readers`, PR #501). Slice B's WP02
also cites `FilesystemPackStore` read l.389-409, beside the new hunk at l.402. Slice B's planned WP06 hunks
(`existingDirectory`, `realPath`, `realPathContained`) are far from this one, so the conflict risk is low.

Do not move the hunk. In the next commit, write an `out-of-map` line that states the shared ownership correctly, and
tell the run lead so the #501 rebase expects it.

## Issue 3 [LOW]: the putPackRelationship description names only two of the four kinds

`contracts/charter/paths/packs_packId_relationships.yaml:110` says "(a template or an asset; FR-014)". The rule, the
`CharterRefusalCode` description and the fragment all name four kinds: a template, an asset, a guard or a lifecycle. A
pack holds guards and lifecycles (`file-kinds.yaml`). Name all four kinds, or state why the write cannot meet a guard
or a lifecycle.

## Not blocking (classified)

- Gap (2), a step source (`action:`), is LOW and out of scope. The ruling and FR-014 list exactly
  `K ∖ Kₐ = {template, asset, guard, lifecycle}`. A step is a node of no kind, which the rules page treats separately.
  The run lead should ask the operator whether the ruling extends to steps. Do not widen the rule in this WP.
- Gap (3), guard and lifecycle covered by unit tests only, is acceptable. The rule is keyed on
  `ArtifactKind.activatable()`, the parameterized tests cover all four kinds, and
  `theFourKindsTheRuleRefusesAreExactlyTheKindsThatAreNotActivatable` pins the set. No scenario is needed.
- FilesystemPackStore.read now parses the graph documents once more per load, because its callers already call
  `readGraphs`. This is a LOW cost, accepted because `DomainRepositoryService` is off-limits.
