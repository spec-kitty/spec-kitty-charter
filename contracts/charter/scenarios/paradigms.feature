@contract @paradigms
Feature: Paradigms are read and registered functionally
  A client reads a pack's paradigms with their relationships populated (FR-014) through the generic
  artifact item and registers a paradigm (POST /packs/{packId}/artifacts/paradigm, PUT
  /packs/{packId}/artifacts/paradigm/{artifactId}), each write answering the artifact in the envelope the
  item read answers, through functional registration (ADR-0026): the whole pack aggregate is revalidated and
  committed atomically, a refusal leaves the pack unchanged (NFR-002), and every write answers an ETag
  revision for optimistic concurrency (FR-012). A paradigm originates relationships — its directive refs
  are minted as requires edges to directives and its typed references as suggests — so a read carries
  them and a walk resolves the referenced directives on demand (US2, SC-002). There is no DELETE or PATCH.

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

  Scenario: A registered paradigm is read back value-equal with its directive relationships (FR-014, SC-001)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward", "directiveRefs": ["ALPHA"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 200
    And the field "id" of the response body is "clean-architecture"
    And the field at "/paradigm/name" is "Clean Architecture"
    And the field at "/paradigm/summary" is "Dependencies point inward"
    And the artifact in the response body has 1 relationship
    And a relationship of the artifact in the response body points to "directive:ALPHA"

  Scenario: Read and walk resolve the paradigm's referenced directives on demand (US2, SC-002)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward", "directiveRefs": ["ALPHA", "BETA"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 200
    And the artifact in the response body has 2 relationships
    And a relationship of the artifact in the response body points to "directive:ALPHA"
    And a relationship of the artifact in the response body points to "directive:BETA"

  Scenario: A duplicate id is a conflict on POST while a distinct id is created (FR-014, SC-003)
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward"}
      """
    Then the response status is 201
    And the response header "ETag" appears once
    And the path of the Location header is the href of the link "self"
    And the response body is:
      """
      {
        "kind": "paradigm",
        "id": "clean-architecture",
        "packId": "qa",
        "paradigm": {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward", "directiveRefs": [], "references": [], "relationships": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/paradigm/clean-architecture"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=paradigm"}
        }
      }
      """
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean again", "summary": "inward"}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "hexagonal", "schemaVersion": "1.0", "name": "Hexagonal", "summary": "ports and adapters"}
      """
    Then the response status is 201

  Scenario: An unknown relationship endpoint kind is refused and a well-formed registration succeeds (FR-014, SC-003)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean", "summary": "inward", "relationships": [{"from": "paradigm:clean-architecture", "to": "nonsense:x", "type": "requires"}]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean", "summary": "inward"}
      """
    Then the response status is 201

  Scenario: A paradigm body may assert only its own edges; a cross-authored edge is refused (dual-write contention)
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward", "relationships": [{"from": "paradigm:other", "to": "directive:ALPHA", "type": "requires"}]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward"}
      """
    Then the response status is 201

  Scenario: A stale If-Match is refused with 412 and a fresh one succeeds (FR-012)
    When I POST "/packs/qa/artifacts/paradigm" as JSON with the body:
      """
      {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward"}
      """
    Then the response status is 201
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with If-Match "sha256:0000000000000000000000000000000000000000000000000000000000000000" and the body:
      """
      {"schemaVersion": "1.0", "name": "Clean replaced", "summary": "inward"}
      """
    Then the response status is 412
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "name": "Clean replaced", "summary": "inward"}
      """
    Then the response status is 200

  Scenario Outline: A null list, or a null item in a list, is refused before the body is bound (#292, #309: <case>)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward"<fields>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "<detail>"
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 404

    Examples:
      | case        | fields                             | detail                                                                                                                |
      | a null list | , "directiveRefs": null            | The directiveRefs list is null; a list is sent as an array, [] when it is empty, and an optional one may be left out. |
      | a null item | , "directiveRefs": ["ALPHA", null] | Item 1 of the directiveRefs list is null; a list holds no null item.                                                  |

  Scenario: A paradigm registration body over 1 MiB is refused before it is read (#310)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body, its "summary" padded to 1048576 bytes:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward"}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The paradigm registration body is larger than 1048576 bytes."
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body, its "summary" padded to 1048000 bytes:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward"}
      """
    Then the response status is 201
