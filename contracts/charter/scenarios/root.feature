@contract
Feature: The API root links to every top-level resource
  A client starts from one known place, GET / (the server prefix with its trailing slash,
  /api/v1/), and follows links from there (ADR-0019 D2 and D4, FR-001): self, info, offering, the
  artifacts of a pack as an RFC 6570 template, and service-desc, the contract document (RFC 8631).
  The root reads no pack file, so it carries no Charter-Source-Fingerprint. Like every operation it
  needs the worktree token.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      """
    And the org pack "qa" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
      """

  Scenario: The root answers its links and carries no fingerprint
    When I GET "/"
    Then the response status is 200
    And the response body is:
      """
      {
        "_links": {
          "self": {"href": "/api/v1/"},
          "info": {"href": "/api/v1/info"},
          "offering": {"href": "/api/v1/offering"},
          "artifacts": {"href": "/api/v1/packs/{packId}/artifacts{?kind}", "templated": true},
          "service-desc": {"href": "/api/v1/openapi.yaml"}
        }
      }
      """
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header
    And the response has no "Link" header

  Scenario: The info link of the root answers the service info
    When I GET "/"
    And I follow the link "info"
    Then the response status is 200
    And the field "worktree" of the response body is "scenario-worktree"

  Scenario: The offering link of the root answers the offering
    When I GET "/"
    And I follow the link "offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "qa"

  Scenario: The service-desc link of the root answers the contract document
    When I GET "/"
    And I follow the link "service-desc"
    Then the response status is 200
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: The artifacts template of the root, expanded with a configured pack, answers its artifacts
    When I GET "/"
    And I follow the templated link "artifacts" with:
      | packId | qa |
    Then the response status is 200
    And the field at "/items/0/id" is "rehearse"
    And the field at "/_links/self/href" is "/api/v1/packs/qa/artifacts"

  Scenario: The artifacts template of the root, expanded with a pack and a kind, answers that kind
    When I GET "/"
    And I follow the templated link "artifacts" with:
      | packId | qa     |
      | kind   | tactic |
    Then the response status is 200
    And the field at "/items/0/kind" is "tactic"
    And the field at "/_links/self/href" is "/api/v1/packs/qa/artifacts?kind=tactic"

  Scenario: The root needs the worktree token
    When I GET "/" without the worktree token
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: The server prefix without its trailing slash is no operation
    The root is /api/v1/, with the slash; /api/v1 is not mapped to it and answers the 404 of a path no
    operation defines, a Problem without a code. A client follows the root's self link as given.
    When I GET ""
    Then the response status is 404
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header
