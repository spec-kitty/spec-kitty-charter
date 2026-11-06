---
work_package_id: WP02
title: 'A0 · Kernel: one kind registry'
dependencies:
- WP01
requirement_refs:
- FR-041
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-layer-merge-and-effective-reads-01M6R0J6
base_commit: e8f624262cda5bcbe6000fceddc2ab14f7c17070
created_at: '2026-11-06T15:34:52.636530+00:00'
subtasks:
- T007
- T008
- T009
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
create_intent: []
execution_mode: code_change
model: ''
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/ArtifactKindTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/NodeKindPluralTest.java
review_status: acknowledged
role: implementer
tags: []
tracker_refs: []
---
# WP02: A0 · Kernel: one kind registry

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Give the service one registry of kinds. Today seven kind sets disagree (`ArtifactKind.activatable`,
`ActivationDocument.KINDS`, `SelectableKind`, `Preset.ACTIVATION_KINDS`, `PresetYaml.LIST_KEYS`, the preset-schema
enum, the contract enums). This WP makes `ArtifactKind` the one source: which kinds are activatable (all document
kinds including `mission_type`), their plural (the `activated_<plural>` and JSON keys), and whether they may be
required. The consumers switch to it in WP03 to WP06.

Implementation command:
`spec-kitty agent action implement WP02 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-20 (kernel). **Requirements:** FR-041.
- **Evidence:** `research/a7-codeshape.md` items 6–7 (the seven sets, the dead flags), `research/a7-architecture.md`
  item 3 (`MISSION_TYPE` `orgRequirable=false` contradicts `RequiredFloor`).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


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

### T007: Delete the dead flags

- Remove `ArtifactKind.orgRequirable`, `selectionOverlayable` and `InForceWhenAbsent` (no main-code reader; verify
  with a search first, and delete their tests rather than disabling them).
- `MISSION_TYPE` becomes requirable, matching `RequiredFloor`. Update the javadoc that says mission types are not
  activatable kinds.

### T008: One activatable set and one plural

- `ArtifactKind.activatable()` answers every kind the document may hold, `mission_type` included, in the contract
  enum's order. `ArtifactKind.plural()` (or the existing equivalent) is the single source of `activated_<plural>`.
- `NodeKind.java:100` repeats the mission-type plural: route it to `ArtifactKind`.
- Tests first: a test pins the activatable set equal to the generated `ActivatableKind` enum values (parity test),
  so a later contract change cannot drift silently.

### T009: Kind token parsing

- A single `ArtifactKind.fromToken(String)` (or the existing parse) used by the API layer and preset reading,
  answering empty for an unknown token. Unit tests for every token and for case-only mismatches (refused).

## Test strategy

`ArtifactKindTest`, `NodeKindTest`, `NodeKindPluralTest`, plus the new parity test.

## Risks

- Removing a flag a test still reads: delete or rewrite those tests in this WP (list them in the hand-off note); never
  disable them.
- The registry is kernel: no Spring, no I/O.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:kernel:check` passes; the new parity test fails if a kind is added to one list but not the registry.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
