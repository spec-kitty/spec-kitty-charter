@contract @activations
Feature: A step enhancement in force fills only a slot the prompt in force declares
  ADR-0066 decision 8, phase 2 (FR-001, FR-006, SC-002, SC-003; #438). C38, the slot declared: every slot a step
  enhancement in force names is declared by the prompt in force of its step. The prompt in force is the step prompt at
  the merged winner's place of action:<type>/<step> (research R8, StepHolding), so a layer override of the mission type
  that replaces the step's prompt replaces its slots too. Only an enhancement in force refuses (OD-1 (B)), and only the
  winning copy of it is judged. The refusal is prompt_slot_missing, naming the enhancement, its pack, the slot and the
  pack that holds the prompt in force:

  - on a composing read of an activation that puts the enhancement in force, 500 (the configured packs are broken, F6:
    after the composition codes and the merge's pack_unreadable);
  - at registration and on the impact-checked pack writes, 422, once per stored activation whose closure the write
    joins, the activation named in the detail (OD-4 (i), squad A2);
  - on an activation write that would put the enhancement in force, 422, and nothing is stored (squad A3).

  Every fixture has two packs. The org pack base holds the mission type software-dev, whose step plan has a prompt
  that declares the slot spdd:reasons-block, and the step enhancement spdd-plan that fills it. The project pack proj
  replaces software-dev and its step plan with a prompt that does not declare the slot. Each rule is shown on each
  surface with one failing fixture and one passing twin that differ only in the faulty part: whether the activation
  puts spdd-plan in force, or whether proj's prompt declares the slot.

  # C38 on the composing reads: the activation selects spdd-plan and its mission type, so spdd-plan is in force, and
  # the prompt in force of plan is proj's, which does not declare the slot.
  Scenario: An enhancement in force of a slot the prompt in force does not declare refuses every composing read
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"
    And the field at "/detail" contains "step_enhancement:spdd-plan"
    And the field at "/detail" contains "spdd:reasons-block"
    And the field at "/detail" contains "action:software-dev/plan"
    And the field at "/packs/0/packId" is "base"
    And the field at "/packs/1/packId" is "proj"
    When I GET "/activations/a-1/skipped"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"
    When I GET "/activations/a-1/graph"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/context"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"

  # The twin (OD-1 (B)): the same packs, and the activation holds spdd-plan without selecting it. An enhancement that
  # is not in force never refuses.
  Scenario: The twin: the same enhancement, held and not in force, is read
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 200
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/context"
    Then the response status is 200

  # C38 at registration (OD-4 (i), squad A2): proj is registered while the stored activation a-1, whose closure it joins,
  # puts spdd-plan in force. The prompt in force becomes proj's, so the registration is refused and nothing is stored.
  Scenario: A registration that leaves an enhancement in force without its slot is refused 422 prompt_slot_missing
    Given registration is switched on
    And the org pack "base" with the file "pack.yaml":
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    And the archive "proj" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the archive "proj" holds the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the archive "proj" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the archive "proj" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    When I PUT "/packs/proj" with the archive "proj"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "prompt_slot_missing"
    And the field at "/problem/detail" contains "a-1"
    And the field at "/problem/detail" contains "step_enhancement:spdd-plan"
    And the field at "/problem/detail" contains "spdd:reasons-block"
    And the field at "/problem/packs/0/packId" is "base"
    And the field at "/problem/packs/1/packId" is "proj"
    When I GET "/packs/proj"
    Then the response status is 404

  # The twin: no stored activation puts spdd-plan in force, so the same registration is accepted.
  Scenario: The twin: the same registration, while no stored activation puts the enhancement in force, is accepted
    Given registration is switched on
    And the org pack "base" with the file "pack.yaml":
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
      """
    And the archive "proj" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the archive "proj" holds the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the archive "proj" holds the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the archive "proj" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    When I PUT "/packs/proj" with the archive "proj"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # C38 on an impact-checked write (G8, squad A2): putPackRelationship judges every stored activation whose closure the
  # write joins, as the closure will be after the write. The edge it adds is the same in both fixtures; they differ only
  # in whether proj's prompt declares the slot.
  Scenario: A relationship write into a closure an activation puts an enhancement in force in without its slot is refused 422
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the project pack "proj" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the project pack "proj" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    When I GET "/packs/proj/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/proj/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 422
    And the field at "/code" is "prompt_slot_missing"
    And the field at "/detail" contains "a-1"
    And the field at "/detail" contains "spdd:reasons-block"
    When I GET "/packs/proj/artifacts/directive/A"
    Then the response header "ETag" is the remembered header

  Scenario: The twin: the same relationship write, where proj's prompt declares the slot, is registered
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the project pack "proj" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the project pack "proj" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    When I GET "/packs/proj/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/proj/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 201

  # C38 on the activation writes (squad A3): a selection that would put spdd-plan in force is judged before the document
  # is stored, and so is a whole document that selects it. The twin differs only in whether proj's prompt declares the
  # slot.
  Scenario: An activation write that would put an enhancement in force without its slot is refused 422, and nothing is stored
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
      """
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 422
    And the problem code is "prompt_slot_missing"
    And the field at "/detail" contains "step_enhancement:spdd-plan"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag
    And the field at "/config/selected/step_enhancement" is absent
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    Then the response status is 422
    And the problem code is "prompt_slot_missing"
    When I GET "/activations/a-2"
    Then the response status is 404

  Scenario: The twin: the same activation writes, where proj's prompt declares the slot, are stored
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
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan.
      """
    And the org pack "base" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: action:software-dev/plan
        relation: enhances
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PJ
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software, the project's way
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the project pack "proj" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Plan the work, step by step.

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the project pack "proj" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
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
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    Then the response status is 201
