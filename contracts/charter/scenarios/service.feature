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

  Scenario: The service names its build version
    The service version is the build's own, a semantic version apart from the contract version, which
    names every operation the service serves.
    When I GET "/info"
    Then the response status is 200
    And the field "contractVersion" of the response body is the contract version
    And the field "serviceVersion" of the response body is a semantic version
    And the field "worktree" of the response body is "scenario-worktree"
    And the field "sourceFingerprint" of the response body is a sha256 digest

  Scenario: The info answer carries the contract version, the worktree and the source fingerprint it verified
    The fingerprint is set once, by the read, and equals the sourceFingerprint of the body it came with.
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Then the response status is 200
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "Charter-Source-Fingerprint" is the remembered "sourceFingerprint"

  Scenario: A refusal carries the contract version and the worktree, not the fingerprint
    The fingerprint is the digest a read verified and set on its 200 answer; no refusal carries one.
    When I GET "/packs/no-such-pack/tactics"
    Then the response status is 404
    And the problem code is "pack_not_found"
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: When the packs cannot be read the refusal carries no fingerprint but still carries the other headers
    No refusal carries the source fingerprint; this one still carries the contract version and the
    worktree.
    Given the service fails unexpectedly while it reads the packs
    When I GET "/info"
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

  Scenario: The source fingerprint follows the project configuration
    The source fingerprint covers config.yaml and charter.yaml under the project configuration
    directory, as bytes: an edit changes it, and restoring the bytes restores it. The files are
    digested, never read.
    Given the project configuration file "config.yaml":
      """
      # written by the scenario
      """
    And the project configuration file "charter.yaml":
      """
      # written by the scenario
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Given the project configuration file "charter.yaml":
      """
      # edited by the scenario
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body differs from the remembered one
    Given the project configuration file "charter.yaml":
      """
      # written by the scenario
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one

  Scenario: Editing config.yaml changes the fingerprint
    Given the project configuration file "config.yaml":
      """
      # written by the scenario
      """
    And the project configuration file "charter.yaml":
      """
      # written by the scenario
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Given the project configuration file "config.yaml":
      """
      # edited by the scenario
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body differs from the remembered one
    Given the project configuration file "config.yaml":
      """
      # written by the scenario
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one

  Scenario: An absent configuration file and an empty one fingerprint differently
    Given the project configuration file "charter.yaml" is absent
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Given the project configuration file "charter.yaml":
      """
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body differs from the remembered one

  Scenario: Without the two configuration files the fingerprint covers the packs only
    The project configuration directory exists but holds neither file. The fingerprint is then
    the same on every read and still follows the pack files.
    Given the project configuration file "config.yaml" is absent
    And the project configuration file "charter.yaml" is absent
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
