@contract @paradigms
Feature: Paradigms are read and registered functionally
  A client reads a pack's paradigms (FR-014) through the generic artifact item and registers a paradigm (POST /packs/{packId}/artifacts/paradigm, PUT
  /packs/{packId}/artifacts/paradigm/{artifactId}), each write answering the artifact in the envelope the
  item read answers, through functional registration (ADR-0026): the whole pack aggregate is revalidated and
  committed atomically, a refusal leaves the pack unchanged (NFR-002), and every write answers an ETag
  revision for optimistic concurrency (FR-012). A paradigm carries no relationship field: its relationships
  to directives and other artifacts are registered through PUT /packs/{packId}/relationships and read
  through the paradigm's references (ADR-0064), and a body that still sends one is refused. There is no
  DELETE or PATCH.

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

  Scenario: A registered paradigm is read back value-equal, with no relationship field (FR-014, SC-001, ADR-0064)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 200
    And the field "id" of the response body is "clean-architecture"
    And the field at "/paradigm/name" is "Clean Architecture"
    And the field at "/paradigm/summary" is "Dependencies point inward"
    And the field at "/paradigm/directiveRefs" is absent
    And the field at "/paradigm/relationships" is absent

  Scenario: A paradigm's directives are related through the relationship write and read through its references (US2, ADR-0064)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean Architecture", "summary": "Dependencies point inward"}
      """
    Then the response status is 201
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "paradigm:clean-architecture", "to": "directive:ALPHA", "type": "requires"}
      """
    Then the response status is 201
    When I remember the ETag
    When I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "paradigm:clean-architecture", "to": "directive:BETA", "type": "requires"}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "directive:ALPHA"
    And the field at "/items/0/relation" is "requires"
    And the field at "/items/1/reference" is "directive:BETA"
    And the field at "/items/2" is absent

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
        "paradigm": {"id": "clean-architecture", "schemaVersion": "1.0", "name": "Clean", "summary": "inward", "principles": []},
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

  Scenario Outline: A body that still sends a removed relationship field is refused, naming it, and nothing is written (ADR-0064)
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean", "summary": "inward", "<member>": <value>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "A paradigm registration has no member <member>; a relationship is registered through PUT /packs/{packId}/relationships."
    When I GET "/packs/qa/artifacts/paradigm/clean-architecture"
    Then the response status is 404
    When I PUT "/packs/qa/artifacts/paradigm/clean-architecture" with the body:
      """
      {"schemaVersion": "1.0", "name": "Clean", "summary": "inward"}
      """
    Then the response status is 201

    Examples:
      | member        | value                                                                            |
      | directiveRefs | ["ALPHA"]                                                                        |
      | directiveRefs | null                                                                             |
      | references    | [{"type": "tactic", "id": "x"}]                                                  |
      | relationships | [{"from": "paradigm:clean-architecture", "to": "directive:ALPHA", "type": "requires"}] |

  Scenario: A paradigm's named principles are read back in authored order (ADR-0067)
    When I PUT "/packs/qa/artifacts/paradigm/test-desiderata" with the body:
      """
      {"schemaVersion": "1.0", "name": "Test Desiderata", "summary": "Properties of good tests", "principles": [{"name": "Isolated", "description": "Tests return the same results whatever order they run in."}, {"name": "Fast", "description": "Tests run quickly."}]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/paradigm/test-desiderata"
    Then the response status is 200
    And the field at "/paradigm/principles/0/name" is "Isolated"
    And the field at "/paradigm/principles/1/name" is "Fast"
    And the field at "/paradigm/principles/1/description" is "Tests run quickly."

  Scenario Outline: A principles list that breaks a rule of ADR-0067 is refused and nothing is written, while a valid list is created
    When I PUT "/packs/qa/artifacts/paradigm/test-desiderata" with the body:
      """
      {"schemaVersion": "1.0", "name": "Test Desiderata", "summary": "Properties of good tests", "principles": <principles>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I GET "/packs/qa/artifacts/paradigm/test-desiderata"
    Then the response status is 404
    When I PUT "/packs/qa/artifacts/paradigm/test-desiderata" with the body:
      """
      {"schemaVersion": "1.0", "name": "Test Desiderata", "summary": "Properties of good tests", "principles": [{"name": "Fast", "description": "Tests run quickly."}]}
      """
    Then the response status is 201

    Examples: a repeated name and a blank name break the rules the schema cannot state; an unknown member breaks the closed item
      | principles                                                                                       |
      | [{"name": "Fast", "description": "Quick.", "weight": 1}]                                         |
      | [{"name": "Fast", "description": "Quick."}, {"name": " fast ", "description": "Again."}]          |
      | [{"name": " ", "description": "Blank name."}]                                                    |

  Scenario Outline: A principles body that breaks the Principle schema is a bad request and nothing is written
    When I PUT "/packs/qa/artifacts/paradigm/test-desiderata" with the body:
      """
      {"schemaVersion": "1.0", "name": "Test Desiderata", "summary": "Properties of good tests", "principles": <principles>}
      """
    Then the response status is 400
    When I GET "/packs/qa/artifacts/paradigm/test-desiderata"
    Then the response status is 404

    Examples: an empty list, a missing description and a 65-character name
      | principles                                                                                       |
      | []                                                                                               |
      | [{"name": "Fast"}]                                                                               |
      | [{"name": "nnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn", "description": "65."}] |

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
