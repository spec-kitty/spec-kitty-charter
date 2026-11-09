### Added

- `getEffectiveGraph` (`GET /activations/{activationId}/graph`) and `getEffectiveActionContext`
  (`GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/context`) are declared as operations
  that answer (IC-11): `ReservedOperationCoverageTest` counts no reserved operation, and `activations.feature` loses
  its two `501` pins; `effective-graph-and-context.feature` pins the reads (US5 AS1 to AS5). Each answers an `ETag` of
  its own representation over the activation's revision and the packs of its closure, and `304` on a matching
  `If-None-Match`.
  - The graph answers the nodes in force (the artifacts in force, and each held step whose mission type is in force),
    the edges among them resolved through the layer overrides (an edge from an overridden version is absent, an edge to
    one ends at the winner), and `layerOverrides`, one record per losing version of a node. A template, an asset, a
    guard and a lifecycle are not nodes, and their overrides are not listed.
  - The action context answers ADR-0018's walk from the action's step over the artifacts in force, sorted by URN, each
    entry as `listEffectiveArtifacts` lists it. An artifact the walk reaches only through `suggests` from a listed
    artifact, that the activation does not select, is listed with `state: inactive`, `reason: not_selected` and the
    edge's `when` and `reason` in `suggestion`; an artifact also reached through `requires` or another hard relation is
    not. A mission type the activation does not put in force answers `artifacts: []` and `enhancements: []`, and the
    mission type is in `skipped.missionTypeNotInForce`. An unknown mission type or action is `404`.
  - `action_not_found` is raised: `404` on `getEffectiveActionContext`, judged against the merged closure.
- `LayerOverride` (`{artifact, winner, loser}`) and `LayerOverrideSide` (`{packId, level}`), `ActionEnhancement`
  (`{target: NodeReference, enhancedBy}`): the enhancers in force of the action's step and of each artifact the context
  lists. A step enhancer enhances the step, which is no artifact, so `enhancements` is the one member that carries it.
- Examples: `EffectiveGraph.one-edge` gains `layerOverrides: []`, `EffectiveGraph.layer-override` is new, and
  `EffectiveActionContext.implement` carries a suggestion and an enhancement.
- `getEffectiveGraph` and `getEffectiveActionContext` declare `400` as the shared `Problem` response, as the other
  activation reads do (`2026-11-06-activation-reads-and-writes-declare-400.md`). The service answers it for a
  malformed query: a `depth` outside 0 to 1 on the context, pinned in `effective-graph-and-context.feature`. The context
  and the graph also say that an unknown mission type or action is `404`, and a closure that cannot merge is `500`,
  before any `304`.
- The context's `skipped.missionTypeNotInForce` is the one exception to the `SkippedReport` carrier rule: it holds the
  requested mission type when that is not in force and is empty otherwise, so an artifact bound to another mission type
  that is not in force is absent from it. `createActivation` and `replaceActivation` describe the `500
  pack_unreadable` of a closure that cannot merge, as the deactivate does (DIV-230). The headers
  `Charter-Source-Fingerprint` and `ETag` no longer call the graph and the context reserved.
