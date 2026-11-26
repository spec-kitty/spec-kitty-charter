---
work_package_id: WP01
title: 'Slice 2: PackGraph holds every pack-graph walk (#637)'
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-013
- NFR-001
- NFR-002
- C-001
- C-003
- C-005
- SC-001
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0082-graph-walk-slices-01M8A867
base_commit: 0d0f7c58a4e18658d6516af2690b2abac6eff6cb
created_at: '2026-11-26T05:31:12.930745+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - Slice 2
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackGraph.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackGraph.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackGraphTest.java
- docs/changelog/2026-11-25-pack-graph-owns-the-walks.md
- service/packs/src/test/java/ai/speckitty/charter/packs/PackGraphWalkBoundaryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/badfixture/HandWrittenDescent.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackGraph.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackGraphTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PresetItemRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoots.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RemovalDependents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackMutabilityPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/InboundReferences.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureRoleFacts.java
- service/packs/src/test/java/ai/speckitty/charter/packs/PackGraphWalkBoundaryTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/badfixture/HandWrittenDescent.java
- docs/changelog/2026-11-25-pack-graph-owns-the-walks.md
- docs/architecture/composition-and-activation-rules.md
- docs/adr/0083-one-answer-on-every-surface-preset-items-stale-manifests-and-pack-doctor.md
- docs/_meta/spelling-words.txt
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 - Slice 2: PackGraph holds every pack-graph walk (#637)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else, and apply its
initialization, boundaries and directives.

## Objective

Every walk over the pack graph lives in `ai.speckitty.charter.packs.domain.PackGraph`: the descents over `depends_on`
and `parent_pack`, the reverse walks, the cycle and strongly connected group searches, the `enhances` grounding, and
(operator ruling, 2026-11-25) the composition resolver's own five walks. An ArchUnit guard keeps the application layer
from writing its own. Answers do not change (NFR-001).

Requirements: FR-001, FR-002, FR-003, FR-013, NFR-001, NFR-002, C-001, C-003, C-005.

## Context

- Built so far on PR #665 (branch `refactor/637-pack-graph`): commit 03e8ab92c (the application walks) and the
  fold commit (the guard, the 13 review findings). Read `git log origin/main..refactor/637-pack-graph`.
- The resolver's walks are in `PackCompositionResolver`: `dependsOnClosure` (a LIFO descent over `depends_on` only),
  `related` / `extendsOnLine` (the `parent_pack` line), `topologicalOrder` (Kahn's pass with a `PriorityQueue` on
  `STABLE_ORDER`), `cyclesOf` (one walk per leftover along its first unplaced dependency) and `shortestCycleThrough`
  (breadth-first back to the pack).
- Squad research: `research.md` R-3 (order), `research/pre-spec-squad.md` S10, S12, S13, S16, S17.

## Subtasks

### T001 Move the application layer's walks into PackGraph (built)

Done in 03e8ab92c: `ImpactRelations` (cycles, Tarjan, grounding), `SpecializationCycleRule`, `ClosureRoots`,
`RemovalDependents`, `PackMutabilityPolicy` (`heldAlong`, `buildsOn`; never its test-only constructors),
`OfferingReadService`, `InboundReferences`, `ClosureRoleFacts`. `ImpactRelations`, `SpecializationCycleRule` and
`ClosureImpactCheck` belong to later WPs' `owned_files`; the edits made here are out-of-map, done before the mission
existed.

### T002 Walk-containment guard (built)

`PackGraphWalkBoundaryTest`: no application class but `OwnerOnly` depends on a `Deque`, and none but `ClosureQueries`
and `OfferingReadService` reads `PackDescriptor.dependencies()` or `parentPack()`. Red first: main's
`ClosureRoleFacts.ancestorsOf` fails it; a bad fixture covers the edge-read rule.

### T003 Fold the review of PR #665 (built)

The 13 findings: read order in `RemovalDependents`, docs overclaims, ADR-0083 pointer, test gaps, Javadoc, naming
(`dependentsClosure`, `ancestorsOf`), the descriptor lookup in `compositionClosure`, the single enhances graph.

### T004 Move the depends_on-only closure and the parent_pack line

1. Red first in `PackGraphTest`: `dependsOnClosure(packId, descriptorOf)` returns the LIFO order of today's walk
   (`a -> [b, c]` gives `a, c, b`, missing targets included, no descent from them); `onParentLine(child, ancestor,
   descriptorOf)` and `related(a, b, descriptorOf)` terminate on a parent cycle.
2. Move the bodies as written: same pending list, same `remove(size - 1)`, same `LinkedHashSet`.
3. The resolver calls them; delete its private copies.

Validation: `PackCompositionResolverTest` unchanged and green; the new tests pin the order.

### T005 Move Kahn's order and the leftover-cycle searches

1. Red first in `PackGraphTest`:
   - `topologicalOrder(packs, idOf, dependenciesById, comparator)` places packs by the comparator among the ready
     ones and returns the leftovers;
   - `leftoverCycles(placed, dependenciesById)` returns the first-walk cycle first, then the shortest cycle through
     each pack a walk passed by, in the order today's code finds them. Use the resolver's own fixtures: the
     dependency-cycle cases in `PackCompositionResolverTest`.
2. Generic over the pack type, so `PackGraph` keeps no resolver type (squad S16). The resolver keeps building its
   `Refusal`s (`Failure.CYCLE`, the detail) from the ids.
3. Replace `HashSet` and `HashMap` used for membership with `LinkedHashSet` and `LinkedHashMap` (FR-013).

Validation: every resolver cycle test unchanged; a 200,000-pack chain does not exhaust the stack (NFR-002).

### T006 Docs and PR body

- Changelog fragment: the resolver walks moved; the "what stays" paragraph shrinks to `activations`' parent-line walk
  (another module) and the artifact walks (epic #636 checklist).
- PR #665 body: the moved walks table, Not done (Scope and the node types with WP02; `GraphWalk`; `extendsChain`),
  the checks.

## Definition of Done

- `PackCompositionResolver` has no walk of its own; `grep -n "ArrayDeque\|PriorityQueue\|while (" ` over it finds
  only non-walk loops.
- `./gradlew :packs:test :packs:checkstyleMain :packs:checkstyleTest` green, then `verifyAll`, `check_docs.py all`,
  markdownlint and cspell.
- The adversarial review comment is on PR #665 with every finding folded.

## Risks

- Kahn's pass and the cycle walks reach refusal details: move them as written, pin the order before moving.
- The resolver is shared by registration, the reads and pack-doctor: run `:pack-validation:test` and
  `:pack-doctor:test` too.

## Reviewer guidance

Compare each moved body with the old one line by line; check the order of every collection that reaches an answer.

Implementation command: `spec-kitty agent action implement WP01 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
