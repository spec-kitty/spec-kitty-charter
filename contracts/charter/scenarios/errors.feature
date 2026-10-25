@contract
Feature: Failures outside the charter's refusals
  A request the service cannot answer for a reason that is not a charter refusal is answered with
  a Problem body too, never with a framework's own error document. A 4xx Problem carries the
  title and the status of the HTTP status and nothing else: no code, because no refusal applies.
  Every 500 is a CharterRefusal: an unexpected failure is refused with internal_error and a
  generic detail that says nothing about the failure. A read whose packs change while it is
  answered is refused with stale_input, never answered from a mix of old and new files, and no
  refusal carries a Charter-Source-Fingerprint header.

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

  Scenario: A path the contract does not define is answered with a not-found problem
    When I GET "/pack"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Not Found", "status": 404}
      """

  Scenario: A request that accepts no representation the operation has is answered with a not-acceptable problem
    The problem is served as application/problem+json although the request does not accept it,
    as RFC 9457 allows for an error response.
    When I GET "/info" accepting "text/csv"
    Then the response status is 406
    And the response body is:
      """
      {"title": "Not Acceptable", "status": 406}
      """

  Scenario: An unexpected failure while the packs are read is refused with internal_error
    Given the service fails unexpectedly while it reads the packs
    When I GET "/info"
    Then the response status is 500
    And the problem code is "internal_error"
    And the response body is:
      """
      {"title": "Internal error", "status": 500, "code": "internal_error", "detail": "The charter service failed unexpectedly; the failure is logged."}
      """

  Scenario: An input the service cannot read is refused with pack_unreadable
    A directory stands where config.yaml should be a file, so reading it fails with an I/O error
    whoever runs the service (a file made unreadable by its permissions would still be readable to
    root). The input is one the service digests, not a seam: nothing about it changes while the
    read is answered, so the refusal is the first digest's own and permanent, not stale_input. It
    names the file relative to the project configuration directory and says nothing of the host.
    Given the project configuration file "config.yaml" is a directory
    When I GET "/info"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response body, apart from "detail", is:
      """
      {"title": "Pack unreadable", "status": 500, "code": "pack_unreadable"}
      """
    And the field "detail" of the response body starts with "project-config/config.yaml cannot be read"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A service info whose inputs change while it is answered is refused with stale_input
    /info digests twice and answers only the digest both reads agree on. A pack file rewritten between the two
    digests, or one the second digest can no longer read, makes the answer stale. The same read, asked again,
    answers.
    Given a pack file changes while the next read is answered
    When I GET "/info"
    Then the response status is 500
    And the problem code is "stale_input"
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the service info was read; read it again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/info"
    Then the response status is 200

  Scenario: A read that watches for a change and sees none answers with the digest it verified
    The negative control of the stale_input scenario: the same seam, armed to change nothing.
    Given no pack file changes while the next read is answered
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "Charter-Source-Fingerprint" is the remembered "sourceFingerprint"

  @reserved
  Scenario: governance_policy_violation is reserved
    A declaration pin, not behavior. No operation of this version raises the code: the unit test
    ReservedRefusalCodesTest fails when a main class other than the enum and its title mapping
    names it, and the mutability-policy epic #55 (ADR-0012, steps #57 to #61), which will raise
    it, deletes that test. Nothing here calls the service.
    Then the contract declares the refusal code "governance_policy_violation"
    And the contract says "governance_policy_violation" is answered with status 409
    And the contract says "governance_policy_violation" may not be retried
    And the contract says "governance_policy_violation" is raised by no operation

  Scenario: The refusal codes the contract marks raised are exactly those an operation raises
    The contract's machine-readable x-refusal map agrees with the behavior of this suite: each
    refusal a scenario observes has the status and the raised flag the contract declares for its
    code, and these are all the codes raised. The per-kind reads add directive_not_found,
    styleguide_not_found and paradigm_not_found (a toolguide reuses pack_not_found for its absent
    sub-resource, so it adds no code); the registration writes add validation_failed and
    registration_conflict. mission_type_not_found and action_not_found are no
    longer raised: the operations that answered them are gone. unresolved_edge_endpoint, and, with the
    load that judged a pack's graph, pack_level_mismatch and drg_schema_error, are raised by no operation.
    Then the contract says exactly these refusal codes are raised by an operation:
      | pack_unreadable            |
      | internal_error             |
      | stale_input                |
      | pack_not_found             |
      | tactic_not_found           |
      | directive_not_found        |
      | styleguide_not_found       |
      | paradigm_not_found         |
      | validation_failed          |
      | registration_conflict      |
