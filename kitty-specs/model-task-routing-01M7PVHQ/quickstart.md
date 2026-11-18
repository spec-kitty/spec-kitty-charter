# Quickstart: Model-task routing

On a service started over charter's example packs (`./gradlew :deployable:bootRun` with the test config root, as the
contract scenarios do):

1. Read the built-in catalog: `GET /packs/built-in/artifacts/routing_catalog/default`.
2. Create an activation over `project` and read the catalog in force:
   `GET /activations/{id}/artifacts/routing_catalog/default` answers org-base's `default`, which replaces built-in's.
3. Ask for a model: `GET /activations/{id}/task-types/implementation/model-recommendation?agentProfile=<profile>`.
   The answer lists the catalog's top-ranked models (ties included) and the profile's model.
4. Add a step: `…&missionType=<type>&step=<step>`. The answer reports the step's tier as `modelTier`, with
   `source: step`, and flags each catalog candidate with `withinModelTier`.
5. Override it: `…&modelTier=low`. `modelTier.source` becomes `request`; the step's offer is still reported.
6. Select the second org catalog as well, and the read answers `409 routing_catalog_conflict`.

The `@contract` scenarios in `contracts/charter/scenarios/model-recommendation.feature` are the executable form of
these steps.
