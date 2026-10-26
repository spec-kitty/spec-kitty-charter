@contract @journey-11
# Journey: docs/context/user-journeys/11-human-reviews.md
Feature: Journey 11 — Suspicious Services Inc. has every review done by a human
  The organisation pins the reviewer role to a locked profile that a human performs, with the operator
  role. Specialised profiles still fit where they do not take the reviewer role away from the human.
  Roles, profiles and pins are not in the contract yet.

  @WIP
  Scenario: Every review step resolves to the human reviewer profile
    Given the org pack pins the role "reviewer" to the locked profile "human-reviewer" performed by a human
    When a Mission step that asks for the role "reviewer" is resolved
    Then the profile "human-reviewer" is chosen
    And the answer says the step is performed by a human with the operator role

  @WIP
  Scenario: A project cannot unpin the human reviewer
    Given the org pack pins the role "reviewer" to the locked profile "human-reviewer"
    When a project pins the role "reviewer" to the profile "agent-reviewer"
    Then the request is refused naming the org pack

  @WIP
  Scenario: A specialisation of the human reviewer profile still fits
    Given the profile "human-appsec-reviewer" specialises "human-reviewer"
    When a security review step that asks for the role "reviewer" is resolved
    Then the profile "human-appsec-reviewer" is chosen
