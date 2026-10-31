@contract @artifacts
Feature: A pack's artifacts are read through one generic list and one generic item
  A client lists a pack's artifacts of the served kinds (tactic, directive, styleguide, paradigm and
  toolguide) as summaries, filtered by an optional kind and paged with the shared page information
  and first/next links (FR-003), and reads one artifact in a closed envelope that holds exactly the
  member its kind names (FR-004). The list is ordered by the served-kind order, then by id in
  code-point order, and its cursor is a position: a page holds the artifacts strictly after it. An
  unknown pack is pack_not_found, a path kind the service does not serve is kind_not_found, and an
  unknown kind filter, a bad cursor or a page size out of range is a 400 Problem without a code
  (FR-007). Each answer is one verified read and carries the fingerprint it verified.

  Background:
    Given the org pack "qa" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
      """
    And the org pack "qa" with the file "directives/guard.directive.yaml":
      """
      schema_version: '1.0'
      id: GUARD
      title: Guard the gate
      intent: Guard it.
      enforcement: required
      """
    And the org pack "qa" with the file "styleguides/java-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: java-style
      title: Java style
      scope: code
      principles:
        - Name things plainly.
      """
    And the org pack "qa" with the file "paradigms/layered.paradigm.yaml":
      """
      schema_version: '1.0'
      id: layered
      name: Layered architecture
      summary: Keep the layers apart.
      """
    And the org pack "qa" with the file "toolguides/git.toolguide.yaml":
      """
      schema_version: '1.0'
      id: git
      tool: git
      title: How to use git
      guide_path: git.guide.md
      summary: Using git under governance.
      """
    And the org pack "qa" with the file "toolguides/git.guide.md":
      """
      # How to use git

      ## Overview

      Use git under governance.
      """

  # ---- one artifact per kind ------------------------------------------------------------------------------------

  Scenario: A tactic is read in the envelope, with no ETag
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "kind": "tactic",
        "id": "rehearse",
        "packId": "qa",
        "tactic": {"id": "rehearse", "schemaVersion": "1.0", "name": "Rehearse", "purpose": null, "notes": null, "steps": [{"title": "Only step", "description": null, "examples": [], "references": []}], "failureModes": [], "appliesToLanguages": [], "references": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=tactic"}
        }
      }
      """

  Scenario: A directive is read in the envelope, with an ETag
    When I GET "/packs/qa/artifacts/directive/GUARD"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "directive",
        "id": "GUARD",
        "packId": "qa",
        "directive": {"id": "GUARD", "schemaVersion": "1.0", "title": "Guard the gate", "intent": "Guard it.", "enforcement": "required", "scope": null, "procedures": [], "integrityRules": [], "validationCriteria": [], "explicitAllowances": [], "references": [], "relationships": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/directive/GUARD"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=directive"}
        }
      }
      """

  Scenario: A styleguide is read in the envelope, with an ETag
    When I GET "/packs/qa/artifacts/styleguide/java-style"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "styleguide",
        "id": "java-style",
        "packId": "qa",
        "styleguide": {"id": "java-style", "schemaVersion": "1.0", "title": "Java style", "scope": "code", "principles": ["Name things plainly."], "patterns": [], "antiPatterns": [], "tooling": [], "qualityTest": null, "structuralLintConfig": null, "appliesToLanguages": [], "references": [], "relationships": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/styleguide/java-style"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=styleguide"}
        }
      }
      """

  Scenario: A paradigm is read in the envelope, with an ETag
    When I GET "/packs/qa/artifacts/paradigm/layered"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "paradigm",
        "id": "layered",
        "packId": "qa",
        "paradigm": {"id": "layered", "schemaVersion": "1.0", "name": "Layered architecture", "summary": "Keep the layers apart.", "directiveRefs": [], "references": [], "relationships": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/paradigm/layered"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=paradigm"}
        }
      }
      """

  Scenario: A toolguide is read in the envelope, with an ETag
    When I GET "/packs/qa/artifacts/toolguide/git"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "toolguide",
        "id": "git",
        "packId": "qa",
        "toolguide": {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "# How to use git\n\n## Overview\n\nUse git under governance.\n", "summary": "Using git under governance.", "commands": [], "appliesToLanguages": [], "lastUpdated": null, "references": []},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/toolguide/git"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=toolguide"}
        }
      }
      """

  # ---- the list --------------------------------------------------------------------------------------------------

  Scenario: A list filtered by a served kind answers that kind's summaries (the positive control of the kind filter)
    When I GET "/packs/qa/artifacts?kind=tactic"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [
          {"kind": "tactic", "id": "rehearse", "title": "Rehearse", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"}}}
        ],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts?kind=tactic"},
          "first": {"href": "/api/v1/packs/qa/artifacts?kind=tactic"}
        }
      }
      """

  Scenario: A kind filter the service does not know is a 400 Problem without a code
    When I GET "/packs/qa/artifacts?kind=tactic"
    Then the response status is 200
    When I GET "/packs/qa/artifacts?kind=bogus"
    Then the response status is 400
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A summary's title is the name of a tactic or a paradigm and the title of the other kinds
    When I GET "/packs/qa/artifacts"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [
          {"kind": "tactic", "id": "rehearse", "title": "Rehearse", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"}}},
          {"kind": "directive", "id": "GUARD", "title": "Guard the gate", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/directive/GUARD"}}},
          {"kind": "styleguide", "id": "java-style", "title": "Java style", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/styleguide/java-style"}}},
          {"kind": "paradigm", "id": "layered", "title": "Layered architecture", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/paradigm/layered"}}},
          {"kind": "toolguide", "id": "git", "title": "How to use git", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/toolguide/git"}}}
        ],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts"},
          "first": {"href": "/api/v1/packs/qa/artifacts"}
        }
      }
      """

  Scenario: A list without a filter is in the served-kind order, then by id in code-point order
    Given the org pack "order" with the file "paradigms/p1.paradigm.yaml":
      """
      schema_version: '1.0'
      id: p1
      name: First paradigm
      summary: One.
      """
    And the org pack "order" with the file "directives/d1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: First directive
      intent: One.
      enforcement: required
      """
    And the org pack "order" with the file "tactics/alpha.tactic.yaml":
      """
      schema_version: '1.0'
      id: alpha
      name: Alpha
      steps:
        - title: Only step
      """
    And the org pack "order" with the file "tactics/zeta.tactic.yaml":
      """
      schema_version: '1.0'
      id: Zeta
      name: Zeta
      steps:
        - title: Only step
      """
    When I GET "/packs/order/artifacts"
    Then the response status is 200
    And the field at "/items/0/id" is "Zeta"
    And the field at "/items/1/id" is "alpha"
    And the field at "/items/2/kind" is "directive"
    And the field at "/items/2/id" is "D1"
    And the field at "/items/3/kind" is "paradigm"
    And the field at "/items/3/id" is "p1"
    And the field at "/page/hasNextPage" is false

  Scenario: An empty pack answers an empty page
    Given the org pack "empty" with the file "pack.yaml":
      """
      pack_id: empty
      pack_version: 1.0.0
      parent_pack: null
      name: empty
      pack_level: org
      """
    When I GET "/packs/empty/artifacts"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/empty/artifacts"},
          "first": {"href": "/api/v1/packs/empty/artifacts"}
        }
      }
      """

  Scenario: A kind the pack holds none of answers an empty page
    Given the org pack "order" with the file "tactics/alpha.tactic.yaml":
      """
      schema_version: '1.0'
      id: alpha
      name: Alpha
      steps:
        - title: Only step
      """
    When I GET "/packs/order/artifacts?kind=styleguide"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/order/artifacts?kind=styleguide"},
          "first": {"href": "/api/v1/packs/order/artifacts?kind=styleguide"}
        }
      }
      """

  # ---- paging ----------------------------------------------------------------------------------------------------

  Scenario: A page of exactly pageSize items is the last page: no next link and no trailing empty page
    When I GET "/packs/qa/artifacts?pageSize=5"
    Then the response status is 200
    And the field at "/items/4/id" is "git"
    And the field at "/page/hasNextPage" is false
    And the field at "/page/pageSize" is 5
    And the answer has no link "next"
    And the field at "/_links/self/href" is "/api/v1/packs/qa/artifacts?pageSize=5"
    And the field at "/_links/first/href" is "/api/v1/packs/qa/artifacts?pageSize=5"

  Scenario: Pages of one item cross the kind boundary and serve every artifact exactly once
    When I GET "/packs/qa/artifacts?pageSize=1"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {"kind": "tactic", "id": "rehearse", "title": "Rehearse", "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/tactic/rehearse"}}}
        ],
        "page": {"hasNextPage": true, "nextPageCursor": "dGFjdGljL3JlaGVhcnNl", "pageSize": 1},
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts?pageSize=1"},
          "first": {"href": "/api/v1/packs/qa/artifacts?pageSize=1"},
          "next": {"href": "/api/v1/packs/qa/artifacts?pageSize=1&pageCursor=dGFjdGljL3JlaGVhcnNl"}
        }
      }
      """
    And following "next" links from the answer, the "items" ids are:
      | kind       | id         |
      | tactic     | rehearse   |
      | directive  | GUARD      |
      | styleguide | java-style |
      | paradigm   | layered    |
      | toolguide  | git        |
    And the field at "/page/hasNextPage" is false
    And the answer has no link "next"

  Scenario: A next link keeps the kind filter and the page size, and its page keeps first without the cursor
    Given the org pack "order" with the file "tactics/alpha.tactic.yaml":
      """
      schema_version: '1.0'
      id: alpha
      name: Alpha
      steps:
        - title: Only step
      """
    And the org pack "order" with the file "tactics/zeta.tactic.yaml":
      """
      schema_version: '1.0'
      id: Zeta
      name: Zeta
      steps:
        - title: Only step
      """
    And the org pack "order" with the file "directives/d1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: First directive
      intent: One.
      enforcement: required
      """
    When I GET "/packs/order/artifacts?kind=tactic&pageSize=1"
    Then the response status is 200
    And the field at "/_links/next/href" is "/api/v1/packs/order/artifacts?kind=tactic&pageSize=1&pageCursor=dGFjdGljL1pldGE"
    And following "next" links from the answer, the "items" ids are:
      | kind   | id    |
      | tactic | Zeta  |
      | tactic | alpha |
    And the field at "/_links/self/href" is "/api/v1/packs/order/artifacts?kind=tactic&pageSize=1&pageCursor=dGFjdGljL1pldGE"
    And the field at "/_links/first/href" is "/api/v1/packs/order/artifacts?kind=tactic&pageSize=1"

  Scenario: The anchor of a cursor deleted between two pages still pages on from its position
    Given the org pack "order" with the file "tactics/alpha.tactic.yaml":
      """
      schema_version: '1.0'
      id: alpha
      name: Alpha
      steps:
        - title: Only step
      """
    And the org pack "order" with the file "tactics/zeta.tactic.yaml":
      """
      schema_version: '1.0'
      id: Zeta
      name: Zeta
      steps:
        - title: Only step
      """
    When I GET "/packs/order/artifacts?pageSize=1"
    Then the response status is 200
    And the field at "/items/0/id" is "Zeta"
    When the file "tactics/zeta.tactic.yaml" of the pack "order" is deleted
    And I follow the link "next"
    Then the response status is 200
    And the field at "/items/0/id" is "alpha"
    And the field at "/page/hasNextPage" is false
    And the answer has no link "next"

  Scenario Outline: A bad page cursor is a 400 Problem without a code (<case>)
    When I GET "/packs/qa/artifacts?<query>"
    Then the response status is 400
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | case                               | query                                        |
      | a cursor for another kind          | kind=tactic&pageCursor=ZGlyZWN0aXZlL0dVQVJE  |
      | a cursor that is not base64url     | pageCursor=***                               |
      | a cursor of a kind not served      | pageCursor=cHJvY2VkdXJlL3g                   |
      | a cursor without a slash           | pageCursor=dGFjdGlj                          |

  Scenario Outline: A page size out of range or not a number is a 400 Problem without a code (<pageSize>)
    When I GET "/packs/qa/artifacts?pageSize=<pageSize>"
    Then the response status is 400
    And the problem has no code
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | pageSize |
      | 0        |
      | 201      |
      | abc      |

  # ---- refusals --------------------------------------------------------------------------------------------------

  Scenario Outline: An unknown pack is pack_not_found, on the list and on the item (<path>)
    When I GET "<path>"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Pack not found", "status": 404, "code": "pack_not_found", "detail": "Pack nope was not found."}
      """
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | path                                  |
      | /packs/nope/artifacts                 |
      | /packs/nope/artifacts?kind=directive  |
      | /packs/nope/artifacts/tactic/rehearse |
      | /packs/nope/artifacts/bogus/x         |

  Scenario Outline: A path kind the service does not serve is kind_not_found (<kind>)
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 200
    When I GET "/packs/qa/artifacts/<kind>/rehearse"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Kind not found", "status": 404, "code": "kind_not_found", "detail": "The service serves no artifact kind <kind>."}
      """
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | kind      |
      | procedure |
      | bogus     |

  Scenario Outline: An unknown id of a served kind is that kind's not-found refusal (<kind>)
    When I GET "/packs/qa/artifacts/<kind>/nope"
    Then the response status is 404
    And the problem code is "<code>"
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | kind       | code                 |
      | tactic     | tactic_not_found     |
      | directive  | directive_not_found  |
      | styleguide | styleguide_not_found |
      | paradigm   | paradigm_not_found   |
      | toolguide  | toolguide_not_found  |

  # ---- read consistency ------------------------------------------------------------------------------------------

  Scenario: A list whose inputs change while it is answered is refused with stale_input
    Given a tactic file changes while the next read is answered
    When I GET "/packs/qa/artifacts"
    Then the response status is 500
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the artifacts were read; read them again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/qa/artifacts"
    Then the response status is 200

  Scenario: An item whose inputs change while it is answered is refused with stale_input
    Given a tactic file changes while the next read is answered
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 500
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the tactic was read; read it again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/qa/artifacts/tactic/rehearse"
    Then the response status is 200
