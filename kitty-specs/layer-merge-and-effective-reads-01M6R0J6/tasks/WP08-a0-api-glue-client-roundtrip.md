---
work_package_id: WP08
title: A0 · API mapping, scenario glue and the client round trip
dependencies:
- WP04
- WP05
- WP06
- WP07
requirement_refs:
- FR-026
- FR-031
- FR-033
- FR-034
- FR-035
- FR-040
- FR-041
- FR-042
- NFR-002
- C-006
- SC-005
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T034
- T035
- T036
- T037
- T038
- T039
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/
create_intent:
- client-java/src/test/java/ai/speckitty/charter/client/ActivationRoundTripTest.java
- contracts/charter/changes/2026-11-07-a0-ruling-changes.md
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapper.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationJson.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdvice.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationProblems.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationJsonTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationBodyAdviceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ActivationConfigConformanceTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRoundTripPropertyTest.java
- client-java/src/test/java/ai/speckitty/charter/client/ActivationRoundTripTest.java
- contracts/charter/changes/2026-11-07-a0-ruling-changes.md
role: implementer
tags: []
tracker_refs: []
---
# WP08: A0 · API mapping, scenario glue and the client round trip

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Close PR A0 end to end: map the new document shape at the HTTP boundary, refuse the remnant members `422`, make
WP01's `activation-kind-model.feature` and the existing activation scenarios green, and pin the client round trip
(FR-042): a GET then PUT through `client-java` leaves the revision unchanged.

Implementation command:
`spec-kitty agent action implement WP08 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concerns:** IC-19 (FR-042 row, the changed-behaviour fragments), IC-20 (API). **Requirements:** FR-026, FR-031,
  FR-033, FR-034, FR-035, FR-040, FR-041, FR-042, NFR-002.
- **Side finding (A7 squad):** the generated client's `ActivationConfig.java:63-73` turns an absent list into `[]`,
  so a GET then PUT changes the stored document. The test comes first; the fix follows from it (the server treats
  `[]` and absent alike when canonicalising, or the client keeps absent absent — choose by what the test shows and
  record why).

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

### T034: Refuse the remnant members (Q4)

- `ActivationBodyAdvice` / `ActivationJson`: a body with `activatedKinds` or `missionTypes` on create, replace,
  the per-artifact writes or apply-preset answers `422 validation_failed`, one violation per member naming
  `selected.mission_type` or "removed" as its replacement. Tests in `ActivationBodyAdviceTest`.

### T035: Map the v2 document

- `ActivationMapper` and `ActivationChangeMapper` map `mission_type` in `selected`/`deactivated`, the `dropped`
  pin entries (B1) and the stale drops of replace (A4). Update their tests.

### T036: Scenario glue

- `ActivationSteps`: a step to seed a stored v1 document; steps for mission-type select/deny. Run
  `activation-kind-model.feature`, `activation-register.feature`, `activation-selections.feature`,
  `activation-presets.feature` green; rewrite (never delete without replacement) scenarios that pinned the old
  kind gate, listing them in the hand-off note.

### T037: Client round trip (FR-042)

- New `client-java/.../ActivationRoundTripTest`: create a document with some lists absent, GET through the client,
  PUT the same object back → `200` with an unchanged revision (or a no-op). Watch it fail first, then fix as above.

### T038: Changed-behaviour fragment

- New `contracts/charter/changes/2026-11-07-a0-ruling-changes.md`: one `### Changed` entry per behaviour (B5, A4,
  B1 writes, A15, B8, A10, A14) and a `### Breaking` block where a `422` becomes a drop (A4, B1).

### T039: Property and conformance tests

- `ActivationRoundTripPropertyTest` and `ActivationConfigConformanceTest` (deployable) updated for v2 and the merged
  kind enum; run `:deployable:test --tests '*Activation*'` and the contract suite for the activation features.

## Test strategy

Mapper/advice unit tests, the activation `@contract` features, `ActivationRoundTripTest` (client), the deployable activation tests.

## Risks

- This is the WP where the about 45 pinned tests surface: keep a list of every rewritten test with one line on why it changed, for the PR description.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.
- PR A0's scope is green end to end: `./gradlew ... :activations:check :packs:check :kernel:check` and the activation contract features.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
