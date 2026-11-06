---
work_package_id: WP01
title: 'A0 · Contract: one kind model on the activation document'
dependencies: []
requirement_refs:
- FR-040
- FR-041
- C-001
- C-007
- NFR-002
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/schemas/
create_intent:
- contracts/charter/changes/2026-11-07-a0-one-kind-model.md
- contracts/charter/scenarios/activation-kind-model.feature
execution_mode: code_change
model: ''
owned_files:
- contracts/charter/schemas/ActivatableKind.yaml
- contracts/charter/schemas/SelectableKind.yaml
- contracts/charter/schemas/CascadeKind.yaml
- contracts/charter/schemas/Activation.yaml
- contracts/charter/schemas/ActivationConfig.yaml
- contracts/charter/schemas/ActivationChange.yaml
- contracts/charter/schemas/ActivationSummary.yaml
- contracts/charter/paths/activations.yaml
- contracts/charter/paths/activations_activationId.yaml
- contracts/charter/paths/activations_activationId_selections_kind_artifactId.yaml
- contracts/charter/paths/activations_activationId_exclusions_kind_artifactId.yaml
- contracts/charter/paths/activations_activationId_presets_presetId.yaml
- contracts/offering/corpus/ActivationConfig/**
- contracts/offering/schemas/ActivationConfig.schema.json
- contracts/packs/schemas/preset.schema.json
- contracts/charter/changes/2026-11-07-a0-one-kind-model.md
- contracts/charter/scenarios/activation-kind-model.feature
- contracts/charter/examples/ActivationChange.selected.yaml
role: implementer
tags: []
tracker_refs: []
---
# WP01: A0 · Contract: one kind model on the activation document

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make the contract say what the A7 ruling decided, before any code serves it: mission types, steps and agent
profiles are ordinary kinds of the activation document. The document is `⟨packs, selected, deactivated, languages⟩`;
`selected` and `deactivated` gain `mission_type`; `ActivatableKind` and `SelectableKind` become one enum;
`activatedKinds` and `missionTypes` leave every request and response and are refused `422` on any write (no
deprecation window, Q4); a preset may name any kind; the stored `schemaVersion` becomes `2`. Write the failing
`@contract` scenarios for the new behaviour.

Implementation command:
`spec-kitty agent action implement WP01 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-20 (contract half). **Requirements:** FR-040, FR-041, C-001, C-007, NFR-002.
- **Rulings:** A7 `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`; Q1 `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S` (mission type cascades to its steps),
  Q2 `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC` (mission types deniable unless required or locked), Q3
  `DM-01M6RCY40CQQTHC130RWF43W0D` (presets name any kind), Q4 `DM-01M6RCY8MYNGNPJ247M2Y4880N` (refuse now),
  v2 upgrade on the next write `DM-01M6RFGMDH859CJP3RHHF9YC3H`.
- **Research:** R18 and `research/a7-kind-model-2026-11-06.md` with the three lens reports `research/a7-*.md`
  (file:line evidence for every schema below).
- **Not here:** the read-side reasons (`kind_not_activated`, `kind_default`) are changed by WP22 (PR B), because the
  reads still answer `501` until then. `SkippedReport.skippedByScope` **stays**: it is the write report of the
  `cascade` scope (`CascadeRules`, `ActivationChangeMapper:60`, `activation-selections.feature`), live in #399; only
  its read-side kind-gate meaning is gone (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.



### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java` — the minimal edit that compiles against the regenerated DTOs (stop reading and writing the removed members; map `mission_type` in `selected`/`deactivated`), so every A0 lane after this one compiles (owned by WP08, which does the full mapping and the `422`)


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

### T001: Merge the kind enums

- `schemas/ActivatableKind.yaml` gains `mission_type`; `SelectableKind.yaml` becomes an alias (`$ref` to
  `ActivatableKind`) or is removed with every `$ref` repointed. `CascadeKind.yaml` allows `mission_type` in a
  cascade scope (rule W21).
- Keep the enum order stable (alphabetical as today) so the generated Java enums do not reorder.
- Validation: `./gradlew ... :charter-api:check` regenerates; fix every compile break in the generated
  interfaces only by schema, never by hand-editing generated code.

### T002: Strip the remnant members from the document shapes

- `Activation.yaml`, `ActivationConfig.yaml` (charter), `ActivationChange.yaml`, `ActivationSummary.yaml` and the
  create/replace request bodies in `paths/activations.yaml` and `paths/activations_activationId.yaml` lose
  `activatedKinds` and `missionTypes`. `selected`/`deactivated` accept `mission_type`.
- Each write operation's description states: `activatedKinds` or `missionTypes` in a body answers
  `422 validation_failed` with one violation per member naming its replacement (`selected.mission_type`).
- The per-artifact selection and exclusion paths accept `kind=mission_type`; the exclusion of a required or
  locked mission type answers `409 artifact_required` / `artifact_locked` like any kind.
- `presets_presetId.yaml` (apply-preset) states that every kind of the preset is applied.
- **Pins (B1), declared here so the contract lands before the code (C-001):** `ActivationChange` gains
  `droppedPins: [{packId, version}]` (required, empty when nothing is dropped). Replace, the per-artifact writes and
  apply-preset drop a pin no pack satisfies into it; `createActivation` keeps `422 version_not_held` for a pin in its
  own body (decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`). Update `ActivationChange.selected.yaml`.

### T003: Offering schema, storage version and corpus

- `contracts/offering/schemas/ActivationConfig.schema.json` (the stored/offered document): `schemaVersion` const
  `2`; `selected`/`deactivated` gain `mission_type`; `activated_kinds`-style members removed (match the
  file's actual member names).
- Corpus `contracts/offering/corpus/ActivationConfig/`: rewrite `valid/02-every-member`, `valid/03-mission-types-empty`,
  `valid/08-activated-kinds-empty`, `invalid/02-schema-version-two` (now valid shape: move or rename to an
  `invalid/..-schema-version-one`), `invalid/07-selected-mission-type` (now valid), `invalid/14-activated-kind-mission-type`;
  add invalid cases pinning `activatedKinds` and `missionTypes` as unknown members; update `invalid-reasons.json`.
- `ActivationConfigMirrorTest` (contracts module) must pass unchanged in logic; only its fixtures move.

### T004: Preset schema: any kind

- `contracts/packs/schemas/preset.schema.json`: remove the `activated_kinds` gate member (a preset carrying it is
  refused at registration, `422`, WP06), allow every activatable kind's `activated_<plural>` list including
  skills and glossary packs, and keep `activated_mission_types` as a plain member list.
- Update the schema `description` text (it still explains the kind gate).

### T005: Changelog fragment with one Breaking block

- New `contracts/charter/changes/2026-11-07-a0-one-kind-model.md` with `### Breaking` (members removed, enum
  merged, schemaVersion 2, `422` on remnant members) and `### Changed` (mission types deniable, cascade to steps,
  presets name any kind). Follow `contracts/charter/changes/README.md` for the fragment format.
- State that core must regenerate its DTOs before calling a server with this change (C-003: handed over, not
  edited in core).

### T006: Red scenarios: activation-kind-model.feature

New `contracts/charter/scenarios/activation-kind-model.feature`, `@contract`, each with its negative control:
- selecting a mission type selects the steps it holds (Q1); unselecting it leaves them unselected per W23;
- a deny entry on a mission type is accepted; on a required or locked mission type it is `409`;
- `activatedKinds` in a create body is `422` naming `selected.<kind>`; `missionTypes` likewise;
- a stored v1 document reads back as its v2 upgrade (the step to seed a v1 file goes in WP08's glue);
- a preset naming a skill applies it;
- FR-042 server half: GET an activation whose lists are absent, PUT the same body with those lists as `[]` → `200`,
  the `ETag` and the stored bytes unchanged (decision `DM-01M6RN62PPCMSH6M54D2HXYZEQ`);
- a seeded v1 document: GET, the four effective reads (while they answer `501`, GET only) and a cascade dry read
  leave the file bytes and mtime unchanged; a conditional PUT with the read's `ETag` → `200` and the file holds v2.
Every absence assertion is paired with a positive control in the same answer (the step that should be there is).
Tag the file `@WIP` (excluded by `CharterContractSuite`): the glue is bound in WP08, which records the red
(an assertion failure with every step bound) and removes the tag. An undefined step is not red (decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`).

## Test strategy

Contract module tests (`ActivationConfigMirrorTest`, schema lint), and the new feature file failing red.

## Risks

- The generated client and server enums change shape: WP03 to WP08 depend on this WP and must compile against it.
- `-PcontractBaseline=none` is required: there is no `contract-charter-v*` baseline yet (0.2.0-SNAPSHOT).
- Do not touch the effective-read schemas (WP22 owns them).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:contracts:check` (or the contract module's check) passes with `-PcontractBaseline=none`; `:activations:compileJava :activations:compileTestJava` pass against the regenerated DTOs; `activation-kind-model.feature` is committed `@WIP`.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
