# The preset reads of a pack (#98, ADR-0074 decision 1; FR-001, FR-002, FR-015): GET /packs/{packId}/presets lists a
# pack's activation presets in id order, and GET /packs/{packId}/presets/{presetId} reads one. Every step is from the
# existing vocabulary. The 400, 401, 403 and 500 answers of both operations are asserted in read-refusals.feature,
# every-operation.feature and unexpected-failure.feature.

@contract @registration
Feature: A pack's presets are listed and read
  A client lists a pack's presets, and reads one by id, to see what applying it would put in force before it applies
  it. A preset answers its id, its description and, for each kind it lists, an array activated<Plural> of its items,
  each with whether it is must-have. Both reads answer the pack revision as their ETag. The pack is judged first: an
  unknown pack is 404 pack_not_found, then a preset the pack does not hold is 404 preset_not_found.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "qa" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: The directive the team preset requires
      enforcement: required
      """
    And the org pack "qa" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: The tactic both presets select
      steps:
        - title: Only step
      """
    And the org pack "qa" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: What a review team puts in force.
      activated_directives:
        - id: D
          must_have: true
      activated_tactics:
        - id: t
      """
    And the org pack "qa" with the file "presets/minimal.yaml":
      """
      schema_version: '1.0'
      id: minimal
      description: The least a review needs.
      activated_tactics:
        - id: t
      """

  Scenario: The list answers both presets in id order, each with its description and its kind lists
    When I GET "/packs/qa/presets"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "id": "minimal",
            "description": "The least a review needs.",
            "activatedTactics": [{"id": "t", "mustHave": false}],
            "_links": {
              "self": {"href": "/api/v1/packs/qa/presets/minimal"},
              "pack": {"href": "/api/v1/packs/qa"}
            }
          },
          {
            "id": "team",
            "description": "What a review team puts in force.",
            "activatedDirectives": [{"id": "D", "mustHave": true}],
            "activatedTactics": [{"id": "t", "mustHave": false}],
            "_links": {
              "self": {"href": "/api/v1/packs/qa/presets/team"},
              "pack": {"href": "/api/v1/packs/qa"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/packs/qa/presets"},
          "pack": {"href": "/api/v1/packs/qa"}
        }
      }
      """
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: One preset answers each kind's items, a must-have item and a plain one
    When I GET "/packs/qa/presets/team"
    Then the response status is 200
    And the response body is:
      """
      {
        "id": "team",
        "description": "What a review team puts in force.",
        "activatedDirectives": [{"id": "D", "mustHave": true}],
        "activatedTactics": [{"id": "t", "mustHave": false}],
        "_links": {
          "self": {"href": "/api/v1/packs/qa/presets/team"},
          "pack": {"href": "/api/v1/packs/qa"}
        }
      }
      """
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: The pack links its presets, and both reads answer the pack revision as their ETag
    When I GET "/packs/qa"
    Then the response header "ETag" is the quoted field "/revision"
    And the field at "/_links/presets/href" is "/api/v1/packs/qa/presets"
    When I remember the header "ETag"
    And I follow the link "presets"
    Then the response status is 200
    And the response header "ETag" is the remembered header
    When I GET "/packs/qa/presets/minimal"
    Then the response header "ETag" is the remembered header

  Scenario: A pack with no preset lists none
    Given the org pack "bare" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: bare-pack
      pack_level: org
      """
    When I GET "/packs/bare/presets"
    Then the response status is 200
    And the field at "/items" holds exactly 0 items

  Scenario: A preset the pack does not hold is preset_not_found
    When I GET "/packs/qa/presets/ghost"
    Then the response status is 404
    And the problem code is "preset_not_found"

  Scenario Outline: An unknown pack is pack_not_found on <operation>, whatever preset the path names
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "pack_not_found"

    Examples:
      | operation       | path                      |
      | listPackPresets | /packs/nope/presets       |
      | getPackPreset   | /packs/nope/presets/team  |
