@contract @offering
Feature: An id a request carries is capped in length
  The request schemas cap every id a path or a body carries (#443, after the pack-side caps of #440): a pack id at
  64 characters, an artifact id at 128 (the ArtifactId cap, which leaves room for the URN spelling), and a reference
  a relationship request writes at 255 as a whole. A request over a cap is refused before anything is read or written; a
  request at the cap is judged as any other.

  Background:
    Given registration is switched on
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: D1
      intent: D1
      enforcement: required
      """

  Scenario: A pack id of 65 characters is a 400 without a code, and one of 64 is judged as any other
    When I GET "/packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts"
    Then the response status is 400
    And the problem has no code
    When I GET "/packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts"
    Then the response status is 404
    And the problem code is "pack_not_found"

  Scenario: An artifact id of 129 characters is a 400 without a code, and one of 128 is the kind's not-found
    When I GET "/packs/alpha/artifacts/directive/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
    Then the response status is 400
    And the problem has no code
    When I GET "/packs/alpha/artifacts/directive/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD"
    Then the response status is 404
    And the problem code is "directive_not_found"

  Scenario: A relationship whose reference is longer than 255 characters is refused, and nothing is written
    When I GET "/packs/alpha"
    And I remember the ETag
    And I PUT "/packs/alpha/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D1", "to": "directive:DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "type": "requires"}
      """
    Then the response status is 400
    And the problem has no code
    When I GET "/packs/alpha/artifacts/directive/D1/references"
    Then the response status is 200
    And the field at "/items" holds exactly 0 items

  Scenario: A relationship DELETE whose reference is longer than 255 characters is a 400 without a code
    When I DELETE "/packs/alpha/relationships?from=directive:D1&to=directive:DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD" with the header "If-Match": "*"
    Then the response status is 400
    And the problem has no code
