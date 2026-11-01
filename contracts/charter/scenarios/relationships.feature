@contract @relationships
Feature: A relationship may be authored from a mission type
  A mission type is an artifact kind (ADR-0044): a pack's mission-type file is loaded as an artifact
  with the URN mission_type:<id>. A standalone relationship whose from is such a URN now resolves
  within the pack and is registered, where it was refused 409 registration_conflict while a mission
  type was a node kind only. Divergence register: DIV-503. The answer is a RelationshipRegistered: the registered
  relationship and the link to the pack's artifacts, with no self, since the relationships path has no GET (R-13).

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      active_charter: org-charter.yaml
      pack_drg: drg/fragment.yaml
      """
    And the org pack "qa" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Conduct systematic research with structured methodology and evidence synthesis
      steps:
        - id: scoping
          title: Research Scoping
          role: researcher
      """

  Scenario: A relationship from a mission type of the pack is registered (ADR-0044, NFR-001)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "mission_type:research", "to": "directive:GUARD", "type": "scope"}
      """
    Then the response status is 201
    And the response body is:
      """
      {
        "relationship": {"from": "mission_type:research", "to": "directive:GUARD", "type": "scope", "rationale": null, "when": null},
        "_links": {"artifacts": {"href": "/api/v1/packs/qa/artifacts"}}
      }
      """
    And the answer has no link "self"

  Scenario: A relationship from a mission type the pack does not hold is still a conflict
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "mission_type:software-dev", "to": "directive:GUARD", "type": "scope"}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"

  Scenario: A relationship registration body over 1 MiB is refused before it is read (#323)
    When I PUT "/packs/qa/relationships" with the body, its "rationale" padded to 1048576 bytes:
      """
      {"from": "mission_type:research", "to": "directive:GUARD", "type": "scope", "rationale": ""}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The relationship registration body is larger than 1048576 bytes."
    When I PUT "/packs/qa/relationships" with the body, its "rationale" padded to 1048000 bytes:
      """
      {"from": "mission_type:research", "to": "directive:GUARD", "type": "scope", "rationale": ""}
      """
    Then the response status is 428
