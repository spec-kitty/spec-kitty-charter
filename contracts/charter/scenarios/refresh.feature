@contract
Feature: Refreshing the packs
  POST /packs/refresh forces the service to re-read every configured pack and the project configuration, and to
  discard any built read model (DM D10; operator ruling 2026-10-19: refresh lives in the packs domain). It answers
  the refreshed pack listing, the GET /packs schema, with the one fingerprint it verified. This version keeps no
  built graph, so a refresh re-reads and re-verifies. It takes no request body, so that a later optional paths hint
  is a clean addition, and it is idempotent. It changes nothing on disk. The scenarios run with the worktree id
  scenario-worktree.

  Background:
    Given the built-in pack "built-in" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      edges: []
      """

  Scenario: A refresh answers the pack listing with the verified fingerprint
    When I POST "/packs/refresh"
    Then the response status is 200
    And the response body is:
      """
      {"packs": [{"name": "built-in", "level": "built-in", "packId": null, "packVersion": null, "parentPack": null, "nodeCount": 1, "edgeCount": 0, "diagnostics": [{"code": "pack_level_missing", "detail": "built-in pack built-in has no pack.yaml; the configured level built-in is used"}]}]}
      """
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "Charter-Source-Fingerprint" appears once

  Scenario: A refresh reports the listing GET /packs reports and the fingerprint /info reports
    When I GET "/packs"
    And I remember the response body
    And I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I POST "/packs/refresh"
    Then the response body is the remembered one
    And the response header "Charter-Source-Fingerprint" is the remembered "sourceFingerprint"

  Scenario: A refresh after a pack file changed reports the new listing and the new fingerprint
    When I POST "/packs/refresh"
    And I remember the response body
    And I remember the header "Charter-Source-Fingerprint"
    Given the built-in pack "built-in" with the file "tactic.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: tactic:added
        kind: tactic
        label: added
      edges: []
      """
    When I POST "/packs/refresh"
    Then the response status is 200
    And the response body differs from the remembered one
    And the response header "Charter-Source-Fingerprint" differs from the remembered header
    When I GET "/packs"
    Then the response header "Charter-Source-Fingerprint" appears once

  Scenario: A refresh is idempotent
    When I POST "/packs/refresh"
    And I remember the response body
    And I remember the header "Charter-Source-Fingerprint"
    And I POST "/packs/refresh"
    Then the response status is 200
    And the response body is the remembered one
    And the response header "Charter-Source-Fingerprint" is the remembered header

  Scenario: A refresh with a body is a 400
    The operation takes no body, so a client that sends one is told, and nothing is read.
    When I POST "/packs/refresh" with the body:
      """
      {"paths": ["tactics"]}
      """
    Then the response status is 400
    And the response body is:
      """
      {"title": "Bad Request", "status": 400}
      """
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A refresh without the token is a 401
    When I POST "/packs/refresh" without the worktree token
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A refresh a browser makes is a 403
    When I POST "/packs/refresh" with the header "Origin": "https://evil.example"
    Then the response status is 403
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: The old refresh path does not exist
    When I POST "/refresh"
    Then the response status is 404

  Scenario: A refresh that fails unexpectedly is refused with internal_error
    Given the service fails unexpectedly while it reads the packs
    When I POST "/packs/refresh"
    Then the response status is 500
    And the problem code is "internal_error"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A refresh of a pack that cannot be loaded is refused with the pack's code
    Given the built-in pack "built-in" with the file "broken.graph.yaml":
      """
      nodes: [
      """
    When I POST "/packs/refresh"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A refresh whose packs change while it is answered is refused with stale_input
    Given a pack file changes while the next read is answered
    When I POST "/packs/refresh"
    Then the response status is 500
    And the problem code is "stale_input"
    And the response has no "Charter-Source-Fingerprint" header
    When I POST "/packs/refresh"
    Then the response status is 200
