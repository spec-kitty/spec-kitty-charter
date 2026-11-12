@contract @directives
Feature: Directives are read and registered functionally
  A client reads a pack's directives (FR-006) through the generic artifact item, and registers a directive (POST /packs/{packId}/artifacts/directive, PUT
  /packs/{packId}/artifacts/directive/{artifactId}) or a relationship through functional registration
  (ADR-0026): the whole pack aggregate is revalidated and committed atomically, a refusal leaves the pack
  unchanged (NFR-002), and every write answers an ETag revision for optimistic concurrency (FR-012) and the
  artifact in the envelope the item read answers. There is no DELETE or PATCH. A directive carries no relationship
  field: a relationship is registered only through PUT /packs/{packId}/relationships and read through the
  directive's references (ADR-0064), and a body that still sends one is refused.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      pack_drg: drg/fragment.yaml
      """

  Scenario: A registered directive is read back value-equal, with no relationship field (FR-006, SC-001, ADR-0064)
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required", "integrityRules": ["The gate runs before the merge."]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    And the field "id" of the response body is "GUARD"
    And the field at "/directive/title" is "Guard the gate"
    And the field at "/directive/integrityRules/0" is "The gate runs before the merge."
    And the field at "/directive/procedures" is absent
    And the field at "/directive/relationships" is absent
    And the field at "/directive/references" is absent

  Scenario: A directive's relationship is registered through the relationship write and read through its references (ADR-0064)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD/references"
    Then the response status is 200
    And the field at "/items/0" is absent
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:GUARD", "to": "tactic:rehearse", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "tactic:rehearse"
    And the field at "/items/0/relation" is "suggests"
    And the field at "/items/1" is absent

  Scenario: A duplicate id is a conflict on POST while a distinct id is created (FR-008)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    And the response header "ETag" appears once
    And the path of the Location header is the href of the link "self"
    And the response body is:
      """
      {
        "kind": "directive",
        "id": "GUARD",
        "packId": "qa",
        "directive": {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required", "scope": null, "integrityRules": [], "validationCriteria": [], "explicitAllowances": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/directive/GUARD"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=directive"}
        }
      }
      """
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard again", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "CHECK", "schemaVersion": "1.0", "title": "Check", "intent": "check", "enforcement": "required"}
      """
    Then the response status is 201

  Scenario Outline: A body that still sends a removed relationship field is refused, naming it, and nothing is written (ADR-0064)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required", "<member>": <value>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "A directive registration has no member <member>; a relationship is registered through PUT /packs/{packId}/relationships."
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 404
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201

    Examples:
      | member        | value                                                             |
      | relationships | [{"from": "directive:GUARD", "to": "tactic:x", "type": "requires"}] |
      | relationships | []                                                                |
      | references    | [{"type": "tactic", "id": "x"}]                                   |
      | procedures    | ["Run the gate before the merge."]                                |

  Scenario: A relationship is idempotent on the unordered pair (FR-009)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:GUARD", "to": "procedure:p", "type": "requires"}
      """
    Then the response status is 201
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:GUARD", "to": "procedure:p", "type": "suggests"}
      """
    Then the response status is 200

  Scenario: A stale If-Match is refused with 412 and a fresh one succeeds (FR-012)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I PUT "/packs/qa/artifacts/directive/GUARD" with If-Match "sha256:0000000000000000000000000000000000000000000000000000000000000000" and the body:
      """
      {"schemaVersion": "1.0", "title": "Guard replaced", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 412
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "title": "Guard replaced", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 200

  Scenario: A relationship write with no precondition is refused with 428 (FR-012)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201
    When I PUT "/packs/qa/relationships" with the body:
      """
      {"from": "directive:GUARD", "to": "procedure:p", "type": "requires"}
      """
    Then the response status is 428

  Scenario Outline: A null list, or a null item in a list, is refused before the body is bound (#292, #309: <case>)
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"<fields>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "<detail>"
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 404

    Examples:
      | case        | fields                               | detail                                                                                                                 |
      | a null list | , "integrityRules": null             | The integrityRules list is null; a list is sent as an array, [] when it is empty, and an optional one may be left out. |
      | a null item | , "integrityRules": ["rule-1", null] | Item 1 of the integrityRules list is null; a list holds no null item.                                                  |

  Scenario: A directive registration body over 1 MiB is refused before it is read (#310)
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body, its "intent" padded to 1048576 bytes:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The directive registration body is larger than 1048576 bytes."
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body, its "intent" padded to 1048000 bytes:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 201

  Scenario: A write that would leave a preset naming the id only in another case is refused before the commit (#412)
    IC-15, operator ruling DM-01M6VG6GC40AKYYFRR35SV8HVW: a per-artifact write is judged over the closures its pack
    joins as the pack will be after it. The preset names directive guard, so holding GUARD would leave an id matched
    only by case, which the compose refuses. The write is refused with the compose's code, nothing is stored and the
    offering still composes; an id the preset does not name is written.
    Given the org pack "qa" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A preset of the team
      activated_directives:
        - id: guard
      """
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required"}
      """
    Then the response status is 422
    And the problem code is "pack_reference_artifact_missing"
    And the field at "/packs/0/packId" is "qa"
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 404
    When I GET "/offering"
    Then the response status is 200
    When I PUT "/packs/qa/artifacts/directive/SHIELD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Shield the gate", "intent": "Shield it", "enforcement": "required"}
      """
    Then the response status is 201
