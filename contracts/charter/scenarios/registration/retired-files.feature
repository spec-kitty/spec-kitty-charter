# The retired step-contract files at registration (slice F, #514; ADR-0077 decision 3). The twins of the retired
# org-charter.yaml scenario of whole-pack-registration.feature, for the two file families slice F retires. They stand in
# their own file because they name the retired kind's files, and RetiredNamesScanTest admits a retired name in the
# contracts only where a file records or pins the retirement.

@contract @registration
Feature: A retired step-contract file is refused by name at registration
  The step contract is retired: its substeps are the mission-type steps' own. A whole-pack registration whose archive
  carries a step-contract file or the graph fragment that held its nodes is refused, naming the file as a retired file
  kind, wherever the file stands; it is never read as a file nothing models.

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

  Scenario Outline: The retired <file> is refused as retired, never merely not held
    Given the archive "step-contract" is a copy of the archive "v1"
    And the archive "step-contract" holds the file "<file>":
      """
      <body>
      """
    When I PUT "/packs/acme" with the archive "step-contract"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "retired"
    And the field at "/problem/violations/0/pointer" is "<file>"
    When I GET "/packs/acme"
    Then the response status is 404

    Examples:
      | file                                              | body                    |
      | missions/rx/step_contracts/a.step-contract.yaml   | id: a                   |
      | mission_step_contract.graph.yaml                  | schema_version: '1.0'   |

  Scenario: The same archive without the retired file registers
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
