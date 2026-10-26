@contract @journey-03
# Journey: docs/context/user-journeys/03-plain-language-request.md
Feature: Journey 03 — a non-engineer makes a plain-language request
  Someone who does not write code asks for a change in plain words. A recipe and an agent profile are
  chosen for them from the active charter, so they never pick governance artifacts by hand. Reading the
  pack's tactics works today; recipes and profile selection are not in the contract yet.

  Background:
    Given the org pack "qa" with the file "rehearse.tactic.yaml":
      """
      id: rehearse
      schema_version: '1.0'
      name: Rehearse
      steps:
        - title: Only step
      """

  Scenario: The tactics a recipe can draw on are readable
    When I GET "/packs/qa/tactics/rehearse"
    Then the response status is 200
    And the field "id" of the response body is "rehearse"

  @WIP
  Scenario: A plain-language request is matched to a mission type
    Given an active charter with the mission type "small-change"
    When a request "please fix the typo on the landing page" is routed
    Then the mission type "small-change" is chosen
    And the person is not asked to name a directive or tactic

  @WIP
  Scenario: A profile is chosen for the request's role
    Given an active charter whose mission type "small-change" asks for the role "implementer"
    When the request is routed
    Then a profile that performs the role "implementer" is chosen
