---
work_package_id: WP07
title: 'Profile under an activation: :profiles-edge route'
dependencies:
- WP04
- WP06
requirement_refs:
- FR-007
- FR-004
- NFR-002
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 280cbcb809a26727f1c729f7e713e3d3229ff4c6
created_at: '2026-11-10T04:01:48.569171+00:00'
subtasks:
- T035
- T036
- T037
- T038
- T039
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/
create_intent:
- contracts/charter/changes/2026-11-09-effective-agent-profile-read.md
- contracts/charter/examples/EffectiveAgentProfileRead.java-jenny.yaml
- contracts/charter/paths/activations_activationId_artifacts_agent_profile_artifactId.yaml
- contracts/charter/scenarios/effective-agent-profile.feature
- contracts/charter/schemas/EffectiveAgentProfileRead.yaml
- contracts/charter/schemas/EffectiveGovernanceEntry.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileProblemParityTest.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfileRead.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfiles.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/ActivationsEffectiveProfiles.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveAgentProfilesController.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileRuntimeHints.java
execution_mode: code_change
model: ''
owned_files:
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/activations/ActivationsEffectiveProfiles.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfiles.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/EffectiveProfileRead.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileRuntimeHints.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/EffectiveAgentProfilesController.java
- contracts/charter/paths/activations_activationId_artifacts_agent_profile_artifactId.yaml
- contracts/charter/schemas/EffectiveAgentProfileRead.yaml
- contracts/charter/schemas/EffectiveGovernanceEntry.yaml
- contracts/charter/examples/EffectiveAgentProfileRead.java-jenny.yaml
- contracts/charter/changes/2026-11-09-effective-agent-profile-read.md
- contracts/charter/scenarios/effective-agent-profile.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileProblemParityTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP07: Profile under an activation: :profiles-edge route

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

`GET /activations/{activationId}/artifacts/agent_profile/{artifactId}` (tag `EffectiveAgentProfiles`, so it
generates its own `EffectiveAgentProfilesApi`, implemented by `EffectiveAgentProfilesController` in :profiles-edge;
`operationId: getEffectiveAgentProfile`) answers `EffectiveAgentProfileRead`. That is WP06's `EffectiveArtifactRead` plus:

- the embedded `agentProfile` body of the winner;
- `governance: EffectiveGovernanceEntry[]`, walked under the activation's in-force states.

The route is served by `:profiles-edge` and calls WP06's `EffectiveReadService.artifact`. This follows the
operator's ruling: the shared surface is the application layer, and one service is wired to several endpoints.

## Context

- Plan: fold C1, C4, C6, A3, A6, W1 and W4.
- **One session (fold A3):** the body comes from the same `EffectiveReadService.artifact` answer (the winner's
  `CharterArtifact`, narrowed to `AgentProfile`). It is never re-read through `ProfileSource`.

## Subtasks

### T035 — Contract (red first)

1. **`schemas/EffectiveGovernanceEntry.yaml`.** `allOf` `EffectiveArtifact`, plus `step` (`GovernanceStep`),
   `relation`, `via` and `hops`. A suggestion sits inline (`suggestion`), as `EffectiveArtifact` already carries it.
   There is no `suggestions[]` (fold C1).
2. **`schemas/EffectiveAgentProfileRead.yaml`.** `allOf` `EffectiveArtifactRead`, plus `agentProfile` and
   `governance`.
3. **The path.**
   - Headers: `Charter-Source-Fingerprint`, `ETag` (`EffectiveTag.Read.PROFILE`), and a `304`.
   - Responses: `400` (depth); `404` (`activation_not_found`, `artifact_not_found`).
   - Its description states:
     - the stored winner is answered, and `enhances` is not resolved, so this is not #380's effective profile;
     - an ancestor not in force is dropped (operator ruling W1, which diverges from the cascade pass-through).
4. **The example.** `java-jenny` under an activation built from this repo's `charter.yaml`. Show a `requires` entry
   in force, and a suggestion with `state: inactive`, `reason: not_selected`.
5. **The fragment:** one `### Added` block.

### T036 — `profiles.application.EffectiveProfiles` (port) and the read service

1. `EffectiveProfiles` (port): `read(activationId, profileId, depth, ifNoneMatch) -> EffectiveProfileRead`. The
   answer holds:
   - the `AgentProfile`;
   - the winner facts and the state;
   - the governance entries with states (profiles-owned value types);
   - the tags.
2. Extend `ProfileReadService` with `underActivation(…)`.
3. Tests with a fake port.

### T037 — `profiles.infra.activations.ActivationsEffectiveProfiles`

1. It calls `EffectiveReadService.artifact(id, AGENT_PROFILE, profileId, depth, ifNoneMatch)`, narrows the winner to
   `AgentProfile`, and maps `GovernanceFacts` to the profiles value types.
2. ArchUnit rule (d) from WP02: this class names only `activations.application` and `activations.domain`.
3. Tests.

### T038 — The controller route and the problem mapping

1. Add `EffectiveAgentProfilesController implements EffectiveAgentProfilesApi`. It is a separate class from WP04's
   controller: one tag per controller avoids a shared owned file and a duplicate Spring mapping. Declare the tag in
   `openapi.yaml`; that file is WP04's, so record a one-line rationale. This deviates from plan fold C6, which put
   both operations under `AgentProfiles`.
2. It maps the body with WP04's `ProfileDtos`, and the governance and envelope with `EffectiveProfileDtos`.
3. Links:
   - envelope: `self`, `artifact` (the winner's pack-scoped profile read) and `activation`;
   - each entry: `effective-artifact` (`/activations/{id}/artifacts/{kind}/{artifactId}`) and `artifact`, for
     served kinds. The relation row is WP08's; coordinate the name `effective-artifact`.
4. **Problem parity (fold A6).** `ActivationProblems` is scoped to `ActivationController`. Make sure a validation
   failure on this route (a bad `depth`) answers the same problem body as the activation routes do. Add
   `ProfileProblemParityTest`, and add an advice scoped to `AgentProfilesController` if needed.
5. **Runtime hints (fold A6).** Add hints for the new DTOs if they are reached through mix-ins, as
   `ActivationRuntimeHints` does. Check with `CharterRuntimeHintsTest`.

### T039 — Scenarios

1. `scenarios/effective-agent-profile.feature` (`@contract`):
   - an activation that selects `java-jenny`: the body, the governance with states, and every link resolves;
   - an ancestor not in force is absent (W1);
   - a profile not selected is still answered, with its state (W4);
   - `If-None-Match` → 304;
   - unknown activation;
   - unknown profile;
   - `depth=2` → 400.
2. Every 2xx is covered.

## Definition of Done

- The route works.
- One session per read.
- Problem parity holds, and the hints are in place.
- The scenarios are green, and ArchUnit is green.
- These are green:
  ```
  ./gradlew --max-workers=1 :profiles-edge:check :contracts:check :deployable:test     --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' --tests '*CharterRuntimeHintsTest*'
  ```

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP07 --agent claude --mission agent-profile-reads-01M70ZDM`

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
