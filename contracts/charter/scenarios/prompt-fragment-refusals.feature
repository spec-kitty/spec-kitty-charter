@contract @registration
Feature: A malformed step enhancement or prompt fragment is refused with prompt_fragment_invalid
  A step enhancement (ADR-0066, issue #428) is checked when a pack is validated, loaded and registered, and when a
  relationship is written. A fault is a violation of the pack at its file, with the code prompt_fragment_invalid, so a
  registration answers 422 validation_failed listing every fault at once, each with the pack-relative file as its
  pointer, and a read of a closure holding such a pack answers 500 pack_unreadable naming the file. The code is raised
  as a violation code (the amended definition of raised in CharterRefusalCode), and on putPackRelationship it is the
  top-level code of a 422.

  Every fixture is the two-pack shape of the spdd pack: a base pack whose software-dev mission type has a step plan
  (its prompt declares the slot spdd:reasons-block empty) and whose research mission type has a step gather, and an
  enhancing pack that depends on the base and holds only enhancements, their fragments and the enhances edges, each
  written qualified with the base pack's name. Each refusal sits beside its passing twin, which differs in the one
  fault: a registration that refused for another reason could not pass the twin. A refused enhancement is also listed
  with its files as unmodelled after the fault, so the scenarios that point at it assert no count; the multi-fault
  scenario keeps its enhancements held and asserts the exact count.

  Background:
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
    And the archive "base" holds the file "missions/research/research.mission-type.yaml":
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
    And the archive "base" holds the file "missions/research/steps/gather.prompt.md":
      """
      # Gather

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
      - urn: action:research/gather
        kind: action
        label: gather
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
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
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

  # The count of each refusal below is exact. A fault the store itself refuses (a blank or frontmatter fragment, a
  # missing companion, a schema violation, an id that differs from its stem) leaves its enhancement and fragment out
  # of the model, so the report lists them as unmodelled too: three violations, the fault first. The faults the rules
  # judge on held files stand alone.
  # FR-004. The store's companion read refuses a blank fragment and records it at the enhancement's yaml (the
  # reason names the fragment), under prompt_fragment_invalid.
  Scenario: A blank fragment is refused at the enhancement that names it, beside the pack with a fragment that says something
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    And the field at "/problem/violations/0/detail" contains "spdd-plan.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-004. The closed prompt-fragment structure schema refuses frontmatter; the fault is the enhancement yaml's,
  # with the fragment in its detail.
  Scenario: A fragment with frontmatter is refused at the enhancement that names it, beside the pack without
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      ---
      title: Guidance
      ---

      Fill the canvas.
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    And the field at "/problem/violations/0/detail" contains "spdd-plan.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-004. The enhancement is held, so the rule names the companion itself.
  Scenario: A fragment that holds a slot marker is refused at the fragment, beside the pack without one
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill the canvas.

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-005, D11: a repeated slot id. The second entry names its own fragment, so only the repeat is wrong.
  Scenario: An enhancement that names one slot twice is refused at the enhancement, beside the pack that names each once
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
        - slot: spdd:reasons-block
          path: spdd-second.prompt-fragment.md
      """
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-second.prompt-fragment.md":
      """
      Name the alternatives you rejected.
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-005. The store's companion read refuses the missing companion; the reason names it.
  Scenario: An enhancement that names a fragment the pack does not hold is refused at the enhancement, beside the pack that holds it
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-gone.prompt-fragment.md
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    And the field at "/problem/violations/0/detail" contains "spdd-gone.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-005, precedence 1: the step-enhancement schema's slot pattern refuses it first and the file gets no
  # semantic rule. The control is the pack with a slot that fits the grammar.
  Scenario: An entry whose slot breaks <namespace>:<name> is a schema violation, not prompt_fragment_invalid
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: Bad:Id
          path: spdd-plan.prompt-fragment.md
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "schema"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # D12, the empty-list shape: minItems refuses it in the schema (documented in the schema's description).
  Scenario: An enhancement with prompt_fragments: [] is a schema violation, not prompt_fragment_invalid
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments: []
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "schema"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # D12, the absent-key shape (A12): the schema accepts it, the FR-006 rule refuses it. The fragment is left out,
  # so it is no orphan and the enhancement is the only fault.
  Scenario: An enhancement with no prompt_fragments key is refused at the enhancement, beside the pack that has one
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      """
    And the archive "bad" lacks the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # D11, A9: no entry names the fragment. Validate mode and the staged load refuse it; a composing read does not
  # hold it.
  Scenario: An orphan fragment beside a valid pair is refused at the orphan, beside the pack without it
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-stray.prompt-fragment.md":
      """
      Nobody names this one.
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-stray.prompt-fragment.md"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-006, D9: exactly one enhances edge.
  Scenario: An enhancement with two enhances edges is refused at the enhancement, beside the pack with one
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:spdd-plan
        target: base::action:research/gather
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-006, D9 (A12): the target must be an action:<type>/<step>.
  Scenario: An enhances edge that does not name a step is refused at the enhancement, beside the pack whose edge does
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::tactic:x
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-006, D7, C-003: the file sits in missions/software-dev/enhancements/, the edge names a research step.
  Scenario: A directory that disagrees with the edge's type is refused at the enhancement, beside the pack that agrees
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:research/gather
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # R-OVR: an enhancement only adds, so no overrides edge may have it as its source, its own mission type
  # included.
  Scenario: An enhancement that is the source of an overrides edge is refused at the enhancement, beside the pack with only the enhances edge
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:spdd-plan
        target: mission_type:software-dev
        relation: overrides
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    # the same overrides edge, its source qualified with the pack's own name, is the same enhancement
    Given the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      - source: enhancer::step_enhancement:spdd-plan
        target: mission_type:software-dev
        relation: overrides
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # Operator ruling A2 (A12): the base also declares a research mission type; the enhancing pack holds spdd-plan
  # in missions/research/ and in missions/software-dev/, each with its own companion, and one enhances edge, to
  # the first file's step. The second file in path order (missions/software-dev/) is refused at its own file and
  # withheld from the pack: the behaviour before this work was one violation at the pointer "pack" from a thrown
  # validation_failed. The twin renames the research one spdd-research and gives it an edge of its own.
  Scenario: An id that repeats across type directories is refused at the second file, not as one pack violation
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/research/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the archive "bad" holds the file "missions/research/enhancements/spdd-plan.prompt-fragment.md":
      """
      Name the question before the sources.
      """
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:research/gather
        relation: enhances
      """
    And the archive "twin" is a copy of the archive "enhancer"
    And the archive "twin" holds the file "missions/research/enhancements/spdd-research.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-research
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-research.prompt-fragment.md
      """
    And the archive "twin" holds the file "missions/research/enhancements/spdd-research.prompt-fragment.md":
      """
      Name the question before the sources.
      """
    And the archive "twin" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:spdd-research
        target: base::action:research/gather
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 2 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "twin"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # Operator ruling A1: the store's stem rule refuses spdd-plan.step-enhancement.yaml declaring id: spdd-other as
  # a schema violation, as the slot grammar and prompt_fragments: [] are. The control is the pack whose id is its
  # stem.
  Scenario: An id that differs from the file stem is a schema violation, not prompt_fragment_invalid
    Given the archive "bad" is a copy of the archive "enhancer"
    And the archive "bad" holds the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-other
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "schema"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "enhancer"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # T-07, E-5. The four enhancements stay held, so no unmodelled entry joins the four violations and the count is
  # exact: a slot marker in a's fragment, two enhances edges on b, an overrides edge from c and a directory
  # mismatch on d. A fault the store refuses (blank, frontmatter, missing companion) is never part of a counted
  # fixture.
  Scenario: A pack with four faulty enhancements is refused with all four faults at once, each at its file
    Given the archive "quad" holds the file "pack.yaml":
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
    And the archive "quad" holds the file "missions/software-dev/enhancements/a.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: a
      prompt_fragments:
        - slot: spdd:reasons-block
          path: a.prompt-fragment.md
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/a.prompt-fragment.md":
      """
      Guidance a.
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/b.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: b
      prompt_fragments:
        - slot: spdd:reasons-block
          path: b.prompt-fragment.md
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/b.prompt-fragment.md":
      """
      Guidance b.
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/c.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: c
      prompt_fragments:
        - slot: spdd:reasons-block
          path: c.prompt-fragment.md
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/c.prompt-fragment.md":
      """
      Guidance c.
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/d.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: d
      prompt_fragments:
        - slot: spdd:reasons-block
          path: d.prompt-fragment.md
      """
    And the archive "quad" holds the file "missions/software-dev/enhancements/d.prompt-fragment.md":
      """
      Guidance d.
      """
    And the archive "quad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:a
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:b
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:c
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:d
        target: base::action:software-dev/plan
        relation: enhances
      """
    And the archive "bad" is a copy of the archive "quad"
    And the archive "bad" holds the file "missions/software-dev/enhancements/a.prompt-fragment.md":
      """
      Guidance a.

      <!-- spdd:reasons-block:start -->
      """
    And the archive "bad" holds the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:a
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:b
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:b
        target: base::action:research/gather
        relation: enhances
      - source: step_enhancement:c
        target: base::action:software-dev/plan
        relation: enhances
      - source: step_enhancement:c
        target: mission_type:software-dev
        relation: overrides
      - source: step_enhancement:d
        target: base::action:research/gather
        relation: enhances
      """
    When I PUT "/packs/enhancer" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 4 item(s)
    And the field at "/problem/violations/0/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/0/pointer" is "missions/software-dev/enhancements/a.prompt-fragment.md"
    And the field at "/problem/violations/1/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/1/pointer" is "missions/software-dev/enhancements/b.step-enhancement.yaml"
    And the field at "/problem/violations/2/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/2/pointer" is "missions/software-dev/enhancements/c.step-enhancement.yaml"
    And the field at "/problem/violations/3/code" is "prompt_fragment_invalid"
    And the field at "/problem/violations/3/pointer" is "missions/software-dev/enhancements/d.step-enhancement.yaml"
    When I PUT "/packs/enhancer" with the archive "quad"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # E-1, the composing read: the fault is an edge fault (R-OVR), not a binding-level one, so this fails unless the
  # composing read runs the edge rules. No read answers a prompt code as the top-level code: it is 500
  # pack_unreadable.
  Scenario: A closure holding a pack whose enhancement overrides is unreadable and names the file
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      - source: step_enhancement:spdd-plan
        target: mission_type:rdev
        relation: overrides
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml"

  # E-1, the composing read, a second edge fault.
  Scenario: A closure holding a pack whose enhancement has two enhances edges is unreadable and names the file
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      - source: step_enhancement:spdd-plan
        target: rbase::action:rres/gather
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml"

  # The 200 beside the two 500s above: the closure differs only in the enhancement's edges.
  Scenario: The positive control: the same closure with the valid pack is read
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 200

  # R-REL: putPackRelationship refuses, before anything is committed, an edge that would give a held step
  # enhancement an overrides edge (R-OVR). It is the one operation that carries prompt_fragment_invalid as the
  # top-level code (422); a read of the pack keeps its ETag.
  Scenario: An overrides edge from a held step enhancement is refused and nothing is written
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      """
    And the org pack "renhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "renhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    Then the contract says "prompt_fragment_invalid" is answered with status 422 by the operation "putPackRelationship"
    And the contract says "prompt_fragment_invalid" is answered with status 500
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/renhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "step_enhancement:spdd-plan", "to": "mission_type:rdev", "type": "overrides"}
      """
    Then the response status is 422
    And the field at "/code" is "prompt_fragment_invalid"
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response header "ETag" is the remembered header

  # R-REL: the held enhancement already has its one enhances edge (the pack loaded), so any new one is a second.
  Scenario: A second enhances edge from a held step enhancement is refused and nothing is written
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      """
    And the org pack "renhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "renhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    Then the contract says "prompt_fragment_invalid" is answered with status 422 by the operation "putPackRelationship"
    And the contract says "prompt_fragment_invalid" is answered with status 500
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/renhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "step_enhancement:spdd-plan", "to": "rbase::action:rres/gather", "type": "enhances"}
      """
    Then the response status is 422
    And the field at "/code" is "prompt_fragment_invalid"
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response header "ETag" is the remembered header

  # R-REL refuses on the source kind: the edge, its type and its target (mission_type:rdev) are those of the first
  # scenario, and only the source differs, a directive of the pack in place of the held step enhancement. The target
  # is written pack-qualified, as a reference to another pack must be; the refusal of the first scenario comes before
  # the target is resolved, so its bare form is refused for the source alone.
  Scenario: The twin: the same overrides edge from a directive of the pack is registered
    Given the org pack "rbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RB
      pack_version: 1.0.0
      parent_pack: null
      name: rbase
      pack_level: org
      """
    And the org pack "rbase" with the file "missions/rdev/rdev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rdev
      title: Software development
      description: Build software
      steps:
        - id: plan
          title: Plan
          role: planner
          prompt_path: steps/plan.prompt.md
      """
    And the org pack "rbase" with the file "missions/rdev/steps/plan.prompt.md":
      """
      # Plan

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "missions/rres/rres.mission-type.yaml":
      """
      schema_version: "1.0"
      id: rres
      title: Research
      description: Research a question
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "rbase" with the file "missions/rres/steps/gather.prompt.md":
      """
      # Gather

      <!-- spdd:reasons-block:start --><!-- spdd:reasons-block:end -->
      """
    And the org pack "rbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rdev/plan
        kind: action
        label: plan
      - urn: action:rres/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "renhancer" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000RE
      pack_version: 1.0.0
      parent_pack: null
      name: renhancer
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000RB
        range: '>=1.0'
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "renhancer" with the file "missions/rdev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill or update the REASONS canvas while composing the plan: the approach, its structure and the
      rejected alternatives.
      """
    And the org pack "renhancer" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: rbase::action:rdev/plan
        relation: enhances
      """
    And the org pack "renhancer" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "renhancer" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/renhancer/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:A", "to": "rbase::mission_type:rdev", "type": "overrides"}
      """
    Then the response status is 201
    When I GET "/packs/renhancer/artifacts/directive/A"
    Then the response header "ETag" differs from the remembered header
