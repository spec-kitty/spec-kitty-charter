### Breaking

- The routing catalog kind (ADR-0080 decision 5 and its amendment, #108). `NodeKind` gains `routing_catalog`, so
  `NodeReference`, `EffectiveGraphNode` and `PackGovernanceEntry` may carry it: an activation holds a catalog, layers
  it and reports its same-id override, though no graph declares one and no edge names one. A guard and a lifecycle
  stay unlisted.
- The kind joins every closed kind enum: `ActivatableKind`, `ArtifactKindToken`, `TaxonomyKind` and `CascadeKind` gain
  `routing_catalog`, so `?kind=routing_catalog` filters `listPackArtifacts` and `listEffectiveArtifacts`, and
  `Artifact` gains the member `routingCatalog` (`RoutingCatalog`), which only the catalog's own literal reads answer.
- An activation selects and denies catalogs: `SelectedArtifacts` and `DeactivatedArtifacts` gain `routing_catalog`, and
  `Preset` gains `activatedRoutingCatalogs` (`activated_routing_catalogs` in the file), which a preset's `must_have`
  can require.
- Four catalog reads (ADR-0080 decision 6): `listPackRoutingCatalogs` and `getPackRoutingCatalog` (tag
  `RoutingCatalogs`), and `listEffectiveRoutingCatalogs` and `getEffectiveRoutingCatalog` (tag
  `EffectiveRoutingCatalogs`), with the schemas `RoutingCatalog` and its parts, `RoutingCatalogSummary`,
  `RoutingCatalogList` and `EffectiveRoutingCatalogRead`. A score, a weight and a price are exact decimals on the wire,
  in plain notation with trailing zeros stripped.
- The model recommendation (ADR-0080 decisions 7 to 13): `getModelRecommendation`,
  `GET /activations/{activationId}/task-types/{taskType}/model-recommendation?agentProfile=&missionType=&step=&modelTier=`
  (tag `ModelRecommendations`), answering `ModelRecommendation` with its `ModelRecommendationCatalog`,
  `ModelRecommendationTier` and `ModelRecommendationCandidate`s, an `EffectiveETag` and `304`. It is advisory and
  deterministic.
- Three refusal codes in `CharterRefusalCode`: `routing_catalog_not_in_force` (409), `routing_catalog_conflict` (409,
  naming the ids in force) and `task_type_not_found` (404), all raised by `getModelRecommendation`.
- `MissionStep` gains `modelTier` (`CostTier`, new: `low`, `medium`, `high`, `premium`), the tier of model a step
  advises.
- The link relations `routing-catalogs` (on the activation), `catalog`, `agent-profile` and `step` join the relation
  table and `link-relations-are-known`.
- Core's seam: regenerate the DTOs; handle `routing_catalog` wherever a kind enum or a `NodeKind` is switched on; map
  the three refusal codes; and replace `charter.model_routing.load` and `evaluate` with the activation's
  `getModelRecommendation` (the catalog is read under the activation, never loaded from a file by core).
