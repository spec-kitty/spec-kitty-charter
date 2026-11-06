---
work_package_id: WP06
title: A0 · Presets name any kind
dependencies:
- WP02
- WP03
requirement_refs:
- FR-041
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-layer-merge-and-effective-reads-01M6R0J6
base_commit: 6ab77f6aa2ec351bbe4ed7f13763b38c046cd55f
created_at: '2026-11-06T16:33:00.218507+00:00'
subtasks:
- T025
- T026
- T027
- T028
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/PresetRules.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/PresetRules.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationPresetService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Preset.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PresetItem.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetFiles.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetDso.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/PresetRulesTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationPresetServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PresetYamlTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PresetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PresetRoundTripTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP06: A0 · Presets name any kind

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

- **Q3 (FR-041):** a preset may name artifacts of any kind; the skill and glossary_pack exclusion and W17's strip
  (`PresetRules.java:74-81`) are dropped. `activated_mission_types` is a plain member list.
- A pack's preset carrying the `activated_kinds` key is refused at registration (`422 validation_failed`, one
  violation naming the file).
- `Preset.missionTypes` and `requiredMissionTypes` fold into the normal per-kind lists; `Preset.ACTIVATION_KINDS` and
  `PresetYaml.LIST_KEYS` come from the kernel registry (WP02).

Implementation command:
`spec-kitty agent action implement WP06 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-20 (presets). **Requirements:** FR-041.
- **Rulings:** Q3 `DM-01M6RCY40CQQTHC130RWF43W0D`; A10 (preset items count as inbound references) is used by WP14/WP19.
- **Evidence:** `research/a7-codeshape.md` items 3, 5, 6 (`"activated_mission_types"` branch in `PresetFiles`,
  the 10 hand-written strings in `PresetYaml.LIST_KEYS`).
- **Ruleset:** W17.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java` — the preset view stops carrying `activatedKinds`/`missionTypes` separately (`PacksAvailability.java:176-179`; owned by WP24)
- `service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/PacksAvailabilityTest.java` — the preset view fields folded into the ordinary lists (owned by WP24)


## Project constraints (apply to every subtask)

- **TDD, contract first (C-001).** Write the failing test or `@contract` scenario first, watch it fail for the right
  reason, then write the code. A contract change lands with its `contracts/charter/changes/` fragment before the code
  that serves it.
- **No disabled tests, no new suppressions (C-006, NFR-001).** No `@Disabled`, no `@WIP` left on a scenario this WP
  makes green, no `@SuppressWarnings`/Checkstyle/PMD/SpotBugs suppression added, and the suppression register does not
  grow. Methods stay within CC ≤ 15, NPath ≤ 1000, ≤ 60 lines and nested-if depth ≤ 1.
- **Hexagonal slices, no shadow logic (C-002).** Merges and walks live in `packs`; `activations` reaches `packs` only
  through the `AvailablePacks` port and `activations.infra.packs`. `HexagonalArchitectureTest` must stay green.
- **No core changes (C-003).** Nothing in spec-kitty core is edited, and no PR or issue is opened in core.
- **git is a host binary (C-008).** Only the system `git` ≥ 2.50.1, never JGit, never a work-tree checkout.
- **Gradle.** Run module tasks, not the full suite (`NO_FULL_HEAVY_SUITES_IN_MISSION`):
  `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none <module>:check`.
  On a Maven Central `429`, wait and re-run the same command; never turn dependency verification off.
- **Divergences (C-007).** Every behaviour that differs from core gets a divergence-register entry in the PR that
  ships it (the docs WP of that PR writes it; tell it what you changed in your hand-off note).
- **Commits** end with the session's attribution lines; no model id in commits or PR text.

## Branch Strategy

- Planning branch: `issue-10-layer-merge-effective-reads`; final merge target: `issue-10-layer-merge-effective-reads`.
- Execution worktrees are allocated per computed lane from `lanes.json`; do not pick a base branch by hand. Dependencies are a claim gate: this WP can be claimed only when its dependencies are approved or done.
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A0).

## Subtasks

### T025: Preset model on the registry

- `Preset` holds one map kind → items; `ACTIVATION_KINDS` is `ArtifactKind.activatable()`.
- Tests: a preset with skills and glossary packs round-trips.

### T026: Preset YAML read and write

- `PresetYaml`/`PresetDso`/`PresetFiles` derive the `activated_<plural>` keys from the registry; the special
  `activated_mission_types` branch goes.
- `activated_kinds` present → registration `422` (the read is used by registration's `WholePackStore.load`).
- `PresetYamlTest` covers both.

### T027: Apply-preset without the strip

- `PresetRules` applies every kind of the preset; remove the kind-gate filter (lines 74–81) and the test
  `aKindGateTheDocumentCannotHoldIsLeftOutOfIt` (rewrite to the new expectation).
- `ActivationPresetService` reports what was applied per kind, mission types included.

### T028: Must-have items of every kind

- Must-have items of any kind (mission types included) feed the floor; `RequiredFloor` already accepts mission types
  — verify with a test in `PresetRulesTest` that a must-have mission type appears in the floor.

## Test strategy

`PresetRulesTest`, `ActivationPresetServiceTest`, `PresetYamlTest`; a startup test that the built-in packs still load.

## Risks

- Built-in packs under `packs/built-in/` may carry `activated_kinds`: search first; if any do, the shipped packs must be updated in this WP (add them to the hand-off note) or registration of built-ins would fail at startup.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` and `:activations:test` pass, including `PresetTest`, `PresetRoundTripTest` and the offering read over a preset with mission types; the built-in packs load at startup.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
