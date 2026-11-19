# The removal of a registered pack whole, DELETE /packs/{packId}, and its dry run (#517, ADR-0081 D5 to D9; Mission
# pack-delete-and-update-guard-01M7SANQ, FR-001 to FR-007, FR-011, SC-002). A removal is synchronous and requires
# If-Match on the pack revision; it is refused 409 pack_has_dependents while a pack, an activation that names the pack,
# or another pack's artifact depends on it. With dryRun=true the same checks answer 200 with a PackImpact and nothing
# is removed: each refusal and each removal below has its dry-run twin, and the twin is shown to leave the pack as it
# was (its revision is unchanged).
#
# Steps this file adds (scenarios/README.md, "Registration steps"): When I DELETE "<path>" without If-Match.

@contract @registration
Feature: A registered pack is removed whole, or not at all
  A client removes an org or project pack it registered. The pack goes whole, so a later read and a repeated removal
  find nothing; or, while something depends on it, the removal is refused naming every dependent, and nothing changes.
  A dry run says which of the two would happen.

  Background:
    Given registration is switched on
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      """
    And the org pack "qa" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: D1
      intent: The directive another pack may require
      enforcement: required
      """

  Scenario: A pack nothing depends on is removed, and a repeated removal finds nothing
    When I GET "/packs/qa"
    And I remember the ETag
    And I DELETE "/packs/qa" with the remembered ETag
    Then the response status is 204
    When I GET "/packs/qa"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 404
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "pack_not_found"

  Scenario: The dry run of a removal nothing refuses answers deleted, and the pack stays
    When I GET "/packs/qa"
    And I remember the ETag
    And I remember the field "/revision" of the response body
    And I DELETE "/packs/qa?dryRun=true" with the remembered ETag
    Then the response status is 200
    And the field at "/operation" is "delete"
    And the field at "/outcome" is "deleted"
    And the field at "/packId" is "qa"
    And the field at "/version" is "1.0.0"
    And the field at "/problem" is absent
    When I GET "/packs/qa"
    Then the response status is 200
    And the field at "/revision" is the remembered one
    When I DELETE "/packs/qa" with the remembered ETag
    Then the response status is 204

  Scenario: A removal needs the pack revision: none is 428, a stale one is 412, the current one is accepted
    When I DELETE "/packs/qa" without If-Match
    Then the response status is 428
    When I DELETE "/packs/qa" with the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    When I DELETE "/packs/qa?dryRun=true" with the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    When I GET "/packs/qa"
    Then the response status is 200
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 204

  Scenario: A pack another pack depends on is not removed, the refusal names it, and its dry run says the same
    Given the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    When I GET "/packs/qa"
    And I remember the field "/revision" of the response body
    And I DELETE "/packs/qa?dryRun=true" with the header "If-Match": "*"
    Then the response status is 200
    And the field at "/outcome" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "pack_has_dependents"
    And the field at "/problem/packs/0/packId" is "other"
    And the field at "/problem/violations/0/code" is "depends_on"
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "pack_has_dependents"
    And the field at "/packs/0/packId" is "other"
    And the field at "/violations/0/code" is "depends_on"
    And the field at "/violations/0/detail" contains "other"
    When I GET "/packs/qa"
    Then the response status is 200
    And the field at "/revision" is the remembered one
    When I DELETE "/packs/other" with the header "If-Match": "*"
    Then the response status is 204
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 204

  Scenario: Every dependent is named: the pack that depends on it, the artifact that refers to it, and the activation
    Given the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the org pack "other" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: A directive of another pack that requires qa's D1
      enforcement: required
      """
    And the activation "team" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}]}
      """
    When I GET "/packs/other"
    And I remember the ETag
    And I PUT "/packs/other/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:R", "to": "qa::directive:D1", "type": "requires"}
      """
    Then the response status is 201
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "pack_has_dependents"
    And the field at "/violations/0/code" is "depends_on"
    And the field at "/violations/1/code" is "activation"
    And the field at "/violations/1/detail" contains "team"
    And the field at "/violations/2/code" is "artifact_reference"
    And the field at "/violations/2/detail" contains "directive:R"
    When I GET "/packs/qa/artifacts/directive/D1"
    Then the response status is 200

  Scenario: An activation that lists no packs does not block a removal
    Given the activation "everything" holds:
      """
      {"schemaVersion": 2}
      """
    When I DELETE "/packs/qa?dryRun=true" with the header "If-Match": "*"
    Then the response status is 200
    And the field at "/outcome" is "deleted"
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 204

  Scenario: A removal answers 503 while registration is switched off, and nothing is removed
    Given registration is switched off
    When I DELETE "/packs/qa" with the header "If-Match": "*"
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I DELETE "/packs/qa?dryRun=true" with the header "If-Match": "*"
    Then the response status is 503
    When I GET "/packs/qa"
    Then the response status is 200

  Scenario: The version history survives a removal: a re-install may take an older version, never with other content
    Given the archive "v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943Q
      pack_version: 2.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" is a copy of the archive "v2"
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943Q
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1-other" is a copy of the archive "v1"
    And the archive "v1-other" holds the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: Content version 1.0.0 never had
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "v2"
    And I poll the registration until it ends
    Then the field at "/result/version" is "2.0.0"
    When I DELETE "/packs/acme" with the header "If-Match": "*"
    Then the response status is 204
    When I PUT "/packs/acme" with the archive "v1-other"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
