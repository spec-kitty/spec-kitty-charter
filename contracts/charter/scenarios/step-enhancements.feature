@contract @activations
Feature: A step enhancement is held, registers, and is selectable like any other artifact
  A step enhancement (ADR-0066, issue #428) is an artifact of the enhancing pack, at
  missions/<type>/enhancements/<id>.step-enhancement.yaml, with one Markdown companion per entry of its
  prompt_fragments, any stem matching *.prompt-fragment.md. It carries no target: its only enhances edge, in the
  enhancing pack's graph documents, names the step it enhances, qualified with the base pack's name
  (base::action:software-dev/plan), the base pack being one the enhancing pack depends_on. The store holds the file
  and its fragments, so the URN step_enhancement:<id> joins the compose: it can be selected directly and through a
  preset, two packs at one level cannot both define one id, and a pack that carries one registers.

  Selection and holding do not depend on being in force. These scenarios assert that the activation lists the id as
  selected; whether it is in force follows rule E6 (OD-6) and is pinned with the effective reads.

  Every fixture follows the two-pack shape of the spdd pack: a base pack whose software-dev mission type has a step
  plan whose prompt declares the slot spdd:reasons-block empty, and an enhancing pack that holds no mission type, only
  the enhancement, its fragment and one enhances edge, and depends on the base. Each tactic selection beside an
  enhancement selection is the control: a 404 for the enhancement comes from the kind, not from a broken fixture.

  # US2 AS1 (FR-003, SC-003): the enhancement is selectable, with a tactic selection as the positive control.
  Scenario: A held step enhancement is selectable, beside a tactic that is selectable too
    Given the org pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the org pack "base" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: t
      steps:
        - title: Only step
      """
    And the org pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "base" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the org pack "enhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E1
      pack_version: 1.0.0
      parent_pack: null
      name: enhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the rejected
      alternatives.
      """
    And the org pack "enhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I PUT "/activations/a-1/selections/tactic/t" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:t
      """
    When I remember the ETag
    And I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the remembered ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      step_enhancement:spdd-plan
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/step_enhancement" holds exactly the ids:
      """
      spdd-plan
      """
    And the field at "/config/selected/tactic" holds exactly the ids:
      """
      t
      """

  # US1 AS1 (FR-001, FR-002): a pack that carries a step enhancement registers.
  Scenario: A pack that carries a step enhancement and its fragment registers, beside the base it depends on
    Given registration is switched on
    And the archive "base" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the archive "base" holds the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the archive "base" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the archive "base" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the archive "enhancer" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E1
      pack_version: 1.0.0
      parent_pack: null
      name: enhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the archive "enhancer" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the archive "enhancer" holds the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the rejected
      alternatives.
      """
    And the archive "enhancer" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    When I PUT "/packs/base" with the archive "base"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/problem" is absent
    When I GET "/packs/enhancer"
    Then the response status is 200
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      step_enhancement:spdd-plan
      """

  # US2 AS3 (FR-003): one id in two sibling packs is a conflict.
  Scenario: Two packs of one level that define one step enhancement id are refused pack_sibling_conflict
    Given the org pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the org pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "base" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the org pack "enhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E1
      pack_version: 1.0.0
      parent_pack: null
      name: enhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "enhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E2
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "rival" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "rival" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Name the risks of the plan before the structure.
      """
    And the org pack "rival" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    And the field at "/detail" contains "step_enhancement:spdd-plan"

  # US2 AS3, the positive twin: two sibling enhancers of one step with different ids compose.
  Scenario: Two packs of one level that enhance one step with different step enhancement ids compose
    Given the org pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the org pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "base" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the org pack "enhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E1
      pack_version: 1.0.0
      parent_pack: null
      name: enhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "enhancer" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "enhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000E2
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000BS
        range: '>=1.0'
      """
    And the org pack "rival" with the file "missions/software-dev/enhancements/rival-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: rival-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: rival-plan.prompt-fragment.md
      """
    And the org pack "rival" with the file "missions/software-dev/enhancements/rival-plan.prompt-fragment.md":
      """
      Name the risks of the plan before the structure.
      """
    And the org pack "rival" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:rival-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 200
    And the answer holds exactly 3 packs
