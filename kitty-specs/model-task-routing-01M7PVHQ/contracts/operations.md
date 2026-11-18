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

Every operation also declares the worktree guard's 401 and 403 and the 500 `OfferingUnreadable` answer as its
siblings do, and the switch-gate register holds each as `ready`.

New refusal codes in `CharterRefusalCode`: `routing_catalog_not_in_force` (409), `routing_catalog_conflict` (409,
naming the ids in force), `task_type_not_found` (404).

`MissionStep` gains `modelTier` (optional cost tier). `?kind=routing_catalog` joins both artifact lists.
