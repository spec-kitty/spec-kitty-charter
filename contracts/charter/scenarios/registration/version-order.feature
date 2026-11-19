# The version order of a replacement (#517, ADR-0081 D2 and D4, amending ADR-0065 D2): a registration that replaces a
# stored pack with other content carries a pack_version newer than the stored one by PEP 440 precedence, or is refused
# 409 pack_version_conflict; sending the stored content is unchanged; a replace reports the version it moved from.
# Mission pack-delete-and-update-guard-01M7SANQ, FR-008 to FR-010.

@contract @registration
Feature: A replacement moves the pack's version forward
  A client that replaces a pack sends a newer pack_version, so a pack never silently goes back to older content. The
  service answers which version the replace moved from. Each refusal is paired with a control that is accepted.

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
    And the archive "newer" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1.2.0
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
    And the archive "older" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1.0.0
      intent: The directive every version holds
      enforcement: required
      """

  Scenario: A newer version replaces the pack and the result names the version it moved from
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/result/previousVersion" is absent
    When I PUT "/packs/acme" with the archive "newer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"
    And the field at "/result/version" is "1.2.0"
    And the field at "/result/previousVersion" is "1.1.0"
    When I GET "/packs/acme"
    Then the field at "/version" is "1.2.0"
    And the field at "/previousVersion" is absent

  Scenario: An older version with other content is refused, and the stored pack is unchanged
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "older"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "pack_version_conflict"
    When I GET "/packs/acme"
    Then the field at "/version" is "1.1.0"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the field at "/directive/title" is "A at version 1.1.0"
    When I PUT "/packs/acme" with the archive "newer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario: The same version with other content is refused, and the stored content sent again is unchanged
    Given the archive "same-version" is a copy of the archive "newer"
    And the archive "same-version" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "same-version"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "unchanged"
    And the field at "/result/previousVersion" is absent

  Scenario: A local version segment does not move the version forward, and a post-release does
    Given the archive "local" is a copy of the archive "newer"
    And the archive "local" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0+build2
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "post" is a copy of the archive "newer"
    And the archive "post" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0.post1
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "local"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"
    When I PUT "/packs/acme" with the archive "post"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/previousVersion" is "1.1.0"

  Scenario: A registration from a source follows the same order
    Given the filesystem source root holds the directory "packs/older" with the archive "older"
    And the filesystem source root holds the directory "packs/newer" with the archive "newer"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/older"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_version_conflict"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/newer"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/previousVersion" is "1.1.0"
