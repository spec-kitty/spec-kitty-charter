# The inline anti-pattern block at registration (ADR-0052 decision 16; ruling R-r; #154; mission
# pack-doctor-coverage-01M7YMQK, WP12, FR-015). A styleguide or a procedure carries no anti_patterns: an anti-pattern
# is its own file, anti_patterns/<id>.anti-pattern.yaml, rejected by a rejects edge in the pack's graph. Both schemas
# are closed, so a file that still carries the key is refused with the code and pointer the closed schema's validated
# read answers (artifact_schema_error, a contract code), the unknown key named. pack-doctor validate answers the same
# code, pointer and detail for the same planted files (InlineAntiPatternCheckTest, ADR-0075 decision 9).
#
# Steps: the existing whole-pack registration steps only (scenarios/README.md, "Registration steps").

@contract @registration
Feature: A whole-pack registration refuses a styleguide or a procedure that carries an anti_patterns block
  A styleguide and a procedure no longer carry anti-patterns. A file with an anti_patterns block refuses the pack at
  the file, which then stores nothing; the same file without the block registers.

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
    And the archive "v1" holds the file "styleguides/java-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: java-style
      title: Java style
      scope: code
      principles:
      - name: Prefer immutability
        description: Make a value final unless it must change.
      """
    And the archive "v1" holds the file "procedures/land-a-change.procedure.yaml":
      """
      schema_version: '1.0'
      id: land-a-change
      title: Land a change
      description: Land a reviewed change.
      entry_condition: A change is ready to land.
      exit_condition: The change has landed.
      steps:
      - title: Land the change
      """

  Scenario: The styleguide and the procedure without an anti_patterns block register
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    When I GET "/packs/acme/artifacts/styleguide/java-style"
    Then the response status is 200
    When I GET "/packs/acme/artifacts/procedure/land-a-change"
    Then the response status is 200

  Scenario: A styleguide with an anti_patterns block refuses the pack at the file
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "styleguides/java-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: java-style
      title: Java style
      scope: code
      principles:
      - name: Prefer immutability
        description: Make a value final unless it must change.
      anti_patterns:
      - name: Mutable by default
        description: A field that is never reassigned is left mutable.
        bad_example: private String name;
        good_example: private final String name;
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "styleguides/java-style.styleguide.yaml"
    And the field at "/problem/violations/0/detail" contains "unknown key 'anti_patterns'"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario: A procedure with an anti_patterns block refuses the pack at the file
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "procedures/land-a-change.procedure.yaml":
      """
      schema_version: '1.0'
      id: land-a-change
      title: Land a change
      description: Land a reviewed change.
      entry_condition: A change is ready to land.
      exit_condition: The change has landed.
      steps:
      - title: Land the change
      anti_patterns:
      - title: Landing unreviewed work
        content: Work lands only after review.
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "procedures/land-a-change.procedure.yaml"
    And the field at "/problem/violations/0/detail" contains "unknown key 'anti_patterns'"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
