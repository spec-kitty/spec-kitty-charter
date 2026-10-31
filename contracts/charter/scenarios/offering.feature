@contract @offering
Feature: The offering lists the configured packs in force
  A client reads which Charter Packs the service answers from (ADR-0020 D1, FR-002): GET /offering
  answers every configured pack with its id, its configured level, the name and the version its
  pack.yaml gives, and the link to its artifacts. The order is the merge order: by configured level
  (built-in, then org, then project), then by pack id in code-point order, so Zeta comes before
  alpha. The level is the one the pack is configured at, never the one its pack.yaml declares.
  Merging is not applied yet (#10, #11): the offering lists the packs, not a merged result. The
  answer is one read verified against the source fingerprint, and carries it; a configured pack
  that cannot be read refuses the whole answer with pack_unreadable, and inputs that change while
  it is read refuse it with stale_input.

  Scenario: The packs are ordered by configured level, then by pack id in code-point order
    The packs are configured out of order, project first and the lower-case id before the
    upper-case one at every level; neither the configured order nor a case-blind order is the answer.
    Given the project pack "charlie" with the file "pack.yaml":
      """
      pack_id: charlie
      pack_version: 1.0.0
      parent_pack: null
      name: charlie pack
      pack_level: project
      """
    And the project pack "Kilo" with the file "pack.yaml":
      """
      pack_id: Kilo
      pack_version: 1.0.0
      parent_pack: null
      name: Kilo pack
      pack_level: project
      """
    And the org pack "bravo" with the file "pack.yaml":
      """
      pack_id: bravo
      pack_version: 1.0.0
      parent_pack: null
      name: bravo pack
      pack_level: org
      """
    And the org pack "Omega" with the file "pack.yaml":
      """
      pack_id: Omega
      pack_version: 1.0.0
      parent_pack: null
      name: Omega pack
      pack_level: org
      """
    And the built-in pack "alpha" with the file "pack.yaml":
      """
      pack_id: alpha
      pack_version: 1.0.0
      parent_pack: null
      name: alpha pack
      pack_level: built-in
      """
    And the built-in pack "Zeta" with the file "pack.yaml":
      """
      pack_id: Zeta
      pack_version: 1.0.0
      parent_pack: null
      name: Zeta pack
      pack_level: built-in
      """
    When I GET "/offering"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "Link" header
    And the response body is:
      """
      {
        "packs": [
          {"packId": "Zeta", "level": "built-in", "name": "Zeta pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/Zeta/artifacts"}}},
          {"packId": "alpha", "level": "built-in", "name": "alpha pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/alpha/artifacts"}}},
          {"packId": "Omega", "level": "org", "name": "Omega pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/Omega/artifacts"}}},
          {"packId": "bravo", "level": "org", "name": "bravo pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/bravo/artifacts"}}},
          {"packId": "Kilo", "level": "project", "name": "Kilo pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/Kilo/artifacts"}}},
          {"packId": "charlie", "level": "project", "name": "charlie pack", "version": "1.0.0", "_links": {"artifacts": {"href": "/api/v1/packs/charlie/artifacts"}}}
        ],
        "_links": {"self": {"href": "/api/v1/offering"}}
      }
      """

  Scenario: The configured level wins over the level pack.yaml declares
    Given the project pack "local" with the file "pack.yaml":
      """
      pack_id: local
      pack_version: 1.0.0
      parent_pack: null
      name: local pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "local"
    And the field at "/packs/0/level" is "project"

  Scenario: A pack whose pack.yaml declares no level is listed at its configured level
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: core
      pack_version: 1.0.0
      parent_pack: null
      name: core pack
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "core"
    And the field at "/packs/0/level" is "built-in"
    And the field at "/packs/0/name" is "core pack"
    And the field at "/packs/0/version" is "1.0.0"

  Scenario: Without configured packs the offering is empty
    When I GET "/offering"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {"packs": [], "_links": {"self": {"href": "/api/v1/offering"}}}
      """

  Scenario: The artifacts link of every pack answers its artifacts
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: core
      pack_version: 1.0.0
      parent_pack: null
      name: core pack
      pack_level: built-in
      """
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa pack
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
    When I GET "/offering"
    Then the response status is 200
    And following the link "artifacts" of every element of "packs" answers 200

  Scenario: A pack whose pack.yaml is invalid refuses the offering with pack_unreadable
    The file is there but is not a pack descriptor: the offering cannot name the pack, so it
    answers nothing rather than a partial list.
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa pack
      pack_level: org
      """
    And the org pack "broken" with the file "pack.yaml":
      """
      pack_id: broken
      pack_version: [unclosed
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header
    And the response has no "Link" header

  Scenario: A configured pack without pack.yaml refuses the offering with pack_unreadable
    Operator ruling of 2026-10-31: a configured pack without a readable pack.yaml is not a readable
    canonical pack, so the offering cannot name it and answers nothing rather than a partial list.
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa pack
      pack_level: org
      """
    And the org pack "bare" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A configured pack whose directory is missing refuses the offering with pack_unreadable
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa pack
      pack_level: org
      """
    And the org pack "gone" with the file "pack.yaml":
      """
      pack_id: gone
      pack_version: 1.0.0
      parent_pack: null
      name: gone pack
      pack_level: org
      """
    And the directory of the pack "gone" is missing
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An offering whose inputs change while it is answered is refused with stale_input
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: core
      pack_version: 1.0.0
      parent_pack: null
      name: core pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      """
    And a pack file changes while the next read is answered
    When I GET "/offering"
    Then the response status is 500
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the offering was read; read it again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/offering"
    Then the response status is 200

  Scenario: The offering needs the worktree token
    When I GET "/offering" without the worktree token
    Then the response status is 401
    And the response has no "Charter-Source-Fingerprint" header
