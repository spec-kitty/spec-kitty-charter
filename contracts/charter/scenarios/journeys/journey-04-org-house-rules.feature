@contract @journey-04
# Journey: docs/context/user-journeys/04-org-house-rules.md
Feature: Journey 04 — a governance lead sets the organisation's house rules
  A governance lead publishes an org pack that requires a preset and locks a directive, so every
  project of the organisation keeps them. Registering a required directive in an org pack works today;
  required presets and locks are not in the contract yet.

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

  Scenario: A required directive registered in the org pack is read back as required
    When I PUT "/packs/qa/directives/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/directives/GUARD"
    Then the response status is 200
    And the field "id" of the response body is "GUARD"
    And the field "title" of the response body is "Guard the gate"

  @WIP
  Scenario: A project of the organisation gets the required preset
    Given the org pack "qa" requires the preset "house-rules"
    When a project of the organisation activates its charter
    Then the active charter holds the preset "house-rules"

  @WIP
  Scenario: A locked directive cannot be deactivated by a project
    Given the org pack "qa" locks the directive "GUARD"
    When a project deactivates the directive "GUARD"
    Then the request is refused naming the pack "qa"
    And the directive "GUARD" stays in the effective set
