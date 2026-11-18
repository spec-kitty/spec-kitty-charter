# The model recommendation under an activation (ADR-0080 decisions 7 to 13, #108): GET
# /activations/{activationId}/task-types/{taskType}/model-recommendation answers the models the routing catalog in force
# recommends for the task type, flagged against the tiers in play, then the asked profile's model. The fixtures are
# charter's example packs (ADR-0070) and the rows are research R7's: each asserts the exact ordered candidate list with
# every flag, never containment. The 401 and 403 are asserted in every-operation.feature and the 500 of an unexpected
# failure in unexpected-failure.feature.
#
# built-in's default is quality_first (design: model-a fit 0.3 premium, model-b fit 0.1 low). org-base's default
# replaces it: balanced, weights 1 and 0.2, design as built-in's, review capped at medium (model-c 0.9 premium, model-d
# 0.6 medium, model-e 0.5 low), triage with no fit. org-base's cost-led is cost_first, weights 0.5 and 0.5. java-jenny
# declares model-x with effort high; generic-agent declares no model. software-dev's review step offers low.

@contract @activations
Feature: A task type's model is recommended under an activation, beside a profile's model
  The service ranks the catalog in force and answers; the client chooses. Exactly one routing catalog must be in force.
  The candidates are the top-ranked models inside every tier in play and the top-ranked models overall, each with every
  tie, flagged and never hidden; the profile's model comes last. The refusals come in a fixed order, each before the
  ETag is compared.

  Background:
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the example pack "project" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"routing_catalog": ["default"]}}
      """
    And the activation "p" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"routing_catalog": ["default"]}}
      """

  Scenario: Row 1 - quality_first answers the unique top by fit, then the profile's model
    When I GET "/activations/b/task-types/design/model-recommendation?agentProfile=java-jenny"
    Then the response status is 200
    And the response body is:
      """
      {
        "taskType": "design",
        "catalog": {"id": "default", "packId": "built-in", "level": "built-in"},
        "objective": "quality_first",
        "candidates": [
          {"source": "catalog", "modelId": "model-a", "score": 0.3, "fitScore": 0.3, "costTier": "premium",
           "withinTierConstraint": true},
          {"source": "profile", "modelId": "model-x", "effort": "high"}
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/b/task-types/design/model-recommendation?agentProfile=java-jenny"},
          "catalog": {"href": "/api/v1/activations/b/artifacts/routing_catalog/default"},
          "agent-profile": {"href": "/api/v1/activations/b/artifacts/agent_profile/java-jenny"}
        }
      }
      """
    And the response header "Charter-Source-Fingerprint" is a sha256 digest
    And the response header "ETag" appears once
    When I follow the link "catalog"
    Then the response status is 200
    And the field at "/artifactId" is "default"
    When I GET "/activations/b/task-types/design/model-recommendation?agentProfile=java-jenny"
    And I follow the link "agent-profile"
    Then the response status is 200
    And the field at "/artifactId" is "java-jenny"

  Scenario: Row 2 - balanced answers the exact-decimal tie, by model id and not by file order
    Under binary floating point model-b would score 0.30000000000000004 and win alone; org-base lists it first.
    When I GET "/activations/p/task-types/design/model-recommendation?agentProfile=java-jenny"
    Then the response status is 200
    And the response body is:
      """
      {
        "taskType": "design",
        "catalog": {"id": "default", "packId": "org-base", "level": "org"},
        "objective": "balanced",
        "candidates": [
          {"source": "catalog", "modelId": "model-a", "score": 0.3, "fitScore": 0.3, "costTier": "premium",
           "withinTierConstraint": true},
          {"source": "catalog", "modelId": "model-b", "score": 0.3, "fitScore": 0.1, "costTier": "low",
           "withinTierConstraint": true},
          {"source": "profile", "modelId": "model-x", "effort": "high"}
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/p/task-types/design/model-recommendation?agentProfile=java-jenny"},
          "catalog": {"href": "/api/v1/activations/p/artifacts/routing_catalog/default"},
          "agent-profile": {"href": "/api/v1/activations/p/artifacts/agent_profile/java-jenny"}
        }
      }
      """

  Scenario: Row 3 - cost_first answers the cheapest by score, and a profile with no model adds no candidate
    Given the activation "c" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"routing_catalog": ["cost-led"]}}
      """
    When I GET "/activations/c/task-types/design/model-recommendation?agentProfile=generic-agent"
    Then the response status is 200
    And the response body is:
      """
      {
        "taskType": "design",
        "catalog": {"id": "cost-led", "packId": "org-base", "level": "org"},
        "objective": "cost_first",
        "candidates": [
          {"source": "catalog", "modelId": "model-b", "score": 0.55, "fitScore": 0.1, "costTier": "low",
           "withinTierConstraint": true}
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/c/task-types/design/model-recommendation?agentProfile=generic-agent"},
          "catalog": {"href": "/api/v1/activations/c/artifacts/routing_catalog/cost-led"},
          "agent-profile": {"href": "/api/v1/activations/c/artifacts/agent_profile/generic-agent"}
        }
      }
      """

  Scenario: Row 4 - the tier constraint flags the best model overall and never filters it
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=generic-agent"
    Then the response status is 200
    And the response body, apart from "_links", is:
      """
      {
        "taskType": "review",
        "catalog": {"id": "default", "packId": "org-base", "level": "org"},
        "objective": "balanced",
        "tierConstraint": "medium",
        "candidates": [
          {"source": "catalog", "modelId": "model-c", "score": 0.9, "fitScore": 0.9, "costTier": "premium",
           "withinTierConstraint": false},
          {"source": "catalog", "modelId": "model-d", "score": 0.732, "fitScore": 0.6, "costTier": "medium",
           "withinTierConstraint": true}
        ]
      }
      """

  Scenario: Row 5 - the step's tier is effective, and the best model inside both tiers is answered
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=generic-agent&missionType=software-dev&step=review"
    Then the response status is 200
    And the response body is:
      """
      {
        "taskType": "review",
        "catalog": {"id": "default", "packId": "org-base", "level": "org"},
        "objective": "balanced",
        "tierConstraint": "medium",
        "modelTier": {"effective": "low", "source": "step", "offer": "low"},
        "candidates": [
          {"source": "catalog", "modelId": "model-c", "score": 0.9, "fitScore": 0.9, "costTier": "premium",
           "withinTierConstraint": false, "withinModelTier": false},
          {"source": "catalog", "modelId": "model-e", "score": 0.7, "fitScore": 0.5, "costTier": "low",
           "withinTierConstraint": true, "withinModelTier": true}
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/p/task-types/review/model-recommendation?agentProfile=generic-agent&missionType=software-dev&step=review"},
          "catalog": {"href": "/api/v1/activations/p/artifacts/routing_catalog/default"},
          "agent-profile": {"href": "/api/v1/activations/p/artifacts/agent_profile/generic-agent"},
          "step": {"href": "/api/v1/activations/p/artifacts/mission_type/software-dev/steps/review"}
        }
      }
      """
    When I follow the link "step"
    Then the response status is 200

  Scenario: Row 6 - the request's tier overrides the step's, and the medium cap still holds against premium
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=review&modelTier=premium"
    Then the response status is 200
    And the response body, apart from "_links", is:
      """
      {
        "taskType": "review",
        "catalog": {"id": "default", "packId": "org-base", "level": "org"},
        "objective": "balanced",
        "tierConstraint": "medium",
        "modelTier": {"effective": "premium", "source": "request", "offer": "low"},
        "candidates": [
          {"source": "catalog", "modelId": "model-c", "score": 0.9, "fitScore": 0.9, "costTier": "premium",
           "withinTierConstraint": false, "withinModelTier": true},
          {"source": "catalog", "modelId": "model-d", "score": 0.732, "fitScore": 0.6, "costTier": "medium",
           "withinTierConstraint": true, "withinModelTier": true},
          {"source": "profile", "modelId": "model-x", "effort": "high"}
        ]
      }
      """
    And the field at "/_links/self/href" is "/api/v1/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=review&modelTier=premium"

  Scenario: Row 7 - a declared task type no model fits answers 200 with no candidate
    When I GET "/activations/p/task-types/triage/model-recommendation?agentProfile=generic-agent"
    Then the response status is 200
    And the field at "/taskType" is "triage"
    And the field at "/candidates" holds exactly 0 items
    And the response body has no field at "/tierConstraint"
    And the response body has no field at "/modelTier"

  Scenario: Row 8 - two catalogs in force are 409 routing_catalog_conflict, naming both
    Given the activation "x" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"routing_catalog": ["default", "cost-led"]}}
      """
    When I GET "/activations/x/task-types/design/model-recommendation?agentProfile=java-jenny"
    Then the response status is 409
    And the problem code is "routing_catalog_conflict"
    And the field at "/detail" is "The activation x puts the routing catalogs cost-led, default in force; deselect all but one."

  Scenario: Row 9 - no catalog in force is 409 routing_catalog_not_in_force
    Given the activation "n" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}]}
      """
    When I GET "/activations/n/task-types/design/model-recommendation?agentProfile=java-jenny"
    Then the response status is 409
    And the problem code is "routing_catalog_not_in_force"
    And the field at "/detail" is "The activation n puts no routing catalog in force; select one."

  Scenario: Row 10 - a task type the catalog in force does not declare is 404 task_type_not_found
    When I GET "/activations/p/task-types/deploy/model-recommendation?agentProfile=java-jenny"
    Then the response status is 404
    And the problem code is "task_type_not_found"
    And the field at "/detail" is "The routing catalog default in force declares no task type deploy."

  Scenario: A held profile out of force still answers its model, as the effective profile read resolves it
    ADR-0080 decision 13: the profile is resolved whatever its own state.
    Given the activation "d" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"routing_catalog": ["default"]}, "deactivated": {"agent_profile": ["java-jenny"]}}
      """
    When I GET "/activations/d/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/state" is "excluded"
    When I GET "/activations/d/task-types/design/model-recommendation?agentProfile=java-jenny"
    Then the response status is 200
    And the field at "/candidates/2/source" is "profile"
    And the field at "/candidates/2/modelId" is "model-x"
    And the field at "/candidates/2/effort" is "high"

  Scenario Outline: <case> is a 400 Problem without a code
    When I GET "<path>"
    Then the response status is 400
    And the problem has no code

    Examples:
      | case                         | path                                                                                              |
      | a taskType outside kebab-case | /activations/p/task-types/Design/model-recommendation?agentProfile=java-jenny                    |
      | no agentProfile              | /activations/p/task-types/design/model-recommendation                                             |
      | an empty agentProfile        | /activations/p/task-types/design/model-recommendation?agentProfile=                               |
      | a modelTier not a cost tier  | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&modelTier=cheap     |
      | a missionType outside a slug | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=A!&step=review |
      | a step outside a slug        | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=A! |
      | missionType without step     | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=software-dev |
      | step without missionType     | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&step=review         |

  Scenario Outline: <case> is 404 <code>
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "<code>"

    Examples:
      | case                         | code                   | path                                                                                                    |
      | an activation not stored     | activation_not_found   | /activations/nope/task-types/design/model-recommendation?agentProfile=java-jenny                        |
      | a profile no pack holds      | artifact_not_found     | /activations/p/task-types/design/model-recommendation?agentProfile=nobody                               |
      | a mission type no pack holds | mission_type_not_found | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=nope&step=review |
      | a step the type does not hold | action_not_found      | /activations/p/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=nope |

  Scenario Outline: A request that breaks two rules is answered the earlier refusal: <case>
    The order of data-model "Refusal order": activation, profile, mission type, step, the 500, the 409s, task type.
    Given the activation "n" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}]}
      """
    When I GET "<path>"
    Then the response status is <status>
    And the problem code is "<code>"

    Examples:
      | case                                     | status | code                         | path                                                                                                  |
      | unknown activation and unknown profile   | 404    | activation_not_found         | /activations/nope/task-types/design/model-recommendation?agentProfile=nobody                           |
      | unknown profile and unknown step         | 404    | artifact_not_found           | /activations/p/task-types/design/model-recommendation?agentProfile=nobody&missionType=software-dev&step=nope |
      | unknown mission type and no catalog      | 404    | mission_type_not_found       | /activations/n/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=nope&step=review |
      | unknown step and no catalog              | 404    | action_not_found             | /activations/n/task-types/design/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=nope |
      | no catalog and unknown task type         | 409    | routing_catalog_not_in_force | /activations/n/task-types/deploy/model-recommendation?agentProfile=java-jenny                          |

  Scenario: The 500 of a closure that fails comes after the activation's 404 and before the 409s and the task type
    Given the activation "n" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}]}
      """
    And the service fails unexpectedly while it reads the packs
    When I GET "/activations/n/task-types/deploy/model-recommendation?agentProfile=java-jenny"
    Then the response status is 500
    And the problem code is "internal_error"
    When I GET "/activations/nope/task-types/deploy/model-recommendation?agentProfile=java-jenny"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A matching If-None-Match is 304, another tag is 200, and the tag changes with the asked tier
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny"
    Then the response status is 200
    When I remember the header "ETag"
    And I remember the response body
    And I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny" with the header "If-None-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 200
    And the response body is the remembered one
    And the response header "ETag" is the remembered header
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny&modelTier=premium" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

  Scenario: Every refusal comes before the 304
    When I GET "/activations/p/task-types/design/model-recommendation?agentProfile=java-jenny"
    And I remember the header "ETag"
    And I GET "/activations/p/task-types/deploy/model-recommendation?agentProfile=java-jenny" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "task_type_not_found"

  Scenario: Two identical requests answer the same body and the same tag
    When I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=review"
    And I remember the header "ETag"
    And I remember the response body
    And I GET "/activations/p/task-types/review/model-recommendation?agentProfile=java-jenny&missionType=software-dev&step=review"
    Then the response status is 200
    And the response body is the remembered one
    And the response header "ETag" is the remembered header
