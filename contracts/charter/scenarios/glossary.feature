# The glossary reads of a pack (#106, ADR-0049 decision 7, ADR-0074 decisions 3 and 9 to 14; FR-007 to FR-010 and
# FR-012): GET /packs/{packId}/artifacts/glossary lists a pack's glossary contexts in id order with their term counts,
# GET …/glossary/{contextId} reads one context in the Artifact envelope with every term, and
# GET …/glossary/{contextId}/terms/{termId} reads one term. The 401, 403 and 500 answers of the three operations are
# asserted in every-operation.feature and unexpected-failure.feature, and their 400s in read-refusals.feature too.

@contract @artifacts
Feature: A pack's glossary contexts are listed and read, and each term is read by the path that mirrors its URN
  A client lists a pack's glossary contexts to learn which vocabularies it holds, reads one context to see its terms,
  and reads one term by its context and its id, the path that mirrors its URN term:<context-id>/<term-id>. Each answer
  carries the fields of the pack file in camel case, and a member the file does not state is absent. Every read answers
  the pack revision as its ETag. The segments are judged in path order: an unknown pack is 404 pack_not_found, then a
  context the pack does not hold is 404 artifact_not_found naming the context, then a term the context does not hold
  is 404 artifact_not_found naming the term and its context.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "qa" with the file "glossaries/spec-kitty-core.glossary.yaml":
      """
      schema_version: '1.0'
      id: spec-kitty-core
      name: Spec Kitty core
      description: Canonical Spec Kitty terminology.
      tags: [spec-kitty, workflow]
      applies_to:
        paths: ['kitty-specs/**']
        languages: [markdown]
      terms:
        - id: mission
          name: mission
          definition: A unit of planned work that runs from a specification to a merge.
          examples: [The mission glossary-contexts-and-terms ships the glossary reads.]
          aliases: [feature]
          avoid: [epic]
          status: active
          see_also: ['term:spec-kitty-core/work-package']
        - id: work-package
          name: work package
          definition: A slice of a mission that one agent implements and one reviewer approves.
          status: active
        - id: lane
          name: lane
          definition: The old name of a work package's worktree.
          status: deprecated
          see_also: ['term:spec-kitty-core/work-package']
      """
    And the org pack "qa" with the file "glossaries/qa.glossary.yaml":
      """
      schema_version: '1.0'
      id: qa
      name: Quality assurance
      description: The vocabulary of testing and its reports.
      terms:
        - id: flaky-test
          name: flaky test
          definition: A test that passes and fails on the same code.
          status: active
        - id: quarantine
          name: quarantine
          definition: Setting a flaky test aside until it is fixed.
          status: draft
      """

  Scenario: The list answers each context's id, name, description, tags and term count, in id order
    When I GET "/packs/qa/artifacts/glossary"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "packId": "qa",
        "items": [
          {
            "id": "qa",
            "name": "Quality assurance",
            "description": "The vocabulary of testing and its reports.",
            "termCount": 2,
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/qa"}}
          },
          {
            "id": "spec-kitty-core",
            "name": "Spec Kitty core",
            "description": "Canonical Spec Kitty terminology.",
            "tags": ["spec-kitty", "workflow"],
            "termCount": 3,
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core"}}
          }
        ],
        "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary"}}
      }
      """
    And following the link "self" of every element of "items" answers 200

  Scenario: The generic list filtered by glossary lists the contexts, each titled by its name
    When I GET "/packs/qa/artifacts?kind=glossary"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {"kind": "glossary", "id": "qa", "title": "Quality assurance", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/qa"}}},
          {"kind": "glossary", "id": "spec-kitty-core", "title": "Spec Kitty core", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core"}}}
        ],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts?kind=glossary"},
          "first": {"href": "/api/v1/packs/qa/artifacts?kind=glossary"}
        }
      }
      """

  Scenario: A context is read in the Artifact envelope with every term in authored order and a templated term link
    When I GET "/packs/qa/artifacts/glossary/spec-kitty-core"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "kind": "glossary",
        "id": "spec-kitty-core",
        "packId": "qa",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core"},
          "collection": {"href": "/api/v1/packs/qa/artifacts/glossary"},
          "term": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core/terms/{termId}", "templated": true}
        },
        "glossary": {
          "schemaVersion": "1.0",
          "id": "spec-kitty-core",
          "name": "Spec Kitty core",
          "description": "Canonical Spec Kitty terminology.",
          "tags": ["spec-kitty", "workflow"],
          "appliesTo": {"paths": ["kitty-specs/**"], "languages": ["markdown"]},
          "terms": [
            {
              "id": "mission",
              "name": "mission",
              "definition": "A unit of planned work that runs from a specification to a merge.",
              "status": "active",
              "examples": ["The mission glossary-contexts-and-terms ships the glossary reads."],
              "aliases": ["feature"],
              "avoid": ["epic"],
              "seeAlso": ["term:spec-kitty-core/work-package"]
            },
            {
              "id": "work-package",
              "name": "work package",
              "definition": "A slice of a mission that one agent implements and one reviewer approves.",
              "status": "active"
            },
            {
              "id": "lane",
              "name": "lane",
              "definition": "The old name of a work package's worktree.",
              "status": "deprecated",
              "seeAlso": ["term:spec-kitty-core/work-package"]
            }
          ]
        }
      }
      """

  Scenario: A context that states no tags and no applies_to answers neither
    When I GET "/packs/qa/artifacts/glossary/qa"
    Then the response status is 200
    And the field at "/glossary/name" is "Quality assurance"
    And the field at "/glossary/tags" is absent
    And the field at "/glossary/appliesTo" is absent
    And the field at "/glossary/terms" holds exactly 2 items

  Scenario: A term is read with every optional member, its URN, its context and the link up to the context
    When I GET "/packs/qa/artifacts/glossary/spec-kitty-core/terms/mission"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "id": "mission",
        "name": "mission",
        "definition": "A unit of planned work that runs from a specification to a merge.",
        "status": "active",
        "examples": ["The mission glossary-contexts-and-terms ships the glossary reads."],
        "aliases": ["feature"],
        "avoid": ["epic"],
        "seeAlso": ["term:spec-kitty-core/work-package"],
        "urn": "term:spec-kitty-core/mission",
        "contextId": "spec-kitty-core",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core/terms/mission"},
          "up": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core"}
        }
      }
      """

  Scenario: A term that states no optional member answers none, and a deprecated term answers its status
    When I GET "/packs/qa/artifacts/glossary/spec-kitty-core/terms/work-package"
    Then the response status is 200
    And the response body is:
      """
      {
        "id": "work-package",
        "name": "work package",
        "definition": "A slice of a mission that one agent implements and one reviewer approves.",
        "status": "active",
        "urn": "term:spec-kitty-core/work-package",
        "contextId": "spec-kitty-core",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core/terms/work-package"},
          "up": {"href": "/api/v1/packs/qa/artifacts/glossary/spec-kitty-core"}
        }
      }
      """
    When I GET "/packs/qa/artifacts/glossary/spec-kitty-core/terms/lane"
    Then the response status is 200
    And the field at "/status" is "deprecated"
    And the field at "/avoid" is absent

  Scenario: The list, the context and the term answer the pack revision as their ETag
    When I GET "/packs/qa"
    And I remember the header "ETag"
    And I GET "/packs/qa/artifacts/glossary"
    Then the response header "ETag" is the remembered header
    When I GET "/packs/qa/artifacts/glossary/qa"
    Then the response header "ETag" is the remembered header
    When I GET "/packs/qa/artifacts/glossary/qa/terms/flaky-test"
    Then the response header "ETag" is the remembered header

  Scenario Outline: <operation> of a pack no configured pack answers to is 404 pack_not_found, whatever the rest names
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "pack_not_found"

    Examples:
      | operation                | path                                                    |
      | listPackGlossaryContexts | /packs/nope/artifacts/glossary                          |
      | getPackGlossaryContext   | /packs/nope/artifacts/glossary/spec-kitty-core          |
      | getPackGlossaryTerm      | /packs/nope/artifacts/glossary/nothing/terms/nothing    |

  Scenario Outline: <operation> of a context the pack does not hold is 404 artifact_not_found naming the context
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "glossary context `nothing` is not held"

    Examples:
      | operation              | path                                           |
      | getPackGlossaryContext | /packs/qa/artifacts/glossary/nothing           |
      | getPackGlossaryTerm    | /packs/qa/artifacts/glossary/nothing/terms/mission |

  Scenario: getPackGlossaryTerm of a term the context does not hold is 404 artifact_not_found naming the term and its context
    When I GET "/packs/qa/artifacts/glossary/qa/terms/mission"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "term `mission` is not held in glossary context `qa`"
    When I GET "/packs/qa/artifacts/glossary/spec-kitty-core/terms/mission"
    Then the response status is 200

  Scenario Outline: <operation> with an id outside the grammar is a 400 without a code, before the pack is judged
    When I GET "<path>"
    Then the response status is 400
    And the problem has no code

    Examples:
      | operation              | path                                                |
      | getPackGlossaryContext | /packs/nope/artifacts/glossary/Core                 |
      | getPackGlossaryTerm    | /packs/qa/artifacts/glossary/spec-kitty-core/terms/Core |
      | getPackGlossaryTerm    | /packs/qa/artifacts/glossary/1core/terms/mission    |
