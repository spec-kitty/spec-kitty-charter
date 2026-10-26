@contract @journey-08
# Journey: docs/context/user-journeys/08-resolve-a-step.md
Feature: Journey 08 — a contract consumer resolves a step
  A contract consumer resolves a Mission step against an activation context: the selection order
  decides, a fallback applies when nothing specific fits, nothing found is said plainly, and ties stay
  open. Resolution is not in the contract yet; the fingerprint that ties an answer to its inputs is.

  Background:
    Given I GET "/info"
    And I remember the field "sourceFingerprint" of the response body

  Scenario: Two reads of unchanged inputs carry the same fingerprint
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one

  @WIP
  Scenario: The selection order decides which artifact a step resolves to
    Given an activation context where the project and the org both offer a tactic for the step "plan"
    When the step "plan" is resolved
    Then the project's tactic is chosen

  @WIP
  Scenario: A fallback applies when nothing specific fits
    Given an activation context with no tactic specific to the step "plan" and a fallback tactic
    When the step "plan" is resolved
    Then the fallback tactic is chosen and the answer says it is a fallback

  @WIP
  Scenario: A step with nothing to resolve is reported as not found
    Given an activation context with no tactic for the step "plan" and no fallback
    When the step "plan" is resolved
    Then the answer says nothing was found for the step "plan"

  @WIP
  Scenario: A tie is left open
    Given an activation context where two tactics at the same level fit the step "plan"
    When the step "plan" is resolved
    Then the answer lists both tactics as a tie and chooses neither
