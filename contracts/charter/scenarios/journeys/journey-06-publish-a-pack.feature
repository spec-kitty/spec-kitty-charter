@contract @journey-06
# Journey: docs/context/user-journeys/06-publish-a-pack.md
Feature: Journey 06 — a pack author publishes a pack
  A pack author registers a paradigm with its principles, the directives that carry its rules, a
  procedure, and a companion asset that supports a rule. Registering paradigms, directives and
  relationships works today; companion assets and publishing a pack version are not in the contract yet.

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

  Scenario: A paradigm is registered with the directives that carry its rules
    When I PUT "/packs/qa/paradigms/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward", "directiveRefs": ["ALPHA"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/paradigms/clean-architecture"
    Then the response status is 200
    And the response body has 1 relationship
    And a relationship of the response body points to "directive:ALPHA"

  Scenario: A rule's directive names the procedure that carries it out
    When I PUT "/packs/qa/directives/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required", "procedures": ["proc-1"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/directives/GUARD"
    Then the response status is 200
    And the response body has 1 relationship
    And a relationship of the response body points to "procedure:proc-1"

  Scenario: A relationship between two artifacts is registered once
    When I POST "/packs/qa/directives" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I POST "/packs/qa/directives" as JSON with the body:
      """
      {"id": "OTHER", "schemaVersion": "1.0", "title": "Other", "intent": "other", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/directives/GUARD"
    Then the response status is 200
    And I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:GUARD", "to": "directive:OTHER", "type": "requires"}
      """
    Then the response status is 201
    And I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:OTHER", "to": "directive:GUARD", "type": "requires"}
      """
    Then the response status is 200

  @WIP
  Scenario: A companion asset that supports a rule is registered with the pack
    Given the pack "qa" holds the directive "GUARD"
    When the author registers the asset "gate-checklist.md" supporting the directive "GUARD"
    Then a read of the directive "GUARD" names the asset "gate-checklist.md"

  @WIP
  Scenario: A published pack version can be registered by others
    Given the pack "qa" at version "1.0.0" is complete and valid
    When the author publishes it
    Then another project can register the pack "qa" at version "1.0.0"
