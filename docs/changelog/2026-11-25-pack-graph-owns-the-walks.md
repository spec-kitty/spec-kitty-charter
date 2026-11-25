---
okf_version: "0.2"
title: PackGraph owns the walks over the pack graph that the application layer wrote by hand
description: The application layer's walks over the pack graph, its cycle finding and the enhances grounding move into one domain class; answers are unchanged.
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

# 2026-11-25 — PackGraph owns the walks over the pack graph that the application layer wrote by hand

**One domain class walks the pack graph** (ADR-0082 slice 2, #637). The service walked the packs' `depends_on` and
`parent_pack` edges in several places, most of them in the application layer, and found cycles in application code.
`PackGraph` in `packs.domain` now holds them:

- the edges a pack builds on (`buildsOn`), and the descent along them (`ancestorsOf`);
- the composition closure (`compositionClosure`, moved from `PackCompositionResolver`);
- the packs that build on a pack (`dependentsClosure`, the reverse index `ClosureRoots` used) and its direct dependents
  (`directDependents`, which a pack removal names);
- the cycles of a graph, one per strongly connected group (`cyclesIn`, with its Tarjan pass, moved from
  `ImpactRelations`), and the enhancers no `enhances` chain grounds (`ungrounded`).

`ImpactRelations`, `SpecializationCycleRule`, `ClosureRoots`, `RemovalDependents`, `PackMutabilityPolicy`,
`OfferingReadService`, `InboundReferences` and `ClosureRoleFacts` call `PackGraph`; none keeps a walk of its own,
and none reads a descriptor's `depends_on` or `parent_pack` to walk them. `PackGraphWalkBoundaryTest` pins both. The
walks and the collections they order now use insertion-ordered collections, never hash order.

`PackCompositionResolver`'s own walks (its dependency order, its dependency-cycle search and its `depends_on`-only
closure) stay in the domain beside `PackGraph`, and so does `activations`' parent-line walk in its closure snapshot;
later slices of epic #636 fold them in.

## What did not change

No refusal code, detail, order of findings or wire shape changes. The existing composition, removal, mutability, offering
and relation tests pass, with only the call target renamed where a test called the moved `compositionClosure`; the cycle
tests moved to `PackGraphTest` with their cases as they were. The `specializes_from` cycle check already runs on
`PackGraph.cyclesIn`; what #632 still needs is that check over the composed closure of a whole set (epic #636).
