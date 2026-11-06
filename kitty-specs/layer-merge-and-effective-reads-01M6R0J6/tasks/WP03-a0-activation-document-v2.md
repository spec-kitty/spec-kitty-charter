---
work_package_id: WP03
title: A0 · Activation document v2 and the storage upgrade
dependencies:
- WP01
- WP02
requirement_refs:
- FR-040
- FR-041
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T010
- T011
- T012
- T013
- T014
phase: PR A0
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationDocument.java
create_intent:
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationDocumentUpgradeTest.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ActivationDocument.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ClosureSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/ArtifactRef.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodec.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepository.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/storage/InMemoryActivationRepository.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ActivationDocumentTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/ClosureSnapshots.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationFileCodecTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationRepositoryContract.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/ActivationDocumentUpgradeTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationSelectionBudgetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/storage/FileActivationRepositoryFailureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ActivationRestartTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP03: A0 · Activation document v2 and the storage upgrade

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Reshape the activation document to `⟨packs, selected, deactivated, languages⟩` with `mission_type` as an ordinary
kind, delete the parallel mission-type set, and store `schemaVersion: 2`. A stored v1 document is upgraded **in
memory on read** (reads never write) and stored as v2 on the next write (decision `DM-01M6RFGMDH859CJP3RHHF9YC3H`).

Implementation command:
`spec-kitty agent action implement WP03 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR A0 (own PR, stacked on #399 `issue-291-activation-writes`, before PR A; decision DM-01M6RFGB9NVD9MDWZ6AP0PBYH7).
- **Concern:** IC-20 (document and storage). **Requirements:** FR-040, FR-041.
- **Evidence:** `research/a7-architecture.md` item 6 (`ActivationFileCodec.java:31-32,66-68` accepts a fixed member
  set and `schemaVersion` 1); `research/a7-codeshape.md` item 3 (the mission-type `Set<String>` in
  `ActivationDocument`, `Written`, `PresetView`, `ClosureSnapshot`, `ClosureView`; four `ArtifactRef(MISSION_TYPE, id)`
  rebuilds) and item 4 (`ActivationDocument.inForce(kind)` at line 127 is test-only: delete it).
- **Ruleset:** rules W2, W5, W34 and the document notation in `composition-and-activation-rules.md` §1.

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` — drop the separate mission-type set from the closure snapshot it hands to activations (owned by WP21)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/PacksAvailability.java` — map mission types into the per-kind held map instead of `ClosureSnapshot.missionTypes` (owned by WP24)
- `service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureQueriesTest.java` — `view.missionTypes()` is gone: assert mission types in the per-kind held map (owned by WP12)
- `service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/PacksAvailabilityTest.java` — the fields the snapshot no longer has (owned by WP24)
- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationMapper.java` — follow the removed domain accessors (owned by WP08)


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

### T010: The document value

- Tests first in `ActivationDocumentTest`: `selected`/`deactivated` hold `mission_type`; there is no `kinds()` and no
  `missionTypes()`; `inForce(kind)` is gone; absent still means none (W2) for every kind.
- `ActivationDocument` drops `kinds` and `mts`; `KINDS` comes from `ArtifactKind.activatable()` (WP02).

### T011: ClosureSnapshot without the mission-type slot

- `ClosureSnapshot.missionTypes` folds into the held-ids-per-kind map. Update `ClosureSnapshots` (the test helper).
- Remove the `ArtifactRef(MISSION_TYPE, id)` rebuilds; mission types are ordinary `ArtifactRef`s.

### T012: Codec: write v2, read v1 and v2

- `ActivationFileCodec` writes `schemaVersion: 2` in the canonical form; it reads v2, and reads v1 by upgrading:
  `missionTypes` → `selected.mission_type`; every selection is kept and `activatedKinds` is discarded (decision `DM-01M6RMJHDKNTXAM055Q8WR6SDN`; the gate was never
  enforced). Locks and the floor were never gated, so nothing observable changes.
- A v1 document with any other unknown member is still refused, as today.
- **One canonical form for "none" (FR-042 server half, decision `DM-01M6RN62PPCMSH6M54D2HXYZEQ`):** an empty list and
  an absent per-kind member of `selected`/`deactivated` are stored the same way (absent), so the revision does not
  move when a client sends `[]` for an absent list. Test: two documents differing only in `[]` vs absent encode to
  identical bytes and the same revision. `languages` is **not** canonicalized: absent means no filter and `[]` means
  keeps only untagged artifacts (`lang(u) ⇔ langs = ⊥ ∨ tags(u) = ∅ ∨ …` in the read rules), so the client must
  keep an absent `languages` absent (WP08 fixes the client).
- `ActivationDocumentUpgradeTest`: table-driven v1 → v2 cases, including an absent, an empty and a narrowing `activatedKinds` (every
  selection is kept in all three, decision `DM-01M6RMJHDKNTXAM055Q8WR6SDN`) and the corpus files from WP01.

### T013: Repositories: upgrade on read, store on write

- `FileActivationRepository` and `InMemoryActivationRepository`: a read of a v1 file answers the upgraded document
  and never rewrites the file; the next write stores v2. The revision (`Revision`/ETag) is computed over the stored
  bytes, so it changes exactly once, on that write.
- `ActivationRepositoryContract` gains: read v1 twice → file bytes unchanged; write → v2 on disk.

### T014: Compile the consumers

- Fix the activations domain and application compile breaks caused by the removed accessors with the minimal change
  that keeps behaviour (WP04 to WP06 then change the behaviour). Leave a `// WP04`-style note nowhere: the change
  must stand on its own.

## Test strategy

`ActivationDocumentTest`, `ActivationFileCodecTest`, `ActivationRepositoryContract` (file and in-memory), `ActivationDocumentUpgradeTest`.

## Risks

- Every client holding an old ETag gets one `412` after the first write: expected and documented (WP09).
- Do not change the JCS canonical ordering beyond the new members.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:activations:test` passes (the owned tests above, plus the three repaired activatedKinds tests); `:deployable:test --tests '*ActivationRestartTest*'` passes; `ClosureQueriesTest` and `PacksAvailabilityTest` compile and pass. The `@contract` activation scenarios are made green by WP08 (the A0 boundary).
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
