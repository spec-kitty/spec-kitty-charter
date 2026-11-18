# The routing catalog reads under an activation (ADR-0080 decision 6, #108): GET
# /activations/{activationId}/artifacts/routing_catalog lists the catalog winners of the activation's closure, and GET
# …/routing_catalog/{artifactId} reads one with its state and the catalog as the winning pack declares it. The 401, 403
# and 500 answers are asserted in every-operation.feature and unexpected-failure.feature, and the 400 of a malformed id
# in read-refusals.feature. The fixtures are charter's example packs (ADR-0070).

@contract @activations
Feature: An activation's routing catalogs are listed and read with their state, the winner of the layer merge
  A routing catalog layers by its id, as every activatable kind does: org-base's default replaces built-in's. Under an
  activation the list answers each winner, whatever its state, linked to its read under the activation and to the
  winning copy's pack read; the item read answers the winner's pack, level, state, reason, policy and required, and the
  catalog. A catalog is in force when the document selects it or a governing preset requires it; it is excluded when
  the document denies it, and inactive when the document does not select it.

  Background:
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the example pack "org-mandate" of the charter
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"routing_catalog": ["default"]}}
      """

  Scenario: org-base's default wins over built-in's, in force with its pack, level, policy and required
    When I GET "/activations/a-1/artifacts/routing_catalog"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "id": "cost-led",
            "description": "A cost-led catalog for the example packs.",
            "objective": "cost_first",
            "taskTypeCount": 1,
            "modelCount": 2,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/routing_catalog/cost-led"},
              "artifact": {"href": "/api/v1/packs/org-base/artifacts/routing_catalog/cost-led"}
            }
          },
          {
            "id": "default",
            "objective": "balanced",
            "taskTypeCount": 3,
            "modelCount": 5,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/routing_catalog/default"},
              "artifact": {"href": "/api/v1/packs/org-base/artifacts/routing_catalog/default"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/routing_catalog"},
          "up": {"href": "/api/v1/activations/a-1"}
        }
      }
      """
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts/routing_catalog"
    Then following the link "artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts/routing_catalog/default"
    Then the response status is 200
    And the field at "/kind" is "routing_catalog"
    And the field at "/artifactId" is "default"
    And the field at "/packId" is "org-base"
    And the field at "/level" is "org"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/policy" is "open"
    And the field at "/required" is false
    And the field at "/routingCatalog/routingPolicy/objective" is "balanced"
    And the field at "/routingCatalog/models/0/id" is "model-b"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/routing_catalog/default"
    And the field at "/_links/artifact/href" is "/api/v1/packs/org-base/artifacts/routing_catalog/default"
    And the field at "/_links/collection/href" is "/api/v1/activations/a-1/artifacts/routing_catalog"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"

  Scenario: cost-led is listed but not in force until the document selects it
    When I GET "/activations/a-1/artifacts/routing_catalog/cost-led"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    Given the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"routing_catalog": ["cost-led"]}}
      """
    When I GET "/activations/a-2/artifacts/routing_catalog/cost-led"
    Then the response status is 200
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"

  Scenario: ?kind=routing_catalog lists the catalogs in both trees
    When I GET "/packs/org-base/artifacts?kind=routing_catalog"
    Then the response status is 200
    And the field at "/items" holds exactly 2 items
    And the field at "/items/0/kind" is "routing_catalog"
    When I GET "/activations/a-1/artifacts?kind=routing_catalog"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      routing_catalog:cost-led
      routing_catalog:default
      """

  Scenario: A catalog the document denies is out of force, and one a governing preset requires is in force
    Given the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "deactivated": {"routing_catalog": ["default"]}}
      """
    When I GET "/activations/a-3/artifacts/routing_catalog/default"
    Then the response status is 200
    And the field at "/state" is "excluded"
    And the field at "/reason" is "deactivated"
    Given the activation "a-4" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-mandate"}]}
      """
    When I GET "/activations/a-4/artifacts/routing_catalog/mandated"
    Then the response status is 200
    And the field at "/packId" is "org-mandate"
    And the field at "/state" is "active"
    And the field at "/reason" is "required"
    And the field at "/required" is true

  Scenario Outline: <operation> answers 304 to a matching If-None-Match, and its tag is not the tag of <against>
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 304
    When I GET "<other>" with If-None-Match the remembered header "ETag"
    Then the response status is 200

    Examples:
      | operation                    | against                          | path                                               | other                                               |
      | listEffectiveRoutingCatalogs | listEffectiveSkills              | /activations/a-1/artifacts/routing_catalog         | /activations/a-1/artifacts/skill                    |
      | getEffectiveRoutingCatalog   | another catalog                  | /activations/a-1/artifacts/routing_catalog/default | /activations/a-1/artifacts/routing_catalog/cost-led |

  Scenario Outline: <operation> under an activation that is not stored is 404 activation_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "activation_not_found"

    Examples:
      | operation                    | path                                                |
      | listEffectiveRoutingCatalogs | /activations/nope/artifacts/routing_catalog         |
      | getEffectiveRoutingCatalog   | /activations/nope/artifacts/routing_catalog/default |

  Scenario: getEffectiveRoutingCatalog of a catalog no pack of the closure holds is 404 artifact_not_found
    When I GET "/activations/a-1/artifacts/routing_catalog/mandated"
    Then the response status is 404
    And the problem code is "artifact_not_found"
