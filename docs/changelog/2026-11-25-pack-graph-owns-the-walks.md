---
okf_version: "0.2"
title: PackGraph owns the packs module's walks over the pack graph
description: The walks over the pack graph, from the application layer and the composition resolver, move into one domain class; answers are unchanged.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - architecture/architectural-patterns-applied
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you write a walk over packs; call PackGraph instead.
---

# 2026-11-25 — PackGraph owns the packs module's walks over the pack graph

**One domain class walks the pack graph** (ADR-0082 slice 2, #637). The service walked the packs' `depends_on` and
`parent_pack` edges in several places, most of them in the application layer, and found cycles in application code.
`PackGraph` in `packs.domain` now holds them:

- the edges a pack builds on (`buildsOn`), and the descent along them (`ancestorsOf`; the caller hands it the edges,
  from `buildsOn` or from a composed closure's resolved parents);
- the composition closure (`compositionClosure`, moved from `PackCompositionResolver`);
- the packs that build on a pack (`dependentsClosure`, the reverse index `ClosureRoots` used) and its direct dependents
  (`directDependents`, which a pack removal names);
- the cycles of a graph, one per strongly connected group (`cyclesIn`, with its Tarjan pass, moved from
  `ImpactRelations`), and the enhancers no `enhances` chain grounds (`ungrounded`);
- the composition resolver's own walks (operator ruling, 2026-11-25): the `depends_on`-only closure
  (`dependsOnClosure`), the `parent_pack` line (`onParentLine`, `related`), the dependency order (`topologicalOrder`,
  Kahn's pass) and the cycles it leaves (`leftoverCycles`), each moved as written and pinned by a test.

The application classes that walked by hand (`ImpactRelations`, `ClosureRoots`, `RemovalDependents`,
`PackMutabilityPolicy`, `InboundReferences`, `ClosureRoleFacts` and others) now call `PackGraph`.
`PackGraphWalkBoundaryTest` fails the build when an application class holds a walk's work queue or reads a descriptor's
edges, except in two read-model projections (`ClosureQueries.parentsOf`,
`OfferingReadService.dependenciesOf`). Walks order by insertion, never by hash.

Still outside: `activations`' parent-line walk (another module) and the artifact walks (`GraphWalk`, `Reachability`,
`GovernanceWalk`, `ActionContextWalk`), which epic #636 schedules.

## What did not change

No refusal code, detail, order of findings or wire shape changes. The existing tests pass; the cycle tests moved to
`PackGraphTest` with their cases as they were. #632 still needs the `specializes_from` cycle over a whole set's composed
closure (epic #636).
