---
work_package_id: WP25
title: B · Effective reads served, write warnings and the deny notice
dependencies: [WP24, WP15]
requirement_refs:
- FR-013
- FR-017
- FR-020
- FR-021
- FR-027
- FR-032
- NFR-003
- NFR-007
- SC-001
- SC-002
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T125
- T126
- T127
- T128
- T129
- T130
phase: PR B
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveCursor.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapter.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadBudgetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapperTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveReadSteps.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveCursor.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapter.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadBudgetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/ActivationControllerTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapperTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveReadSteps.java
role: implementer
---
# WP25: B · Effective reads served, write warnings and the deny notice

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Serve `listEffectiveArtifacts` and `getActivationSkippedReport` from one `PacksSession` each (FR-013, FR-017, FR-020,
FR-021), make `layer-merge.feature` and `effective-reads.feature` green, and ship IC-17: `ActivationChange.warnings`
(FR-032) and `Activation.denyNotApplied` (FR-027), plus the `ActivationImpact` adapter that lists activations whose
in-force set a registration changes (FR-027, B4).

Implementation command:
`spec-kitty agent action implement WP25 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concerns:** IC-09 (service, ETag, refusals, timing), IC-17, IC-15's `ActivationImpact`. **Requirements:** FR-013,
  FR-017, FR-020, FR-021, FR-027, FR-032, NFR-003, NFR-007, SC-001, SC-002.
- **Refusals:** `404 activation_not_found`; a closure that cannot compose or merge answers `500` with its code.
- **NFR-003:** an effective read over 10 packs and 2,000 artifacts, end to end through MockMvc (warm JVM, 10 warm-up
  runs) under 300 ms wall time at p95 over 50 runs; `timing`-tagged (`:activations:timingBudgets`, report-only).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationChange.java` — add the `warnings` list (owned by WP04)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationChangeMapper.java` — map `warnings` (owned by WP08)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java` — map `denyNotApplied` (owned by WP08)


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

### T125: EffectiveReadService: list and skipped

Stable order (kind, then id in code points), `kind`/`state` filters, keyset cursor carrying the tag (`400` for a foreign tag), `summary`; skipped report.

### T126: Controller: two reads

Remove the two `@ReservedOperation` marks; `ETag` and `304` on `If-None-Match`; `EffectiveReadMapper`.

### T127: Write warnings (IC-17)

After a write, compute the in-force states of the stored document once (same session) and report `warnings` for applied artifacts that are not in force (`enhanced_target_not_in_force`, `overridden`).

### T128: denyNotApplied on GET /activations/{id}

The deny entries the floor or a lock overrides (B4 activation half).

### T129: ActivationImpact adapter

Implements the `packs` port: given the before/after closures, list activation ids whose in-force set changes (bounded: activations composing the pack only).

### T130: Scenario glue and timing

`EffectiveReadSteps`; both feature files green; `EffectiveReadBudgetTest` with the NFR-003 fixture.

## Test strategy

Service, controller and mapper tests; the two features; the timing test reported in the hand-off note.

## Risks

- Paging over a recomputed set is stable only for one ETag: the cursor carries the tag.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
