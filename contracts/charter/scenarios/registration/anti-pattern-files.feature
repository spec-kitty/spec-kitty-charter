# The anti-pattern file at registration (ADR-0052 decision 8, as amended by ADR-0053 decision 30; #154; mission
# pack-doctor-coverage-01M7YMQK, WP10, FR-013). A whole-pack registration holds and judges
# anti_patterns/<id>.anti-pattern.yaml, which it warned file_not_held before: a valid one is held, an invalid one is
# refused with the code and pointer the closed schema's validated read answers (artifact_schema_error, a contract code),
# and a misplaced or misnamed one by the store's placement and stem rule. pack-doctor validate answers the same codes,
# pointers and details for the same planted files (AntiPatternFileCheckTest, ADR-0075 decision 9). The service serves no
# anti-pattern body over HTTP yet, so the held file is shown by the registration that succeeds with no diagnostic.
#
# Steps: the existing whole-pack registration steps only (scenarios/README.md, "Registration steps").

@contract @registration
Feature: A whole-pack registration holds a valid anti-pattern file and refuses an invalid one
  An anti-pattern is authored as its own file, anti_patterns/<id>.anti-pattern.yaml, closed, with schema_version, id,
  title, description, severity (smell, severe or breaking, never defaulted) and bad_example required. A registration
  holds a valid file; a file the schema refuses, or one outside anti_patterns/ or whose id is not its file stem, refuses
  the pack, which then stores nothing. Each refusal is paired with the valid file registering in the same pack.

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
      title: A at version 1
      intent: The directive that stays
      enforcement: required
      """
    And the archive "v1" holds the file "anti_patterns/clever-over-direct.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: clever-over-direct
      title: Clever over direct
      description: Using a clever language feature when a direct, familiar idiom would be easier to review.
      severity: smell
      bad_example: result = next((v for v in xs if pred(v)), None)
      good_example: write the loop that finds the first match
      """

  Scenario: A pack holding a valid anti-pattern file registers, and the file is held, not warned file_not_held
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/diagnostics" holds exactly 0 item(s)
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200

  Scenario Outline: An anti-pattern file the closed schema refuses (<case>) refuses the pack at the file
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "anti_patterns/clever-over-direct.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: clever-over-direct
      title: Clever over direct
      description: Using a clever language feature when a direct, familiar idiom would be easier to review.
      <severity>
      <bad_example>
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "<pointer>"
    And the field at "/problem/violations/0/detail" contains "<detail>"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples:
      | case                 | severity        | bad_example                   | pointer                                                       | detail                                       |
      | no severity          |                 | bad_example: next(...)        | anti_patterns/clever-over-direct.anti-pattern.yaml            | missing 'severity'                           |
      | no bad_example       | severity: smell | good_example: write the loop  | anti_patterns/clever-over-direct.anti-pattern.yaml            | missing 'bad_example'                        |
      | an unknown severity  | severity: minor | bad_example: next(...)        | anti_patterns/clever-over-direct.anti-pattern.yaml#/severity  | 'severity' has an unsupported value 'minor'  |

  Scenario: An anti-pattern file with a key the closed schema does not declare refuses the pack at the file
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "anti_patterns/clever-over-direct.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: clever-over-direct
      title: Clever over direct
      description: Using a clever language feature when a direct, familiar idiom would be easier to review.
      severity: smell
      bad_example: result = next((v for v in xs if pred(v)), None)
      rejected_by: styleguide:boring-code-review
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "anti_patterns/clever-over-direct.anti-pattern.yaml"
    And the field at "/problem/violations/0/detail" contains "unknown key 'rejected_by'"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario Outline: An anti-pattern file <case> is refused by the placement and stem rule
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" lacks the file "anti_patterns/clever-over-direct.anti-pattern.yaml"
    And the archive "bad" holds the file "<path>":
      """
      schema_version: '1.0'
      id: <id>
      title: Clever over direct
      description: Using a clever language feature when a direct, familiar idiom would be easier to review.
      severity: smell
      bad_example: result = next((v for v in xs if pred(v)), None)
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "<path>#/id"
    And the field at "/problem/violations/0/detail" contains "<detail>"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples:
      | case                     | path                                                | id                 | detail                                                                                                  |
      | outside anti_patterns/   | clever-over-direct.anti-pattern.yaml                | clever-over-direct | anti_pattern 'clever-over-direct' is not at its place 'anti_patterns/clever-over-direct.anti-pattern.yaml' |
      | whose id is not its stem | anti_patterns/clever-over-direct.anti-pattern.yaml  | other              | anti_pattern id 'other' is not its file stem 'clever-over-direct'                                       |
