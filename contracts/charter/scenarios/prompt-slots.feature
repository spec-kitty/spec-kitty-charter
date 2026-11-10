@contract @registration
Feature: A step prompt's slots are judged behind the descriptor-version gate
  A base prompt declares its slots as paired HTML comment markers (ADR-0066, issue #424): well formed, unique, not
  nested and empty. A malformed declaration is prompt_slot_malformed whatever the pack descriptor declares. Text
  between paired markers is prompt_slot_not_empty only when the descriptor declares schema_version "1.0"; an
  unversioned (legacy) descriptor admits it with a warning that never refuses the pack. A refusal is a violation of
  the pack at the prompt file, so a registration answers 422 validation_failed with the pack-relative file as the
  pointer, and a read of a closure holding such a pack answers 500 pack_unreadable naming the file. Both codes are
  raised as violation codes (the amended definition of raised in CharterRefusalCode).

  Every fixture is one pack with a software-dev mission type whose step plan declares the slot spdd:reasons-block,
  in a versioned form (its pack.yaml carries schema_version "1.0") and an unversioned twin. Each refusal sits beside
  its passing twin, which differs in the one fault: a registration that refused for another reason could not pass the
  twin.

  Background:
    Given registration is switched on
    And the archive "versioned" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      schema_version: "1.0"
      """
    And the archive "versioned" holds the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the archive "versioned" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the archive "unversioned" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      """
    And the archive "unversioned" holds the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the archive "unversioned" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """

  # FR-007: a malformed declaration is refused whatever the version (this scenario: versioned).
  Scenario: A slot opened and never closed is refused in a versioned pack, beside the pack with paired markers
    Given the archive "bad" is a copy of the archive "versioned"
    And the archive "bad" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      """
    When I PUT "/packs/slots" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_slot_malformed"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/steps/plan.prompt.md"
    When I PUT "/packs/slots" with the archive "versioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-007: the version does not matter for a malformed declaration (this scenario: unversioned).
  Scenario: A slot opened and never closed is refused in an unversioned pack too, beside its twin
    Given the archive "bad" is a copy of the archive "unversioned"
    And the archive "bad" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      """
    When I PUT "/packs/slots" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_slot_malformed"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/steps/plan.prompt.md"
    When I PUT "/packs/slots" with the archive "unversioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-007: each slot id is declared once.
  Scenario: A slot declared twice is refused, beside the pack that declares it once
    Given the archive "bad" is a copy of the archive "unversioned"
    And the archive "bad" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    When I PUT "/packs/slots" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/code" is "prompt_slot_malformed"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/steps/plan.prompt.md"
    When I PUT "/packs/slots" with the archive "unversioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-007: a slot does not open inside another.
  Scenario: Nested slots are refused, beside the pack whose slots stand apart
    Given the archive "bad" is a copy of the archive "unversioned"
    And the archive "bad" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:outer-block:start -->
      <!-- spdd:inner-block:start --><!-- spdd:inner-block:end -->
      <!-- spdd:outer-block:end -->
      """
    When I PUT "/packs/slots" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/code" is "prompt_slot_malformed"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/steps/plan.prompt.md"
    When I PUT "/packs/slots" with the archive "unversioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-007, the version gate: schema_version "1.0" makes text between paired markers a refusal.
  Scenario: Text in a slot is refused under schema_version 1.0, beside the pack whose slot is empty
    Given the archive "bad" is a copy of the archive "versioned"
    And the archive "bad" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      Legacy guidance that belongs to an enhancement now.
      <!-- spdd:reasons-block:end -->
      """
    When I PUT "/packs/slots" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_slot_not_empty"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/steps/plan.prompt.md"
    When I PUT "/packs/slots" with the archive "versioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-007, NFR-002, OD-7: the unversioned twin of the scenario above registers; the text is a warning, listed in the
  # result's diagnostics at the prompt file, and never a violation.
  Scenario: The same text in an unversioned pack registers and the result lists the warning
    Given the archive "legacy" is a copy of the archive "unversioned"
    And the archive "legacy" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      Legacy guidance that belongs to an enhancement now.
      <!-- spdd:reasons-block:end -->
      """
    When I PUT "/packs/slots" with the archive "legacy"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/diagnostics" holds exactly 1 item(s)
    And the field at "/result/diagnostics/0/code" is "prompt_slot_not_empty"
    And the field at "/result/diagnostics/0/detail" contains "missions/software-dev/steps/plan.prompt.md"
    When I GET "/packs/slots"
    Then the response status is 200
    And the response body has no field at "/diagnostics"

  # OD-7: the member is present, and empty, when the load found nothing to warn about.
  Scenario: A registration with nothing to warn about lists no diagnostics
    When I PUT "/packs/slots" with the archive "unversioned"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/diagnostics" holds exactly 0 item(s)

  # FR-007, the composing read: no read answers a prompt code as the top-level code, it is 500 pack_unreadable.
  Scenario: A closure holding a pack with a malformed slot is unreadable and names the prompt file
    Given the org pack "slots" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      """
    And the org pack "slots" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "slots" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "missions/software-dev/steps/plan.prompt.md"
  # The twin of the scenario above.
  Scenario: A closure holding the same pack with paired markers reads
    Given the org pack "slots" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      """
    And the org pack "slots" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "slots" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    When I GET "/offering"
    Then the response status is 200
  # FR-007, the version gate on a read.
  Scenario: A closure holding a versioned pack with text in a slot is unreadable and names the prompt file
    Given the org pack "slots" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      schema_version: "1.0"
      """
    And the org pack "slots" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "slots" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      Legacy guidance that belongs to an enhancement now.
      <!-- spdd:reasons-block:end -->
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "missions/software-dev/steps/plan.prompt.md"
  # NFR-002: a warning never makes a read unreadable.
  Scenario: A closure holding the unversioned pack with the same text reads
    Given the org pack "slots" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000S1
      pack_version: 1.0.0
      parent_pack: null
      name: slots
      pack_level: org
      """
    And the org pack "slots" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "slots" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start -->
      Legacy guidance that belongs to an enhancement now.
      <!-- spdd:reasons-block:end -->
      """
    When I GET "/offering"
    Then the response status is 200
