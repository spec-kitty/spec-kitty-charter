---
work_package_id: WP05
title: Endpoint refusal at read and write
dependencies: [WP04]
requirement_refs:
- FR-014
- FR-015
- FR-016
- C-004
- NFR-002
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/test/resources/planted/r30-edge-procedure-requires-action/expected.json
- service/packs/src/test/resources/planted/r31-edge-overrides-across-kinds/expected.json
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/OverrideSourceRule.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipWire.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/override-source-kind.feature
- contracts/charter/scenarios/prompt-fragment-refusals.feature
- contracts/charter/scenarios/pack-write-refusals.feature
- contracts/charter/scenarios/read-refusals.feature
- service/packs/src/test/resources/planted/r*-edge-*/**
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ExampleCoverageTest.java
role: implementer
---

# Work Package Prompt: WP05 - Endpoint refusal at read and write

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-01.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

A graph edge whose endpoint kinds its relation does not allow is refused at read (validate mode, the load, and composing reads) and at write (`putPackRelationship`, whole-pack registration), with the violation `relationship_endpoint_not_allowed`. Slice C's `overrides_source_not_activatable` keeps its code for its own fault. Every `Relationship` is built through the typed factories or `EndpointRule`. #350 is delivered.

## Context

- WP03 added `EndpointRule`, the factories and the table; WP04 retired the old relations.
- Slice C's precedent, on `main`:
  - `OverrideSourceRule`;
  - `PackFindingCode.OVERRIDES_SOURCE_NOT_ACTIVATABLE`, mapped by `PackPolicy.violationKind`;
  - a violation inside `422 validation_failed` at `/from` on write;
  - `pack_unreadable` on a composing read, with the pointer at the graph document;
  - listed in `CharterRefusalCode.yaml`'s `validation_failed` prose.
- Squad findings: D-M1, T-M4, T-M5, M-M5.

## Subtasks

### T020: Construction through the bridge

Route the 7 main construction sites:

- `EdgeIndex:199/422`: add `Relationship.withToPack(..)` for the copies;
- `WholePackStore:693`;
- `Guard:144`: use `Relationship.implementedBy`;
- `DomainRepositoryService:633`;
- `PackPolicy:776`;
- `RelationshipWire:88`.

Then:

- Make `Relationship`'s raw constructor package-private, an out-of-map edit in WP03's file.
- Add an ArchUnit rule to `HexagonalArchitectureTest`: only `kernel.domain` may call `Relationship.<init>`. Give it a known-bad control, as its sibling rules have.
- Update about 32 test files to use the factories (`KindRef.directive(..)` and so on).

### T021: The read side

- `PackPolicy.endpoints`, or `edge()`, calls `EndpointRule.bind`.
  - A `Fault` becomes a blocking `PackFinding` with the new `PackFindingCode.RELATIONSHIP_ENDPOINT_NOT_ALLOWED`. Map it in `violationKind`, as C does.
  - Use the same pointer as C, the graph document.
  - The message names the relation and both kinds.
- `DomainRepositoryService.typedEdge` calls `bind`, and refuses where it used to drop silently. `WholePackStore.load` already refuses `DRG_SCHEMA_ERROR`; follow the same path for the new code.
- Composing reads answer `500 pack_unreadable`, as C's code does.

### T022: The write side and the fault order

- `RelationshipRegistrationService` refuses with `422 validation_failed`, with the violation `relationship_endpoint_not_allowed` at `/type`. C's fault keeps `/from`.
- Fix the order, and test every pair:
  1. `StepEnhancementRules` / `refuseStepEnhancementEdge` (existing code);
  2. then `OverrideSourceRule`;
  3. then the table.
- Fold `OverrideSourceRule` into `EndpointRule`'s first check, or keep the class and call it from the bridge. Either way, one predicate and one code string.

### T023: Registration and example coverage

- `CharterRefusalCode.yaml`: list `relationship_endpoint_not_allowed` beside `overrides_source_not_activatable`, in `validation_failed`'s description and its `x-refusal` text, the way C did.
- Leave room for WP06 and WP07's codes (`mission_step_unlinked`, `substep_delegation_unresolved`, `substep_delegation_unscoped`). Those work packages add their rows as out-of-map edits.
- Extend `ExampleCoverageTest`, so that every violation code listed there needs an example. Add the examples.

### T024: Invalid packs and scenarios

- Add one planted invalid pack per relation family where a refusal is reachable: requires, suggests, scope, instantiates, refines, specializes_from, enhances, rejects, vocabulary and applies.
- Add #350's `procedure -requires-> action`, and `overrides` between two different activatable kinds.
- Each pack carries exactly one fault (`expected.json`).
- Scenarios:
  - `pack-write-refusals.feature`: a `PUT` for #350, and a `PUT` for overrides across kinds;
  - `read-refusals.feature`: a composing read of a stored pack.
- `override-source-kind.feature`: rewrite the scenario that allows an `overrides` edge from an activatable kind "whatever its target". It now needs the same kind. Keep C's refusals.
- `prompt-fragment-refusals.feature`: rewrite each fixture so it has exactly one fault under the fixed order.

### T025: Tension

Where `Tension` is read (`in_tension_with`) and written, bind it through `EndpointRule.bindTension`. A non-guidance endpoint is refused with `relationship_endpoint_not_allowed`.

## Validation

- `./gradlew :kernel:test :packs:test :activations:test :deployable:test --max-workers=1` is green.
- Every new invalid pack is refused with its single named fault.
- The census over the example packs, scenarios and fixtures refuses 0 valid-path edges.

## Definition of Done

- T020 to T025 are done.
- #350 is covered by an invalid pack and a scenario.
- The ArchUnit rule is in place, with a known-bad control.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **A large test diff from constructor typing.** Keep it mechanical; review the factories, not every test.
- **A fault-order regression.** One parameterized test covers every pair of rules.
- **#554 landing first.** If it has, put the read check in its graph rule, keeping the same codes.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP05 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
