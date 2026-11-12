@contract @journey-04
# Journey: docs/context/user-journeys/04-org-house-rules.md
Feature: Journey 04 — a governance lead sets the organisation's house rules
  A governance lead publishes an org pack whose governing activation preset carries must-have items and
  whose policy.yaml locks a directive, so every project of the organisation keeps them. Registering a
  required directive in an org pack works today; the effect of a governing preset's must-have items and
  of policy.yaml locks on a project's activations is not covered by a scenario here yet.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      pack_drg: drg/fragment.yaml
      """

  Scenario: A required directive registered in the org pack is read back as required
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    And the field "id" of the response body is "GUARD"
    And the field at "/directive/title" is "Guard the gate"

  @WIP
  Scenario: A project of the organisation gets the required preset
    Given the org pack "qa" ships the governing preset "house-rules" with the directive "GUARD" as a must-have item
    When a project of the organisation applies the preset "house-rules"
    Then the directive "GUARD" is a required item of the project's activations

  @WIP
  Scenario: A locked directive cannot be deactivated by a project
    Given the org pack "qa" locks the directive "GUARD" in its policy.yaml
    When a project deactivates the directive "GUARD"
    Then the request is refused naming the pack "qa"
    And the directive "GUARD" stays in the effective set
