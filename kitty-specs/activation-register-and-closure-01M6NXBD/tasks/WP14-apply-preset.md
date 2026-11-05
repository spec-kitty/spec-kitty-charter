---
work_package_id: WP14
title: apply a preset (scenarios first) and retire the binding pin
dependencies:
- WP13
requirement_refs:
- FR-016
- FR-010
- C-001
- NFR-002
- NFR-007
- SC-001
- SC-002
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T084
- T085
- T086
- T087
- T088
- T089
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/PresetRules.java
create_intent:
- contracts/charter/scenarios/activation-presets.feature
- contracts/charter/changes/2026-11-05-activation-preset-application.md
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/PresetRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationPresetService.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/PresetRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationPresetServiceTest.java
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/paths/activations_activationId_presets_presetId.yaml
- contracts/charter/scenarios/activation-presets.feature
- contracts/charter/changes/2026-11-05-activation-preset-application.md
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/PresetRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationPresetService.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/PresetRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationPresetServiceTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP14: apply a preset (scenarios first) and retire the binding pin

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Build `applyActivationPreset` (`PUT /activations/{id}/presets/{presetId}`), red first (FR-016, US5):

- each key the preset lists replaces that key in the document, `activatedKinds` and `missionTypes` included; other keys
  are untouched (ADR-0059 D6);
- `force` also clears the deny entries that match the preset's members, reported in `cleared`;
- without `force`, a denied member is not selected, and its deny entry stays;
- `applied` lists the members the write selected;
- a preset id no pack in the closure holds answers `404 preset_not_found`.

The contract drops the last write's `x-reserved`. The reserved count goes 5 → 4: only the four effective reads remain.
The "501 before binding" pin is retired, because no reserved operation takes a body any more. A test asserts it.

Implementation command:
`spec-kitty agent action implement WP14 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** B. **Concern:** IC-09b. **Contract delta:** "PR B" (`preset_not_found` raised; `x-reserved` removed).
- **Rulings:** ADR-0059 D6 (replace each listed key, core's behaviour), ADR-0020 D6 (`force` clears matching deny
  entries), ADR-0031 and ADR-0042 (presets as pack-level files).
- **Collisions:** rebase PR B on PR A and `main`.
- **Grounding:**
  - The packs `Preset` record has `activatedKinds` (a `List`, `List.copyOf`), `artifacts` (an `EnumMap` per kind) and
    `missionTypes`. An absent `activated_kinds` in the YAML and `activated_kinds: []` both become `[]` there. So
    "the preset lists the key" can only be read as "the list is non-empty" unless the storage DSO keeps the
    distinction. Check `PresetDso` and `PresetFiles.toPreset`. If the DSO keeps absence, carry an
    `Optional`/"listed" flag through WP04's `ClosureSnapshot.PresetView`. If not, rule "listed = present and
    non-empty", record it as an open review item in the PR description, and pin it with a test. Do not widen the
    packs `Preset` record in this WP without the operator's agreement.
  - WP04's `ClosureView.presets` resolves a preset id to its holding pack (the last in merge order wins).
  - WP13 moved the binding pin to this operation; it is the last reserved operation with a request body.
  - `ActivationBodyAdvice` (WP09) already guards `PresetApplication`.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure` (PR B).
- Lane worktree from `lanes.json`. It depends on WP13 (shared write flow, session port).
- Rebase onto the lane base before review.

## Out-of-map edits (sequenced by dependency)

- `contracts/charter/schemas/CharterRefusalCode.yaml` (owner WP09): `preset_not_found` becomes `raised: true`; the
  reserved-codes paragraph names only the codes of the effective reads.
- `contracts/charter/scenarios/activations.feature` (owner WP08): remove the preset pin and the binding pin; reword the
  feature text (only the four effective reads remain, built by the resolution mission, #103).
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ReservedOperationCoverageTest.java` (owner
  WP08): 5 → 4, and the new no-body assertion.
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java` (owner WP09):
  `PRESET_NOT_FOUND` raised.
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java` (owner
  WP05): implement `applyActivationPreset`.
- `service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java` (owner WP13):
  reuse it; add a hook only if the preset flow needs one.
- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java` (owner WP06), and
  the adapter (owner WP05): the "listed" flags, only if the DSO keeps absence.

## Subtask Guidance

### Subtask T084: Contract B, part two

**Steps:**
1. `paths/activations_activationId_presets_presetId.yaml`: remove `x-reserved`, the 501 sentence and the `501`
   response. Check it declares `200` (`ActivationChange`), `404` (`preset_not_found` and `activation_not_found`),
   `412`, `428` and `422`. Reword the description to ADR-0059 D6, naming `activatedKinds` and `missionTypes`.
2. `CharterRefusalCode.yaml`: `preset_not_found` → `raised: true`.
3. Fragment `contracts/charter/changes/2026-11-05-activation-preset-application.md` (date it the day you write it),
   `### Changed`: `applyActivationPreset` is served; a preset replaces each key it lists; `force` clears; and only
   the four effective reads stay reserved. This is the closing fragment of contract B.
4. `./gradlew :contracts:check :charter-api:compileJava`.

### Subtask T085: Red scenarios (US5)

**Steps:**
1. `contracts/charter/scenarios/activation-presets.feature`, tagged `@contract @activations`. Fixture: an org pack with
   `presets/team.yaml` listing directives B and C and `activated_kinds: [directive]`, and a preset `force-me` whose
   member M the document denies. Use the existing file step. Copy the preset shape from a fixture already in
   `composition.feature` or from `contracts/packs/schemas/preset.schema.json`.
2. Scenarios:
   - US5 AS1: from `selected.directive` [A] and `selected.tactic` [T], apply `team` → `selected.directive` [B, C],
     `activatedKinds` [directive], tactic [T] untouched, `applied` [B, C];
   - US5 AS2: without `force`, M is not selected, the deny entry stays, and M is absent from `applied`; with `force`,
     M is in `cleared` and selected;
   - US5 AS3: an unknown preset answers 404 `preset_not_found`;
   - **`missionTypes` replacement (FR-016, R7):** the starting document has `missionTypes: [old-type]`, and the preset
     lists `activated_mission_types: [software-dev]`, which the closure holds. Applying it gives
     `missionTypes: [software-dev]`, and `applied` lists `{kind: mission_type, artifactId: software-dev}`. A preset
     that lists no mission types leaves `missionTypes` untouched;
   - a preset held by a pack **outside** the activation's closure also answers 404 (FR-010);
   - a missing `If-Match` answers 428; applying the same preset twice answers the same revision the second time
     (no-op).
3. Run the suite and record the failing names (501 now).

### Subtask T086: Retire the binding pin (5 → 4)

**Steps:**
1. `activations.feature`: remove the preset pin and the "answers 501 before it reads its request" pin.
2. `ReservedOperationCoverageTest`: `theActivationSetReservesFourOperations` with `hasSize(4)`. Add
   `noReservedOperationTakesABody`, which asserts that no `x-reserved` operation in the contract declares a
   `requestBody`. It is the reason the binding pin can go: if a future reserved write with a body appears, the test
   fails and asks for the pin back. Name that in its message.
3. `ReservedRefusalCodesTest`: `PRESET_NOT_FOUND` raised. With all writes built, the unraised activation codes are
   only those the effective reads would raise; check the list matches the contract.

### Subtask T087: `PresetRules` (pure domain)

**Steps (red first, `PresetRulesTest`):**
1. Replace listed keys: each `selected.<kind>` the preset lists becomes exactly its members (sorted, deduplicated);
   unlisted kinds stay as they are; `activatedKinds` and `missionTypes` are replaced when listed.
2. Without `force`, members that are denied are left out of the replaced list, and their deny entries stay (US5 AS2).
3. With `force`, the deny entries of members are cleared (`cleared`), a deny list that empties is dropped, and the
   members are selected.
4. `applied` lists the members that are selected after the write and were not before (state changes, as for select),
   sorted.
5. A member the closure does not hold (a stale preset): it is not selected, and it is reported as a violation by
   `DocumentRules` when the service checks the result. Decide: skip it with no error, or refuse 422. Recommended:
   `DocumentRules` refuses 422 at `/presets/{presetId}`, since a broken preset is a pack fault; record the choice.
6. The preset's must-have items are the floor (WP04). Applying the preset never denies them, so no 409 can arise from
   a preset; assert that in a test.

`PresetRules.apply(document, PresetView preset, boolean force)` returns an `ActivationChange` (WP12's value), with an
empty skipped report.

### Subtask T088: `ActivationPresetService` and the controller

**Steps (red first, `ActivationPresetServiceTest`):**
1. Use `ActivationWrites` (WP13): `If-Match` (428 and 412), load (404 activation), one session, then resolve
   `presetId` in `snapshot.presets()` (404 `preset_not_found`), `PresetRules.apply`, `DocumentRules.check`, a no-op
   check, and the CAS write.
2. `ActivationController.applyActivationPreset`: map the change with WP13's `ActivationChangeMapper`; set `ETag`;
   remove its `@ReservedOperation`. Only the four effective reads stay marked.

### Subtask T089: All green, and evidence

**Steps:** run the gates; record the red-run names from T085, the green summary, and the reserved count 4 with the
no-body test.

## Checkstyle limits (NFR-001)

The limits are still off on `main`, held until the methods over them are refactored, so they are held by
construction: the reviewer measures new and changed methods by hand.

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. No new suppression, no disabled test, and no
`@WIP`.

## Gate commands

```bash
./gradlew :contracts:check :charter-api:compileJava
./gradlew :activations:check
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*ReservedOperationCoverageTest*' \
  --tests '*ReservedRefusalCodesTest*' --tests '*OperationCoverageTest*' --tests '*HexagonalArchitectureTest*'
```

## Definition of Done

- [ ] `applyActivationPreset` is served per ADR-0059 D6; `force` clears; unknown and outside-closure presets answer 404.
- [ ] `activation-presets.feature` covers US5 AS1–AS3 and the edge cases; each was seen red first.
- [ ] The reserved count is 4; the binding pin is gone; `noReservedOperationTakesABody` holds.
- [ ] `preset_not_found` is raised, and the contract B fragment is written.
- [ ] The "listed key" reading of a preset is decided, tested and recorded.
- [ ] No new suppression, no disabled test, and no `@WIP`.

## Risks

- **Absent versus empty in presets** (see Grounding). This is the one place PR B may need an operator ruling; do not
  silently pick.
- **Preset id collisions across packs**: the merge-order rule from WP04 decides; a scenario with two packs holding the
  same preset id pins it.

## Reviewer guidance (reviewer-renata)

- **Untouched keys:** US5 AS1 must assert `selected.tactic` is still [T]. Without it, "replace the whole document"
  passes.
- **Force:** check both halves of US5 AS2 (with and without `force`), and that the deny entry **stays** without it.
- **Pin retirement is safe:** read `noReservedOperationTakesABody`, and check it fails when you add a `requestBody` to
  a reserved path in a scratch copy.
- **Non-fakeable evidence:** the `ReservedOperationCoverageTest` output (4), the suite summary, and the recorded
  "listed key" decision.
