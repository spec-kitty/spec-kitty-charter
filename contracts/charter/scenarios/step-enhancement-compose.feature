@contract @activations
Feature: A step enhancement composes only onto a held step whose mission type takes it
  ADR-0066 decision 8, phase 2 (FR-002, SC-002; #438). Two compose checks judge every step enhancement, a
  step_enhancement whose one enhances edge names an action:<type>/<step>:

  - C37, the target held: the step is held (OD-9, StepHolding) by the pack the edge names, and that pack is the
    enhancing pack or one of its depends_on closure. A qualifier naming a pack outside the closure is
    pack_reference_pack_missing; a pack of the closure that does not hold the step (it does not define the mission
    type and declare the step) is pack_reference_artifact_missing.
  - The inherited lock (C22 and C25 as amended, OD-2 (A)): a step takes its mission type's mutability level, judged
    along the closure without the enhancing pack's own policy. An enhancement of a step whose mission type is locked
    is refused: 422 override_not_permitted at registration and on the impact-checked writes, 500 pack_unreadable on
    the reads, whatever the activation selects, as a forbidden layer override is (C21). An extendable mission type
    takes the enhancement.

  Every rule is shown on each surface with one failing fixture and one passing twin that differ only in the faulty
  part: the step the edge names, the pack its qualifier names, or the level of the mission type. The reads are the
  offering and the activation reads; the activation write composes as the reads do. Every fixture follows the
  two-pack shape of step-enhancements.feature: a base pack whose software-dev mission type has a step plan, and an
  enhancing pack that depends on it and holds only the enhancement, its fragment and its one enhances edge (and, for
  the impact-checked write, two directives the write relates).

  # C37 on the reads (FR-002): the twin differs only in the step the edge names.
  Scenario: A step enhancement of a step the named pack does not hold is refused on the reads as pack_reference_artifact_missing
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
        target: base::action:software-dev/nosuch
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"
    And the field at "/detail" contains "step_enhancement:spdd-plan"
    And the field at "/detail" contains "action:software-dev/nosuch"
    And the field at "/packs/0/packId" is "enhancer"
    And the field at "/packs/1/packId" is "base"
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"

  # C37 on the reads (FR-002): the twin differs only in the step the edge names.
  Scenario: The twin: a step enhancement of the step the named pack holds is read
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
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I GET "/offering"
    Then the response status is 200
    And the answer holds exactly 2 packs
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      step_enhancement:spdd-plan
      """

  # C37 on the reads: the pack other is in the compose but not in the enhancer's depends_on closure. The twin
  # differs only in the pack the edge's qualifier names.
  Scenario: A step enhancement whose qualifier names a pack outside its depends_on closure is refused on the reads as pack_reference_pack_missing
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
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000O1
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the org pack "other" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "other" with the file "missions/research/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "other" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:research/gather
        kind: action
        label: gather
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
        target: other::action:software-dev/plan
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field at "/detail" contains "other::action:software-dev/plan"
    And the field at "/packs/0/packId" is "enhancer"

  # C37 on the reads: the pack other is in the compose but not in the enhancer's depends_on closure. The twin
  # differs only in the pack the edge's qualifier names.
  Scenario: The twin: a step enhancement whose qualifier names a pack of its depends_on closure is read
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
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000O1
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the org pack "other" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "other" with the file "missions/research/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "other" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:research/gather
        kind: action
        label: gather
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
    When I GET "/offering"
    Then the response status is 200
    And the answer holds exactly 3 packs

  # C37 at registration (OD-4): the registration composes the closure the pack joins as the reads do, and answers
  # the composition code as 422. The twin archive differs only in the step the edge names.
  Scenario: A registration whose step enhancement names a step its base does not hold is refused, and its twin is accepted
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
      Fill or update the REASONS canvas while composing the plan.
      """
    And the archive "enhancer" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/nosuch
        relation: enhances
      """
    And the archive "enhancer-ok" is a copy of the archive "enhancer"
    And the archive "enhancer-ok" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "pack_reference_artifact_missing"
    And the field at "/problem/detail" contains "action:software-dev/nosuch"
    When I GET "/packs/enhancer"
    Then the response status is 404
    When I PUT "/packs/enhancer" with the archive "enhancer-ok"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # C37 on an impact-checked write (G8): putPackRelationship judges the closure as it will be after the write. The
  # edge it adds is the same in both; the fixtures differ only in the step the enhancement names.
  Scenario: A relationship write into a pack whose step enhancement names a step its base does not hold is refused 422
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
        target: base::action:software-dev/nosuch
        relation: enhances
      """
    And the org pack "enhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "enhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/enhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 422
    And the field at "/code" is "pack_reference_artifact_missing"
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response header "ETag" is the remembered header

  # C37 on an impact-checked write (G8): putPackRelationship judges the closure as it will be after the write. The
  # edge it adds is the same in both; the fixtures differ only in the step the enhancement names.
  Scenario: The twin: the same relationship write into a pack whose step enhancement names the held step is registered
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
    And the org pack "enhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "enhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/enhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 201

  # The inherited lock on the reads (OD-2 (A)): a closure-level refusal, as C21 is, so the activation selects
  # nothing and its reads still refuse. The twin differs only in the mission type's level.
  Scenario: An enhancement of a step whose mission type is locked makes the activation reads unreadable, whatever is selected
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: locked
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "step_enhancement:spdd-plan"
    And the field at "/detail" contains "mission_type:software-dev"
    And the field at "/detail" contains "locked"
    And the field at "/packs/0/packId" is "enhancer"
    And the field at "/packs/1/packId" is "base"
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 500
    And the problem code is "pack_unreadable"

  # The inherited lock on the reads (OD-2 (A)): a closure-level refusal, as C21 is, so the activation selects
  # nothing and its reads still refuse. The twin differs only in the mission type's level.
  Scenario: The twin: an enhancement of a step whose mission type is extendable is read
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: extendable
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}, {"packId": "enhancer"}]}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 200
    When I PUT "/activations/a-1/selections/step_enhancement/spdd-plan" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      step_enhancement:spdd-plan
      """

  # The inherited lock at registration (OD-2 (A)): the impact check names the enhancing pack, then the pack that
  # locks the mission type. The twin differs only in the mission type's level.
  Scenario: A registration that enhances a step whose mission type is locked is refused override_not_permitted
    Given registration is switched on
    And the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: locked
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
      Fill or update the REASONS canvas while composing the plan.
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
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "override_not_permitted"
    And the field at "/problem/detail" contains "mission_type:software-dev"
    And the field at "/problem/packs/0/packId" is "enhancer"
    And the field at "/problem/packs/1/packId" is "base"
    When I GET "/packs/enhancer"
    Then the response status is 404

  # The inherited lock at registration (OD-2 (A)): the impact check names the enhancing pack, then the pack that
  # locks the mission type. The twin differs only in the mission type's level.
  Scenario: The twin: a registration that enhances a step whose mission type is extendable is accepted
    Given registration is switched on
    And the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: extendable
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
      Fill or update the REASONS canvas while composing the plan.
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
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # The inherited lock on an impact-checked write (G8): the edge the write adds is the same in both; the fixtures
  # differ only in the mission type's level.
  Scenario: A relationship write into a pack that enhances a step whose mission type is locked is refused 422 override_not_permitted
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: locked
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
    And the org pack "enhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "enhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/enhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 422
    And the field at "/code" is "override_not_permitted"
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response header "ETag" is the remembered header

  # The inherited lock on an impact-checked write (G8): the edge the write adds is the same in both; the fixtures
  # differ only in the mission type's level.
  Scenario: The twin: the same relationship write into a pack that enhances a step whose mission type is extendable is registered
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000BS
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        mission_type:software-dev: extendable
      """
    And the built-in pack "base" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the built-in pack "base" with the file "mission_type.graph.yaml":
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
    And the org pack "enhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "enhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    When I GET "/packs/enhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/enhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 201
