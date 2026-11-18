# Contract sketch: the operations this Mission adds

The WPs turn this sketch into `contracts/charter/` files (paths, schemas, examples, scenarios, switch-gate entries).
The authority is ADR-0080; this file only lists the shapes in one place.

| operationId | Method and path | Answer | Refusals |
| --- | --- | --- | --- |
| `listPackRoutingCatalogs` | `GET /packs/{packId}/artifacts/routing_catalog` | `RoutingCatalogList` of `RoutingCatalogSummary` (id, description, objective, taskTypeCount, modelCount) | 404 `pack_not_found` |
| `getPackRoutingCatalog` | `GET /packs/{packId}/artifacts/routing_catalog/{artifactId}` | `RoutingCatalog` | 404 `pack_not_found`, 404 `artifact_not_found` |
| `listEffectiveRoutingCatalogs` | `GET /activations/{activationId}/artifacts/routing_catalog` | `RoutingCatalogList` of effective summaries (state, reason, policy, required, pack, level) | 404 `activation_not_found` |
| `getEffectiveRoutingCatalog` | `GET /activations/{activationId}/artifacts/routing_catalog/{artifactId}` | `EffectiveRoutingCatalogRead` | 404 `activation_not_found`, 404 `artifact_not_found` |
| `getModelRecommendation` | `GET /activations/{activationId}/task-types/{taskType}/model-recommendation?agentProfile=&missionType=&step=&modelTier=` | `ModelRecommendation`; `304` | as data-model.md "Refusal order" |

Statuses and headers, as the skill reads and the selection reads have them (squad finding C5):

- **Pack reads.** `200` with `PackRevisionETag`, `Charter-Source-Fingerprint` and `Charter-Worktree`; `400`, `401`,
  `403`, `404` and `500`. No `304`.
- **Effective twins.** They take `If-None-Match` and answer `200` with `EffectiveETag`, `Charter-Source-Fingerprint`
  and `Charter-Worktree`; `304`, `400`, `401`, `403`, `404` and `500`.
- **`getModelRecommendation`.** As the effective twins, plus `409` with the two new codes.

Every declared status is asserted by a `@contract` scenario, and the switch-gate register holds each operation as
`ready`. The tags are `RoutingCatalogs`, `EffectiveRoutingCatalogs` and `ModelRecommendations`. The link relations,
kebab-case and in the lint's closed enum, are `routing-catalogs` (on the activation), `catalog`, `agent-profile` and
`step`.

New refusal codes in `CharterRefusalCode`: `routing_catalog_not_in_force` (409), `routing_catalog_conflict` (409,
naming the ids in force), `task_type_not_found` (404).

`MissionStep` gains `modelTier` (optional cost tier). `?kind=routing_catalog` joins both artifact lists.
