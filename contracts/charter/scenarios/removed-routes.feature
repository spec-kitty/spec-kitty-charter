@contract @removed-routes
Feature: The per-kind routes are gone, with no alias
  A pack's artifacts are read through the generic list and item, and the writable kinds are registered
  under the same tree: POST /packs/{packId}/artifacts/<kind> and PUT /packs/{packId}/artifacts/<kind>/{artifactId}
  for directive, styleguide, paradigm and toolguide (FR-005). Every per-kind route of the earlier contract,
  /packs/{packId}/{tactics,directives,styleguides,paradigms,toolguides} and the same with an id, is removed
  without an alias (FR-006): it answers the 404 of a path no operation defines, a Problem without a code,
  even with a body a registration would accept. A method a path of the tree does not define is a 405 Problem
  without a code: a GET on a write path, and a PUT on the item of a kind that has no write (a tactic), which
  is not kind_not_found, since the path names a served kind.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
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

  Scenario Outline: A removed per-kind read answers 404 without a code (<path>)
    When I GET "<path>"
    Then the response status is 404
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | path                    |
      | /packs/qa/tactics       |
      | /packs/qa/tactics/x     |
      | /packs/qa/directives    |
      | /packs/qa/directives/x  |
      | /packs/qa/styleguides   |
      | /packs/qa/styleguides/x |
      | /packs/qa/paradigms     |
      | /packs/qa/paradigms/x   |
      | /packs/qa/toolguides    |
      | /packs/qa/toolguides/x  |

  Scenario Outline: A removed per-kind write answers 404 without a code, whatever its body (<method> <path>)
    The body is one the write's successor accepts, so the 404 is the routing answer, not a refusal of the body.
    When I send <method> "<path>" as JSON with the body:
      """
      <body>
      """
    Then the response status is 404
    And the problem has no code
    When I GET "/packs/qa/artifacts?kind=<kind>"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts?kind=<kind>"},
          "first": {"href": "/api/v1/packs/qa/artifacts?kind=<kind>"}
        }
      }
      """

    Examples:
      | method | path                    | kind       | body                                                                                                                                         |
      | POST   | /packs/qa/directives    | directive  | {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}                                      |
      | POST   | /packs/qa/styleguides   | styleguide | {"id": "java-style", "schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}                  |
      | POST   | /packs/qa/paradigms     | paradigm   | {"id": "clean", "schemaVersion": "1.0", "name": "Clean", "summary": "inward"}                                                                |
      | POST   | /packs/qa/toolguides    | toolguide  | {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\\n\\nUse git.", "summary": "Using git."} |
      | PUT    | /packs/qa/directives/x  | directive  | {"schemaVersion": "1.0", "title": "Guard", "intent": "guard", "enforcement": "required"}                                                     |
      | PUT    | /packs/qa/styleguides/x | styleguide | {"schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Prefer immutability"]}                                     |
      | PUT    | /packs/qa/paradigms/x   | paradigm   | {"schemaVersion": "1.0", "name": "Clean", "summary": "inward"}                                                                               |
      | PUT    | /packs/qa/toolguides/x  | toolguide  | {"schemaVersion": "1.0", "tool": "x", "title": "How to use x", "guide": "## Overview\\n\\nUse x.", "summary": "Using x."}                     |

  Scenario: A GET on a write path is a 405 Problem without a code
    When I GET "/packs/qa/artifacts/directive"
    Then the response status is 405
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A PUT on the item of a kind that has no write is a 405 Problem without a code, not kind_not_found
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 200
    When I send PUT "/packs/qa/artifacts/tactic/rehearse" as JSON with the body:
      """
      {"schemaVersion": "1.0", "name": "Rehearse", "steps": [{"title": "Only step"}]}
      """
    Then the response status is 405
    And the problem has no code
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 200
    And the field at "/tactic/name" is "Rehearse"
