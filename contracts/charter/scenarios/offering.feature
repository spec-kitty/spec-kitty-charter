@contract @offering
Feature: The offering lists the configured packs in force
  A client reads which Charter Packs the service answers from (ADR-0020 D1, FR-002): GET /offering
  answers every configured pack with its id, its configured level, the name and the version its
  pack.yaml gives, and the link to its artifacts. The order is the merge order: by configured level
  (built-in, then org, then project), then by pack id in code-point order, so a-zeta comes before
  alpha. The level is the one the pack is configured at, never the one its pack.yaml declares.
  Merging is not applied yet (#10, #11): the offering lists the packs, not a merged result. The
  answer is one read verified against the source fingerprint, and carries it; a configured pack
  that cannot be read refuses the whole answer with pack_unreadable, and inputs that change while
  it is read refuse it with stale_input.

  Scenario: The packs are ordered by configured level, then by pack id in code-point order
    The packs are configured out of order, project first and the letter-first id before the one with a
    hyphen at every level; neither the configured order nor an order that ignores the hyphen is the answer.
    Given the project pack "charlie" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000CHAR11E
      pack_version: 1.0.0
      parent_pack: null
      name: charlie-pack
      pack_level: project
      """
    And the project pack "c-kilo" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000K110
      pack_version: 1.0.0
      parent_pack: null
      name: kilo-pack
      pack_level: project
      """
    And the org pack "bravo" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000BRAV0
      pack_version: 1.0.0
      parent_pack: null
      name: bravo-pack
      pack_level: org
      """
    And the org pack "b-zulu" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000MEGA
      pack_version: 1.0.0
      parent_pack: null
      name: zulu-pack
      pack_level: org
      """
    And the built-in pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000A1PHA
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: built-in
      """
    And the built-in pack "a-zeta" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000ZETA
      pack_version: 1.0.0
      parent_pack: null
      name: zeta-pack
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
          {"packId": "a-zeta", "descriptorPackId": "01ARWG13C0000000000000ZETA", "level": "built-in", "name": "zeta-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/a-zeta/artifacts"}}},
          {"packId": "alpha", "descriptorPackId": "01ARWG13C000000000000A1PHA", "level": "built-in", "name": "alpha-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/alpha/artifacts"}}},
          {"packId": "b-zulu", "descriptorPackId": "01ARWG13C0000000000000MEGA", "level": "org", "name": "zulu-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/b-zulu/artifacts"}}},
          {"packId": "bravo", "descriptorPackId": "01ARWG13C000000000000BRAV0", "level": "org", "name": "bravo-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/bravo/artifacts"}}},
          {"packId": "c-kilo", "descriptorPackId": "01ARWG13C0000000000000K110", "level": "project", "name": "kilo-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/c-kilo/artifacts"}}},
          {"packId": "charlie", "descriptorPackId": "01ARWG13C0000000000CHAR11E", "level": "project", "name": "charlie-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/charlie/artifacts"}}}
        ],
        "_links": {"self": {"href": "/api/v1/offering"}, "kinds": {"href": "/api/v1/offering/kinds"}}
      }
      """

  Scenario: The configured level wins over the level pack.yaml declares
    Given the project pack "local" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C00000000000010CA1
      pack_version: 1.0.0
      parent_pack: null
      name: local-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "local"
    And the field at "/packs/0/level" is "project"

  Scenario: A pack whose pack.yaml declares no level is listed at its configured level
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000C0RE
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "core"
    And the field at "/packs/0/level" is "built-in"
    And the field at "/packs/0/name" is "core-pack"
    And the field at "/packs/0/version" is "1.0.0"

  Scenario: Without configured packs the offering is empty
    When I GET "/offering"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {"packs": [], "_links": {"self": {"href": "/api/v1/offering"}, "kinds": {"href": "/api/v1/offering/kinds"}}}
      """

  Scenario: The artifacts link of every pack answers its artifacts
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000C0RE
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
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
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "broken" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C00000000000BR0KEN
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
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
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
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "gone" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000G0NE
      pack_version: 1.0.0
      parent_pack: null
      name: gone-pack
      pack_level: org
      """
    And the directory of the pack "gone" is missing
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: One unreadable file of a configured pack refuses the offering, while the pack-scoped read still answers
    Operator ruling of 2026-11-04: the offering fails closed when any file of any configured pack
    cannot be read, an asset sidecar whose payload is missing included, because the offering names
    and composes every pack whole. A read scoped to that pack judges only the artifact it names.
    Given the org pack "docs" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000D0C5
      pack_version: 1.0.0
      parent_pack: null
      name: docs-pack
      pack_level: org
      """
    And the org pack "docs" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
      """
    And the org pack "docs" with the file "assets/notes.md.asset.yaml":
      """
      id: notes
      mime: text/markdown
      path: notes.md
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file assets/notes.md.asset.yaml of docs cannot be read: payload assets/notes.md does not exist"
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/docs/artifacts/tactic/rehearse"
    Then the response status is 200

  Scenario: A configured pack holding an agent profile that fails its schema refuses the offering
    PR-CHARTER-SHARING-001 (#374): a team pack carrying an unsound profile is not reported healthy. The
    profile's role is not a lowercase role name, so the file fails the agent-profile schema, and the
    offering names the pack and the file instead of answering the pack as sound.
    Given the org pack "team" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C00000000000000TEM
      pack_version: 1.0.0
      parent_pack: null
      name: team-pack
      pack_level: org
      """
    And the org pack "team" with the file "agent_profiles/reviewer.agent.yaml":
      """
      profile-id: reviewer
      name: Reviewer
      role: Reviewer
      purpose: Review the work
      specialization:
        primary-focus: review
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file agent_profiles/reviewer.agent.yaml of team cannot be read: 'role' does not match its pattern"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A configured pack holding an asset whose path leaves assets/ refuses the offering
    PR-CHARTER-SHARING-004 (#374): a shared asset that reaches outside its pack is not accepted as sound.
    The sidecar's path climbs out of assets/ and out of the pack to a file that does exist, the project's
    charter.yaml, so a check that only looks on disk would accept it. The load path names the escape, whether or not a
    file is at the target (#382, ADR-0040 decision 6 as amended), and the offering refuses the pack.
    Given the project configuration file "charter.yaml":
      """
      charter: outside the pack
      """
    And the org pack "docs" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000D0C5
      pack_version: 1.0.0
      parent_pack: null
      name: docs-pack
      pack_level: org
      """
    And the org pack "docs" with the file "assets/notes.md.asset.yaml":
      """
      id: notes
      mime: text/markdown
      path: ../../../.kittify/charter.yaml
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file assets/notes.md.asset.yaml of docs cannot be read: asset path '../../../.kittify/charter.yaml' escapes the pack's assets/ root"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A configured pack whose fragment names a body_path outside the pack refuses the offering
    #254: a fragment node's body_path is relative to the pack root. One that leaves it is a fault of the fragment, so
    the graph document cannot be read and the offering refuses the pack, as for any graph document it cannot read.
    Given the org pack "docs" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000D0C5
      pack_version: 1.0.0
      parent_pack: null
      name: docs-pack
      pack_level: org
      pack_drg: drg/fragment.yaml
      """
    And the org pack "docs" with the file "drg/fragment.yaml":
      """
      nodes:
      - id: D1
        kind: directives
        title: D1
        body_path: /etc/passwd
      edges: []
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    # A composing read names no file for a graph fault (CharterRefusalCode); the registration names the escape.
    And the field "detail" of the response body is "a graph document of docs cannot be read"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An offering whose inputs change while it is answered is refused with stale_input
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C0000000000000C0RE
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
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

  # R18 (#112): the kind taxonomy is the model's, so it is the same whatever packs are configured.
  Scenario: The kind taxonomy lists every kind and every relation, each relation with the kinds it may connect
    The kinds are the model's node kinds in their order, guard and lifecycle among them and no retired kind, with
    glossary_context, whose artifact kind is glossary, and term, only ever the target of a vocabulary edge, which has
    no plural and stands for no artifact kind (#106); the
    relations are the relation types in their order, then in_tension_with, and none of the retired impacts, replaces
    or delegates_to. Every relation names at least one pair of kinds it may connect. The
    answer reads no pack file, so it carries no fingerprint, and its ETag is the digest of the body it serves.
    When I GET "/offering/kinds"
    Then the response status is 200
    And the response has no "Charter-Source-Fingerprint" header
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
    And the response header "ETag" is the quoted sha256 digest of the response body
    And the field "kind" of the elements of "kinds" is, in order:
      """
      directive
      tactic
      styleguide
      toolguide
      paradigm
      procedure
      agent_profile
      glossary_context
      term
      skill
      anti_pattern
      asset
      template
      action
      mission_type
      guard
      lifecycle
      step_enhancement
      routing_catalog
      """
    And the field "relation" of the elements of "relations" is, in order:
      """
      requires
      suggests
      applies
      scope
      vocabulary
      instantiates
      refines
      specializes_from
      enhances
      overrides
      reconciles_tension
      rejects
      implemented_by
      in_tension_with
      """
    And every element of "relations" has a non-empty "endpoints"
    And the field at "/relations/13/heldAs" is "tension"
    And the field at "/relations/12/derived" is true
    And the field at "/relations/12/endpoints/0/source" is "guard"
    And the field at "/kinds/13/artifact" is false
    And the field at "/kinds/15/declarableInGraphs" is false
    And the field at "/kinds/15/edgeTarget" is false
    And the field at "/kinds/13/declarableInGraphs" is true
    And the field at "/kinds/13/edgeTarget" is true
    And the field at "/kinds/13/artifactKind" is absent
    And the field at "/kinds/13/plural" is "actions"
    And the field at "/kinds/14/artifactKind" is "mission_type"
    And the field at "/kinds/7/artifactKind" is "glossary"
    And the field at "/kinds/7/plural" is "glossaries"
    And the field at "/kinds/8/artifact" is false
    And the field at "/kinds/8/declarableInGraphs" is false
    And the field at "/kinds/8/edgeTarget" is true
    And the field at "/kinds/8/plural" is absent
    And the field at "/kinds/8/artifactKind" is absent
    And the field at "/kinds/0/_links/artifacts/href" is "/api/v1/packs/{packId}/artifacts?kind=directive"
    And the field at "/kinds/0/_links/artifacts/templated" is true
    And the field at "/kinds/13/_links/artifacts" is absent
    And the field at "/kinds/8/_links/artifacts" is absent
    And the field at "/kinds/18/artifactKind" is "routing_catalog"
    And the field at "/kinds/18/activatable" is true
    And the field at "/kinds/18/declarableInGraphs" is false
    And the field at "/kinds/18/edgeTarget" is false
    And the field at "/kinds/18/_links/artifacts/href" is "/api/v1/packs/{packId}/artifacts?kind=routing_catalog"
    And the field at "/_links/self/href" is "/api/v1/offering/kinds"

  Scenario: A kind's artifacts template, expanded with a configured pack, answers that pack's artifacts of the kind
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
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
    When I GET "/offering"
    And I follow the link "kinds"
    Then the response status is 200
    When I follow the templated link "artifacts" of the element 1 of "kinds" with:
      | packId | qa |
    Then the response status is 200
    And the field at "/items/0/id" is "rehearse"
    And the field at "/_links/self/href" is "/api/v1/packs/qa/artifacts?kind=tactic"

  Scenario: The kind taxonomy answers 304 to an If-None-Match that matches its ETag, and sends the ETag again
    When I GET "/offering/kinds"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/offering/kinds" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    And the response has no "Charter-Source-Fingerprint" header
    And the response header "Charter-Contract-Version" is the contract version
    And the response header "Charter-Worktree" is "scenario-worktree"
