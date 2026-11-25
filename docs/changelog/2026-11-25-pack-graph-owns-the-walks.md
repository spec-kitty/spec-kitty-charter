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

- the edges a pack builds on (`buildsOn`), and the descent along them (`ancestorsOf`);
- the composition closure (`compositionClosure`, moved from `PackCompositionResolver`);
- the packs that build on a pack (`dependentsClosure`, the reverse index `ClosureRoots` used) and its direct dependents
  (`directDependents`, which a pack removal names);
- the cycles of a graph, one per strongly connected group (`cyclesIn`, with its Tarjan pass, moved from
  `ImpactRelations`), and the enhancers no `enhances` chain grounds (`ungrounded`);
- the composition resolver's own walks (operator ruling, 2026-11-25): the `depends_on`-only closure
  (`dependsOnClosure`), the `parent_pack` line (`onParentLine`, `related`), the dependency order (`topologicalOrder`,
  Kahn's pass) and the cycles it leaves (`leftoverCycles`), each moved as written and pinned by a test.

`ImpactRelations`, `SpecializationCycleRule`, `ClosureRoots`, `RemovalDependents`, `PackMutabilityPolicy`,
`OfferingReadService`, `InboundReferences` and `ClosureRoleFacts` call `PackGraph`; none keeps a walk of its own,
and none reads a descriptor's `depends_on` or `parent_pack` itself, but two read-model projections.
`PackGraphWalkBoundaryTest` pins that no application class holds a work queue or reads those edges, by a call or a
method reference. The
walks and the collections they order now use insertion-ordered collections, never hash order.

Two walks stay outside it: `activations`' parent-line walk in its closure snapshot (another module), and the
application layer's walks over artifact relationships (`GraphWalk`, `Reachability`, `GovernanceWalk`,
`ActionContextWalk`), which epic #636 schedules.

## What did not change

No refusal code, detail, order of findings or wire shape changes. The existing composition, removal, mutability, offering
and relation tests pass; where a test called the moved `compositionClosure`, only the call and the type of its lookup
helper changed. The cycle
tests moved to `PackGraphTest` with their cases as they were. The `specializes_from` cycle check already runs on
`PackGraph.cyclesIn`; what #632 still needs is that check over the composed closure of a whole set (epic #636).
