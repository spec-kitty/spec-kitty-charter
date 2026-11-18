### Breaking

- The routing catalog kind (ADR-0080 decision 5 and its amendment, #108). `NodeKind` gains `routing_catalog`, so
  `NodeReference`, `EffectiveGraphNode` and `PackGovernanceEntry` may carry it: an activation holds a catalog, layers
  it and reports its same-id override, though no graph declares one and no edge names one. A guard and a lifecycle
  stay unlisted.
- Core's seam: regenerate the DTOs and handle `routing_catalog` wherever a `NodeKind` is switched on.
