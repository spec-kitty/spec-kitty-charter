@contract @activations
Feature: The activation register and the packs an activation builds on
  Core records one project's activation document on the service and reads it back (ADR-0020 D3-D6): POST
  mints an id, PUT stores under the client's id, both in canonical form under a revision, GET answers the
  stored document with the quoted revision as its ETag, and DELETE removes it. If-Match makes a write
  conditional and without it the last writer wins; If-None-Match: * creates only. The answer depends only on
  the stored document (ADR-0059 D5), so a document an offering change made stale is served as stored (ADR-0059
  D4). A PUT is judged against the document it replaces (A4): what the packs no longer satisfy in it is dropped
  and reported, not refused, so a GET then PUT of the same body never fails without a pack change. The closure read lists the packs the document builds on: the depends_on
  closure of its packs, in dependency order, in the offering's pack shape. Absent means none (ADR-0053 d10)
  for every selected kind; an absent packs means every configured pack.

  Background:
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/DIRECTIVE_001.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_001
      title: First directive
      intent: Do the first thing
      enforcement: required
      """
    And the org pack "alpha" with the file "directives/DIRECTIVE_002.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_002
      title: Second directive
      intent: Do the second thing
      enforcement: required
      """

  # User Story 1: record and read an activation document.

  Scenario: A created document is stored sorted and deduplicated, under its quoted revision
    US1 AS1: the stored document lists the selected ids once, in code-point order, and the ETag is the
    revision in double quotes.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002", "DIRECTIVE_001", "DIRECTIVE_002"]}}
      """
    Then the response status is 201
    And the field at "/activationId" is "a-1"
    And the field at "/config/selected/directive" holds exactly 2 items
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"
    And the field at "/config/selected/directive/1" is "DIRECTIVE_002"
    And the response header "ETag" is the quoted field "/revision"
    And the path of the Location header is the href of the link "self"

  Scenario: POST stores the document under an id the service mints
    ADR-0020 D3: the service mints the id; Location names the new activation and the ETag is its revision.
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 201
    And the field at "/config/schemaVersion" is 2
    And the response header "ETag" is the quoted field "/revision"
    And the path of the Location header is the href of the link "self"

  Scenario: A fetched config registered unchanged answers 200 with the same ETag
    US1 AS2, the round trip (ADR-0020 D6). getActivation's ETag is the closure tag (operator decision
    01M6Y11E55YP5TG1EJ1MDKCFRP), not the revision: the GET tag differs from the stored revision, a PUT may carry it as
    If-Match (decision 01M6Y3GZFRC5WEQQ7G6GT0YMW3), and the write answers the unchanged revision as its ETag.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the field at "/revision" is the stored revision
    When I remember the ETag
    And I remember the header "ETag"
    And I remember the field "revision" of the response body
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field "revision" of the response body is the remembered one
    And the response header "ETag" differs from the remembered header

  Scenario: A stale If-Match is refused and the current revision is kept
    US1 AS3: the If-Match names the first revision after another write replaced it.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    And I remember the ETag
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 200
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": []}}
      """
    Then the response status is 412
    And the problem has no code
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/revision" is the remembered header "ETag" without its quotes
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"

  Scenario: Without If-Match the last writer wins
    US1 AS4 (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 200
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"
    And the response header "ETag" is the quoted field "/revision"

  Scenario: Every known spelling of an id stores the declared id under one revision
    ADR-0020 D4 (operator ruling of 2026-11-06): the full URN and a case variant normalize to the declared id.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 201
    When I remember the header "ETag"
    And I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["directive:DIRECTIVE_001", "directive_001", "DIRECTIVE:Directive_001"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"
    And the response header "ETag" is the remembered header
    When I GET "/activations/a-2"
    Then the field at "/config/selected/directive/0" is "DIRECTIVE_001"
    And the field at "/revision" is the remembered header "ETag" without its quotes

  Scenario: A deny entry spelled as a URN is judged as the declared id
    Given the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The first directive is required.
      activated_directives:
        - id: DIRECTIVE_001
          must_have: true
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["directive:directive_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"

  Scenario: A URN of another kind is an unknown id
    No kind's id grammar admits two ids that differ only by case, so the ambiguous refusal is pinned in DocumentRulesTest.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["tactic:DIRECTIVE_001"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive/0"
    And the field at "/violations/0/code" is "unknown_artifact"

  Scenario: An id no pack of the closure holds is refused, naming the id, and nothing is stored
    US1 AS5.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001", "NO_SUCH_DIRECTIVE"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive/1"
    And the field at "/violations/0/code" is "unknown_artifact"
    When I GET "/activations/a-1"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A deny entry on a must-have item of a governing preset is refused, naming the pack
    US1 AS6: alpha's governing preset requires DIRECTIVE_001, so the document cannot deactivate it.
    Given the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The first directive is required.
      activated_directives:
        - id: DIRECTIVE_001
          must_have: true
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "alpha"
    When I GET "/activations/a-1"
    Then the response status is 404

  Scenario: A child pack's preset takes the place of its parent's, and its must-have item stays required
    The operator ruling of 2026-11-06: the parent_pack is an ordering edge, so "aaa-child", which sorts before
    "zzz-parent", still merges after it, and its copy of the preset "team" is the governing one. Its must-have
    item CHILD_ONLY is required and cannot be denied; the parent's losing copy requires nothing, so PARENT_ONLY can.
    Given the org pack "zzz-parent" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: parent-pack
      pack_level: org
      """
    And the org pack "zzz-parent" with the file "directives/PARENT_ONLY.directive.yaml":
      """
      schema_version: '1.0'
      id: PARENT_ONLY
      title: The parent's directive
      intent: Do the parent's thing
      enforcement: required
      """
    And the org pack "zzz-parent" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The parent's copy.
      activated_directives:
        - id: PARENT_ONLY
          must_have: true
      """
    And the org pack "aaa-child" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943T
      name: child-pack
      pack_level: org
      """
    And the org pack "aaa-child" with the file "directives/CHILD_ONLY.directive.yaml":
      """
      schema_version: '1.0'
      id: CHILD_ONLY
      title: The child's directive
      intent: Do the child's thing
      enforcement: required
      """
    And the org pack "aaa-child" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The child's copy.
      activated_directives:
        - id: CHILD_ONLY
          must_have: true
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "aaa-child"}], "deactivated": {"directive": ["CHILD_ONLY"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "aaa-child"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "aaa-child"}], "deactivated": {"directive": ["PARENT_ONLY"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1/packs"
    Then the response status is 200
    And the field at "/packs/0/packId" is "zzz-parent"
    And the field at "/packs/1/packId" is "aaa-child"

  Scenario: A preset item that names a held id only by case refuses the closure
    The operator ruling of 2026-11-06: ids are matched exactly, so a must-have item "directive_001" would govern
    nothing and a deny entry on DIRECTIVE_001 would pass. The compose is refused instead, as pack_reference_artifact_missing.
    Given the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The first directive is required, misspelled.
      activated_directives:
        - id: directive_001
          must_have: true
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"
    And the field at "/packs/0/packId" is "alpha"
    When I GET "/activations/a-1"
    Then the response status is 404

  Scenario Outline: A preset item whose id is outside the id grammar refuses the pack as pack_unreadable (<spelling>)
    The operator ruling of 2026-11-07: an item id is spelled with the characters a policy key allows, never as a URN,
    a pack-qualified reference or with stray whitespace. Such an item would match no held id, so a must-have one put
    nothing on the floor and a deny entry on DIRECTIVE_001 answered 201; the preset file is refused instead.
    Given the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: An item spelled outside the id grammar.
      activated_directives:
        - id: "<id>"
          must_have: <must>
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"

    Examples:
      | spelling       | id                        | must  |
      | URN            | directive:DIRECTIVE_001   | true  |
      | URN upper case | DIRECTIVE:directive_001   | true  |
      | trailing space | DIRECTIVE_001\x20           | true  |
      | qualified      | alpha-pack::DIRECTIVE_001 | false |

  Scenario: A policy lock that names a held id only by case refuses the closure
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:directive_001: locked
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"

  Scenario: A policy lock on an id no pack of the closure holds changes no write
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:NOT_HELD: locked
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 201

  Scenario: An empty deny list is refused, never stored
    US1 AS7.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": []}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A second DELETE finds nothing
    US1 AS8 (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2}
      """
    When I send DELETE "/activations/a-1"
    Then the response status is 204
    When I send DELETE "/activations/a-1"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: If-None-Match * creates only
    US1 AS9 (ADR-0020 D3).
    When I PUT "/activations/a-1" with the header "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 201
    When I PUT "/activations/a-1" with the header "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 412

  Scenario: A read whose If-None-Match matches the ETag answers 304 without a body
    FR-002, ADR-0059 D5 as amended (operator decision 01M6Y11E55YP5TG1EJ1MDKCFRP): the ETag of the read is the closure
    tag, so the tag the read answered is the one that matches; the revision, which a write answers, is not the read's
    tag and answers 200 (the control).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/activations/a-1" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    When I GET "/activations/a-1" with If-None-Match the stored ETag
    Then the response status is 200
    And the response header "ETag" is the remembered header

  Scenario: Reading an unknown activation answers 404
    FR-002 (R7).
    When I GET "/activations/no-such-activation"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: Both validators on one request answer 400
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2}
      """
    When I PUT "/activations/a-1" with the headers "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 400

  Scenario: An If-None-Match other than * on a write answers 400
    J10: a malformed precondition.
    When I PUT "/activations/a-1" with the header "If-None-Match": "\"x\"" and the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 400

  Scenario: A body over 1 MiB is refused as too large
    #323: the cap is judged before the document.
    When I PUT "/activations/a-1" with the body, its "padding" padded to 1048576 bytes:
      """
      {"schemaVersion": 2, "padding": ""}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/code" is "body_too_large"

  Scenario: A member the document does not declare is refused
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "favourite": "DIRECTIVE_001"}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/favourite"
    And the field at "/violations/0/code" is "unknown_member"

  Scenario: A member stated twice is refused, never read last-wins
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"], "directive": []}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive"
    And the field at "/violations/0/code" is "duplicate_member"
    When I GET "/activations/a-1"
    Then the response status is 404

  Scenario Outline: A scalar of another type than the declared one is refused, never coerced
    When I PUT "/activations/a-1" with the body:
      """
      <body>
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "<pointer>"
    And the field at "/violations/0/code" is "unreadable_body"
    When I GET "/activations/a-1"
    Then the response status is 404

    Examples:
      | body                                                          | pointer                 |
      | {"schemaVersion": "1"}                                        | /schemaVersion          |
      | {"schemaVersion": 2.0}                                        | /schemaVersion          |
      | {"schemaVersion": 2, "selected": {"directive": [1, true]}}    | /selected/directive/0   |

  Scenario: A null list is refused
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": null}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A null item in a list is refused at its index
    #125: null is never a value, at any depth; nothing is stored.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001", null]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive/1"
    And the field at "/violations/0/code" is "null_value"
    When I GET "/activations/a-1"
    Then the response status is 404

  Scenario: A pack pinned to a version the service does not hold is dropped by a PUT and refused by a POST
    R2: alpha is held at 1.0.0. Rewritten for B1 (decision DM-01M6RN5Y406SB4KA13QYMQ2P8J): a PUT drops a pin no pack
    holds and lists it in droppedPins, so the stored document builds on alpha as held; it was 422 version_not_held.
    createActivation keeps the refusal for a pin in its own body, and its violation tells the client both versions.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "alpha", "version": "2.0.0"}]}
      """
    Then the response status is 201
    And the field at "/droppedPins/0/packId" is "alpha"
    And the field at "/droppedPins/0/version" is "2.0.0"
    And the field at "/droppedPins" holds exactly 1 item
    And the field at "/config/packs/0/packId" is "alpha"
    And the response body has no field at "/config/packs/0/version"
    And the response body has no field at "/dropped"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "alpha", "version": "2.0.0"}]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/packs/0/version"
    And the field at "/violations/0/code" is "version_not_held"
    And the field at "/violations/0/detail" is "The pack 'alpha' is held at 1.0.0, not 2.0.0."

  Scenario: A pack pinned to a blank version is refused at the pin
    A version of only blanks is no version: the contract's pattern refuses it, and so does the service, 422.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "alpha", "version": "  "}]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/packs/0/version"
    And the field at "/violations/0/code" is "invalid_value"

  Scenario: A document naming an unconfigured pack is refused at the pack's id
    R4.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "no-such-pack"}]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/packs/0/packId"

  # The stale documents of the Edge Cases (FR-017, ADR-0059 D4).

  Scenario: A document naming an id the pack no longer holds is served as stored, and a PUT of it drops the id
    ADR-0059 D4 on read. Rewritten for A4 (decision DM-01M6RA7XC5HC8915GZT2R3H83W): a PUT of the body a GET answered
    drops the id the replaced document named and the pack no longer holds, and lists it in dropped; it was 422. An
    unknown id the replaced document did not name is still refused (the negative control).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    When I remember the header "ETag"
    And the file "directives/DIRECTIVE_002.directive.yaml" of the pack "alpha" is deleted
    And I GET "/activations/a-1" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 200
    And the field at "/dropped" holds exactly the ids:
      """
      directive:DIRECTIVE_002
      """
    And the response body has no field at "/droppedPins"
    And the response body has no field at "/config/selected"
    And the field at "/config/schemaVersion" is 2
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive/0"

  Scenario: A deny entry on an id a pack now locks is kept on read and by a PUT, and a new one is refused
    Rewritten for A4 (decision DM-01M6RA7XC5HC8915GZT2R3H83W): a PUT judges the floor on the deny entries it adds
    only, so re-sending the stored deny entry is 200 and keeps it; it was 409. The same deny entry on another
    activation, where it is new, is 409 artifact_locked (the negative control).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:DIRECTIVE_001: locked
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/deactivated/directive/0" is "DIRECTIVE_001"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/config/deactivated/directive/0" is "DIRECTIVE_001"
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "alpha"
    When I GET "/activations/a-2"
    Then the response status is 404

  Scenario: A deny entry on an id a pack now requires is kept on read and by a PUT, and a new one is refused
    ADR-0059 D4: alpha's governing preset arrives after the document was stored. Rewritten for A4 (decision
    DM-01M6RA7XC5HC8915GZT2R3H83W): the PUT re-sends the stored deny entry, which it does not add, so it is 200 with
    the same ETag and stores nothing new; it was 409. On another activation the same deny entry is new: 409.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    And the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The first directive is required.
      activated_directives:
        - id: DIRECTIVE_001
          must_have: true
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/deactivated/directive/0" is "DIRECTIVE_001"
    When I remember the ETag
    And I remember the header "ETag"
    And I remember the field "revision" of the response body
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field "revision" of the response body is the remembered one
    And the field at "/config/deactivated/directive/0" is "DIRECTIVE_001"
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "alpha"
    When I GET "/activations/a-1"
    Then the response status is 200
    And the response header "ETag" is the remembered header

  # User Story 2: the packs an activation builds on.

  Scenario: The closure read lists the activated pack after its dependency, and not an unrelated pack
    US2 AS1: alpha depends on beta; gamma is available and unrelated.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 2 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/1/dependsOn/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/dependsOn/0/range" is ">=1.0"
    And the field at "/packs/1/dependsOn/0/version" is "1.0.0"

  Scenario: A broken pack outside the closure is never judged
    US2 AS2: gamma's pack.yaml is not YAML, and the alpha activation's closure does not hold gamma.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: [not yaml
      """
    And the project activates the pack "alpha"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 2 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"

  Scenario: A document without packs builds on every configured pack, in the offering's order
    US2 AS3.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the activation "project" holds:
      """
      {"schemaVersion": 2}
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/2/packId" is "gamma"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 3 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/2/packId" is "gamma"

  Scenario: A closure that does not compose is refused as the offering refuses it
    US2 AS4: the activation is stored, then alpha's range stops admitting the version beta holds.
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.9.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the project activates the pack "alpha"
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '==2.0.0'
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_dependency_out_of_range"
    And the field at "/packs/0/packId" is "alpha"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A qualified reference to a pack outside the closure refuses the closure read
    R7: alpha writes beta-pack::directive:SHARE, but beta is not in alpha's depends_on closure.
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the project activates the pack "alpha"
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: beta-pack::directive:SHARE
        relation: requires
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field at "/packs/0/packId" is "alpha"

  Scenario: A pack of the closure whose directory is gone refuses the closure read as unreadable
    R4: the dependency is configured, so it is unreadable, not unknown.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    And the directory of the pack "beta" is missing
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A pack of the closure whose pack.yaml is not YAML refuses the closure read as unreadable
    R4.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: [not yaml
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: The closure read of an unknown activation answers 404
    When I GET "/activations/no-such-activation/packs"
    Then the response status is 404
    And the problem code is "activation_not_found"

  # User Story 6: a pack declares locks.

  Scenario: A directive an org pack's policy file locks cannot be deactivated
    US6 AS1 (FR-022, ADR-0063).
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "house" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: locked
      """
    And the org pack "house" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: House rule
      intent: Keep the house rule
      enforcement: required
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "house"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "house"

  Scenario: A lock along the closure wins over a pack nearer built-in that leaves the directive open
    US6 AS2: the built-in pack holds D1 open; the org pack depending on it locks D1.
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: base-pack
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: open
      """
    And the built-in pack "base" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: Base rule
      intent: Keep the base rule
      enforcement: required
      """
    And the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      mutability_policy: policy.yaml
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943W
        range: '>=1.0'
      """
    And the org pack "house" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: locked
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "house"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "house"

  Scenario: A pack naming a policy file that is missing is unreadable on the offering and on the closure read
    US6 AS3.
    Given the project activates the pack "alpha"
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A pack naming a policy file that fails its schema is unreadable on the offering and on the closure read
    US6 AS3 (R7): role locks are reserved, so a roles key makes the file invalid.
    Given the project activates the pack "alpha"
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      roles:
        reviewer: locked
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"
