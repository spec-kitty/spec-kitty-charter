---
work_package_id: WP21
title: 'B · packs: the layer merge'
dependencies: [WP12, WP07]
requirement_refs:
- FR-009
- FR-010
- FR-011
- FR-012
- FR-023
- FR-028
- NFR-007
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T103
- T104
- T105
- T106
- T107
- T108
phase: PR B
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerMerge.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerMerge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MergedClosure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerOverride.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositionFactsCache.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/LayerMergeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewMergedTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositionFactsCacheTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerMerge.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/MergedClosure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/LayerOverride.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositionFactsCache.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/LayerMergeTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewMergedTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositionFactsCacheTest.java
role: implementer
---
# WP21: B · packs: the layer merge

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Answer, for a closure, which version of each artifact id is in force across levels and record every override
(FR-009–FR-012): `LayerMerge` is a pure `packs.domain` function and the one winner function for every id, presets
included. `ClosureView.merged()` builds it lazily from the verified read it already holds (NFR-007), so writes and
`/offering` do not change (FR-023, SC-004).

Implementation command:
`spec-kitty agent action implement WP21 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concern:** IC-07 in full. **Requirements:** FR-009, FR-010, FR-011, FR-012, FR-023, FR-028, NFR-007.
- **Rulings:** A1 `DM-01M6RA7FHRC5K8QPX7KN5FZ4S8` (a preset id's requirement is the union of the must-have items over
  every governing copy); A10 (held ids only, from WP07); #357 (a settled sibling's version in force); research R3
  (the override checked against the owner's mutability level along the closure, the winning pack excluded).
- **Risk from the plan:** `holderOf` answers the first holder in closure order today; the merged view answers the
  winner without changing the compose's reference checks (which keep the first-holder rule).

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

### T103: LayerMerge: per level and across levels

Tests first: built-in/org/project with the same id → project wins whole, no field inherited (ADR-0059 D3); a settled sibling contest puts the named member's version in force (#357); an unsettled one still refuses in the compose.

### T104: Mutability of overrides

Each override checked against the owner's level along the closure (winner excluded): `locked` or `extendable` refuses with `pack_unreadable` naming artifact, both packs and level (FR-011, ADR-0063 D3); the A15 scope applies.

### T105: MergedClosure and merged edges

Per URN the winner (configured `packId`, descriptor `pack_id`, `level`); `LayerOverride` records; `EdgeIndex.merged(...)`: edges from a losing place dropped, edges into it (bare or `<pack>::`-qualified) resolved to the winner (FR-010).

### T106: Presets in the merge (A1)

The preset id's winner plus the union of the must-have items of every governing copy, computed beside the winner; requirements only tighten. Test the review's A1 counter-example (a project copy without the org's must-have items does not drop them).

### T107: Languages and the facts cache

`PackCompositionFacts` gains `languages` (each held URN's `applies_to_languages`) read in the same assembly (`DomainRepositoryService.compositionFacts`); `CompositionFactsCache` keyed by pack revision with the per-pack generation of WP12, so a read recomposes only what changed.

### T108: ClosureView.merged()

Lazy, from the same verified read; `/offering` and the write paths byte-identical (pin with the existing offering tests).

## Test strategy

`LayerMergeTest` (table-driven per rule id C-family), `EdgeIndexTest`, `ClosureViewMergedTest`, `CompositionFactsCacheTest`, offering tests unchanged.

## Risks

- Keep `LayerMerge` free of I/O and Spring; the facts cache is application-level.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
