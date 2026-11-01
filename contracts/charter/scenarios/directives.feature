@contract @directives
Feature: Directives are read and registered functionally
  A client reads a pack's directives with their relationships populated (FR-006) through the generic
  artifact item, and registers a directive (POST /packs/{packId}/artifacts/directive, PUT
  /packs/{packId}/artifacts/directive/{artifactId}) or a relationship through functional registration
  (ADR-0026): the whole pack aggregate is revalidated and committed atomically, a refusal leaves the pack
  unchanged (NFR-002), and every write answers an ETag revision for optimistic concurrency (FR-012) and the
  artifact in the envelope the item read answers. There is no DELETE or PATCH.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      active_charter: org-charter.yaml
      pack_drg: drg/fragment.yaml
      """

  Scenario: A registered directive is read back value-equal with its relationships (FR-006, SC-001)
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it", "enforcement": "required", "procedures": ["proc-1"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    And the field "id" of the response body is "GUARD"
    And the field at "/directive/title" is "Guard the gate"
    And the artifact in the response body has 1 relationship
    And a relationship of the artifact in the response body points to "procedure:proc-1"

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
        "directive": {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required", "scope": null, "procedures": [], "integrityRules": [], "validationCriteria": [], "explicitAllowances": [], "references": [], "relationships": []},
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

  Scenario: An unknown relationship endpoint kind is refused and a well-formed registration succeeds (FR-010, SC-003)
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required", "relationships": [{"from": "directive:GUARD", "to": "nonsense:x", "type": "requires"}]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I PUT "/packs/qa/artifacts/directive/GUARD" with the body:
      """
      {"schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}
      """
    Then the response status is 201

  Scenario: A directive body may assert only its own edges; a cross-authored edge uses the relationship endpoint (dual-write contention)
    When I POST "/packs/qa/artifacts/directive" as JSON with the body:
      """
      {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required", "relationships": [{"from": "directive:OTHER", "to": "procedure:p", "type": "requires"}]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
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
      {"from": "directive:GUARD", "to": "directive:OTHER", "type": "suggests"}
      """
    Then the response status is 201

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
      | case        | fields                           | detail                                                                                                             |
      | a null list | , "procedures": null             | The procedures list is null; a list is sent as an array, [] when it is empty, and an optional one may be left out. |
      | a null item | , "procedures": ["proc-1", null] | Item 1 of the procedures list is null; a list holds no null item.                                                  |

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
