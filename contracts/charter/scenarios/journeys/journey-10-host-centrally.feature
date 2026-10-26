@contract @journey-10
# Journey: docs/context/user-journeys/10-host-centrally.md
Feature: Journey 10 — a team lead hosts the charter service centrally
  A team lead runs the service for the whole team instead of one per worktree. Callers present a token,
  the service fails closed, and the active charter is written through; on a cold start the local copy
  wins and says so. The worktree token guard works today; hosting, login and write-through are not in
  the contract yet.

  Scenario: A request without the token is refused
    When I GET "/info" without the worktree token
    Then the response status is 401
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A request with the token is answered for its worktree
    When I GET "/info"
    Then the response status is 200
    And the field "worktree" of the response body is "scenario-worktree"

  @WIP
  Scenario: A hosted service refuses a caller without a login token
    Given the service is hosted for the team
    When a caller without a login token reads the active charter
    Then the request is refused and nothing is answered from the packs

  @WIP
  Scenario: A change to the active charter is written through to the hosted service
    Given a project connected to the hosted service
    When the project updates its active charter
    Then the hosted service answers with the updated active charter

  @WIP
  Scenario: On a cold start the local active charter wins visibly
    Given a project whose local active charter differs from the hosted one
    When the hosted service starts cold
    Then the local active charter is used
    And the answer says the local copy won
