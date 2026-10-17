# One feature per promise area. Every scenario cites the promise it checks.
@promise:PR-AREA-NNN
Feature: [Promise area]

  Scenario: [One observable behaviour]
    Given [the state of the world before the trigger]
    When [the single trigger]
    Then [the outcome an outside observer can verify]

  # A confirmed defect in the agreed reproduction scope (QA_008).
  @defect:ISSUE-NNN
  Scenario: [The defect's behaviour, stated as the correct outcome]
    Given [the precondition that exposes the defect]
    When [the trigger]
    Then [the correct outcome, which fails until the fix lands]

  # Behaviour that is checked by a person for now.
  @manual @manual-result:pending @manual-last-tested:X.Y.Z
  Scenario: [Manual check]
    Given [context]
    When [action]
    Then [outcome]
