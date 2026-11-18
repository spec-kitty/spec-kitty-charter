---
work_package_id: WP02
title: The kernel kind and cost tier
dependencies: []
requirement_refs:
- FR-001
- FR-005
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
subtasks:
- T004
- T005
- T006
- T007
phase: Phase 1 - Foundations
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/CostTier.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/kinds/RoutingCatalog.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/CostTierTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/ArtifactKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/NodeKind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/CostTier.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/kinds/Kind.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/kinds/RoutingCatalog.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/KindTypes.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/KindRef.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/EndpointTable.java
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/EndpointTableAdrTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/domain/CascadeRules.java
- docs/adr/0077-kind-taxonomy-and-step-authority.md
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 – The kernel kind and cost tier

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP02 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP02 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

Make `routing_catalog` a kernel kind and `CostTier` the one tier vocabulary. A catalog must not become an edge
endpoint through any relation (ADR-0080 decision 5; plan IC-01; squad findings A1, A4, A7).

## Subtasks

### T004 – `ROUTING_CATALOG` in `ArtifactKind` and `NodeKind`

1. In `ArtifactKind.java`, append `ROUTING_CATALOG("routing_catalog", "routing_catalogs", true)` as the **last**
   constant.
   - The declaration order is the contract's `ActivatableKind` order, pinned by a parity test.
   - `offering.feature` (around lines 425–432) pins taxonomy indexes, so appending keeps every earlier index.
   - WP03 appends the token last in every contract enum to match.
2. In `NodeKind.java`, append `ROUTING_CATALOG("routing_catalog", ArtifactKind.ROUTING_CATALOG, "routing_catalogs",
   false, false)`: not declarable in graphs and not an edge target. `byArtifactKind` (around line 120) fails at class
   init if an artifact kind has no node kind, so both constants land together.
3. Create `kinds/RoutingCatalog.java`, a type marker that is never instantiated, as `kinds/Skill.java` is. It
   implements `Kind` only: it is not an `Advisor`, not `GraphAuthored`, not `Enhanceable` and not `Overridable`.
   Add it to `kinds/Kind.java`'s `permits`.
4. Add `Map.entry(RoutingCatalog.class, NodeKind.ROUTING_CATALOG)` to `KindTypes.java` (near line 38), and a
   `KindRef.routingCatalog(String id)` factory (near line 181), as for `skill`.

### T005 – `CostTier`

- Create `CostTier.java`: an enum `LOW("low")`, `MEDIUM("medium")`, `HIGH("high")`, `PREMIUM("premium")`, declared in
  ascending order. It has:
  - `token()`;
  - `static Optional<CostTier> ofToken(String)`;
  - `boolean atMost(CostTier other)`.
- It is the only tier vocabulary: the catalog's `cost.tier` and `max_tier`, the step's `model_tier`, the request's
  `modelTier` and the ranker all use it.
- Javadoc cites ADR-0080 decisions 5 and 12.
- Write `CostTierTest`: the order, `atMost` at each boundary, and that an unknown token gives empty.

### T006 – No relation admits a catalog

- **The leak.** In `EndpointTable.java` (lines 66–70), `ENHANCES` and `OVERRIDES` build their diagonals from
  `activatableExcept(...)`, which reads `ArtifactKind.activatable()`. `routing_catalog` is the first kind that is
  activatable and not declarable, so it would silently admit `routing_catalog -enhances/overrides-> routing_catalog`.
- **The fix.** Exclude `NodeKind.ROUTING_CATALOG` from both diagonals.
  - Prefer one rule over a listed exception: restrict both diagonals to activatable kinds whose `NodeKind` is an edge
    target.
  - Before you choose it, check that every kind they admit today is an edge target. If one is not, add the explicit
    exclusion instead, and say which in the Javadoc.
- **Why no edge is needed.** A layer override needs no edge: across levels, the same id is the override
  (`docs/architecture/composition-and-activation-rules.md`, line 251).
- **The ADR rows.** Amend ADR-0077 decision 5's endpoint table rows in
  `docs/adr/0077-kind-taxonomy-and-step-authority.md`, so `EndpointTableAdrTest`
  (`service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/EndpointTableAdrTest.java`) keeps the record
  and the code equal. The amendment is dated 2026-11-18, cites ADR-0080 decision 5, and says "X → X, where X is
  activatable and an edge target".
- **The tests.** Add a kernel test asserting that no relation's endpoint set contains `ROUTING_CATALOG`, on either
  side. `EndpointTableAgreementTest` and `EndpointRuleTest` must still pass.

### T007 – Cascade rules and the enumerating tests

- **Cascade rules.** Read `CascadeRules.java` (activations, around lines 419 and 465) and `CascadeKind`. Check whether
  anything assumes that an activatable kind is reachable by an edge (a cascade target, or a `required` that follows
  edges).
  - A catalog has no edges, so `required` reduces to the preset `RequiredFloor`.
  - If the code already handles a kind with no edges (it should: it is driven by `activatable()` and the graph),
    record that in the history and change nothing.
  - If it does not, fix it here and add a unit test.
- **Kernel tests.** Update the tests that enumerate kinds, under `service/kernel/src/test/java/.../domain/`:
  `ArtifactKindTest`, `NodeKindTest`, `NodeKindPluralTest`, `EndpointTableAgreementTest`, `EndpointRuleTest` and
  `KindRefTest`.
  - Assert the new rows (token, plural, flags).
  - Do not loosen an existing assertion.

## Definition of Done

- `ROUTING_CATALOG` is the last constant of both enums, activatable, not declarable and not an edge target.
- `CostTier` exists, with its order and its tests.
- No relation admits a catalog, and ADR-0077 decision 5 says so.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check that a catalog appears in no endpoint set, and that the ADR row and the code agree through
  `EndpointTableAdrTest`.
- Check that the enum order puts the new kind last.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
