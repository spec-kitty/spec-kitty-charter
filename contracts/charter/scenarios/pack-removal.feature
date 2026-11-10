# Removal from a pack (ADR-0065 D4, US2 AS1 to AS4 of layer-merge-and-effective-reads, FR-005, FR-006, FR-007, FR-036,
# SC-001; decisions DM-01M6TQ62HXYF362GAFS2BZ3PGF, DM-01M6TQ67C9ZTNGZB9CY28JS54F and DM-01M6TR1KDAJ64GMJD6AYNZKT59).
# deletePackArtifact and deletePackRelationship remove one artifact, or one registered edge, from a pack without
# sending the pack again. Both are synchronous and require If-Match on the pack revision.
#
# State: bound by WP19 (the removal services), which recorded the red assertion failure and removed the @WIP tags
# (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW). The A10 scenario of activation-ruling-changes.feature (another pack's preset
# names the artifact) is WP19's as well.
#
# Steps WP19 adds (listed in scenarios/README.md, "Registration steps"): When I DELETE "<path>" with the remembered ETag.
# Everything else is the existing vocabulary. A DELETE rewrites no file the author wrote, so "the pack is byte-identical"
# is read as an unchanged sourceFingerprint of GET /info, which digests every pack file.
#
# Not covered here, by design: the 409 governance_policy_violation of the REMOVE operation. It fires only when an
# ancestor of the pack holds the id and locks it, and a pack that holds an id its ancestor locks does not compose
# (FR-011), so no fixture reaches it through the API. WP19's unit tests pin it.
# The same holds for the edge gate of the relationship DELETE (WP20, decision DM-01M6TR1KDAJ64GMJD6AYNZKT59 item 4: the
# removed edge is a RELATIONSHIP write on its source, so 409 governance_policy_violation when an ancestor locks the
# source) and for the pack-qualified targets of its pair compare (A to base::X and A to other::X are two edges, and one
# DELETE names one): RelationshipRemovalServiceTest pins both over fixture packs.

@contract @registration
Feature: One artifact or one registered relationship is removed from a pack
  A client removes an artifact, or an edge that PUT /packs/{packId}/relationships registered, without sending the pack
  again (FR-005, FR-006). An artifact DELETE removes the artifact, its outgoing registered edges and every preset item
  of the pack that names it; it is refused while another artifact or pack still refers to it, judged against the tree
  after the removal. A relationship DELETE removes only an edge of registered.graph.yaml and never rewrites a file the
  author wrote. A refused DELETE changes nothing (FR-007). Both answer 428 without If-Match and 412 with a stale one.
  PR A is strict: a removal that leaves a closure unable to compose, or breaking a relation rule, is refused 422.

  Background:
    Given registration is switched on
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      """
    And the org pack "qa" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: D1
      intent: The directive the scenarios remove
      enforcement: required
      """
    And the org pack "qa" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: A directive that stays
      enforcement: required
      """
    And the org pack "qa" with the file "directives/D3.directive.yaml":
      """
      schema_version: '1.0'
      id: D3
      title: D3
      intent: A directive nothing refers to
      enforcement: required
      """
    And the org pack "qa" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: t
      steps:
        - title: Only step
      """
    And the org pack "qa" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The team's directive and tactic.
      activated_directives:
        - id: D1
      activated_tactics:
        - id: t
      """

  # bound by: WP19 (US2 AS1)
  Scenario: Deleting an artifact removes it and the preset item that names it, and keeps the rest of the pack
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}]}
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D1" with the remembered ETag
    Then the response status is 204
    And the response header "ETag" appears once
    When I GET "/packs/qa"
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 404
    And the problem code is "directive_not_found"
    When I GET "/packs/qa/artifacts/directive/D2"
    Then the response status is 200
    And the field at "/directive/title" is "D2"
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:t
      """

  # bound by: WP19 (US2 AS1: the artifact's outgoing registered edges go with it)
  Scenario: Deleting an artifact removes its outgoing registered edges, so a new artifact of that id starts with none
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D3", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the field at "/items/0/reference" is "directive:D3"
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D1" with the remembered ETag
    Then the response status is 204
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "D1", "schemaVersion": "1.0", "title": "D1 again", "intent": "Registered anew", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the response status is 200
    And the field at "/items/0" is absent
    When I GET "/packs/qa/artifacts/directive/D3"
    Then the response status is 200

  # bound by: WP05 of agent-profile-reads (operator decision 01M70ZFW6S2HG3TEE33RW8VGSB: an agent profile is removed as the
  # five kinds are, with the If-Match of the ETag its read gave)
  Scenario: An agent profile is deleted with the ETag its own read gave, and a later read and the list no longer hold it
    Given the org pack "qa" with the file "agent_profiles/jenny.agent.yaml":
      """
      profile-id: jenny
      name: Java Jenny
      roles:
        - implementer
      purpose: Implement.
      specialization:
        primary-focus: Java
      """
    When I GET "/packs/qa/artifacts/agent_profile/jenny"
    Then the response status is 200
    And I remember the ETag
    When I DELETE "/packs/qa/artifacts/agent_profile/jenny" with the remembered ETag
    Then the response status is 204
    And the response header "ETag" appears once
    When I GET "/packs/qa/artifacts/agent_profile/jenny"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/packs/qa/artifacts?kind=agent_profile"
    Then the response status is 200
    And the field at "/items/0" is absent
    When I GET "/packs/qa/artifacts/directive/D2"
    Then the response status is 200

  # bound by: WP05 of agent-profile-reads (a profile is refused as a directive is: an edge of the pack that points at it)
  Scenario: An agent profile another artifact of the pack points at is not deleted, and the refusal names the referrer
    Given the org pack "qa" with the file "agent_profiles/jenny.agent.yaml":
      """
      profile-id: jenny
      name: Java Jenny
      roles:
        - implementer
      purpose: Implement.
      specialization:
        primary-focus: Java
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "agent_profile:jenny", "type": "requires"}
      """
    Then the response status is 201
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/agent_profile/jenny" with the remembered ETag
    Then the response status is 409
    And the problem code is "artifact_referenced"
    When I GET "/packs/qa/artifacts/agent_profile/jenny"
    Then the response status is 200

  # bound by: WP19 (US2 AS4, FR-005: a 404 per code, each with the control of the delete that follows)
  Scenario Outline: A path that names nothing the pack holds is a 404 with its own code
    When I DELETE "<path>" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "<code>"
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D3" with the remembered ETag
    Then the response status is 204

    Examples: the pack, the kind, the artifact
      | path                                   | code                |
      | /packs/nope/artifacts/directive/D3     | pack_not_found      |
      | /packs/qa/artifacts/procedure/D3       | kind_not_found      |
      | /packs/qa/artifacts/directive/NOPE     | directive_not_found |
      | /packs/qa/artifacts/agent_profile/NOPE | artifact_not_found  |

  # bound by: WP19 (US2 AS2: another pack's edge refers to the artifact; the refusal changes nothing)
  Scenario: An artifact another pack refers to is not deleted, and the refusal names the referrer
    Given the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the org pack "other" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: A directive of another pack that requires qa's D1
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}]}
      """
    When I GET "/packs/other"
    And I remember the ETag
    And I PUT "/packs/other/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:R", "to": "qa::directive:D1", "type": "requires"}
      """
    Then the response status is 201
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D1" with the remembered ETag
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/packs/0/packId" is "other"
    And the field at "/violations/0/detail" contains "directive:R"
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 200
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {}
      """
    Then the field at "/applied" holds exactly the ids:
      """
      directive:D1
      tactic:t
      """
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: WP19 (US2 AS2, A10: a preset item of another pack is a referrer)
  Scenario: An artifact another pack's preset names is not deleted, and the preset is named
    Given the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the org pack "other" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: D1 of qa.
      activated_directives:
        - id: D1
          must_have: true
      """
    When I DELETE "/packs/qa/artifacts/directive/D1" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/packs/0/packId" is "other"
    And the field at "/violations/0/detail" contains "governing"
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 200
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: WP19 (US2 AS2, operator Q2: an artifact of the same pack that targets it is a referrer, one the same
  # request removes is not)
  Scenario: An artifact of the same pack that points at it blocks the delete until that artifact is gone
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "directive:D1", "type": "suggests"}
      """
    Then the response status is 201
    When I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D1" with the remembered ETag
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/violations/0/detail" contains "directive:D2"
    When I DELETE "/packs/qa/artifacts/directive/D2" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D1" with the remembered ETag
    Then the response status is 204

  # bound by: WP19 (decision DM-01M6TR1KDAJ64GMJD6AYNZKT59: an artifact the author wrote into a graph document)
  Scenario: An artifact an author-written graph document names is not deleted, and no file is rewritten
    Given the org pack "qa" with the file "lineage.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/artifacts/directive/D1" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 200
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: WP19 (US2 AS4's precondition half: 428 without If-Match, 412 with a stale one, each changing nothing)
  Scenario: A delete needs the pack revision: none is 428, a stale one is 412, the current one is accepted
    When I send DELETE "/packs/qa/artifacts/directive/D3"
    Then the response status is 428
    And the problem has no code
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    And the problem has no code
    When I GET "/packs/qa/artifacts/directive/D3"
    Then the response status is 200
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/directive/D3" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa/artifacts/directive/D3"
    Then the response status is 404

  # bound by: WP19 (ADR-0065 D13, FR-037: both removals are writes of the registration family, so
  # charter.registration.enabled gates them, before the pack, the path or If-Match is judged)
  Scenario: Both deletes answer 503 while registration is switched off, and nothing is removed
    Given registration is switched off
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "*"
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I send DELETE "/packs/qa/artifacts/directive/D3"
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I GET "/packs/qa/artifacts/directive/D3"
    Then the response status is 200
    Given registration is switched on
    When I DELETE "/packs/qa/artifacts/directive/D3" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: WP19 (US2 AS3: a registered edge is removed, in either orientation; the artifacts stay)
  Scenario Outline: A registered relationship is removed, whichever end the request names first
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D2", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/relationships?from=<from>&to=<to>" with the remembered ETag
    Then the response status is 204
    And the response header "ETag" appears once
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the response status is 200
    And the field at "/items/0" is absent
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 200
    When I GET "/packs/qa/artifacts/directive/D2"
    Then the response status is 200
    When I DELETE "/packs/qa/relationships?from=<from>&to=<to>" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "relationship_not_found"

    Examples: the pair as registered, and reversed
      | from           | to             |
      | directive:D1   | directive:D2   |
      | directive:D2   | directive:D1   |

  # bound by: WP19 (decision DM-01M6TQ62HXYF362GAFS2BZ3PGF: an edge the author wrote into a source shard is not a
  # registered edge)
  Scenario: An edge the author wrote into a source shard is refused, and the file is not rewritten
    Given the org pack "qa" with the file "lineage.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "relationship_not_registered"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the field at "/items/0/reference" is "directive:D2"
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D3" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "relationship_not_found"

  # bound by: WP19 (the same, in drg/fragment.yaml, a header-less document of nodes and edges)
  Scenario: An edge the author wrote into drg/fragment.yaml is refused, and the file is not rewritten
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      pack_drg: drg/fragment.yaml
      """
    And the org pack "qa" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges:
      - {source: 'directive:D1', target: 'directive:D2', relation: suggests}
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "relationship_not_registered"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the field at "/items/0/reference" is "directive:D2"
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D3" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "relationship_not_found"

  # bound by: WP19 (a pair held both as a registered edge and in an author-written document: nothing changes)
  Scenario: A pair held both as a registered edge and in an author-written shard is refused, and the edge stays in force
    Given the org pack "qa" with the file "lineage.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:D2', target: 'directive:D1', relation: suggests}
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D2", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "relationship_not_registered"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts/directive/D1/references"
    Then the field at "/items/0/reference" is "directive:D2"

  # bound by: WP19 (ADR-0064 D7: a derived relation is not a registered edge)
  Scenario: A derived relation is refused as not registered
    Given the org pack "qa" with the file "guards/traceability-ready.guard.yaml":
      """
      schema_version: '1.0'
      id: traceability-ready
      title: The traceability carrier is ready
      description: Every promise of the work package traces to a scenario.
      interface: guard/1
      implemented_by: asset:carrier-lint
      on_indeterminate: pass
      """
    And the org pack "qa" with the file "assets/carrier_lint.py.asset.yaml":
      """
      id: carrier-lint
      mime: text/x-python
      path: carrier_lint.py
      title: Traceability carrier lint
      executable:
        runtime: python
        min_version: '3.11'
        entry: carrier_lint.py
        args:
        - --guard
      """
    And the org pack "qa" with the file "assets/carrier_lint.py":
      """
      print("pass")
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=guard:traceability-ready&to=asset:carrier-lint" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "relationship_not_registered"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D2", "type": "suggests"}
      """
    Then the response status is 201
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: WP19 (a relationship DELETE needs the revision too, and a pair with no edge is 404)
  Scenario: A relationship delete needs the pack revision, and a pair with no edge is not found
    When I send DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2"
    Then the response status is 428
    And the problem has no code
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    When I DELETE "/packs/nope/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "relationship_not_found"

  # bound by: WP19 (decision DM-01M6TQ67C9ZTNGZB9CY28JS54F: PR A is strict, a removal that leaves a closure broken is a
  # 422). The activation lists no pack, so it roots at every configured pack, and the pack rival that is installed after
  # it holds D1 as qa does: its closure does not compose (two packs of one level hold one id), so no removal from qa
  # repairs it. A removal cannot break a closure that composes:
  # an enhancer chain over held artifacts always ends at an artifact that enhances nothing, and a held target is never
  # removed while something refers to it (409).
  Scenario: A removal that leaves a closure of a stored activation broken is refused 422, and is accepted once the closure is whole
    Given the org pack "qa" with the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes:
      - urn: directive:D2
        kind: directive
      - urn: directive:D3
        kind: directive
      edges:
      - source: directive:D2
        target: directive:D3
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2}
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      """
    And the org pack "rival" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: The rival D1
      intent: A directive of a pack that does not build on qa
      enforcement: required
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=directive:D2&to=directive:D3" with the header "If-Match": "*"
    Then the response status is 422
    And the problem code is "pack_sibling_conflict"
    And the field at "/packs/0/packId" is "rival"
    And the field at "/packs/1/packId" is "qa"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I DELETE "/activations/a-1" with the header "If-Match": "*"
    Then the response status is 204
    When I DELETE "/packs/qa/relationships?from=directive:D2&to=directive:D3" with the header "If-Match": "*"
    Then the response status is 204

  # The control of the scenario above (squad finding Q5): the strict 422 pins a closure that was already broken, so it
  # holds only when the broken closure is one the pack belongs to. This activation lists the one pack "rival" and not
  # qa. "rival" then comes to depend on "rival-two", which holds D1 as it does, so the closure of the activation does not
  # compose; it does not hold qa, and the same removal from qa is not refused for it.
  Scenario: A removal is not refused for the broken closure of a stored activation that does not list the pack
    Given the org pack "qa" with the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes:
      - urn: directive:D2
        kind: directive
      - urn: directive:D3
        kind: directive
      edges:
      - source: directive:D2
        target: directive:D3
        relation: requires
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      """
    And the org pack "rival" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: The first rival D1
      intent: A directive of a pack that does not build on qa
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "rival"}]}
      """
    And the org pack "rival-two" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: rival-two
      pack_level: org
      """
    And the org pack "rival-two" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: The second rival D1
      intent: Another directive of a pack that does not build on qa
      enforcement: required
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943T
          range: '>=1.0'
      """
    When I DELETE "/packs/qa/relationships?from=directive:D2&to=directive:D3" with the header "If-Match": "*"
    Then the response status is 204
