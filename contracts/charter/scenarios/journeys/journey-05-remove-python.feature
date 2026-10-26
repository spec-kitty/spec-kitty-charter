@contract @journey-05
# Journey: docs/context/user-journeys/05-remove-python.md
Feature: Journey 05 — We Hate Snakes LLC removes the Python artifacts
  A team that writes no Python removes the Python-specific artifacts from the offering through its
  derived pack, one at a time. A locked artifact is refused. Reading a pack's toolguides works today; removing an artifact
  from the offering is not in the contract yet.

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

  Scenario: The team finds a language-specific toolguide by reading the pack
    When I PUT "/packs/qa/toolguides/pytest" with the body:
      """
      {"schemaVersion": "1.0", "tool": "pytest", "title": "How to use pytest", "guidePath": "pytest.md", "summary": "Using pytest under governance."}
      """
    Then the response status is 201
    When I GET "/packs/qa/toolguides/pytest"
    Then the response status is 200
    And the field "tool" of the response body is "pytest"

  @WIP
  Scenario: Removing one Python toolguide leaves the rest of the pack in the offering
    Given the pack "qa" offers the toolguides "pytest" and "git"
    When the team's derived pack removes the toolguide "pytest" from the offering
    Then the toolguide "pytest" is not in the offering
    And the toolguide "git" is in the offering

  @WIP
  Scenario: Removing a locked Python artifact is refused
    Given the org pack "qa" locks the styleguide "python-style"
    When the team's derived pack removes the styleguide "python-style" from the offering
    Then the request is refused naming the pack "qa"
    And the styleguide "python-style" stays in the offering
