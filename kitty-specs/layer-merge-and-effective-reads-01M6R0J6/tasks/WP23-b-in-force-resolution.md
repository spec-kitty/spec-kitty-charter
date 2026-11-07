---
work_package_id: WP23
title: 'B · packs: in-force resolution, a stratified fixed point'
dependencies:
- WP21
requirement_refs:
- FR-013
- FR-014
- FR-015
- FR-016
- FR-028
- FR-029
- FR-030
- FR-038
- FR-040
- C-002
planning_base_branch: issue-10-layer-merge-effective-reads
merge_target_branch: issue-10-layer-merge-effective-reads
branch_strategy: Planning artifacts for this mission were generated on issue-10-layer-merge-effective-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-10-layer-merge-effective-reads unless the human explicitly redirects the landing branch.
subtasks:
- T114
- T115
- T116
- T117
- T118
- T119
phase: PR B
history:
- timestamp: '2026-11-06T00:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceRequest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceStates.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionPropertyTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceResolution.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceRequest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/InForceStates.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/InForceResolutionPropertyTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP23: B · packs: in-force resolution, a stratified fixed point

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Decide what is in force, in `packs` (the steps walk edges, so C-002 puts them here): `activations` passes the
document's selection, deny entries and languages through the port and gets one state and reason per closure
artifact back. Strata (research R13), each never changing an earlier one:
1. candidates: selected ids of the merged closure (every kind) within the language filter, minus deny entries, plus
   every locked artifact (a deny on a locked artifact is not applied);
2. overrides, transitive top to bottom (A3: `F → E → T` leaves F; F hides E and T and satisfies a requirement on T);
   a locked target stays (`overrideNotApplied`); an override cycle refuses `pack_unreadable`;
3. the floor (held ids only, A10; the A1 union), judged on the final set and iterated to a fixed point (A2): a
   required, not locked artifact is satisfied by itself or an active override of it, else put in force with reason
   `required` and its deny entry not applied;
4. pruning, a least fixed point: an enhancer is in force only by its own route and only with support grounded in a
   non-enhancer in force (A8); anything bound to mission type M (its steps included) is in force only while M is
   (A7 Q1 binding) — the floor is no exception (`DM-01M6RFGFW3S0YJ222QMHD2W11B`: dropped and reported).
Also FR-038: the write cascade walks the merged edges.

Implementation command:
`spec-kitty agent action implement WP23 --agent claude --mission layer-merge-and-effective-reads-01M6R0J6`

## Context

- **Delivery:** PR B (stacked on PR A).
- **Concerns:** IC-09 (where it lives, the strata), IC-07 (FR-038). **Requirements:** FR-013–FR-016, FR-028–FR-030,
  FR-038, FR-040.
- **Rulings:** A2 `DM-01M6RA7M56M1409EV9JX1EW83K`, A3 `DM-01M6RA7RPNZS810FTFC98Z13GH`, A8, Q6/Q8 (operator
  planning), floor enhancer `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ`, cascade over merged edges `DM-01M6R4W1H9P3451V89KHP97XKM`.
- **Ruleset:** E1–E20, I10 (conditional), I11 (nothing outlives its target or its mission type).

- **Read first:** `plan.md` (the IC sections named above, in full), `spec.md` (the FRs above), `data-model.md`, `contracts/contract-delta.md`, `research.md` (R-numbers above), and `docs/architecture/composition-and-activation-rules.md` for the rule ids cited. All paths in this prompt are repo-root relative.


### Files shared with other work packages

These files are owned by another WP (the no-overlap rule) but this WP must make the small edit named. Make only that edit, and record a one-line rationale in the commit message (`out-of-map: <file> — <why>`):

- `service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java` — walk the merged `EdgeIndex` once the merge exists (FR-038; owned by WP05)
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` — expose `inForce(InForceRequest)` over the merged closure (owned by WP21)


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

### T114: Request and result values

`InForceRequest` (selection per kind, deny per kind, languages, pins) and `InForceStates` (per URN: state, reason, satisfiedBy, bucket); values only, so `activations` can map them without naming `packs` types beyond the port.

### T115: Strata 1–2

Candidates and transitive overrides; tests per rule (E4, E9, E10, A3 chain, locked target, cycle).

### T116: Stratum 3: the floor to a fixed point

Iterate the requirement check to a fixed point and judge satisfaction on the final set (`DM-01M6RA7M56M1409EV9JX1EW83K`).
Pin the ruled answers exactly (no disjunctive oracle):
- **A2:** `overrides(E,T)`, `overrides(U,E)`, T and U required, E and T selected → U `active`/`required`; E `inactive`
  `overridden` by U; T `inactive` `overridden` with `satisfiedBy` U (transitive overrides, A3).
- **Mutual override:** two required artifacts overriding each other form an override cycle among base ∪ unsatisfied:
  `500 pack_unreadable` at read (C28; decision `DM-01M6RN6BWE6SVZZE8ERQ8Z9MV6`), never a loop.
- **Cross-stratum:** F overrides required T, F bound to a mission type not in force → stratum 4 prunes F, so the
  fixed point re-judges T: T `active`/`required` (not left neither in force nor satisfied).

### T117: Stratum 4: enhancer and binding pruning

Least fixed point; enhancer rings and `enhances(E,E)` out; a step of a mission type not in force out; a required one dropped and reported.

### T118: Property tests

`InForceResolutionPropertyTest` with seeded generators in the style of `ActivationRoundTripPropertyTest` (no jqwik:
it is excluded in `gradle/libs.versions.toml`): I10 (conditional), I11, determinism under input order, termination,
and the **FR-029 invariant**: every held required id is `active`, or `satisfiedBy` an `active` artifact, or listed in
`requiredNotInForce`/`requiredNotHeld`. Log the seed on failure.

### T119: Merged cascade (FR-038)

`Reachability` walks `EdgeIndex.merged(...)`; a test that a cascade never follows an edge of an overridden artifact.

## Test strategy

`InForceResolutionTest` (one test per rule id), `InForceResolutionPropertyTest`, `ReachabilityTest` for FR-038.

## Risks

- Termination: each stratum is monotone over a finite set; assert an iteration bound in code (closure size) and fail loudly past it.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** `:packs:test` passes with `InForceResolutionTest` (one test per rule id; the A2, mutual-override and cross-stratum answers pinned) and the property test over at least 1,000 seeded cases.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
