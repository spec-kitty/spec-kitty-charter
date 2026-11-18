# The routing catalog reads of a pack (ADR-0080 decision 6, #108): GET /packs/{packId}/artifacts/routing_catalog lists
# a pack's catalogs in id order, and GET …/routing_catalog/{artifactId} reads one catalog in the Artifact envelope. The
# 401, 403 and 500 answers of the two operations are asserted in every-operation.feature and unexpected-failure.feature,
# and the 400 of a malformed id also in read-refusals.feature. The catalog under an activation is
# effective-routing-catalogs.feature's.

@contract @artifacts
Feature: A pack's routing catalogs are listed and read, as their files declare them
  A client lists a pack's routing catalogs and reads one: its task types, its models with their fits and costs, its
  routing policy and its sources, in camel case. Every number is the exact decimal the file writes, in plain notation;
  an optional member the file does not declare is absent, never null. Every read answers the pack revision as its ETag.
  The pack is judged first: an unknown pack is 404 pack_not_found, then a catalog the pack does not hold is 404
  artifact_not_found. A catalog layers by its id, as every activatable kind does: a pack's policy that locks a catalog
  refuses a later pack's same-id copy, and no edge may name a catalog.

  Scenario: built-in's catalog is listed with its objective and counts, and read whole, every number exact
    Given the example pack "built-in" of the charter
    When I GET "/packs/built-in/artifacts/routing_catalog"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "id": "default",
            "objective": "quality_first",
            "taskTypeCount": 1,
            "modelCount": 2,
            "_links": {"self": {"href": "/api/v1/packs/built-in/artifacts/routing_catalog/default"}}
          }
        ],
        "_links": {"self": {"href": "/api/v1/packs/built-in/artifacts/routing_catalog"}}
      }
      """
    And following the link "self" of every element of "items" answers 200
    When I GET "/packs/built-in/artifacts/routing_catalog/default"
    Then the response status is 200
    And the response body is:
      """
      {
        "kind": "routing_catalog",
        "id": "default",
        "packId": "built-in",
        "_links": {
          "self": {"href": "/api/v1/packs/built-in/artifacts/routing_catalog/default"},
          "collection": {"href": "/api/v1/packs/built-in/artifacts/routing_catalog"}
        },
        "routingCatalog": {
          "id": "default",
          "generatedAt": "2026-11-18T00:00:00Z",
          "taskTypes": [{"id": "design", "title": "Design"}],
          "models": [
            {
              "id": "model-a",
              "provider": "example",
              "tools": [],
              "strengths": [],
              "weaknesses": [],
              "taskFit": [{"taskType": "design", "score": 0.3}],
              "cost": {"tier": "premium"}
            },
            {
              "id": "model-b",
              "provider": "example",
              "tools": [],
              "strengths": [],
              "weaknesses": [],
              "taskFit": [{"taskType": "design", "score": 0.1}],
              "cost": {"tier": "low"}
            }
          ],
          "routingPolicy": {
            "objective": "quality_first",
            "weights": {"quality": 1, "cost": 0.2, "risk": 0, "latency": 0},
            "tierConstraints": []
          },
          "sources": [
            {
              "name": "example",
              "url": "https://example.invalid/catalog",
              "accessMethod": "manual",
              "snapshotAt": "2026-11-18T00:00:00Z"
            }
          ]
        }
      }
      """

  Scenario: org-base lists its two catalogs in id order, and each optional member is answered when declared
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    When I GET "/packs/org-base/artifacts/routing_catalog"
    Then the response status is 200
    And the field at "/items" holds exactly 2 items
    And the field at "/items/0/id" is "cost-led"
    And the field at "/items/0/description" is "A cost-led catalog for the example packs."
    And the field at "/items/0/objective" is "cost_first"
    And the field at "/items/1/id" is "default"
    And the field at "/items/1/objective" is "balanced"
    And the field at "/items/1/taskTypeCount" is 3
    And the field at "/items/1/modelCount" is 5
    And the field at "/items/1/description" is absent
    When I GET "/packs/org-base/artifacts/routing_catalog/cost-led"
    Then the response status is 200
    And the field at "/routingCatalog/description" is "A cost-led catalog for the example packs."
    And the field at "/routingCatalog/taskTypes/0/qualitySensitivity" is "high"
    And the field at "/routingCatalog/taskTypes/0/costSensitivity" is "medium"
    And the field at "/routingCatalog/models/0/family" is "example-large"
    And the field at "/routingCatalog/models/0/taskFit/0/confidence" is "high"
    And the field at "/routingCatalog/models/0/taskFit/0/rationale" is "Strong at trade-offs."
    And the field at "/routingCatalog/models/0/cost/inputPer1mUsd" is 15
    And the field at "/routingCatalog/models/0/cost/currency" is "USD"
    And the field at "/routingCatalog/models/0/latencyTier" is "high"
    And the field at "/routingCatalog/models/1/family" is absent
    And the field at "/routingCatalog/models/1/latencyTier" is absent
    And the field at "/routingCatalog/models/1/taskFit/0/confidence" is absent
    And the field at "/routingCatalog/sources/0/licenseNotes" is "Example content, no licence terms."
    When I GET "/packs/org-base/artifacts/routing_catalog/default"
    Then the response status is 200
    And the field at "/routingCatalog/models/0/id" is "model-b"
    And the field at "/routingCatalog/models/1/id" is "model-a"
    And the field at "/routingCatalog/routingPolicy/tierConstraints/0/taskType" is "review"
    And the field at "/routingCatalog/routingPolicy/tierConstraints/0/maxTier" is "medium"

  Scenario: The list and the item answer one pack revision as their ETag
    Given the example pack "built-in" of the charter
    When I GET "/packs/built-in/artifacts/routing_catalog"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/packs/built-in/artifacts/routing_catalog/default"
    Then the response header "ETag" is the remembered header

  Scenario Outline: <operation> of a pack no configured pack answers to is 404 pack_not_found
    Given the example pack "built-in" of the charter
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "pack_not_found"

    Examples:
      | operation               | path                                          |
      | listPackRoutingCatalogs | /packs/nope/artifacts/routing_catalog         |
      | getPackRoutingCatalog   | /packs/nope/artifacts/routing_catalog/default |

  Scenario: getPackRoutingCatalog of a catalog the pack does not hold is 404 artifact_not_found
    Given the example pack "built-in" of the charter
    When I GET "/packs/built-in/artifacts/routing_catalog/cost-led"
    Then the response status is 404
    And the problem code is "artifact_not_found"

  Scenario Outline: getPackRoutingCatalog of an id off the kebab-case grammar is 400
    Given the example pack "built-in" of the charter
    When I GET "<path>"
    Then the response status is 400

    Examples:
      | path                                                 |
      | /packs/built-in/artifacts/routing_catalog/Default    |
      | /packs/built-in/artifacts/routing_catalog/de--fault  |

  Scenario: A same-id catalog over a catalog its level locks is 500 pack_unreadable, and over an open one it is served
    FR-005, ADR-0063 D3: core (built-in) locks its routing catalog default and leaves spare open. The org pack mandate
    replaces default, which a locked artifact does not allow, so a read of an activation over core and mandate refuses
    500 pack_unreadable, naming the catalog and the level. The org pack replaces-spare replaces the open spare: the
    control, an activation over core and replaces-spare, answers 200 and lists spare with that pack's provenance.
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "core" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        routing_catalog:default: locked
      """
    And the built-in pack "core" with the file "routing_catalogs/default.routing-catalog.yaml":
      """
      id: default
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-a, provider: example, task_fit: [{task_type: design, score: 0.5}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    And the built-in pack "core" with the file "routing_catalogs/spare.routing-catalog.yaml":
      """
      id: spare
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-a, provider: example, task_fit: [{task_type: design, score: 0.5}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    And the org pack "mandate" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: mandate-pack
      pack_level: org
      """
    And the org pack "mandate" with the file "routing_catalogs/default.routing-catalog.yaml":
      """
      id: default
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-b, provider: example, task_fit: [{task_type: design, score: 0.9}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    And the org pack "replaces-spare" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: replaces-spare-pack
      pack_level: org
      """
    And the org pack "replaces-spare" with the file "routing_catalogs/spare.routing-catalog.yaml":
      """
      id: spare
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-b, provider: example, task_fit: [{task_type: design, score: 0.9}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"core"},{"packId":"mandate"}],"schemaVersion":2,"selected":{"routing_catalog":["default"]}}
      """
    And the stored file of the activation "a-2" holds:
      """
      {"packs":[{"packId":"core"},{"packId":"replaces-spare"}],"schemaVersion":2,"selected":{"routing_catalog":["spare"]}}
      """
    When I GET "/activations/a-1/artifacts/routing_catalog/default"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "routing_catalog:default"
    And the field at "/detail" contains "locked"
    When I GET "/activations/a-2/artifacts/routing_catalog/spare"
    Then the response status is 200
    And the field at "/packId" is "replaces-spare"
    And the field at "/level" is "org"
    And the field at "/state" is "active"
    And the field at "/routingCatalog/models/0/id" is "model-b"

  Scenario: An overrides relationship between two routing catalogs is refused, and nothing is written
    Squad A1: a routing catalog layers by its id, never by an edge: no catalog is an edge endpoint, so the relationship
    write refuses it validation_failed, naming the catalog, and the pack keeps its revision.
    Given the org pack "cbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PB
      pack_version: 1.0.0
      parent_pack: null
      name: cbase
      pack_level: org
      """
    And the org pack "cbase" with the file "routing_catalogs/default.routing-catalog.yaml":
      """
      id: default
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-a, provider: example, task_fit: [{task_type: design, score: 0.5}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    And the org pack "covr" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PV
      pack_version: 1.0.0
      parent_pack: null
      name: covr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000PB
        range: '>=1.0'
      """
    And the org pack "covr" with the file "routing_catalogs/mine.routing-catalog.yaml":
      """
      id: mine
      generated_at: '2026-11-18T00:00:00Z'
      task_types: [{id: design, title: Design}]
      models: [{id: model-b, provider: example, task_fit: [{task_type: design, score: 0.9}], cost: {tier: low}}]
      routing_policy: {objective: balanced, weights: {quality: 1, cost: 0, risk: 0, latency: 0}}
      sources: [{name: example, url: 'https://example.invalid/catalog', access_method: manual, snapshot_at: '2026-11-18'}]
      """
    When I GET "/packs/covr/artifacts/routing_catalog/mine"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/covr/relationships" with the remembered ETag and the body:
      """
      {"from": "routing_catalog:mine", "to": "cbase::routing_catalog:default", "type": "overrides"}
      """
    Then the response status is 422
    And the field at "/code" is "validation_failed"
    And the field at "/detail" contains "a routing catalog is no graph node and no edge endpoint, it layers by its id"
    And the field at "/detail" contains "routing_catalog:mine"
    When I GET "/packs/covr/artifacts/routing_catalog/mine"
    Then the response header "ETag" is the remembered header
