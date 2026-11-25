---
okf_version: "0.2"
title: PackGraph owns every cycle, group and descending walk over the packs
description: The walks over depends_on and parent_pack, the cycle and strongly connected group finding and the enhances grounding move into one domain class; answers and refusals are unchanged.
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

# 2026-11-25 — PackGraph owns every cycle, group and descending walk over the packs

**One domain class walks the pack graph** (ADR-0082 slice 2, #637). The service walked the packs' `depends_on` and
`parent_pack` edges in several places, most of them in the application layer, and found cycles in application code.
`PackGraph` in `packs.domain` now holds each of these walks:

- the edges a pack builds on (`buildsOn`), and one breadth-first descent along any edges (`reachable`);
- the composition closure (`compositionClosure`, moved from `PackCompositionResolver`);
- the packs that build on a pack (`buildingOn`, the reverse index `ClosureRoots` used) and its direct dependents
  (`directDependents`, which a pack removal names);
- the cycles of a graph, one per strongly connected group (`cyclesIn`, with its Tarjan pass, moved from
  `ImpactRelations`), and the enhancers no `enhances` chain grounds (`ungrounded`).

`ImpactRelations`, `SpecializationCycleRule`, `ClosureRoots`, `RemovalDependents`, `PackMutabilityPolicy`,
`OfferingReadService`, `InboundReferences` and `ClosureRoleFacts` read their nodes and edges and hand them to
`PackGraph`; none keeps a walk of its own. The walks and the collections they order now use insertion-ordered
collections, never hash order.

## What did not change

No refusal code, detail, order of findings or wire shape changes. The existing composition, removal, mutability, offering
and relation tests pass unchanged; the cycle tests moved to `PackGraphTest` with their cases as they were. The
`specializes_from` cycle check of #632 can now reuse `PackGraph.cyclesIn` from the domain.
