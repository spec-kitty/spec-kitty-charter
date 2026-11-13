# The references twin (#532; FR-012, research R1 and R10): listEffectiveArtifactReferences answers the pack read of the
# references (listArtifactReferences) of the pack that holds the winner, item for item, with an effective-artifact link
# on each target the activation's closure holds. The 400, 401, 403 and 500 answers are asserted in read-refusals.feature,
# every-operation.feature and unexpected-failure.feature.
#
# Fixture: core (built-in) holds directive D, tactics rehearse and drill, agent profile jenny and asset run; its graph document
# writes D requires rehearse, and jenny requires D and suggests rehearse. Activation a-1 builds on core alone and selects D. The
# override scenario adds proj (project), whose own D requires its own drill, and activation a-2 over both.

@contract @activations
Feature: The references of an artifact under an activation are the pack read of the winner's pack
  Under an activation, the references of an artifact are the references the version in force writes: the pack read of
  the pack that holds the winner, for the same kind and id, so the two reads cannot disagree. Each target the
  activation's closure holds also links its read under the activation. The activation is judged first, then the kind,
  then whether a pack of the closure holds the artifact; the artifact is answered whatever its state.

  Background:
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at built-in
      intent: D at built-in
      enforcement: required
      """
    And the built-in pack "core" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
      """
    And the built-in pack "core" with the file "tactics/drill.tactic.yaml":
      """
      schema_version: '1.0'
      id: drill
      name: Drill
      steps:
        - title: Only step
      """
    And the built-in pack "core" with the file "agent_profiles/jenny.agent.yaml":
      """
      profile-id: jenny
      name: Java Jenny
      role: implementer
      purpose: Implement.
      specialization:
        primary-focus: Java
      """
    And the built-in pack "core" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      """
    And the built-in pack "core" with the file "assets/run.py":
      """
      print("built-in")
      """
    And the built-in pack "core" with the file "core.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D
        target: tactic:rehearse
        relation: requires
      - source: agent_profile:jenny
        target: directive:D
        relation: requires
      - source: agent_profile:jenny
        target: tactic:rehearse
        relation: suggests
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}], "selected": {"directive": ["D"]}}
      """

  Scenario: A requires edge is answered with the target linked under the activation and in its pack
    When I GET "/activations/a-1/artifacts/directive/D/references"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "items": [
          {
            "reference": "tactic:rehearse",
            "relation": "requires",
            "holdingPack": {"packId": "core", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "name": "core-pack"},
            "urn": "tactic:rehearse",
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/tactic/rehearse"},
              "artifact": {"href": "/api/v1/packs/core/artifacts/tactic/rehearse"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/directive/D/references"},
          "up": {"href": "/api/v1/activations/a-1"}
        }
      }
      """
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts/directive/D/references"
    Then the response status is 200
    And following the link "artifact" of every element of "items" answers 200

  Scenario: An artifact the activation does not select is answered all the same
    The tactic drill is neither selected nor required by what is, so it is inactive under the activation; its references
    are answered whatever its state, as its effective read is. drill writes none.
    When I GET "/activations/a-1/artifacts/tactic/drill"
    Then the response status is 200
    And the field at "/state" is "inactive"
    When I GET "/activations/a-1/artifacts/tactic/drill/references"
    Then the response status is 200
    And the field at "/items" holds exactly 0 items
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/tactic/drill/references"

  Scenario: An overridden artifact answers the references its winning copy writes
    proj (project) holds its own D, whose graph document writes D requires drill: the layer merge keeps the project copy, so
    the references are proj's pack read of D, and core's D requires rehearse is not answered.
    Given the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at project
      intent: D at project
      enforcement: required
      """
    And the project pack "proj" with the file "tactics/drill.tactic.yaml":
      """
      schema_version: '1.0'
      id: drill
      name: Drill at project
      steps:
        - title: Only step
      """
    And the project pack "proj" with the file "proj.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D
        target: tactic:drill
        relation: requires
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"directive": ["D"]}}
      """
    When I GET "/activations/a-2/artifacts/directive/D"
    Then the response status is 200
    And the field at "/packId" is "proj"
    When I GET "/activations/a-2/artifacts/directive/D/references"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "reference": "tactic:drill",
            "relation": "requires",
            "holdingPack": {"packId": "proj", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943S", "name": "proj-pack"},
            "urn": "tactic:drill",
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-2/artifacts/tactic/drill"},
              "artifact": {"href": "/api/v1/packs/proj/artifacts/tactic/drill"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-2/artifacts/directive/D/references"},
          "up": {"href": "/api/v1/activations/a-2"}
        }
      }
      """
    When I GET "/packs/proj/artifacts/directive/D/references"
    Then the response status is 200
    And I remember the items of the response body without their links
    When I GET "/activations/a-2/artifacts/directive/D/references"
    Then the response status is 200
    And the items of the response body, without their links, are the remembered ones

  Scenario Outline: Parity: the same referrer read in its pack and under the activation answers the same items (<kind> <id>)
    The twin is the pack read of the winner's pack: every member of every item but the links is the pack read's, in
    its order, an agent profile's references included, which its pack's graph documents write for it. Only the links
    differ: each tree links into itself.
    When I GET "/packs/core/artifacts/<kind>/<id>/references"
    Then the response status is 200
    And I remember the items of the response body without their links
    When I GET "/activations/a-1/artifacts/<kind>/<id>/references"
    Then the response status is 200
    And the items of the response body, without their links, are the remembered ones
    And the field at "/items" holds exactly <count> items
    And following the link "effective-artifact" of every element of "items" answers 200

    Examples:
      | kind          | id    | count |
      | directive     | D     | 1     |
      | agent_profile | jenny | 2     |

  Scenario: The references of an asset are read at the template, which no literal asset path shadows
    No literal path of the activation tree ends in /references, so the references of an asset are this read, never the
    asset's item or its payload. run writes none.
    When I GET "/activations/a-1/artifacts/asset/run/references"
    Then the response status is 200
    And the field at "/items" holds exactly 0 items
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/asset/run/references"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"

  Scenario: An unknown activation is activation_not_found, before the kind and the artifact are judged
    When I GET "/activations/nope/artifacts/template/GHOST/references"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario Outline: A kind the service does not list is kind_not_found (<kind>)
    When I GET "/activations/a-1/artifacts/<kind>/D/references"
    Then the response status is 404
    And the problem code is "kind_not_found"
    And the field at "/detail" is "The service serves no artifact kind <kind>."

    Examples:
      | kind     |
      | template |
      | nonsense |

  Scenario Outline: An artifact no pack of the closure holds is artifact_not_found, whatever its kind (<kind>)
    When I GET "/activations/a-1/artifacts/<kind>/GHOST/references"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "No pack of the activation's closure holds the <kind> GHOST."

    Examples:
      | kind          |
      | directive     |
      | agent_profile |
      | asset         |
      | mission_type  |
