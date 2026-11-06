---
work_package_id: WP14
title: 'A · Whole-pack registration: validate, version rule, gate, atomic replace'
dependencies: [WP12, WP13, WP07]
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-007
- FR-036
- NFR-006
- SC-003
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts were generated on issue-10-layer-merge-effective-reads; execution worktrees come from lanes.json and completed changes merge back into issue-10-layer-merge-effective-reads. The four PRs (A0, A, B, C) are cut from it at closeout.
subtasks:
- T064
- T065
- T066
- T067
- T068
- T069
phase: PR A
assignee: ''
agent: claude
shell_pid: ''
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WholePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/InboundReferencesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationConcurrencyTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/WholePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackCommitStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StorageRootLocations.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/InboundReferencesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackCommitStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WholePackStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationConcurrencyTest.java
role: implementer
---
# WP14: A · Whole-pack registration: validate, version rule, gate, atomic replace

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make a registration hold exactly the uploaded pack or change nothing (FR-001–FR-003, FR-007): the IC-04 flow, steps
1–8 and 10 (step 9, the impact check, is WP15). Slow work first, outside every lock; the locked steps under the
registry lock then the pack lock.

Implementation command:
`spec-kitty agent action implement WP14 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concern:** IC-04 in full (the ten-step flow, the version rule, identity checks, the write gate along the new
  closure per ADR-0063 D3, the inbound scan against the staged tree, the commit). **Requirements:** FR-001, FR-002,
  FR-003, FR-007, FR-036, NFR-006, SC-003.
- **Rulings:** Q2/Q3 (a removed artifact referenced from another pack refuses `409 artifact_referenced`, listing the
  referrers); B3 `DM-01M6RA9MQ6YAP048KD43DNFS4F` (judge against the new tree; referrers removed by the same request
  do not block; every `409` lists referrers); A10 (other packs' preset items count as inbound references); B8 (own
  policy does not bind the pack, WP07); Q5 (shared `<config-root>/packs`, one writable root).
- **packId grammar:** `^[a-z0-9][a-z0-9-]{0,62}$`, unique after case folding; the resolved directory's real path has
  that name under the writable root.

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
- At closeout the mission branch is cut into the stacked PRs #399 → A0 → A → B → C; keep this WP's commits inside its PR letter (A).

## Subtasks

### T064: Service skeleton and port

`PackRegistrationService.register(packId, StagedPack, Preconditions)` returning a `RegistrationOutcome` (created/replaced/unchanged + revision + notices); `WholePackWriteStore` port implemented by `PackCommitStore`.

### T065: Load and identity (steps 2–3)

`WholePackStore.load(staging)` violations → `422`; `pack_level` org/project only (`409 registration_conflict` for built-in); same level and same `pack_id` on replace (`422`); a new pack's id and name unique (`409`).

### T066: Revision and the version rule (steps 4–6)

Revision of the staged tree (research R4); same revision → `unchanged`, nothing written; same `pack_version`, other revision → `409 pack_version_conflict` (WP15 extends this with the history); then `If-Match`/`If-None-Match: *` (`412`) — after the version rule, so an identical re-send is a no-op even under a stale tag.

### T067: Write gate and inbound scan (steps 7–8)

Diff added/changed/removed artifacts and gate them along the new closure (`409 governance_policy_violation`); `InboundReferences.scan(stagedTree, removed)` over every configured pack, counting artifact references and other packs' preset items, excluding referrers the same request removes → `409 artifact_referenced` listing pack and artifact of each referrer; the scan runs before any compose.

### T068: Commit (step 10)

`PackCommitStore.replaceWhole(packId, staging)` via the two-rename swap and #281 crash repair; `create(...)` via `WholePackStore.write`'s reserve and move; generation odd during the swap (WP12).

### T069: Concurrency (NFR-006)

`RegistrationConcurrencyTest`: 8 concurrent uploads of one pack with readers in parallel, 100 runs — every read sees one complete version; 8 creates of one `pack_id` — one wins, the rest `409`; a replace racing `PUT`s with `If-Match` gives one linear ETag chain.

## Test strategy

`PackRegistrationServiceTest` (each step's refusal and the happy paths), `InboundReferencesTest`, `PackCommitStoreTest`, `WholePackStoreTest`, `RegistrationConcurrencyTest`.

## Risks

- PR #377 and #390 collide on `PackCommitStore`/`FilesystemPackStore`: rebase in order before review.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
