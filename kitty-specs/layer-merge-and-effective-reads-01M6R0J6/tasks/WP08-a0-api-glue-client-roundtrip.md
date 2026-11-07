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
- T147
- T148
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
- contracts/charter/scenarios/activation-ruling-changes.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/UndefinedStepGuard.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/UndefinedStepGuardTest.java
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
- contracts/charter/scenarios/activation-selections.feature
- contracts/charter/scenarios/activation-presets.feature
- contracts/charter/scenarios/activation-ruling-changes.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/CharterContractSuite.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/UndefinedStepGuard.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/UndefinedStepGuardTest.java
- build-logic/src/main/kotlin/charter.client-java.gradle.kts
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

- `ActivationSteps`: a step to seed a stored v1 document; steps for mission-type select/deny. Bind every step of
  `activation-kind-model.feature` first, remove its `@WIP`, run it against the code before T034/T035 and record the
  assertion failures (wrong status or body) as the red evidence; then make it green (decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`).
  Run
  `activation-kind-model.feature`, `activation-register.feature`, `activation-selections.feature`,
  `activation-presets.feature` green; rewrite (never delete without replacement) scenarios that pinned the old
  kind gate, listing them in the hand-off note.

### T037: Client round trip (FR-042)

Decision `DM-01M6RN62PPCMSH6M54D2HXYZEQ`; the side finding is the generated `ActivationConfig.java:63-73`
initializing absent lists to `[]`.
- **Client half** — new `client-java/.../ActivationRoundTripTest`: a JDK `com.sun.net.httpserver.HttpServer` (no new
  dependency) answers a GET with a document whose `languages` and some per-kind lists are absent and records the PUT
  body the generated client sends back. Assert the PUT body's JSON equals the GET body's (absent stays absent; in
  particular `languages` is never sent as `[]`, which would mean "keep only untagged artifacts"). Watch it fail on
  today's client.
- **Fix:** in `charter.client-java.gradle.kts`, set the generator option that keeps absent containers null
  (`containerDefaultToNull=true`, or the generator's equivalent) and regenerate; `ClientOutputTest`/`AdditiveMinorTest`
  stay green.
- **Server half:** bind the FR-042 scenario of `activation-kind-model.feature` (PUT with `[]` keeps the `ETag` and the
  stored bytes; WP03's canonical form).

### T038: Changed-behaviour fragment

- New `contracts/charter/changes/2026-11-07-a0-ruling-changes.md`: one `### Changed` entry per behaviour (B5, A4,
  B1 writes, A15, B8, A10, A14) and a `### Breaking` block where a `422` becomes a drop (A4, B1).

### T039: Property and conformance tests

- `ActivationRoundTripPropertyTest` and `ActivationConfigConformanceTest` (deployable) updated for v2 and the merged
  kind enum; run `:deployable:test --tests '*Activation*'` and the contract suite for the activation features.

### T147: SC-005 scenarios for the A0 rulings

New `contracts/charter/scenarios/activation-ruling-changes.feature` (`@contract`), one scenario per counter-example
of the ruleset review that A0 changes, each pinning the ruled answer with a positive control in the same answer:
- **A4:** select E and T (E overrides required T), deny T, unselect E, GET then PUT the same body → `200`; and GET,
  pack upgrade removing X, PUT the old body → `200`, `dropped` lists X.
- **B1 (writes):** a pin to a version no pack holds is dropped by a per-artifact write into `droppedPins`; the same
  pin in a create body → `422 version_not_held`.
- **B5:** a select with `includeDeactivated` clears the deny entries of reached artifacts only; a lineage ancestor it
  passes keeps its deny entry.
- **A10:** a must-have item no pack holds is not in force and the closure answer reports it; `DELETE` of an artifact
  another pack's preset names is `409 artifact_referenced` (PR A surface: keep this half `@WIP` with a note naming WP19).
- **A14:** two org packs enhancing one built-in artifact compose (no sibling conflict).
- **A15:** a project pack's `kind_defaults: {directive: locked}` locks its own and its ancestors' directives only; a
  directive of an unrelated pack in the same closure stays deniable.
- **B8:** a pack's own lock does not refuse its own per-artifact `PUT`.
Record the red per scenario (bound glue, assertion failure) before the code of WP04–WP07 is merged into this lane, or,
when that code is already present, record the failing run of the scenario against the #399 head
(`issue-291-activation-writes`) instead.

### T148: Suite guard: an undefined or pending step fails the run

Decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`: Cucumber 8 reports an undefined step as an aborted (skipped) test, so the build stays green.
- New `UndefinedStepGuard` (a Cucumber `ConcurrentEventListener`): records every `TestStepFinished` whose status is
  `UNDEFINED` or `PENDING` and fails the run on `TestRunFinished` with the list of scenario and step names.
- Register it in `CharterContractSuite`'s `PLUGIN_PROPERTY_NAME` beside `pretty, summary`.
- `UndefinedStepGuardTest`: feed the listener synthetic events — one undefined step → failure naming it; all
  passed → no failure. `@WIP` scenarios are not selected, so they never trip it.

## Test strategy

Mapper/advice unit tests; `activation-kind-model.feature` and `activation-ruling-changes.feature` (red recorded,
then green); the existing activation features rewritten where they pinned the old gate; `ActivationRoundTripTest`
(client, wire body); `UndefinedStepGuardTest`; the deployable activation tests.

## Risks

- This is the WP where the about 45 pinned tests surface: keep a list of every rewritten test with one line on why it changed, for the PR description.

## Definition of Done

- Every subtask above is done, and recorded with `spec-kitty agent tasks mark-status <Txxx> --status done`.
- The tests and scenarios named above were seen failing first (an assertion failure with every step bound, decision `DM-01M6RN6GC7PJS8BGHMAYM4JSSW`) and now pass.
- **Exit check:** the A0 boundary is green: `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none :kernel:check :packs:check :activations:check :deployable:check :client-java:check`; no `@WIP` remains on an A0 scenario except the A10 `DELETE` half (named for WP19); the guard fails a run with an undefined step.
- No disabled test, no new suppression, `HexagonalArchitectureTest` green, Checkstyle limits met.
- Changes stay inside `owned_files`, plus the shared-file edits listed above with their rationale.
- PR A0's scope is green end to end: `./gradlew ... :activations:check :packs:check :kernel:check` and the activation contract features.

## Reviewer guidance

- Check each behaviour against the plan IC and the rule ids cited, not against core's Python answers (C-004).
- Check the red-first evidence (the commit order or the run log) and that no test was weakened to pass.
- Check that nothing outside `owned_files` changed beyond the listed shared-file edits.
