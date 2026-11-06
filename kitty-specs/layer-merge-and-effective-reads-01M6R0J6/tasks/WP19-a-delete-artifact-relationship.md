---
work_package_id: WP19
title: A · DELETE of an artifact and of a relationship
dependencies:
- WP14
- WP16
requirement_refs:
- FR-005
- FR-006
- FR-007
- FR-036
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T094
- T095
- T096
- T097
- T098
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRemovalController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRemovalControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RemovalConcurrencyTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRemovalSteps.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PackRemovalController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RelationshipRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/PackRemovalControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RemovalConcurrencyTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/PackRemovalSteps.java
role: implementer
tags: []
tracker_refs: []
---
# WP19: A · DELETE of an artifact and of a relationship

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Remove one artifact (with its outgoing edges, its graph node and its preset items in that pack) or one relationship
from a pack without re-sending it (FR-005, FR-006, FR-007). Synchronous, entirely under the registry lock then the
pack lock; `If-Match` required (`428`/`412`).

Implementation command:
`spec-kitty agent action implement WP19 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-06 in full. **Requirements:** FR-005, FR-006, FR-007, FR-036.
- **Rulings:** Q2 (a same-pack referrer refuses too, naming the artifact); B3 (every `409` lists the referrers); A10
  (other packs' preset items are inbound references).
- A `DELETE` does not consult activations: a saved selection of the removed artifact reads as stale and drops on the
  next write.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PresetFiles.java` — remove the preset items naming the deleted artifact (owned by WP06)
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java` — reuse the scan with a single-artifact removal set (owned by WP14)
- `contracts/charter/scenarios/activation-ruling-changes.feature` — remove `@WIP` from the A10 `DELETE` scenario (owned by WP08)


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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T094: Artifact removal

Load → `404` per code (`pack_not_found`, `kind_not_found`, `artifact_not_found`) → inbound scan (`409 artifact_referenced`, referrers listed, same-pack referrer included) → write gate (`409 governance_policy_violation` on a locked target) → mutate staging (file and companions, outgoing edges in the shard and graph documents, graph node, preset items; an emptied preset key reads as not listed) → revalidate the whole aggregate → atomic swap.

### T095: Relationship removal

Remove one edge of the unordered `{from, to}` pair from the registered shard or a graph document; an edge that is a field of an artifact's own body → `409 registration_conflict`; none → `404 relationship_not_found`.

### T096: Controller

`PackRemovalController` on the generated interface, `PackIfMatchRequired` (`428` absent, `412` stale, checked under the locks), `204`; audit record through WP16's `AuditLog`.

### T097: Concurrency

`RemovalConcurrencyTest`: `DELETE A::X` against a relationship `PUT B → A::X`, 100 runs — never both succeed with a dangling reference. Tag it `stress` (excluded from PIT).

### T098: Scenarios

`PackRemovalSteps`; bind `pack-removal.feature`, record the red, remove `@WIP`, make it green. Also remove the `@WIP`
of the A10 `DELETE` scenario in `activation-ruling-changes.feature` (WP08's file, a shared edit) and make it green.
Every absence assertion keeps its positive control. One audit record per `DELETE` outcome (`204`, `404`, `409`,
`412`, `428`), asserted in a unit test.

## Test strategy

Service and controller unit tests, `RemovalConcurrencyTest`, `pack-removal.feature`.

## Risks

- The scan reads every configured pack per delete; deletes are rare, accepted as ADR-0063 accepted the write gate's compose.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` and the contract suite pass; `pack-removal.feature` and the A10 `DELETE` scenario have no `@WIP`; each `DELETE` outcome writes one audit record.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
