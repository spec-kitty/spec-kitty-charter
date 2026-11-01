@contract @styleguides
Feature: Styleguides are read and registered functionally
  A client reads a pack's styleguides through the generic artifact item and registers a styleguide (POST
  /packs/{packId}/artifacts/styleguide, PUT /packs/{packId}/artifacts/styleguide/{artifactId}), each write
  answering the artifact in the envelope the item read answers, through functional registration
  (ADR-0026): the whole pack aggregate is revalidated and committed atomically, a refusal leaves the pack
  unchanged (NFR-002), and every write answers an ETag revision for optimistic concurrency (FR-012). A
  styleguide mints no relationships (its references are bare citations), so the relationships array is
  always empty (FR-013). There is no DELETE or PATCH.

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

  Scenario: A registered styleguide is read back value-equal with no relationships (FR-013, SC-001)
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/artifacts/styleguide/java-style"
    Then the response status is 200
    And the field "id" of the response body is "java-style"
    And the field at "/styleguide/title" is "Java style"
    And the field at "/styleguide/scope" is "code"
    And the artifact in the response body has 0 relationships

  Scenario: A duplicate id is a conflict on POST while a distinct id is created (FR-008)
    When I POST "/packs/qa/artifacts/styleguide" as JSON with the body:
      """
      {"id": "java-style", "schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 201
    And the response header "ETag" appears once
    And the path of the Location header is the href of the link "self"
    And the response body is:
      """
      {
        "kind": "styleguide",
        "id": "java-style",
        "packId": "qa",
        "styleguide": {"id": "java-style", "schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"], "patterns": [], "antiPatterns": [], "tooling": [], "qualityTest": null, "structuralLintConfig": null, "appliesToLanguages": [], "references": [], "relationships": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/styleguide/java-style"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=styleguide"}
        }
      }
      """
    When I POST "/packs/qa/artifacts/styleguide" as JSON with the body:
      """
      {"id": "java-style", "schemaVersion": "1.0", "title": "Java style again", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I POST "/packs/qa/artifacts/styleguide" as JSON with the body:
      """
      {"id": "docs-style", "schemaVersion": "1.0", "title": "Docs style", "scope": "docs", "principles": ["Write for the reader"]}
      """
    Then the response status is 201

  Scenario: A malformed body is refused and a well-formed registration succeeds (FR-010, SC-003)
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "nonsense", "principles": ["Prefer immutability"]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 201

  Scenario: A stale If-Match is refused with 412 and a fresh one succeeds (FR-012)
    When I POST "/packs/qa/artifacts/styleguide" as JSON with the body:
      """
      {"id": "java-style", "schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 201
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with If-Match "sha256:0000000000000000000000000000000000000000000000000000000000000000" and the body:
      """
      {"schemaVersion": "1.0", "title": "Java style replaced", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 412
    When I GET "/packs/qa/artifacts/styleguide/java-style"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "title": "Java style replaced", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 200

  Scenario Outline: A null list, or a null item in a list, is refused before the body is bound (#292, #309: <case>)
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]<fields>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "<detail>"
    When I GET "/packs/qa/artifacts/styleguide/java-style"
    Then the response status is 404

    Examples:
      | case        | fields                                  | detail                                                                                                             |
      | a null list | , "references": null                    | The references list is null; a list is sent as an array, [] when it is empty, and an optional one may be left out. |
      | a null item | , "references": ["docs/style.md", null] | Item 1 of the references list is null; a list holds no null item.                                                  |

  Scenario: A styleguide registration body over 1 MiB is refused before it is read (#310)
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body, its "title" padded to 1048576 bytes:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The styleguide registration body is larger than 1048576 bytes."
    When I PUT "/packs/qa/artifacts/styleguide/java-style" with the body, its "title" padded to 1048000 bytes:
      """
      {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}
      """
    Then the response status is 201
