---
work_package_id: WP04
title: 'Pack-scoped profile read: contract and :profiles-edge'
dependencies:
- WP02
- WP03
requirement_refs:
- FR-002
- FR-003
- FR-004
- FR-010
- NFR-001
- NFR-002
- SC-003
- SC-004
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: a378c597eee9297e98d9a7cfe6b0470e8105b565
created_at: '2026-11-09T23:10:20.688419+00:00'
subtasks:
- T016
- T017
- T018
- T019
- T020
- T021
- T022
- T023
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/
create_intent:
- contracts/charter/changes/2026-11-09-agent-profile-read.md
- contracts/charter/examples/Artifact.agent_profile.yaml
- contracts/charter/paths/packs_packId_artifacts_agent_profile_artifactId.yaml
- contracts/charter/scenarios/agent-profile-reads.feature
- contracts/charter/schemas/AgentProfile.yaml
- contracts/charter/schemas/AgentProfileExcluding.yaml
- contracts/charter/schemas/GovernanceStep.yaml
- contracts/charter/schemas/PackGovernanceEntry.yaml
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileRoutingTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ProfileSteps.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadTest.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/GovernanceEntry.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileRead.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileReadService.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileSource.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/AgentProfilesController.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileDtos.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/packs/PacksProfileSource.java
execution_mode: code_change
model: ''
owned_files:
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileReadService.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileSource.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/ProfileRead.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/application/GovernanceEntry.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/packs/PacksProfileSource.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/AgentProfilesController.java
- service/profiles-edge/src/main/java/ai/speckitty/charter/profiles/infra/api/ProfileDtos.java
- service/profiles-edge/src/test/**
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ForeignArtifactRead.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ForeignArtifactReadTest.java
- contracts/charter/schemas/AgentProfile.yaml
- contracts/charter/schemas/AgentProfileExcluding.yaml
- contracts/charter/schemas/PackGovernanceEntry.yaml
- contracts/charter/schemas/GovernanceStep.yaml
- contracts/charter/schemas/Artifact.yaml
- contracts/charter/schemas/_index.yaml
- contracts/charter/paths/packs_packId_artifacts_agent_profile_artifactId.yaml
- contracts/charter/openapi.yaml
- contracts/charter/examples/Artifact.agent_profile.yaml
- contracts/charter/examples/_index.yaml
- contracts/charter/changes/2026-11-09-agent-profile-read.md
- contracts/charter/scenarios/agent-profile-reads.feature
- contracts/charter/scenarios/README.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/ProfileSteps.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ProfileRoutingTest.java
role: implementer
tags: []
tracker_refs: []
---
# WP04: Pack-scoped profile read: contract and :profiles-edge

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

`GET /packs/{packId}/artifacts/agent_profile/{artifactId}` answers the stored profile, in the `Artifact`
envelope (`kind: agent_profile`, an `agentProfile` member), with its pack-scoped governance. This answers #407's
read and #96 for `agent_profile`.

Contract and implementation land together, so no operation is ever reserved.

## Context

- Plan: IC-04 and IC-05, fold C1 to C3, C6, C9 to C11, A2 to A5, W5, P4, P5. Read `data-model.md`.
- The list, the references read and DELETE do NOT open in this WP: `SERVED` is not widened here, WP05 does that.
  The literal route works without `SERVED`.

## Subtasks

### T016 — Contract schemas (red first: lint and generation)

1. **`schemas/AgentProfile.yaml`.**
   - It is closed (`additionalProperties: false`).
   - It has one property per field of `contracts/packs/schemas/agent-profile.schema.json`, in camelCase. That file
     is the oracle (fold C10).
   - Arrays stay arrays: `writingStyle` and `complexityPreference` are arrays.
   - Nullable where the pack schema allows null.
   - Every property has `x-source`, citing `service/profiles/src/main/java/ai/speckitty/charter/profiles/domain/AgentProfile.java`
     accessors (fold C9; `XSourceCitationsTest` checks that the paths exist).
2. **`schemas/AgentProfileExcluding.yaml`** (fold C2). It is closed: `{ wholeFields: string[] | null, fieldValues:
   [{ field, values: string[] }] | null }`.
   - At most one of the two is set, and an absent `excluding` is `null` on the profile.
   - Document the mapping from the two authored forms (`Exclusion.WholeFields` and `Exclusion.FieldValues`).
3. **`schemas/PackGovernanceEntry.yaml`** (fold C1). It has:
   - `kind` (`$ref` NodeKind), `artifactId`, `urn`;
   - `step` (`GovernanceStep`: `lineage`, `requires`, `suggests`, `requires_of_suggested`, `vocabulary`);
   - `relation` (`specializes_from`, `requires`, `suggests`, `vocabulary`; a subset of `Relation`);
   - `via`, `hops`;
   - `suggestion` (`{when, reason}`, nullable, only on `suggests`);
   - `packId` (nullable), `held`;
   - `_links` (`artifact`, only for a held target of a served kind).

   Its properties use `x-derived`. The description states two things:
   - `suggests` entries are listed by this service; core's profile channel treats them as inert (research D4).
   - Body references (`directiveReferences` and the others) are not walked.
4. **`schemas/Artifact.yaml`.**
   - Add the `agentProfile` member, plus an optional `governance: PackGovernanceEntry[]` present only for
     `agent_profile`.
   - Follow the existing `oneOf` and discriminator style.
   - Reword "the other four members" (line 5) and line 31 (fold C9).

### T017 — The path, the tag and the example

1. **`paths/packs_packId_artifacts_agent_profile_artifactId.yaml`.** `GET`, `operationId: getPackAgentProfile`,
   tag `AgentProfiles`. Declare the new tag in the top-level `tags:` of `openapi.yaml`. It generates
   `AgentProfilesApi` (`useTags=true`).
   - Parameters: `PackId`, `ArtifactId`, `Depth`. Check `parameters/Depth.yaml`: its description speaks only of the
     action context, so widen it.
   - Headers on 200: `Charter-Contract-Version`, `Charter-Source-Fingerprint` (the closure fingerprint), and
     `ETag`.
     - The ETag is the pack revision, as for a directive, because DELETE (WP05) needs `If-Match`.
     - State this ETag meaning here. WP05's `### Changed` fragment covers the generic path's wording.
   - 404: `pack_not_found` and `artifact_not_found`.
   - Add `getPackAgentProfile` to the `openapi.yaml` paths list.
2. **`examples/Artifact.agent_profile.yaml`.** A trimmed `java-jenny`, with governance entries showing:
   - `requires` (`java-conventions`);
   - a `suggests` entry with a suggestion;
   - `_links` `self`, `collection` and `references`.

   Register it in `examples/_index.yaml`.
3. **`changes/2026-11-09-agent-profile-read.md`.** One `### Added` block (fold C5).
4. Run `./gradlew --max-workers=1 :contracts:check :charter-api:compileJava :client-java:test` and fix the lint.

### T018 — `profiles.application`

1. **`ProfileSource` (port).** `read(packId, profileId, depth) -> ProfileRead`, where `ProfileRead` holds:
   - the `AgentProfile`;
   - the pack revision;
   - the closure fingerprint;
   - `List<GovernanceEntry>`, a profiles-owned value type: `applicationIsEdge` forbids packs types here.
2. **`ProfileReadService`.** It delegates to the port and owns the read's rules: refusal codes and depth bounds
   (0 or 1, as the action context).
3. Unit tests with a fake port.

### T019 — `profiles.infra.packs.PacksProfileSource`

1. Add a new `packs.application` class, `ForeignArtifactRead`, with `read(PackIdentifier pack, ArtifactKind kind,
   String id, int depth)`. It lives in its own class because WP05 owns `ArtifactReadService`. Reuse that class's
   collaborators (the pack port, `ConsistentRead`, `ClosureQueries`). In ONE `ConsistentRead.read` supplier
   (fold W5) it:
   - loads the item for a kind that is not served by the generic route;
   - composes the pack's closure;
   - runs WP03's pack-scoped walk;
   - returns `Verified<{CharterArtifact, PackRevision, closureFingerprint, entries, servedKinds}>`. `servedKinds`
     is `ArtifactReadService.SERVED`, so the API ring can decide which entries link without naming packs.
2. A missing profile refuses with `ARTIFACT_NOT_FOUND`. A closure whose compose or merge is refused refuses with the
   same code the references read gives. Test both.
3. The adapter narrows `CharterArtifact` to `AgentProfile` (a `profiles.domain` type, which its own slice may name)
   and maps the entries to `GovernanceEntry`.
4. Tests: `ForeignArtifactReadTest`; an adapter test.

### T020 — `profiles.infra.api.AgentProfilesController` and `ProfileDtos`

1. The controller implements `AgentProfilesApi.getPackAgentProfile` under
   `@RequestMapping("${charter.api.base-path:/api/v1}")`.
2. It builds the `Artifact` DTO with:
   - `kind`, `id`, `packId`, `agentProfile`, `governance`;
   - `_links` `self`, `collection` (`?kind=agent_profile`) and `references`;
   - each entry's `artifact` link, for held targets of a served kind. The served list arrives in `ProfileRead`.
     It is filled by the adapter, so the API ring never names packs.
3. Build the links and headers the way `ActivationController` does. Never use `packs.infra.api.Hypermedia` or
   `ArtifactDtos` (fold A2). Use WP02's kernel header names.
4. **`ProfileDtos`** maps every field.
   - Exhaustive mapping test: walk the properties of `agent-profile.schema.json`, and assert that each one appears in
     the serialized DTO of a fixture profile that sets every field.
   - Null versus empty (fold P5): where the domain collapses the two, record it in a test name and assert the wire
     value.
   - `excluding`: both forms round-trip.

### T021 — Routing test (fold A5)

Add `ProfileRoutingTest` in `:deployable`, as a full-context test:

- `GET …/agent_profile/java-jenny` reaches `AgentProfilesController`;
- `GET …/directive/X` still reaches `ArtifactController`;
- a `PUT` on `agent_profile` answers `405`.

### T022 — `@contract` scenarios

1. **`scenarios/agent-profile-reads.feature`** (`@contract`; never touch `profiles.feature`, which is `@WIP` for
   #380). Scenarios:
   - a profile with lineage and edges is read: body, governance and links, with `requires java-conventions` on the
     corpus-like fixture;
   - a missing parent is listed with `held` false;
   - an unknown profile: `404 artifact_not_found`;
   - an unknown pack: `404 pack_not_found`;
   - `depth=2`: `400`.
2. Steps go in `service/deployable/src/test/java/…/deployable/bdd/ProfileSteps.java`. Reuse `CharterSteps` and
   `ScenarioHttp`. Add the step vocabulary to `scenarios/README.md`.
3. Write the scenarios red first, before T018 to T020 exist, and record the red run.

### T023 — Corpus test (NFR-001, SC-004, fold P4)

1. A test over the open-packs corpus at the pin reads all 31 profiles (26 built-in plus 5 qa) through
   `ProfileSource`.
2. It asserts, for each, that the body's meaning round-trips against the pack YAML: parse the YAML, map it to the
   wire shape, and compare.
3. It asserts that `java-jenny`'s governance has `styleguide:java-conventions` as a `requires` entry (SC-003).
4. Put it where the corpus is available. Follow how `WholePackOpenPacksRoundTripTest` gets `OpenPacksCorpus`.

## Definition of Done

- The literal route answers per the contract.
- Every new 2xx is covered by a scenario, and `OperationCoverageTest` is green.
- Mapping is exhaustive, and the corpus test is green on 31 profiles.
- ArchUnit is green.
- These are green:
  ```
  ./gradlew --max-workers=1 :profiles-edge:check :packs:check :contracts:check     :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*' --tests '*ProfileRoutingTest*'
  ```

## Risks

- **`NullOmissionTest`:** keep `required` and `nullable` consistent.
- **Native hints:** if new DTOs are reached only through mix-ins, add runtime hints, as `ActivationRuntimeHints` does.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP04 --agent claude --mission agent-profile-reads-01M70ZDM`

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
