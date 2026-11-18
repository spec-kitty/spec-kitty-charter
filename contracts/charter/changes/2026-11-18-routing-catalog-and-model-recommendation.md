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
- Core's seam (DIV-701 to DIV-707):
  - regenerate the DTOs; handle `routing_catalog` wherever a kind enum or a `NodeKind` is switched on; map the three
    refusal codes;
  - replace `charter.model_routing.load` and `evaluate` with the activation's `getModelRecommendation`: the catalog is
    read under the activation, never loaded from a file by core, and the profile is named by id (`agentProfile`), not
    passed as an object;
  - choose among the candidates: the answer is zero or more catalog candidates, every tied model among them and none
    filtered by a tier, each flagged `withinTierConstraint` and `withinModelTier`, then the profile's model, where core
    took one pick; a client that honors the tiers takes the candidates flagged inside them;
  - read the step's tier as `model_tier` (`modelTier`), a cost tier, in place of the free-string
    `recommended_model_tier`, and pass a runtime override as `modelTier`; the resolution's `source` is `step` or
    `request`, in place of `offer` or `override`;
  - treat `404 task_type_not_found` where core answered no candidate, and honor the `ETag` and `304`;
  - migrate the seed catalog to the pack file: an `id`, no `schema_version`, `source_snapshot`, `override_policy` or
    `freshness_policy`, and the content rules the registration judges (declared task types, no duplicates, an ISO-8601
    `generated_at`).
