# The preset items at registration (issues #629 and #632 item 1; rulings R-a, R-k, R-l, R-m; mission
# pack-doctor-coverage-01M7YMQK, WP03, FR-002, FR-003). A preset item is judged over the declaring pack's own closure:
# the pack, its depends_on and its parent_pack, never a dependent. A must_have item that no pack of it holds as an
# artifact file is refused preset_item_not_held; an item that no pack of it declares as its kind, by a graph node or a
# file, is refused preset_item_undeclared. Each item gets one fault, with one violation at
# presets/<id>.yaml#/activated_<kinds>/<index>, and an item that names an artifact only by case keeps
# pack_reference_artifact_missing. pack-doctor validate answers the same code, pointer and detail for the same planted
# preset (PackSetCheckTest, PresetItemRulesTest). A composing read does not judge it: it reports such a must_have as
# requiredNotHeld (activation-ruling-changes.feature, A10).
#
# Steps: the existing whole-pack registration steps only (scenarios/README.md).

@contract @registration
Feature: A preset item must be in the declaring pack's own closure
  A must_have item is held as an artifact file, and every item is declared as its kind, by a pack of the declaring
  pack's own closure. A preset that breaks either refuses the pack, which then stores nothing; the same preset over a
  pack that holds its items registers.

  Background:
    Given registration is switched on
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: The directive the pack holds
      enforcement: required
      """

  Scenario: A must_have item no pack of the own closure holds is refused preset_item_not_held at the item
    Given the archive "unheld" is a copy of the archive "v1"
    And the archive "unheld" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A held directive and one no pack holds.
      activated_directives:
        - id: A
          must_have: true
        - id: B
          must_have: true
      """
    When I PUT "/packs/acme" with the archive "unheld"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "preset_item_not_held"
    And the field at "/problem/detail" is "the preset `team` of pack `acme` requires directive:B (must_have), but no pack of its own closure (`acme`) holds it as an artifact file"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/pointer" is "presets/team.yaml#/activated_directives/1"
    And the field at "/problem/violations/0/code" is "preset_item_not_held"
    When I GET "/packs/acme"
    Then the response status is 404

  Scenario: An item no pack of the own closure declares is refused preset_item_undeclared at the item
    Given the archive "undeclared" is a copy of the archive "v1"
    And the archive "undeclared" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A tactic no pack declares.
      activated_tactics:
        - id: ghost
      """
    When I PUT "/packs/acme" with the archive "undeclared"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "preset_item_undeclared"
    And the field at "/problem/detail" is "the preset `team` of pack `acme` activates tactic:ghost (not must_have), but no pack of its own closure (`acme`) declares it as a tactic"
    And the field at "/problem/violations/0/pointer" is "presets/team.yaml#/activated_tactics/0"
    And the field at "/problem/violations/0/code" is "preset_item_undeclared"

  Scenario: An item that names a held artifact only by case keeps the case-only code
    Given the archive "cased" is a copy of the archive "v1"
    And the archive "cased" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The held directive A, spelled a.
      activated_directives:
        - id: a
          must_have: true
      """
    When I PUT "/packs/acme" with the archive "cased"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "pack_reference_artifact_missing"

  Scenario: The same preset registers once the pack holds its items
    Given the archive "held" is a copy of the archive "v1"
    And the archive "held" holds the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: The directive the preset requires
      enforcement: required
      """
    And the archive "held" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: Two directives the pack holds.
      activated_directives:
        - id: A
          must_have: true
        - id: B
          must_have: true
      """
    When I PUT "/packs/acme" with the archive "held"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
