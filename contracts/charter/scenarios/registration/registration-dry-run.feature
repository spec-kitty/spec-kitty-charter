# The dry run of a whole-pack registration (#517, ADR-0081 D5; Mission pack-delete-and-update-guard-01M7SANQ, FR-012,
# FR-013, NFR-002, SC-002). PUT /packs/{packId}?dryRun=true and POST /packs?dryRun=true run the registration's job
# through every check and stop before the commit: the job ends as the registration would, its result says dryRun, and
# nothing is stored, the version history included.

@contract @registration
Feature: A registration can be judged without being made
  A client asks what a registration would do before making it. The answer is the registration's own judgement, by the
  same code; the stored pack, its version history and its revision are left as they were.

  Background:
    Given registration is switched on
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1.1.0
      intent: The directive every version holds
      enforcement: required
      """
    And the archive "newer" is a copy of the archive "v1"
    And the archive "newer" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.2.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "newer-other" is a copy of the archive "newer"
    And the archive "newer-other" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1.2.0, the content that is registered
      intent: The directive every version holds
      enforcement: required
      """
    And the archive "older" is a copy of the archive "v1"
    And the archive "older" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """

  Scenario: The dry run of a create answers created, and no pack is stored
    When I PUT "/packs/acme?dryRun=true" with the archive "v1"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/dryRun" is true
    And the field at "/_links/pack" is absent
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/result/outcome" is "created"
    And the field at "/result/dryRun" is absent

  Scenario: The dry run of a replace answers replaced and the version it would move from, and the pack stays
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the field "revision" of the response body
    And I PUT "/packs/acme?dryRun=true" with the archive "newer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"
    And the field at "/result/previousVersion" is "1.1.0"
    And the field at "/result/dryRun" is true
    When I GET "/packs/acme"
    Then the field at "/version" is "1.1.0"
    And the field "revision" of the response body is the remembered one

  Scenario: The dry run of an older version is refused as the registration is
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme?dryRun=true" with the archive "older"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"
    When I PUT "/packs/acme" with the archive "older"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"

  Scenario: A dry run writes no version history: the version it judged stays free for other content
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme?dryRun=true" with the archive "newer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/acme" with the archive "newer-other"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/version" is "1.2.0"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the field at "/directive/title" is "A at version 1.2.0, the content that is registered"

  Scenario: The dry run of a registration from a source judges it and stores nothing
    Given the filesystem source root holds the directory "packs/acme" with the archive "v1"
    When I POST "/packs?dryRun=true" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/dryRun" is true
    When I GET "/packs/acme"
    Then the response status is 404

  Scenario: A dryRun that is neither true nor false is 400 on every operation, and nothing is written
    When I PUT "/packs/acme?dryRun=yes" with the archive "v1"
    Then the response status is 400
    And the problem has no code
    When I GET "/packs/acme"
    Then the response status is 404
    Given the filesystem source root holds the directory "packs/acme" with the archive "v1"
    When I POST "/packs?dryRun=1" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 400
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I DELETE "/packs/acme?dryRun=on" with the header "If-Match": "*"
    Then the response status is 400
    When I GET "/packs/acme"
    Then the response status is 200
