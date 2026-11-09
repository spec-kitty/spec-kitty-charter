@contract @offering
Feature: A pack id longer than its cap refuses the pack as unreadable
  The pack schemas cap a directive id at 64 characters, an executable entry and an expected artifact key at 255 (the
  contract change of #440 lists every capped id; operator decisions of 2026-11-09). The schema refuses a longer id
  before the service reads it, so a pack carrying one is unreadable, as a pack with any other schema error is. The
  domain applies the same cap, so a write over it is refused before it is stored and the pack stays readable.

  Background:
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """

  Scenario: A directive id of exactly 64 characters is read
    Given the org pack "alpha" with the file "directives/DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL
      title: An id at the cap
      intent: Be read
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 200

  Scenario: A directive id of 65 characters refuses the pack as pack_unreadable
    Given the org pack "alpha" with the file "directives/DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX
      title: An id one character over the cap
      intent: Be refused
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file directives/DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX.directive.yaml of alpha cannot be read: maxLength violated at /id"

  Scenario: An executable entry of exactly 255 characters is read
    Given the org pack "alpha" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      executable:
        runtime: python
        entry: eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.py
      """
    And the org pack "alpha" with the file "assets/run.py":
      """
      print("pass")
      """
    And the org pack "alpha" with the file "assets/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.py":
      """
      print("pass")
      """
    When I GET "/offering"
    Then the response status is 200

  Scenario: An executable entry of 256 characters refuses the pack as pack_unreadable
    Given the org pack "alpha" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      executable:
        runtime: python
        entry: eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.py
      """
    And the org pack "alpha" with the file "assets/run.py":
      """
      print("pass")
      """
    And the org pack "alpha" with the file "assets/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee/eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.py":
      """
      print("pass")
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file assets/run.py.asset.yaml of alpha cannot be read: maxLength violated at /executable/entry"

  Scenario: An expected artifact key of 256 characters refuses the pack as pack_unreadable
    Given the org pack "alpha" with the file "missions/long-key/long-key.mission-type.yaml":
      """
      schema_version: '1.0'
      id: long-key
      title: A mission with a key over the cap
      description: Be refused
      steps:
      - id: only
        title: Only step
        expected_output_artifacts:
        - artifact_key: key.kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk
          artifact_class: output
          path_pattern: out.md
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file missions/long-key/long-key.mission-type.yaml of alpha cannot be read: maxLength violated at /steps/0/expected_output_artifacts/0/artifact_key"

  Scenario: A kebab tactic id of 65 characters refuses the pack as pack_unreadable
    Given the org pack "alpha" with the file "tactics/ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt.tactic.yaml":
      """
      schema_version: '1.0'
      id: ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt
      name: A tactic id one character over the cap
      steps:
        - title: Only step
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "the file tactics/ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt.tactic.yaml of alpha cannot be read: maxLength violated at /id"

  Scenario: A directive written with an id of 65 characters is refused, and the pack stays readable
    When I PUT "/packs/alpha/artifacts/directive/DIRECTIVE_LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX" with the body:
      """
      {"schemaVersion": "1.0", "title": "An id one character over the cap", "intent": "Be refused", "enforcement": "required"}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "Constraint breached: A directive identifier must match ^[A-Z][A-Z0-9_-]*$ and be at most 64 characters."
    When I GET "/offering"
    Then the response status is 200
