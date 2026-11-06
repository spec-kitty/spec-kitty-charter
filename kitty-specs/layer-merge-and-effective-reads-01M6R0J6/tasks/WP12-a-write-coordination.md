---
work_package_id: WP12
title: 'A · Write coordination: registry lock, write generation, read retry'
dependencies:
- WP09
requirement_refs:
- FR-007
- NFR-006
- NFR-009
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T053
- T054
- T055
- T056
- T057
phase: PR A
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistryWriteLock.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackWriteGeneration.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistryWriteLockTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/MixedReadConcurrencyTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistryWriteLock.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackWriteGeneration.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ConsistentRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureQueries.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackWriteLocks.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CommitStorePackWriteStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ConsistentReadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureQueriesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistryWriteLockTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/MixedReadConcurrencyTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP12: A · Write coordination: registry lock, write generation, read retry

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Close the mixed-read window PR A's whole-pack writes open (research R-lock, NFR-006, FR-007): a fair
`RegistryWriteLock` taken before the per-pack `PackWriteLocks` on **every** pack write; a `PackWriteGeneration`
seqlock (odd while a swap runs); `ConsistentRead` checks it beside the two digests; `ClosureQueries.snapshot` retries
a stale read up to three times, then answers `stale_input`.

Implementation command:
`spec-kitty agent action implement WP12 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A (stacked on PR A0).
- **Concerns:** IC-04 (locks), IC-07's read guard (moved to PR A). **Requirements:** FR-007, NFR-006, NFR-009.
- **Research:** R-lock (lock order, the test matrix), Q7 (a `stale_input` answer is acceptable; a mixed one fails).
- **One replica only:** in-JVM locks (ADR-0061); say so in the class javadoc.

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

### T053: RegistryWriteLock

A fair `ReentrantReadWriteLock`-based or `ReentrantLock(true)` registry lock with `tryLock(5 s)` (NFR-009: on timeout the caller ends `refused` `503`). Order: registry, then the pack's lock — a test asserts the order by instrumenting `PackWriteLocks`.

### T054: PackWriteGeneration

A per-registry (and per-pack, for the facts cache of WP21) generation counter: incremented to odd before a swap, to even after, in `finally`. `CommitStorePackWriteStore` brackets its swap with it.

### T055: ConsistentRead and the retry

`ConsistentRead` reads the generation before and after; odd or changed → `STALE_INPUT`; a first-digest failure while it moves → `STALE_INPUT`. `ClosureQueries.snapshot` retries up to three times. `/offering` and `listActivationPacks` answers do not change (tests pin them).

### T056: Existing writes take the registry lock

The per-artifact registration services and `RelationshipRegistrationService.putPackRelationship` take the registry lock before the pack lock (so `ComposedRelationshipTargetCheck` cannot race a `DELETE`).

### T057: Concurrency tests (R-lock)

`MixedReadConcurrencyTest`, each case 100 runs from a `CyclicBarrier`, 30 s timeout: 8 writers cycling A, B, C
(ABA included) against 8 readers with marker files and a slow rename (a test hook in the commit store) — every read
sees one complete version or `stale_input`; starvation: 16 `PUT`s and one replace all complete. The `DELETE` vs
relationship `PUT`, the 8 creates and the `If-Match` chain cases go in WP14/WP19, which own those writers.

## Test strategy

`ConsistentReadTest`, `ClosureQueriesTest`, `RegistryWriteLockTest`, `MixedReadConcurrencyTest`.

## Risks

- The fair lock must not deadlock with the activation store's own lock: activations never take packs' write locks (verify by search).

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first and now pass; the module checks pass with the Gradle flags above.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
