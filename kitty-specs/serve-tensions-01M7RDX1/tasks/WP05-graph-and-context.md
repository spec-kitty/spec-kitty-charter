---
work_package_id: WP05
title: The effective graph and the action context
dependencies:
- WP04
requirement_refs:
- FR-007
- FR-008
- NFR-004
subtasks:
- T024
- T025
- T026
- T027
- T028
authoritative_surface: service/activations/src/main/java/ai/speckitty/charter/activations/
owned_files:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/GraphSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/TensionSnapshot.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveContext.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/EffectiveTag.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/PacksSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/EffectiveReadService.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSession.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapper.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveTagTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/domain/EffectiveContextTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/EffectiveReadOperationBudgetTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/api/EffectiveReadMapperTest.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionTest.java
- contracts/charter/schemas/EffectiveGraphEdge.yaml
- contracts/charter/schemas/EffectiveGraph.yaml
- contracts/charter/schemas/EffectiveActionContext.yaml
- contracts/charter/schemas/ContextTension.yaml
- contracts/charter/examples/EffectiveGraph.tension.yaml
- contracts/charter/examples/EffectiveGraph.one-edge.yaml
- contracts/charter/examples/EffectiveGraph.layer-override.yaml
- contracts/charter/examples/EffectiveActionContext.implement.yaml
- contracts/charter/examples/EffectiveActionContext.tension.yaml
- contracts/charter/headers/EffectiveETag.yaml
create_intent:
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/TensionSnapshot.java
- contracts/charter/schemas/ContextTension.yaml
- contracts/charter/examples/EffectiveGraph.tension.yaml
- contracts/charter/examples/EffectiveActionContext.tension.yaml
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ClosureViewSessionTest.java
planning_base_branch: feat/602-tensions
merge_target_branch: feat/602-tensions
branch_strategy: Planning artifacts for this mission were generated on feat/602-tensions. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge
  back into feat/602-tensions unless the human explicitly redirects the landing branch.
agent_profile: java-jenny
role: implementer
agent: claude
execution_mode: code_change
history:
- at: '2026-11-19T07:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
tracker_refs:
- '#602'
tags: []
---

# Work Package Prompt: WP05 - The effective graph and the action context

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/serve-tensions-01M7RDX1/`: `spec.md`, `plan.md` (its last section, "Revisions after the
plan-point squad and rulings G1 to G3", wins over the concerns above it), `data-model.md`, `research.md`,
`research/squad-plan.md`; and `docs/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field.md`
(accepted, with rulings G1 to G3). Read `.kittify/charter/charter.md` and `AGENTS.md`.

Charter rules that bind every subtask:

- The charter's model is canonical: no reader of `in_tension_with`, no shim, no deprecation window.
- Tests and examples carry only the canonical shape, except error-handling fixtures. Never exclude a field from a
  parity or round-trip check to make a test pass.
- Tests are held to the production bar: outcome assertions, a positive control beside every refusal, no fake green.

## Branch Strategy

- Planning base and final merge target: `feat/602-tensions` (PR #606 to `main`). The mission branch is
  `kitty/mission-serve-tensions-01M7RDX1`.
- The execution worktree is allocated per computed lane from `lanes.json`.
- Start with `spec-kitty agent action implement WP05 --agent claude --mission serve-tensions-01M7RDX1`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names. Conventional commit subjects naming
  `(#602)`.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time. Before `for_review`, run the module tests
  you touched and `./gradlew verifyAll --max-workers=1` when the WP changes the contract.
- An edit outside `owned_files` is allowed only when small and recorded in the WP's activity log with a one-line
  reason.


## Objective

`getEffectiveGraph` answers each tension in force as an edge `tension` with `reconciledBy` and its words;
`getEffectiveActionContext` answers a required `tensions` member for the pairs whose two sides it lists in force
(ADR-0045 decisions 9 and 10; spec FR-007, FR-008, NFR-004, US2; plan IC-05, IC-06 and the Revisions section). Both
answer from WP04's `ClosureView.tensions(states)`.

## Context (re-check each line)

- Graph: `EffectiveReadService.graph` (:503-514) → `PacksSession.graph` (:94) → `ClosureViewSession.graph`
  (:130-132, `graphOf` :358-372) → `GraphSnapshot` (`Edge(NodeRef, RelationType, NodeRef)` :31) →
  `EffectiveReadMapper.toGraph` (:154-175).
- Context: `EffectiveReadService.actionContext` (:528-561) → `ClosureViewSession.actionContext` (:135) →
  `EffectiveContext.of(set, walked, missionType, action)` (`EffectiveContext.java:91-110`; record :22) →
  `EffectiveReadMapper.toContext` (:185-224).
- `HexagonalArchitectureTest` (:69) limits packs access to `activations.infra.packs`; `PacksSession` imports only
  `activations.domain` types. So the snapshot type is an activations domain record, mapped in `ClosureViewSession`.
- `EffectiveTag.DOMAIN = "charter-effective-read\0v3\n"` (:51); its javadoc requires a bump when bodies change for the
  same inputs. v3 was released (0.2.0-rc.1), so v4.
- `schemas/_index.yaml` and `examples/_index.yaml` are WP06's files: adding `ContextTension` and the new examples there
  is a recorded one-line out-of-map edit each.

## Subtasks

### T024: Domain and session

1. `activations.domain.TensionSnapshot(NodeRef source, NodeRef target, Optional<String> when, Optional<String> reason, List<NodeRef> reconciledBy)`.
2. `GraphSnapshot.Edge` gains `Optional<TensionWords>` (or the three fields) present only for `RelationType.TENSION`.
3. `PacksSession`: `List<TensionSnapshot> tensions(InForceQuery)`; `ClosureViewSession` maps from
   `ClosureView.tensions(statesOf(query))`; `graphOf` maps the projection's tension edges with their words.
4. One merged closure per read: the graph read and the tensions must not resolve the closure twice (NFR-004).

### T025: The graph's wire

1. `EffectiveGraphEdge.yaml`: add `when`, `reason`, `reconciledBy` (array of `Urn`, `uniqueItems: true`, sorted), each
   with `x-derived`. Express "only on a tension edge" with a documented `oneOf`, as `Artifact.yaml:20-32` does:
   - branch 1: `properties.relation.const: tension`, `required: [reconciledBy]`;
   - branch 2: `relation: {not: {const: tension}}` and `not: {anyOf: [{required: [when]}, {required: [reason]}, {required: [reconciledBy]}]}`.
   Keep `additionalProperties: false` working with the `oneOf` (put the properties at the top level).
2. `EffectiveGraph.yaml`: the `edges` description says tension edges come from the tensions in force (canonical,
   collapsed); fix the stale `governanceFingerprint` text if it disagrees with the skipped report's closure digest
   (squad C12) after checking what the service actually answers.
3. `EffectiveReadMapper.toGraph`: map the words for tension edges only.
4. Examples: new `EffectiveGraph.tension.yaml` (one reconciled tension beside a `requires` on the same pair); existing
   examples stay valid.

### T026: The context

1. `EffectiveContext` gains `List<TensionSnapshot> tensions`: those whose two sides are among the context's items in
   state active (a suggestion never counts). A mission type not in force → `[]`.
2. `ContextTension.yaml` (new): `source`, `target`, `when?`, `reason?`, `reconciledBy` (required), sharing the
   property definitions with `EffectiveGraphEdge` (`$ref` the same property schemas so they cannot drift).
3. `EffectiveActionContext.yaml`: required `tensions` (array of `ContextTension`), with `x-derived`; describe the rule
   (both sides listed in force; every reconciler in force; `depth` matters only through what is listed).
4. Mapper `toContext`; examples: `EffectiveActionContext.implement.yaml` gains `tensions: []`; new
   `EffectiveActionContext.tension.yaml`.

### T027: Tags and budget

1. `EffectiveTag.DOMAIN` → `charter-effective-read\0v4\n`; javadoc names why (graph, context and activation bodies
   change for the same inputs; #602). `headers/EffectiveETag.yaml`: the recipe text says v4.
2. `EffectiveTagTest` (:346-369): the pinned literals move to v4; add a twin asserting a v3-built tag no longer
   matches.
3. `EffectiveReadOperationBudgetTest`: for a closure with no tension, the graph, context and skipped reads perform the
   same counts as before (pin the counts you find on `main`); with tensions, the graph and context reads add no
   closure resolution.

### T028: Unit tests

`EffectiveContextTest`: both sides active → listed; one side a suggestion → absent; mission type not in force → `[]`
(twin: the same activation's graph has the tension). `EffectiveReadMapperTest`: a tension edge maps its words; a
`requires` edge has none. `ClosureViewSessionTest`: the snapshot mapping. These feed the activations PIT floor (85).

## Definition of Done

`./gradlew :activations:test :deployable:test --max-workers=1` and `verifyAll` pass; the contract checks accept the
new schemas and examples.

## Risks and reviewer guidance

- The required `tensions` member and the new edge fields are breaking for a closed-schema client: WP08 records them
  under `### Breaking`.
- Reviewers: validate the `oneOf` with a tension edge missing `reconciledBy` (must fail) and a `requires` edge with
  `when` (must fail).
