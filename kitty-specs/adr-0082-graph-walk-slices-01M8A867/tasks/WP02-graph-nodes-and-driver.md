---
work_package_id: WP02
title: 'Slice 3a: graph nodes, scope and the driver (#638)'
dependencies:
- WP01
requirement_refs:
- FR-004
- NFR-002
- NFR-003
- C-006
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0082-graph-walk-slices-01M8A867
base_commit: fa3bd25891f4c1e886d2d5c34e90afc4a6d88eb0
created_at: '2026-11-26T16:15:20.532679+00:00'
subtasks:
- T007
- T008
- T009
- T010
- T011
phase: Phase 2 - Slice 3
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/PackGraphNode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/PackNode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/ArtifactNode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/EdgeNode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/EdgeKind.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/Scope.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackGraphWalkTest.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/GraphWalker.java
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Descent.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/GraphWalk.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/GraphWalkTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/graph/**
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackGraphWalkTest.java
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 - Slice 3a: graph nodes, scope and the driver (#638)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

Build the type design the operator ruled for slice 3 (D1, 2026-11-25): the kernel's visitor contract becomes generic,
and `PackGraph` drives one visitor over typed nodes within a `Scope`. No rule moves in this WP (WP03).

Requirements: FR-004, NFR-002, NFR-003, C-001, C-006. Design: `research.md` R-1 to R-3, `data-model.md`.

## Subtasks

### T007 Generic GraphWalker<N, E>

- `GraphWalker<N, E>` with `onNode(N)` and `onRelationship(E)`; `Descent` unchanged (Javadoc only).
- Users gain `<DrgNode, Relationship>`: `GraphWalk` (`walk`, `visit`), `Reachability` (`Cascade`, `Ancestry`),
  `GraphWalkTest` (`walkFrom`, `Recorder`). Nothing else implements or calls it (grep to confirm).
- Behaviour unchanged: `GraphWalkTest` and the reachability tests stay green with no other edit.

### T008 Node types (package `packs.domain.graph`)

- `sealed interface PackGraphNode permits PackNode, ArtifactNode, EdgeNode` with `pack()` and `origin()` (from
  `PackOrigins`).
- `PackNode(ConfiguredPack, PackDescriptor)`; never `PackUnderResolution` (no class cycle with the resolver).
- `ArtifactNode(PackIdentifier pack, Urn urn, boolean held)` from the pack's composition facts.
- `EdgeNode(EdgeKind kind, PackIdentifier pack, Written written)`; `Written` is sealed: `DependsOn(PackDependency)`,
  `ParentPack(String)`, `Related(Relationship)`, `Referenced(PackQualifiedReference)`, `Delegated(...)`.
- `EdgeKind`: `DEPENDS_ON`, `PARENT_PACK`, `OVERRIDES`, `ENHANCES`, `SPECIALIZES`, `REFERENCES`, `STEP_LINK`,
  `DELEGATES`. `SPECIALIZES_FROM` maps to `SPECIALIZES`; a mission type's `requires` to an action that is its own step
  is `STEP_LINK` (see `MissionStepLinkRule`).
- The edge is kept as declared, never resolved to a holder: one graph serves many closures (R-2).

### T009 Scope and walk modes

- `record Scope(List<PackIdentifier> roots, Predicate<? super PackGraphNode> selection)`, `Scope.of(roots)` selects
  everything. It never names edges.
- The domain's walk modes, an enum in `PackGraph`: `PACKS` (pack nodes only) and `RELATIONS` (pack nodes plus their
  `OVERRIDES`, `ENHANCES`, `SPECIALIZES` and `STEP_LINK` edges). A caller never picks a mode: the factory's checks
  carry it (WP03).

### T010 PackGraph.of and the driver

- `PackGraph.of(List<PackNode> packsInMergeOrder, Function<PackIdentifier, PackCompositionFacts> factsOf)`: facts are
  read lazily and memoized; packs indexed by `pack_id` to a list (duplicates stay visible).
- `walk(Scope, Walk, GraphWalker<? super PackGraphNode, ? super EdgeNode>)`: the members are the composition closure
  of the roots (`compositionClosure`); visit in the graph's merge order: the pack node, then its edges of the mode's
  kinds in declared order. The selection filters what the walker is handed and never prunes membership. `SKIP` on a
  pack node skips its edges; `STOP` ends the walk. Iterative (NFR-002).
- `PackGraph` is owned by WP01: the instance part is an out-of-map edit, recorded in the commit message.

### T011 PackGraphWalkTest, red first

Members once each, in input order; a cycle terminates; an unselected node is not handed over but its dependencies are
still visited; `SKIP`; `STOP`; edges in declared order and only the mode's kinds; the `EdgeKind` classification
(specializes, a step link against a plain `requires`); `PACKS` never calls `factsOf`; a 200,000-pack chain.

## Definition of Done

- The node types, scope and driver exist with their tests; the kernel change compiles everywhere with behaviour
  unchanged; `:kernel:test :packs:test` and checkstyle green.
- No rule uses the driver yet; WP03 makes the first ones.

## Risks

- A god walker: `walk` keeps three parameters; nothing caller-specific goes in.
- Facts read eagerly would cost every registration: assert `PACKS` never reads them.

Implementation command: `spec-kitty agent action implement WP02 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
