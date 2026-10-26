@contract @journey-07
# Journey: docs/context/user-journeys/07-add-a-mission-type.md
Feature: Journey 07 — a pack author adds a mission type
  A pack author adds a mission type whose steps ask for roles, plus specialist profiles that perform
  those roles and a fallback profile. None of these surfaces is in the contract yet.

  @WIP
  Scenario: A mission type whose steps ask for roles is registered
    When the author registers the mission type "security-audit" with a step that asks for the role "reviewer"
    Then a read of the mission type "security-audit" lists that step and its role

  @WIP
  Scenario: A specialist profile is chosen for the role it performs
    Given the profile "appsec-reviewer" performs the role "reviewer" for the mission type "security-audit"
    When the step that asks for the role "reviewer" is resolved
    Then the profile "appsec-reviewer" is chosen

  @WIP
  Scenario: The fallback profile is chosen when no specialist fits
    Given no profile specialised for the mission type "security-audit" performs the role "scribe"
    And the fallback profile "generalist" performs the role "scribe"
    When the step that asks for the role "scribe" is resolved
    Then the profile "generalist" is chosen
