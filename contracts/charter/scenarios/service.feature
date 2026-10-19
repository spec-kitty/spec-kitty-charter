@contract
Feature: What the service says about itself
  The seam in core checks that it talks to the right service about the right worktree (ADR-0011):
  the info operation and three headers on every response of the charter's operations name the
  contract version the service was built against, the worktree it serves (an opaque id, never a
  host path) and a fingerprint of the pack files it answers from. The scenarios run with the
  worktree id scenario-worktree.

  Background:
    Given the built-in pack "built-in" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """

  Scenario: The info operation names the contract version, the worktree and the source fingerprint
    When I GET "/info"
    Then the response status is 200
    And the field "contractVersion" of the response body is the contract version
    And the field "worktree" of the response body is "scenario-worktree"
    And the field "sourceFingerprint" of the response body is a sha256 digest

  Scenario: Every answer carries the contract version, the worktree and the source fingerprint
    When I GET "/packs"
    Then the response status is 200
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: A refusal carries the headers too
    When I GET "/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "action_not_found"
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: A bad request carries the headers too
    The 400 for a depth this version does not answer is the shared Problem, and it carries the
    headers like every other response.
    When I GET "/mission-types/software-dev/actions/plan/context?depth=2"
    Then the response status is 400
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: When the packs cannot be read the fingerprint header is left out and the refusal still answers
    The source fingerprint is left out when a pack file cannot be read, because the read itself
    answers why; the refusal still carries the contract version and the worktree.
    Given the service fails unexpectedly while it reads the packs
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "internal_error"
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: The source fingerprint follows the pack files
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    Given the built-in pack "built-in" with the file "tactic.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body differs from the remembered one
