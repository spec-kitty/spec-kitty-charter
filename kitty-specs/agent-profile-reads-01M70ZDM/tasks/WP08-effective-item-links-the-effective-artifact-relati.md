---
work_package_id: WP08
title: Effective item links, the effective-artifact relation and the root activations link
dependencies:
- WP06
requirement_refs:
- FR-008
- FR-009
- NFR-002
planning_base_branch: issue-407-agent-profile-reads
merge_target_branch: issue-407-agent-profile-reads
branch_strategy: Planning artifacts for this mission were generated on issue-407-agent-profile-reads. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-407-agent-profile-reads unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-agent-profile-reads-01M70ZDM
base_commit: 60fcfb460e2191937efe24994f0cffdb5464bd88
created_at: '2026-11-10T09:26:26.924850+00:00'
subtasks:
- T040
- T041
- T042
- T043
- T044
history:
- timestamp: '2026-11-09T21:00:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
create_intent:
- contracts/charter/changes/2026-11-09-effective-item-links.md
execution_mode: code_change
model: ''
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapperTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/InfoController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/InfoControllerTest.java
- contracts/charter/schemas/EffectiveArtifact.yaml
- contracts/charter/schemas/EffectiveGraphNode.yaml
- contracts/charter/schemas/ApiRoot.yaml
- contracts/charter/examples/ApiRoot.links.yaml
- contracts/charter/examples/EffectiveArtifactList.first-page.yaml
- contracts/charter/examples/EffectiveActionContext.implement.yaml
- contracts/charter/examples/EffectiveGraph.one-edge.yaml
- contracts/charter/examples/EffectiveGraph.layer-override.yaml
- contracts/charter/changes/2026-11-09-effective-item-links.md
- contracts/charter/scenarios/root.feature
- contracts/charter/scenarios/effective-reads.feature
- contracts/lint/ruleset.yaml
- docs/development/hypermedia-hateoas.md
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/RelationTable.java
role: implementer
tags: []
tracker_refs: []
---
# WP08: Effective item links, the effective-artifact relation and the root activations link

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

A client can get from the API root to the reads without building URLs:

- The root links `activations` with `method: POST`.
- Every item of a served kind in the effective list, the effective graph and the action context carries:
  - `effective-artifact`: WP06's or WP07's activation-scoped read;
  - `artifact`: the winner's pack-scoped read.

This is the item-link part of #450 for the served kinds. The mission-types session adds its kinds through `SERVED`.

## Context

Plan: fold C8 and D6. Operator: `activations` on the root is in the contract now.

## Subtasks

### T040 — The relation (red first)

1. Add `effective-artifact` to the table in `docs/development/hypermedia-hateoas.md`:
   - target: `…/activations/{activationId}/artifacts/{kind}/{artifactId}`;
   - method: `GET`;
   - On: effective items, graph nodes, action-context items and profile governance entries;
   - status: "In the contract".
2. In the same table, widen the "On" column of `artifact` to include effective items and governance entries.
3. Change the `activations` row to "In the contract", with `POST` from the root.
4. Add the relation to the ruleset enum in `contracts/lint/ruleset.yaml` in the same commit
   (`RelationTableParityTest`). Update `RelationTable.java` in the bdd tests if it holds a list.
5. `RelationSetParityTest` checks only the packs `Relation` enum, so `effective-artifact` built in activations or
   profiles would go unchecked (fold A6). Extend the check, or add a test that every relation an activations or
   profiles mapper emits is in the table.

### T041 — Contract

1. `EffectiveArtifact.yaml` and `EffectiveGraphNode.yaml` gain `_links` (closed `Links`). The action-context items
   use `EffectiveArtifact`. Check that.
2. `ApiRoot` gains `activations` (`method: POST`). `LinkChecks` resolves by method.
3. Update the examples:
   - `ApiRoot.links`;
   - the effective list;
   - the action context;
   - both graph examples.
4. One `### Added` fragment.

### T042 — Mappers

1. `EffectiveReadMapper` emits the two links on items of a served kind, in the list, the graph and the action
   context. A graph node is a URN plus a kind: link it only for a served kind, never for `action` nodes. Unit tests
   cover each.
2. `InfoController.getApiRoot` (packs.infra.api) adds the `activations` link with `method: POST`.

### T043 — Scenarios

1. `root.feature:27` pins the root's body exactly: update it.
2. In `effective-reads.feature` (or whichever file holds the effective-list and graph scenarios): items carry links,
   and following `effective-artifact` answers 200.

### T044 — Verify

Run:
```
./gradlew --max-workers=1 :activations:check :packs:check :contracts:check :deployable:test   --tests '*CharterContractSuite*' --tests '*RelationTableParityTest*' --tests '*LinkChecksTest*'
```

## Definition of Done

- The links are present and resolve.
- The relation table, the ruleset and the parity checks are green.
- `root.feature` is updated.

## Branch Strategy

- Planning branch: `kitty/mission-agent-profile-reads-01M70ZDM`, which lands on `issue-407-agent-profile-reads`. The final merge target of the PR is `main`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Never pick a base branch by hand.
- Implementation command: `spec-kitty agent action implement WP08 --agent claude --mission agent-profile-reads-01M70ZDM`

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
