@contract @journey-02
# Journey: docs/context/user-journeys/02-adopt-a-pack.md
Feature: Journey 02 — an individual developer adopts a pack and deactivates one directive
  A developer adds a pack from open-packs, reads what it offers, and switches one optional directive
  off. A required directive cannot be switched off. Reading the pack works today; activation and
  deactivation are not in the contract yet.

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

  Scenario: The adopted pack's directives are listed
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts?kind=directive"
    Then the response status is 200
    And the field at "/items/0/id" is "GUARD"

  Scenario: An unknown pack is refused, not read as empty
    When I GET "/packs/no-such-pack/artifacts"
    Then the response status is 404
    And the problem code is "pack_not_found"

  @WIP
  Scenario: Deactivating an optional directive removes it from the effective set
    Given the pack "qa" is active with the optional directive "STYLE"
    When the developer deactivates the directive "STYLE"
    Then the directive "STYLE" is not in the effective set
    And the active charter records the exclusion

  @WIP
  Scenario: Deactivating a required directive is refused
    Given the pack "qa" is active with the required directive "GUARD"
    When the developer deactivates the directive "GUARD"
    Then the request is refused naming the pack "qa"
    And the directive "GUARD" stays in the effective set
