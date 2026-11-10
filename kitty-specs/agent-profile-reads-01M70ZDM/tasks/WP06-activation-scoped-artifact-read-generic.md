---
work_package_id: WP06
title: Activation-scoped artifact read (generic)
dependencies:
- WP03
- WP05
requirement_refs:
- FR-007
- FR-010
- NFR-002
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 2047fbb965c1eff2f5b49d855f9e99753be10fce
created_at: '2026-11-10T03:00:10.490126+00:00'
subtasks:
- T029
- T030
- T031
- T032
- T033
- T034
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/
create_intent:
- contracts/charter/changes/2026-11-09-effective-artifact-read.md
- contracts/charter/examples/EffectiveArtifactRead.directive.yaml
- contracts/charter/paths/activations_activationId_artifacts_kind_artifactId.yaml
- contracts/charter/scenarios/effective-artifact.feature
- contracts/charter/schemas/EffectiveArtifactRead.yaml
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveArtifactAnswer.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveArtifactRead.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/GovernanceFacts.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveArtifactMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceArtifactTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionArtifactTest.java
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveArtifactAnswer.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveArtifactRead.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/GovernanceFacts.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/ActivationController.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveArtifactMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadServiceArtifactTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionArtifactTest.java
- contracts/charter/paths/activations_activationId_artifacts_kind_artifactId.yaml
- contracts/charter/schemas/EffectiveArtifactRead.yaml
- contracts/charter/examples/EffectiveArtifactRead.directive.yaml
- contracts/charter/changes/2026-11-09-effective-artifact-read.md
- contracts/charter/scenarios/effective-artifact.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/EffectiveReadSteps.java
role: implementer
tags: []
tracker_refs: []
---
# WP06: Activation-scoped artifact read (generic)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

`GET /activations/{activationId}/artifacts/{kind}/{artifactId}` answers, for every served kind, the
layer-merge winner:

- `kind`, `artifactId`, `packId`, `level`;
- `state`, `required`, `policy`, `reason`;
- `activationId`, `revision`, `governanceFingerprint`;
- `_links` `self` and `artifact` (the winner's pack-scoped read).

It embeds no body. The application service built here is also the one WP07's profile route uses. The mission-types
session wires its `mission_type` route to it, so keep the names promised to them.

## Context

- Operator decisions `01M70ZFPHZTBH670GF7046XDW0` and `01M710DQEMV7VPKDNQPWKPGD4F`. Plan: IC-06, fold A3, C4, C6.
- **Promised to the mission-types session:**
  - `PacksSession.effectiveArtifact(kind, id, depth, InForceQuery)`;
  - `EffectiveReadService.artifact(ActivationId, ArtifactKind, String artifactId, int depth[, ifNoneMatch])`, whose
    answer carries the winner as a kernel `CharterArtifact`.

  If you must change either, tell the orchestrator.

## Subtasks

### T029 — Contract (red first)

1. **The path**, tag `Activation` (singular; it generates `ActivationApi`, fold C6), `operationId:
   getEffectiveArtifact`.
   - Parameters: `ActivationId`, `ArtifactKind`, `ArtifactId`, `Depth`.
   - Headers on 200: `Charter-Source-Fingerprint` (required), `ETag`, `Charter-Contract-Version`.
   - Responses (fold C4):
     - `304` on `If-None-Match`;
     - `400` for a bad `depth`;
     - `404`: `activation_not_found`, `kind_not_found` and `artifact_not_found`. The last applies when no pack of
       the activation holds it.
   - The description says that `agent_profile` is answered by `getEffectiveAgentProfile` (WP07).
2. **`schemas/EffectiveArtifactRead.yaml`.** It is closed. Reuse the `EffectiveArtifact` field definitions
   (`allOf`, or `$ref` per field), plus `activationId`, `revision` and `governanceFingerprint`.
3. Example for a directive, and a `### Added` fragment.

### T030 — `packs.application` side

Use WP03's `ClosureView.effectiveArtifact`. If WP03 left a gap, fill it in WP03's file with a one-line rationale.

### T031 — `PacksSession` and `ClosureViewSession`

1. Add `PacksSession.effectiveArtifact(ArtifactKind kind, String id, int depth, InForceQuery query) ->
   Optional<…>`, answered from ONE session.
2. `ClosureViewSession` maps it into `activations.domain` value types:
   - `EffectiveArtifactRead`: the winner `CharterArtifact`, pack, level and state facts;
   - `GovernanceFacts` with entries (step, relation, via, hops, suggestion and state), for WP07. These are new
     `activations.domain` types with their own step enum. `contextOf` drops step, via and hops, so don't reuse it.
3. Tests.

### T032 — `EffectiveReadService.artifact`

1. It opens one `PacksSession`, resolves the in-force states as the effective list does, and answers the winner, the
   state and (for a profile) the governance with states.
2. ETag: add `EffectiveTag.Read.ARTIFACT` (and `PROFILE`, for WP07), whose query covers kind, id and depth. It
   honours `If-None-Match` → 304, as the effective list does.
3. A kind not in `ArtifactReadService.SERVED` refuses `kind_not_found`. Expose a `packs.application` query for that
   list, because activations may name `packs.application`.
4. Unit tests:
   - the winner under a layer override;
   - an inactive winner (the state is reported);
   - no holder (`artifact_not_found`);
   - an unknown activation.

### T033 — The controller

1. `ActivationController` implements `getEffectiveArtifact`. Map with an `EffectiveArtifactMapper`.
2. Links follow `EffectiveReadMapper`'s style:
   - `self`;
   - `artifact`, to `/api/v1/packs/{winnerPackId}/artifacts/{kind}/{artifactId}`;
   - `activation`.

### T034 — Scenarios

1. `scenarios/effective-artifact.feature` (`@contract`):
   - read a selected directive under an activation: the winner, its state, and the `artifact` link resolves;
   - the overridden winner;
   - `If-None-Match` → 304;
   - unknown activation;
   - unserved kind;
   - missing id.
2. Steps go in `EffectiveReadSteps`.
3. Every new 2xx is covered.

## Definition of Done

- The route works for every served kind except `agent_profile` (WP07).
- One session per read.
- The ETag and 304 work.
- The scenarios are green, and ArchUnit is green.
- These are green:
  ```
  ./gradlew --max-workers=1 :activations:check :contracts:check :deployable:test     --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*'
  ```

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP06 --agent claude --mission agent-profile-reads-01M70ZDM`

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
