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
# is read as an unchanged sourceFingerprint of GET /info, which digests every pack file. The one exception is a mission
# type's removal, which drops its own step links and the scope edges from its own step actions from the author-written
# documents that hold them (operator rulings DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ and DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6).
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
      role: implementer
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

  # bound by: #484 (operator ruling 2026-11-11: a procedure is removed as the served kinds are; the item's ETag is the
  # pack revision, so it is a valid If-Match)
  Scenario: A procedure is deleted with the ETag its own read gave, and a later read and the list no longer hold it
    Given the org pack "qa" with the file "procedures/refactoring.procedure.yaml":
      """
      schema_version: '1.0'
      id: refactoring
      title: Refactoring
      description: Change the structure, not the behavior.
      entry_condition: The tests pass.
      exit_condition: The tests still pass.
      steps:
        - title: Take one small step
      """
    When I GET "/packs/qa/artifacts/procedure/refactoring"
    Then the response status is 200
    And I remember the ETag
    When I DELETE "/packs/qa/artifacts/procedure/refactoring" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa/artifacts/procedure/refactoring"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/packs/qa/artifacts?kind=procedure"
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
      role: implementer
      purpose: Implement.
      specialization:
        primary-focus: Java
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "agent_profile:jenny", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/agent_profile/jenny" with the remembered ETag
    Then the response status is 409
    And the problem code is "artifact_referenced"
    When I GET "/packs/qa/artifacts/agent_profile/jenny"
    Then the response status is 200

  # bound by: #489 (a procedure is refused as a directive is while an edge of the pack still names it)
  Scenario: A procedure an edge of the pack still names is not deleted until the edge's source is gone
    Given the org pack "qa" with the file "procedures/refactoring.procedure.yaml":
      """
      schema_version: '1.0'
      id: refactoring
      title: Refactoring
      description: Change the structure, not the behavior.
      entry_condition: The tests pass.
      exit_condition: The tests still pass.
      steps:
        - title: Take one small step
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "procedure:refactoring", "type": "requires"}
      """
    Then the response status is 201
    When I remember the ETag
    And I DELETE "/packs/qa/artifacts/procedure/refactoring" with the remembered ETag
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/violations/0/detail" contains "directive:D2"
    When I GET "/packs/qa/artifacts/procedure/refactoring"
    Then the response status is 200
    When I DELETE "/packs/qa/artifacts/directive/D2" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/procedure/refactoring" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa/artifacts/procedure/refactoring"
    Then the response status is 404
    And the problem code is "artifact_not_found"

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
      | /packs/qa/artifacts/template/D3        | kind_not_found      |
      | /packs/qa/artifacts/directive/NOPE     | directive_not_found |
      | /packs/qa/artifacts/agent_profile/NOPE | artifact_not_found  |
      | /packs/qa/artifacts/procedure/NOPE     | artifact_not_found  |

  # Slice A2 (R14): an asset and a skill are listed and read, but the removal keeps its own kinds, so a DELETE of one
  # the pack holds answers as before they were listed, and removes nothing.
  Scenario Outline: An asset or a skill the pack holds is not removed: the removal serves no such kind (<kind>)
    Given the org pack "qa" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      """
    And the org pack "qa" with the file "assets/run.py":
      """
      print("run")
      """
    And the org pack "qa" with the file "skills/land-pr.skill.yaml":
      """
      schema_version: '1.0'
      id: land-pr
      title: Land a PR
      description: Use when a pull request needs landing.
      form: prompt
      body_path: land-pr.skill.md
      """
    And the org pack "qa" with the file "skills/land-pr.skill.md":
      """
      Goal: land it.
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/artifacts/<kind>/<id>" with the header "If-Match": "*"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Kind not found", "status": 404, "code": "kind_not_found", "detail": "The service removes no artifact of kind <kind>."}
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts?kind=<kind>"
    Then the response status is 200
    And the field at "/items/0/id" is "<id>"

    Examples:
      | kind  | id      |
      | asset | run     |
      | skill | land-pr |

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

  # ADR-0045 decisions 1 and 5 (#602, WP03): a tension is a relationship of its own pair class. It is registered beside
  # the structural relationship of its pair, stored in its stored orientation, and removed by its relation.
  Scenario: A tension is registered beside a requires on the same pair, stored smaller first, and removed by its relation
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "directive:D1", "type": "requires"}
      """
    Then the response status is 201
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "directive:D1", "type": "tension", "rationale": "both decide the depth"}
      """
    Then the response status is 201
    And the field at "/relationship/from" is "directive:D2"
    And the file "registered.graph.yaml" of the pack "qa" contains "relation: tension"
    And the file "registered.graph.yaml" of the pack "qa" contains "source: directive:D1"
    And the file "registered.graph.yaml" of the pack "qa" contains "relation: requires"
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D2", "type": "tension"}
      """
    Then the response status is 200
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2&relation=in_tension_with" with the remembered ETag
    Then the response status is 400
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2&relation=tension" with the remembered ETag
    Then the response status is 204
    And the file "registered.graph.yaml" of the pack "qa" does not contain "relation: tension"
    And the file "registered.graph.yaml" of the pack "qa" contains "relation: requires"
    When I DELETE "/packs/qa/relationships?from=directive:D1&to=directive:D2&relation=tension" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "relationship_not_found"

  Scenario: The old spelling in_tension_with is an unknown relation type, and nothing is written
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:D2", "type": "in_tension_with"}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations/0/code" is "relationship_type_unknown"

  Scenario: A tension on a pair an author-written shard already joins by a tension is refused tension_duplicated
    Given the org pack "qa" with the file "tension.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D1
        target: directive:D2
        relation: tension
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "directive:D1", "type": "tension"}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations/0/code" is "tension_duplicated"
    And the field at "/violations/0/pointer" is "/to"
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D2", "to": "directive:D3", "type": "tension"}
      """
    Then the response status is 201

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

  # FR-005 (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9): a substep's delegation candidate needs a scope edge from its
  # step's action, so the last such edge is not removed while the substep delegates to the candidate, and nothing is
  # written. The control removes a registered scope edge from the same action that no substep needs.
  Scenario: Removing the last scope edge a substep's delegation candidate needs is refused, naming the edge at /to
    Given the org pack "qa" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose one step delegates to the tactic t.
      steps:
      - id: a
        title: A
        substeps:
        - id: apply
          description: Apply the tactic
          delegates_to:
            kind: tactic
            candidates:
            - t
      """
    And the org pack "qa" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    And the org pack "qa" with the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes: []
      edges:
      - source: action:rx/a
        target: tactic:t
        relation: scope
      - source: action:rx/a
        target: directive:D2
        relation: scope
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/relationships?from=action:rx/a&to=tactic:t" with the header "If-Match": "*"
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations" holds exactly 1 item(s)
    And the field at "/violations/0/code" is "substep_delegation_unscoped"
    And the field at "/violations/0/pointer" is "/to"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I DELETE "/packs/qa/relationships?from=action:rx/a&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 204

  # #605 (operator ruling of 2026-11-18): a removal is judged on the whole pack it leaves, with the registration's full
  # read. A step enhancement needs exactly one enhances edge, so deleting its only one, held in registered.graph.yaml, is
  # refused at the enhancement's file and nothing is written. The control removes a registered edge no rule needs, on
  # the same fixture.
  Scenario: Removing a step enhancement's only enhances edge is refused at its file, and an edge no rule needs is removed
    Given the org pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the org pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "base" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges:
      - source: mission_type:software-dev
        target: action:software-dev/plan
        relation: requires
      """
    And the org pack "enhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E1
      pack_version: 1.0.0
      parent_pack: null
      name: enhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "enhancer" with the file "directives/D1.directive.yaml":
      """
      schema_version: "1.0"
      id: D1
      title: D1
      intent: Intent of D1
      enforcement: required
      """
    And the org pack "enhancer" with the file "directives/D2.directive.yaml":
      """
      schema_version: "1.0"
      id: D2
      title: D2
      intent: Intent of D2
      enforcement: required
      """
    And the org pack "enhancer" with the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      - source: directive:D1
        target: directive:D2
        relation: requires
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/enhancer/relationships?from=step_enhancement:spdd-plan&to=base::action:software-dev/plan" with the header "If-Match": "*"
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations" holds exactly 1 item(s)
    And the field at "/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I DELETE "/packs/enhancer/relationships?from=directive:D1&to=directive:D2" with the header "If-Match": "*"
    Then the response status is 204

  # bound by: the landing fix pass of slice F (operator ruling DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6)
  Scenario: A mission type whose substeps delegate is removed with its step links and its scope edges
    Operator rulings DM-01M7CF9PA1V2DS0P7ZZVANJ1WZ and DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6: the type's own requires edges
    to its steps and the scope edges from its own step actions, bare or written with the pack's own name, go with it
    from the author-written document that holds them; the document keeps every other edge.
    Given the org pack "qa" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose two steps delegate to the tactic t.
      steps:
      - id: a
        title: A
        substeps:
        - id: apply
          description: Apply the tactic
          delegates_to:
            kind: tactic
            candidates:
            - t
      - id: b
        title: B
        substeps:
        - id: check
          description: Check with the tactic
          delegates_to:
            kind: tactic
            candidates:
            - qa::t
      """
    And the org pack "qa" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: mission_type:rx
        target: action:rx/b
        relation: requires
      - source: action:rx/a
        target: tactic:t
        relation: scope
      - source: qa::action:rx/b
        target: qa::tactic:t
        relation: scope
      - source: tactic:t
        target: directive:D2
        relation: requires
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/mission_type/rx" with the remembered ETag
    Then the response status is 204
    And the pack "qa" holds no file "missions/rx/rx.mission-type.yaml"
    And the file "mission_type.graph.yaml" of the pack "qa" does not contain "action:rx"
    And the file "mission_type.graph.yaml" of the pack "qa" does not contain "mission_type:rx"
    And the file "mission_type.graph.yaml" of the pack "qa" contains "directive:D2"
    When I GET "/packs/qa/artifacts/mission_type/rx"
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  # bound by: review cycle 3 of the slice F landing (operator ruling DM-01M7F1ARDGCHPSB62QNY0EZW7Y)
  # The scope edges are seeded in registered.graph.yaml, as in the FR-005 scenario above: putPackRelationship refuses
  # an action source that the pack holds no artifact for, and a pack-qualified source, so no PUT reaches this state.
  Scenario: A delegating mission type is removed with the registered scope edges from its own step actions
    Operator ruling DM-01M7F1ARDGCHPSB62QNY0EZW7Y: registered.graph.yaml follows the same rule as the author-written
    documents, so the registered scope edges from the type's own step actions, bare or written with the pack's own
    name, go with it, and the shard keeps every other edge.
    Given the org pack "qa" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose two steps delegate to the tactic t.
      steps:
      - id: a
        title: A
        substeps:
        - id: apply
          description: Apply the tactic
          delegates_to:
            kind: tactic
            candidates:
            - t
      - id: b
        title: B
        substeps:
        - id: check
          description: Check with the tactic
          delegates_to:
            kind: tactic
            candidates:
            - t
      """
    And the org pack "qa" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: mission_type:rx
        target: action:rx/b
        relation: requires
      """
    And the org pack "qa" with the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes: []
      edges:
      - source: action:rx/a
        target: tactic:t
        relation: scope
      - source: qa::action:rx/b
        target: tactic:t
        relation: scope
      - source: tactic:t
        target: directive:D2
        relation: requires
      """
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa/artifacts/mission_type/rx" with the remembered ETag
    Then the response status is 204
    And the pack "qa" holds no file "missions/rx/rx.mission-type.yaml"
    And the file "registered.graph.yaml" of the pack "qa" does not contain "action:rx"
    And the file "registered.graph.yaml" of the pack "qa" contains "directive:D2"
    And the file "mission_type.graph.yaml" of the pack "qa" does not contain "mission_type:rx"
    When I GET "/packs/qa/artifacts/mission_type/rx"
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: A delegating mission type that another authored edge still names is not removed, and nothing is rewritten
    Control for DM-01M7DTNBMQ6Y88RKJWD3ZFH6G6: only the type's own step links and the scope edges from its own step
    actions go with it. An applies edge from its action is another authored reference, so the DELETE refuses 409 and
    the pack is byte-identical.
    Given the org pack "qa" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates to the tactic t.
      steps:
      - id: a
        title: A
        substeps:
        - id: apply
          description: Apply the tactic
          delegates_to:
            kind: tactic
            candidates:
            - t
      """
    And the org pack "qa" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: action:rx/a
        target: tactic:t
        relation: scope
      - source: action:rx/a
        target: directive:D2
        relation: applies
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/qa/artifacts/mission_type/rx" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "artifact_referenced"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/qa/artifacts/mission_type/rx"
    Then the response status is 200

  Scenario Outline: A relationship removal whose ends are malformed or missing is a 400 Problem without a code that says why (<query>)
    #411: the detail names the query parameter, and the value it carried when it carried one.
    When I DELETE "/packs/qa/relationships?<query>" with the header "If-Match": "*"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "<detail>"

    Examples:
      | query                         | detail                                                                                                      |
      | from=D1&to=directive:D2       | The query parameter from is \"D1\", which is not in the form this parameter accepts. |
      | from=directive:D1             | The query parameter to is missing, and it is required.                                                      |
