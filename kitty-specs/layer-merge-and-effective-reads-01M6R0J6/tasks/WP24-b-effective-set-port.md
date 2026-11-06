---
work_package_id: WP24
title: 'B · activations: effective set, snapshot values and the port'
dependencies: [WP22, WP23]
requirement_refs:
- FR-013
- FR-017
- FR-020
- NFR-007
- C-002
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T120
- T121
- T122
- T123
- T124
phase: PR B
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSet.java
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSet.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/MergedSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveSetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveTagTest.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSet.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveSkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/MergedSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/SkippedReport.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/AvailablePacks.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveSetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveTagTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/FakeAvailablePacks.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/PacksAvailabilityTest.java
role: implementer
---
# WP24: B · activations: effective set, snapshot values and the port

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Give `activations` the merged facts through the existing port, as values: `AvailablePacks.session(...)` answers
`merged()` (a `MergedSnapshot`: winners with provenance and policy level, languages per URN, layer overrides) and
`inForce(request)` (the states from WP23). `EffectiveSet` keeps only the precedence (one state and reason per
artifact, one bucket per skipped artifact, data-model.md) and the summary; it never walks.

Implementation command:
`spec-kitty agent action implement WP24 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concern:** IC-09 (port, `EffectiveSet`, ETag). **Requirements:** FR-013, FR-017, FR-020, NFR-007, C-002.
- **ETag:** `EffectiveTag(revision, fingerprint)` (research R5).
- **ArchUnit:** only `activations.infra.packs` names `packs`; `MergedSnapshot` holds values only.

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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (B).

## Subtasks

### T120: MergedSnapshot and the port

Port methods `merged()` and `inForce(...)` on `PacksSession`; `ClosureViewSession`/`PacksAvailability` map `MergedClosure` and `InForceStates` into domain values; `FakeAvailablePacks` supports both.

### T121: EffectiveSet precedence

Per data-model precedence table; tests per reason and bucket, including the A7 rows (mission types listed like any kind).

### T122: Summary and buckets

`EffectiveSkippedReport` with the typed buckets of WP22; `summary` counts.

### T123: EffectiveTag

Tag over activation revision and pack fingerprint; tests for stability and change.

### T124: Hexagonal check

Run `HexagonalArchitectureTest`; fix any leak of a `packs` type into `activations.domain`/`application`.

## Test strategy

`EffectiveSetTest`, `EffectiveTagTest`, `PacksAvailabilityTest`, `HexagonalArchitectureTest`.

## Risks

- One verified read per request (NFR-007): `merged()` and `inForce()` reuse the session's read; assert with a counting fake.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
