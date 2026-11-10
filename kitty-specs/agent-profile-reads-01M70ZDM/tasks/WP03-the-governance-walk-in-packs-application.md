---
work_package_id: WP03
title: The governance walk in packs.application
dependencies:
- WP01
requirement_refs:
- FR-003
- NFR-004
- SC-003
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 8be30285cef89c60851ff09dab2a1a5d7aca70b1
created_at: '2026-11-09T21:56:38.415579+00:00'
subtasks:
- T010
- T011
- T012
- T013
- T014
- T015
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GovernanceWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileGovernance.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewGovernanceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GovernanceWalkTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActionContextWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GovernanceWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ProfileGovernance.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ActionContextWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GovernanceWalkTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewGovernanceTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP03: The governance walk in packs.application

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Walk a profile's governance with the action-context walk's rules, from the profile node. Give `packs.application`
two entry points:

- **pack-scoped:** over one pack's composed closure, merged;
- **activation-scoped:** over the merged closure, with the in-force predicate.

This WP adds no REST surface.

## Context

- Operator decisions `01M70ZFGS5PH4K5TFCS5XPTRRX`, `01M710DXJR9AK5WBGVCDJNANA8` and `01M7115WE6WQ7Q41HSS9ZHD1F3`
  ("as confirmed").
- Plan: IC-03, fold W1 to W6.
- **The engine.** `ActionContextWalk` (around 470 lines) has five steps:
  1. `SCOPE`, one hop from the action node;
  2. `REQUIRES`, transitive, from the scoped artifacts;
  3. `SUGGESTS`, `depth` hops;
  4. `REQUIRES_OF_SUGGESTED`;
  5. `VOCABULARY`, one hop from everything listed.

  It keeps minimum-step semantics and the parent tie order (fewest hops, then the smallest URN, then the smallest
  edge), and sorts entries by URN.
- **The profile walk:** `start = agent_profile:<id>`. The steps run in this order:
  1. `LINEAGE`: `specializes_from`, transitive, through the predicate.
  2. `REQUIRES`: from `{start} ∪ lineage`.
  3. `SUGGESTS(depth)`: from `{start} ∪ lineage`.
  4. `REQUIRES_OF_SUGGESTED`.
  5. `VOCABULARY`: from `{start} ∪ listed` (fold W3).

  The start is exempt from the predicate (fold W4).
- **Rules that differ from the cascade (operator ruling W1):**
  - Under an activation, an ancestor that is not in force is not listed and seeds nothing.
  - Ancestors in force seed both `requires` and `suggests`.

## Subtasks

### T010 — Extract the engine without changing the action walk

1. Refactor `ActionContextWalk.Run` into a shared engine. Its inputs are:
   - a start node;
   - an ordered plan of steps, each a relation, a layer bound and its seeds;
   - the predicate;
   - an optional index of written links (T012).

   Choose the shape yourself. The constraint: `ActionContextWalk.walk` answers exactly what it answers today.
2. The existing `ActionContextWalkTest` (and the effective action-context scenarios) must pass unchanged. Run them
   before and after. Do not touch their assertions.

### T011 — The `LINEAGE` step and the profile walk (`GovernanceWalk`)

1. Add the profile walk as `GovernanceWalk`, or a method on the engine, using the step plan above.
2. Its step enum is separate from `ActionContextWalk.Step`, or extends it with `LINEAGE(SPECIALIZES_FROM)`. Fold W6:
   `Step` reaches no existing wire. Still, map it exhaustively wherever it is switched on.
3. Each entry carries:
   - `urn`, `step`, `relation`, `via` and `hops`;
   - `when` and `reason` for a `suggests` entry;
   - `held` (T012).

   No `enhancedBy` or `enhances` (fold W4).
4. Out-of-force `suggests` targets are reported as candidates, as the action walk reports them.
5. Write the tests red first. Each one builds its own `EdgeIndex` fixture.
   - Lineage of two levels: the ancestors' `requires` and `suggests` are listed, and `via` is the ancestor.
   - A `specializes_from` cycle terminates, and every node is listed once (NFR-004: under 1 s).
   - A target reached by both `requires` and `suggests` is listed once, by the minimum step.
   - The profile's own `vocabulary` edge is listed (W3).
   - `suggests` with `when` and `reason` keeps both.
   - Under a predicate that refuses an ancestor, the ancestor and its contributions are absent (W1).
   - A start refused by the predicate is still walked (W4).

### T012 — Written links, so an unheld target can be listed (`held=false`; fold W2)

1. `EdgeIndex.links()` drops edges whose target no pack of the closure holds. Add `writtenLinks()`: the written
   edges of the closure's packs, dangling targets included.
2. Only the pack-scoped walk uses it. It lists an unheld target with `held: false` and never expands it.
3. Red test: a fixture profile whose `specializes_from` parent no pack holds. The parent is listed with
   `held=false`, and nothing is listed below it.

### T013 — `ClosureView` entry points

1. **Activation-scoped:** `ClosureView.governance(Urn profile, int depth, InForceStates states)`. It runs over
   `merged().edges()` with `effective(states)`, as `actionContext` does.
2. **The winner read:** `ClosureView.effectiveArtifact(ArtifactKind kind, String id, int depth, InForceStates states)
   -> Optional<EffectiveArtifactView>`. The view carries:
   - the winning `CharterArtifact` entity;
   - its holder (configured pack id and level);
   - for `AGENT_PROFILE`, the governance.

   Find how the merged closure exposes the winning entity:
   - `MergedClosure`;
   - `MergeInputs`;
   - `ClosureView.MergedView.resolutionEdges`.

   If nothing public exposes it, add the smallest accessor. This method is promised to the mission-types session
   (they narrow `CharterArtifact` to `MissionType`), so keep the name and the signature.
3. **Pack-scoped:** a method that takes a pack's composed `depends_on` closure (as `ClosureQueries.snapshot(Optional.of(List.of(pack)))`
   builds it) and walks its merged view with every node in force, plus `writtenLinks` (fold W5).
   - Decide whether this lives on `ClosureView`, or as a `ProfileGovernance` service in `packs.application`.
   - It must run inside the caller's `ConsistentRead.read` supplier, with no inner retry. WP04 composes it with the
     body read.
4. Tests:
   - `ClosureViewGovernanceTest` over a two-pack closure: a `built-in::`-qualified parent in another pack is
     resolved.
   - A layer override, where a higher-level pack's copy of a profile wins: its edges are the ones walked.

### T014 — Holding pack per entry

For each entry, give the holding pack:

- pack-scoped: the first holder in closure order (`holderOf`);
- activation-scoped: the winner.

Test both.

### T015 — Performance guard

A test over the real built-in corpus at the pin walks all 26 built-in profiles plus the 5 qa profiles, and asserts
that each walk finishes. Keep the time budget generous; NFR-004 is the cycle fixture.
- If the corpus test infrastructure (`OpenPacksCorpus`) can be used from `:packs` tests, use it.
- Otherwise, defer this to WP04's corpus test and note that here.

## Definition of Done

- The action walk is unchanged, and all its tests are green.
- The profile walk passes every case listed.
- `writtenLinks` gives `held=false`.
- Both `ClosureView` entry points exist and are tested.
- `:packs:check` is green.

## Risks

- **Changing the action walk's answers:** run the activations scenarios (`:deployable:test --tests '*CharterContractSuite*'`)
  before handing off.
- **NullAway and Error Prone:** keep `Optional` and nullability explicit.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP03 --agent claude --mission agent-profile-reads-01M70ZDM`

## Rules that hold for every WP of this mission

- **Charter wins.** Read `.kittify/charter/charter.md`. Both are gates:
  - no divergence from the charter's own model, contract and packs;
  - no fake green. Never skip, disable or weaken a test to get green.
- **Test first.** Write the failing test or scenario first, see it red, then make it green. Record the red run in the
  WP's commit message body (one line: the test and its failure).
- **Gradle:**
  - JDK: `JAVA_HOME=/usr/lib/jvm/temurin-25`.
  - Always `--max-workers=1`, in the foreground, one run at a time, never `--stop`.
  - On a Maven Central 429, wait and rerun.
- **Before you hand off:**
  - Run the module's tests, plus `./gradlew --max-workers=1 :deployable:test --tests '*HexagonalArchitectureTest*'`
    when you touch a ring.
  - Run `./gradlew --max-workers=1 spotlessApply` and the module's `check`.
  - The final WP runs the full `verifyAll`.
- **Commits:**
  - Each commit is green on its own: the repo lands commits individually.
  - Commits are SSH-signed, which is automatic here.
  - End each message with:
    ```
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
    Claude-Session: https://claude.ai/code/session_01E5wJUBWJn7yEXdCeUEJGmP
    ```
  - No model identifiers in code, comments or commit text.
- **Contract changes** follow ADR-0022 (0.x line) and ADR-0047:
  - one fragment per `###` block in `contracts/charter/changes/`;
  - never edit `contracts/charter/CHANGELOG.md`;
  - every 2xx response gets an example with `_links`;
  - every new 2xx is exercised by an `@contract` scenario (`OperationCoverageTest`).
- **Javadoc:** every new package has a `package-info.java` annotated `@NullMarked`. Javadoc runs with `-Werror`.
- **Design references:** `plan.md` (the IC sections, then the "Fold of the plan-point squad", which wins where they
  differ), `data-model.md` and `research.md` in `kitty-specs/agent-profile-reads-01M70ZDM/`.
