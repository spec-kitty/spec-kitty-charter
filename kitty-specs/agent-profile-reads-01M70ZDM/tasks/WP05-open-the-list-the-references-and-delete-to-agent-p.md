---
work_package_id: WP05
title: Open the list, the references and DELETE to agent_profile
dependencies:
- WP04
requirement_refs:
- FR-005
- FR-006
- FR-010
- NFR-002
- C-005
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 2a5c7ac3e37ff0f688137297c0dec43a9bce9529
created_at: '2026-11-10T00:31:59.117880+00:00'
subtasks:
- T024
- T025
- T026
- T027
- T028
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
create_intent:
- contracts/charter/changes/2026-11-09-agent-profile-read-changes.md
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactReadServiceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactDtos.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ArtifactController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactRemovalService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactPosition.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ArtifactRemovalServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ArtifactControllerTest.java
- contracts/charter/schemas/ArtifactKindToken.yaml
- contracts/charter/schemas/ArtifactSummary.yaml
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/parameters/ArtifactKind.yaml
- contracts/charter/paths/packs_packId_artifacts_kind_artifactId.yaml
- contracts/charter/paths/packs_packId_artifacts.yaml
- contracts/charter/paths/packs_packId_artifacts_kind_artifactId_references.yaml
- contracts/charter/changes/2026-11-09-agent-profile-read-changes.md
- contracts/charter/scenarios/artifacts.feature
- contracts/charter/scenarios/pack-removal.feature
role: implementer
tags: []
tracker_refs: []
---
# WP05: Open the list, the references and DELETE to agent_profile

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Make `agent_profile` a served kind of the generic surface:

- the pack's artifact list (and its `?kind=` filter);
- the references read;
- DELETE (operator decision `01M70ZFW6S2HG3TEE33RW8VGSB`).

The generic GET never renders a profile body: WP04's literal route wins.

## Context

- Plan: fold A4, C3, C5 and C7.
- This WP owns `ArtifactReadService.java` and its test. WP04 put its read in `ForeignArtifactRead`.

## Subtasks

### T024 — Widen `SERVED` (red first)

1. Append `AGENT_PROFILE` LAST in `SERVED` and in `ArtifactKindToken`. `WireTokenParityTest:184-188` pins them equal,
   in order.
2. In `ABSENT`, map `AGENT_PROFILE → ARTIFACT_NOT_FOUND`. Without it, `absentCode` throws and DELETE answers 500.
3. Add `Item.Foreign(CharterArtifact, PackRevision)` for kinds served by another slice's controller.
4. In `ArtifactDtos.artifact`, the `Foreign` case throws `IllegalStateException("served by the kind's own
   controller")`. It is unreachable, and WP04's `ProfileRoutingTest` proves that.
5. The list item's title comes from `label()`.
6. `ArtifactPosition` follows `SERVED`.
7. Update `ArtifactReadServiceTest:443` (which pins `SERVED`) and `WireTokenParityTest`.

### T025 — DELETE

1. Make removal work for `agent_profile` with the five kinds' rules. `ArtifactFiles` already covers every kind
   through `KindBindings.all()`.
2. Tests:
   - delete a profile from a writable pack with `If-Match` = the pack revision from WP04's `ETag`;
   - a locked or required profile is refused exactly as a directive is;
   - a later read answers 404.

### T026 — Contract wording (`### Changed`)

1. Reword the descriptions that list the five served kinds (fold C9):
   - `ArtifactKindToken.yaml:3-5`;
   - `parameters/ArtifactKind.yaml`;
   - `ArtifactSummary.yaml:18`;
   - the GET and DELETE descriptions and not-found code lists of `paths/packs_packId_artifacts_kind_artifactId.yaml`
     (lines 9-11, 76-79, 122-125). State that `agent_profile` is answered by `getPackAgentProfile`, and that its
     not-found code is `artifact_not_found`.
   - the references and list paths.
2. In `CharterRefusalCode.yaml`, edit ONLY the two description sentences (fold C7):
   - `kind_not_found`'s served list (lines 12-13);
   - `artifact_not_found`'s scope (line 46). It now also answers a missing profile on the pack path.

   This is #437's hot file: change nothing else there. Do not touch `errors.feature`, `ReservedRefusalCodesTest` or
   `ContractDescriptionsTest`.
3. Add `changes/2026-11-09-agent-profile-read-changes.md`, one `### Changed` block covering:
   - the served-kinds wording;
   - `artifact_not_found` on the pack path (the rule: kinds served from now on use `artifact_not_found`);
   - the profile's `ETag` (the pack revision, for DELETE).

   Widening the `ArtifactKindToken` enum is `### Added`, and belongs in WP04's fragment. Append one line there if
   WP04 did not cover it.

### T027 — Scenarios

1. In `artifacts.feature`:
   - the list filtered by `kind=agent_profile` lists profiles, each linking to its read;
   - the references read of a profile lists its graph edges.
2. In `pack-removal.feature`: DELETE of a profile.
3. Every new 2xx is covered.

### T028 — Verify

Run:
```
./gradlew --max-workers=1 :packs:check :contracts:check :deployable:test --tests '*CharterContractSuite*'   --tests '*OperationCoverageTest*'
```

## Definition of Done

- List, filter, references and DELETE work for profiles.
- The parity tests are green.
- `CharterRefusalCode.yaml` changes by two sentences only.
- Fragment present.

## Risks

- Hot-file overlap with #437: tell the orchestrator via the review notes when this lands.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP05 --agent claude --mission agent-profile-reads-01M70ZDM`

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
