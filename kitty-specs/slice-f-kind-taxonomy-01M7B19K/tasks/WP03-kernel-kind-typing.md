---
work_package_id: WP03
title: Kernel kind typing and the endpoint table
dependencies: []
requirement_refs:
- FR-012
- FR-013
planning_base_branch: slice-f-537-kind-taxonomy
merge_target_branch: slice-f-537-kind-taxonomy
branch_strategy: Planning artifacts for this mission were generated on slice-f-537-kind-taxonomy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-f-537-kind-taxonomy unless the human explicitly redirects the landing branch.
subtasks:
- T011
- T012
- T013
- T014
- T015
agent: claude
history:
- at: '2026-11-13T20:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/kinds/Kind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/KindRef.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointRule.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointPair.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/EndpointTableAgreementTest.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/EndpointRuleTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/kinds/**
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/KindRef.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointRule.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointPair.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Relationship.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/**
role: implementer
---

# Work Package Prompt: WP03 - Kernel kind typing and the endpoint table

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny`, or read `java-jenny.agent.yaml` under `/root/.cache/spec-kitty/open-packs/*/packs/built-in/agent_profiles/` and adopt it.

Then read these files in `kitty-specs/slice-f-kind-taxonomy-01M7B19K/`:

- `spec.md`: its Intent Summary and the requirements this work package maps to.
- `plan.md`: concern IC-01.
- `data-model.md`.
- `research.md`: R6, R14 and R15.
- `research/squad-post-plan.md`.

## Objective

Put ADR-0064 decision 13's endpoint table into the kernel as data on `RelationType`, with typed factories that cannot build a disallowed edge, and one checked bridge from untyped URNs. The change is additive: no caller changes yet, and the retired relations are still present (WP04 removes them).

## Context

- The table is research R6 and the data model.
- The squad's D-B1, D-M1 and D-M2 apply:
  - one factory per rectangle;
  - `KindRef` built only through leaf factories;
  - one source for the table, checked by reflection.
- `NodeRef` exists in `activations.domain`; do not clash with it.
- The kernel must stay pure (`DOMAIN_IS_PURE`: java, JSpecify and the kernel only).

## Subtasks

### T011: Kind markers and groups

- In `kernel.domain.kinds`, add a sealed `Kind` interface, with one final marker class (or record) per `NodeKind` constant: `Directive`, `Tactic`, …, `Action`, `MissionType`, `Template`, `Asset`, `Guard`, `Lifecycle`, `StepEnhancement`, `GlossaryPack`, `MissionStepContract` (WP08 removes it), and so on.
- Add group interfaces as sealed interfaces that the markers implement:
  - `Guidance` (the six guidance kinds);
  - `Activatable` (the kinds whose `ArtifactKind.activatable()` is true);
  - one source group and one target group per rectangle of the table, for example `RequiresSource` = Guidance, AgentProfile and Skill.
- Name the groups after the rectangles. Document each with its row of the table.

### T012: KindRef<K extends Kind>

- A typed URN: optional pack, `NodeKind`, id. The constructor is private.
- Add static leaf factories, one per kind: `KindRef<Directive> directive(String id)`, `directive(PackIdentifier pack, String id)`, and so on. There is no factory for a group, so a `KindRef<Guidance>` cannot be made.
- Add `Urn urn()`, which converts to the existing `Urn`.
- Add `static Optional<KindRef<?>> of(Urn)`, which returns the leaf-typed ref (wildcard), for the bridge.

### T013: The table on RelationType

- Each `RelationType` constant carries `Set<EndpointPair> endpoints()`, where `EndpointPair(NodeKind source, NodeKind target)`.
- Build the sets from the group definitions in one static table. That table is the only place the rows are written.
- The retired relations (`IMPACTS`, `REPLACES`, `DELEGATES_TO`) get an empty set for now. WP04 removes them.
- Add `boolean allows(NodeKind source, NodeKind target)`.
- A row of the form "X → X for the activatable X" (`overrides`, same-kind `enhances`) expands to the diagonal pairs.

### T014: Typed factories and the bridge

- On `Relationship`, add one static factory per rectangle, typed by its groups:
  - `requires(KindRef<? extends RequiresSource>, KindRef<? extends RequiresTarget>)`;
  - `requiresAsset(KindRef<Asset>, KindRef<Asset>)`;
  - `requiresStep(KindRef<MissionType>, KindRef<Action>)`;
  - `suggests(...)`, `scope(...)`, `applies(...)`, `vocabulary(...)`, `instantiates(...)`, `refines(...)`, `specializesFrom(...)`, `enhancesStep(KindRef<StepEnhancement>, KindRef<Action>)`;
  - `<K extends Kind & Activatable> enhancesSame(KindRef<K>, KindRef<K>)`, excluding mission_type and step_enhancement, which need their own marker group;
  - `<K extends Kind & Overridable> overrides(KindRef<K>, KindRef<K>)`;
  - `rejects(...)`, `reconcilesTension(...)`, `implementedBy(KindRef<Guard>, KindRef<Asset>)`.
- Overloads with the same erasure do not compile, so give each rectangle a distinct name.
- The factories build through the existing constructor, which WP05 makes package-private.
- `EndpointRule`: `bind(RelationType, Urn source, Urn target)` returns a sealed result: `Bound(Relationship)`, or `Fault(String code, RelationType, NodeKind source, NodeKind target)`.
  - Check order: `overrides_source_not_activatable` (slice C's rule; reuse or move `OverrideSourceRule`'s predicate, which lives in `packs.domain` today, so keep the code string here and let WP05 fold C's class) first, then `relationship_endpoint_not_allowed`.
  - `bindTension(Urn, Urn)` checks G ↔ G.

### T015: The agreement test and the kernel tests

- `EndpointTableAgreementTest` reflects over each public static factory of `Relationship`. For each parameter's bound, it expands the permitted leaf markers with `Class.getPermittedSubclasses()` recursively, and maps them to `NodeKind`.
- It asserts that, per relation, the union of the products of its factories equals `RelationType.endpoints()` exactly.
- Positive control: a test-only skewed table (one row with an extra pair) must fail the same comparison.
- `EndpointRuleTest`: every row allows its pairs and refuses one neighbour, the fault order holds, and tension works.
- A `KindRef` test shows that every `NodeKind` has exactly one leaf factory.

## Validation

- `./gradlew :kernel:test --max-workers=1` is green.
- `HexagonalArchitectureTest` (`:deployable:test`) is green, with the kernel still pure.

## Definition of Done

- T011 to T015 are done.
- The table is written once.
- The agreement test passes, and its positive control fails as intended.
- Every commit is signed, carries no `Co-Authored-By` trailer and names no model.
- The tests this work package touches are green. Gradle runs in the foreground with `--max-workers=1`, one run at a time.
- Out-of-map edits are recorded with a one-line rationale in the commit message.

## Risks and reviewer guidance

- **Generic bounds and sealed hierarchies with many markers.** Keep the groups flat (a marker implements several groups) rather than deep.
- **A same-kind factory accepting two different leaves.** Prove the failure with a compile-negative note in the test Javadoc. Javac tests are not needed; the reflection test is the guard.

## Branch Strategy

Planning happened on `slice-f-537-kind-taxonomy`, which is also the final merge target. `finalize-tasks` computes the
execution lanes into `lanes.json`, and each lane gets its own worktree. Do not choose a base branch by hand.

```bash
spec-kitty agent action implement WP03 --agent claude --mission slice-f-kind-taxonomy-01M7B19K
```

## Coordination

- **Slice C** is already on `main` (PR #566), and this branch is based on it.
- **#554 and #556 (epic #549).** Until #554 lands, the read check stays in `PackPolicy`, and whichever lands second rehomes it. Check the open PRs before each WP.
- **#551.** It changes the `kernel.validation` API; the second of the two to land rebases.
- **#106.** It renames `glossary_pack` to `glossary`; the second to land rebases.
- **#552.** It drops `MissionStepContractIdentifier` from its list.
- **Slice D close-out (#367, #371).** It runs in parallel, so rebase or merge `main` often. Never force-push a pushed branch.
