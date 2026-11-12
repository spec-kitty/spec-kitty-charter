# Mission effective-step-and-action-context (WP07; FR-003 to FR-005, FR-007, SC-001; research R5, R8, R10; squad R1, R6,
# D1, A10, A11): the effective prompt of one step, getEffectiveStepPrompt. Written red first (C-004): the scenarios were
# committed with every step bound and failed against the 404 of a path the contract did not declare, then the read was
# served. Every refusal is read beside a twin that differs only in the faulty part, and every absence beside a positive
# control in the same answer. The fixtures are authored inline, but for SC-001, which reads charter's example packs.
#
# Steps this file adds to the vocabulary of README.md (bound in EffectiveGraphSteps and CharterSteps):
# - `Given the example pack "<name>" of the charter` configures one of charter's example packs (ADR-0070;
#   service/packs/src/testFixtures/resources/example-packs), every file as it is held, under its own name and at its own
#   level: built-in, org-base or project.
# - `Then the prompt holds exactly the slots:` followed by a doc string, in the answer's order: one line per fragment,
#   `<slot> <level> <packId> "<packName>" <enhancement>`, and `<slot> (empty)` for a slot no fragment fills.
# - `Then the field at "<json-pointer>" is the file "<path>" of the pack "<name>"` - the string member is the held file,
#   byte for byte.

@contract @activations
Feature: The effective prompt of a step is the prompt in force with the fragments in force of each slot
  getEffectiveStepPrompt answers, for one step of a mission type the activation puts in force, the prompt in force as it
  is stored (ADR-0066 decision 9: unchanged; research R8: at the merged winner's place of the step) and every slot it
  declares, in declaration order, each with the fragments of the step enhancements in force that fill it (decision 4:
  nothing pulls an enhancement into force), ordered by pack level, then pack name, then enhancement id, by code point
  (decision 5), each with its pack and enhancement. A slot nothing fills is listed with no fragments; a slot that still
  holds legacy text is listed with its fragments and a prompt_slot_not_empty diagnostic (OD-7, OD-8). The refusals come
  in one order (research R10): 404 activation_not_found, mission_type_not_found, action_not_found (judged on the merged
  closure), 409 mission_type_not_in_force for a mission type the closure holds and the activation does not put in force
  (OD-5), then the 500 of a closure that does not compose, prompt_slot_missing included, and only then the ETag and a
  304. The action context of a mission type in force links the read as prompt.

  # The fixture of most scenarios: the org pack base holds software-dev, whose step plan declares two slots, implement
  # declares none and review has no prompt, and research, whose step gather has a prompt. Two step enhancements of base
  # fill plan's spdd:reasons-block: spdd-plan and risk-notes. The pack other is configured and outside every activation's
  # closure. a-1 selects software-dev and spdd-plan; a-2 selects software-dev, research and both enhancements.
  Background:
    Given the org pack "base" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000BASE
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
        - id: implement
          title: Implement
          role: implementer
          prompt_path: steps/implement.prompt.md
        - id: review
          title: Review
          role: reviewer
      """
    And the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Write the plan.

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->

      <!-- team:checklist:start -->
      <!-- team:checklist:end -->
      """
    And the org pack "base" with the file "missions/software-dev/steps/implement.prompt.md":
      """
      # Implement

      Write the code.
      """
    And the org pack "base" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Find things out
      steps:
        - id: gather
          title: Gather
          role: researcher
          prompt_path: steps/gather.prompt.md
      """
    And the org pack "base" with the file "missions/research/steps/gather.prompt.md":
      """
      # Gather

      Gather the sources.
      """
    And the org pack "base" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:software-dev/review
        kind: action
        label: review
      - urn: action:research/gather
        kind: action
        label: gather
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
      Fill the REASONS canvas before the concern map.
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/risk-notes.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: risk-notes
      prompt_fragments:
        - slot: spdd:reasons-block
          path: risk-notes.prompt-fragment.md
      """
    And the org pack "base" with the file "missions/software-dev/enhancements/risk-notes.prompt-fragment.md":
      """
      List each risk with the step that retires it.
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
      - source: step_enhancement:risk-notes
        target: action:software-dev/plan
        relation: enhances
      """
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000THER
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the org pack "other" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["spdd-plan"]}}
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "base"}], "selected": {"mission_type": ["software-dev", "research"], "step_enhancement": ["spdd-plan", "risk-notes"]}}
      """

  Scenario: SC-001 - on charter's example packs, plan's slot holds plan-risk-notes of built-in, then spdd-plan of org-base
    SC-001, FR-003: the activation selects software-dev and the two example enhancements of software-dev/plan. The prompt
    in force is built-in's plan prompt, as it is stored; it declares one slot, spdd:reasons-block, which both fragments
    fill, built-in's first (decision 5: level first), each with its pack, its pack's name and its enhancement. The pack
    is configured under its example name, so packId and packName agree here.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["plan-risk-notes", "spdd-plan"]}}
      """
    When I GET "/activations/a-3/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/software-dev/steps/plan.prompt.md" of the pack "built-in"
    And the field at "/promptPackId" is "built-in"
    And the prompt holds exactly the slots:
      """
      spdd:reasons-block built-in built-in "built-in" plan-risk-notes
      spdd:reasons-block org org-base "org-base" spdd-plan
      """
    And the field at "/slots/0/fragments/0/text" contains "### Risk Notes - Plan"
    And the field at "/slots/0/fragments/1/text" contains "### REASONS Guidance - Plan"
    And the field at "/diagnostics" holds exactly 0 items
    And the field at "/missionType" is "software-dev"
    And the field at "/action" is "plan"

  Scenario: SC-001, squad R1 - the fragments of a slot are ordered by level, then pack name, then enhancement id, and in no other order
    Decision 5 on a fixture where every other key gives another order: four enhancements in force fill plan's slot, one
    per pack. The two org packs' configured ids sort opposite to their names (zz-org-arch is architecture-notes, aa-spdd
    is spdd), the built-in enhancement's id sorts after every other, and the project's before every other. Ordered by
    id, by packId or by name alone, the four come out otherwise.
    Given the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01M774HB000000000000000BN1
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
      """
    And the built-in pack "built-in" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "built-in" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Write the plan.

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->
      """
    And the built-in pack "built-in" with the file "missions/software-dev/enhancements/zz-base-note.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: zz-base-note
      prompt_fragments:
        - slot: spdd:reasons-block
          path: zz-base-note.prompt-fragment.md
      """
    And the built-in pack "built-in" with the file "missions/software-dev/enhancements/zz-base-note.prompt-fragment.md":
      """
      Name the constraint each decision answers.
      """
    And the built-in pack "built-in" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges:
      - source: step_enhancement:zz-base-note
        target: action:software-dev/plan
        relation: enhances
      """
    And the org pack "zz-org-arch" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000ARCH
      pack_version: 1.0.0
      parent_pack: null
      name: architecture-notes
      pack_level: org
      depends_on:
      - pack_id: 01M774HB000000000000000BN1
        range: '>=1.0'
      """
    And the org pack "zz-org-arch" with the file "missions/software-dev/enhancements/y-arch-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: y-arch-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: y-arch-plan.prompt-fragment.md
      """
    And the org pack "zz-org-arch" with the file "missions/software-dev/enhancements/y-arch-plan.prompt-fragment.md":
      """
      Name the ADR each decision answers to.
      """
    And the org pack "zz-org-arch" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:y-arch-plan
        target: built-in::action:software-dev/plan
        relation: enhances
      """
    And the org pack "aa-spdd" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000SPDD
      pack_version: 1.0.0
      parent_pack: null
      name: spdd
      pack_level: org
      depends_on:
      - pack_id: 01M774HB000000000000000BN1
        range: '>=1.0'
      """
    And the org pack "aa-spdd" with the file "missions/software-dev/enhancements/spdd-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: spdd-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: spdd-plan.prompt-fragment.md
      """
    And the org pack "aa-spdd" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill the REASONS canvas before the concern map.
      """
    And the org pack "aa-spdd" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:spdd-plan
        target: built-in::action:software-dev/plan
        relation: enhances
      """
    And the project pack "team" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000TEAM
      pack_version: 1.0.0
      parent_pack: null
      name: team
      pack_level: project
      depends_on:
      - pack_id: 01M774HB000000000000000BN1
        range: '>=1.0'
      """
    And the project pack "team" with the file "missions/software-dev/enhancements/a-team-plan.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: a-team-plan
      prompt_fragments:
        - slot: spdd:reasons-block
          path: a-team-plan.prompt-fragment.md
      """
    And the project pack "team" with the file "missions/software-dev/enhancements/a-team-plan.prompt-fragment.md":
      """
      Link the team's ADR index.
      """
    And the project pack "team" with the file "enhancements.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: step_enhancement:a-team-plan
        target: built-in::action:software-dev/plan
        relation: enhances
      """
    And the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "aa-spdd"}, {"packId": "zz-org-arch"}, {"packId": "team"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["a-team-plan", "spdd-plan", "y-arch-plan", "zz-base-note"]}}
      """
    When I GET "/activations/a-3/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the prompt holds exactly the slots:
      """
      spdd:reasons-block built-in built-in "built-in" zz-base-note
      spdd:reasons-block org zz-org-arch "architecture-notes" y-arch-plan
      spdd:reasons-block org aa-spdd "spdd" spdd-plan
      spdd:reasons-block project team "team" a-team-plan
      """
    And the field at "/slots/0/fragments/3/text" contains "Link the team's ADR index."
    And the field at "/promptPackId" is "built-in"

  Scenario: FR-003 - an enhancement held and not in force is absent, a slot nothing fills is listed empty, and a prompt with no slot lists none
    Decision 4: risk-notes is held by base and fills plan's spdd:reasons-block, and a-1 does not select it, so its
    fragment is absent, while spdd-plan, which a-1 selects, is listed: the positive control in the same answer. a-2, the
    twin, selects risk-notes too and lists both, risk-notes first (same pack, so by id). team:checklist is declared and
    nothing fills it, so it is listed with no fragments. implement's prompt declares no slot; review has no prompt at all,
    so its prompt is empty and no pack is named (squad A11).
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/software-dev/steps/plan.prompt.md" of the pack "base"
    And the field at "/promptPackId" is "base"
    And the prompt holds exactly the slots:
      """
      spdd:reasons-block org base "base" spdd-plan
      team:checklist (empty)
      """
    And the field at "/diagnostics" holds exactly 0 items
    When I GET "/activations/a-2/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the prompt holds exactly the slots:
      """
      spdd:reasons-block org base "base" risk-notes
      spdd:reasons-block org base "base" spdd-plan
      team:checklist (empty)
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/software-dev/steps/implement.prompt.md" of the pack "base"
    And the field at "/promptPackId" is "base"
    And the field at "/slots" holds exactly 0 items
    When I GET "/activations/a-1/mission-types/software-dev/actions/review/prompt"
    Then the response status is 200
    And the field at "/prompt" is ""
    And the field at "/promptPackId" is absent
    And the field at "/slots" holds exactly 0 items

  Scenario: FR-004 - a mission type the closure holds and the activation does not put in force is 409, where the context answers empty
    OD-5, research R5: a-1 does not select research, so research/gather has no step to run and its prompt is refused 409
    mission_type_not_in_force, also against an If-None-Match that * would otherwise match; the action context of the same
    action answers 200 with no artifacts, and names research in missionTypeNotInForce. a-2, the twin, selects research
    and reads the prompt.
    When I GET "/activations/a-1/mission-types/research/actions/gather/prompt"
    Then the response status is 409
    And the problem code is "mission_type_not_in_force"
    And the field at "/detail" contains "research"
    When I GET "/activations/a-1/mission-types/research/actions/gather/prompt" with the header "If-None-Match": "*"
    Then the response status is 409
    And the problem code is "mission_type_not_in_force"
    When I GET "/activations/a-1/mission-types/research/actions/gather/context"
    Then the response status is 200
    And the field at "/artifacts" holds exactly 0 items
    And the field at "/skipped/missionTypeNotInForce" holds exactly the ids:
      """
      mission_type:research
      """
    When I GET "/activations/a-2/mission-types/research/actions/gather/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/research/steps/gather.prompt.md" of the pack "base"

  Scenario: Research R10 - each 404 is judged before the 304, and so is the 409; a path segment outside its pattern is 400
    The order of the refusals: an unknown activation, then a mission type the merged closure does not hold, then an action
    it does not hold for the mission type, judged on the closure whether or not the mission type is in force (research is
    not in force for a-1, and its unknown action is 404, not 409). Each is sent with If-None-Match: *, which matches any
    tag, and is refused all the same. The control is the read of a held step in force, which * makes a 304 with no body.
    When I GET "/activations/unknown/mission-types/software-dev/actions/plan/prompt" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/a-1/mission-types/missing/actions/plan/prompt" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/missing/prompt" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/research/actions/missing/prompt" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt" with the header "If-None-Match": "*"
    Then the response status is 304
    And the response body is empty
    When I GET "/activations/a-1/mission-types/Software-Dev/actions/plan/prompt"
    Then the response status is 400
    And the problem has no code

  Scenario: Research R10 - the ETag changes with a fragment's bytes and not with a pack outside the closure
    A matching If-None-Match is a 304. An edit of nothing but the bytes of spdd-plan's fragment, a file of the closure,
    moves the tag, and the read answers the new text. An edit of a file of other, a configured pack outside a-1's closure,
    leaves the tag as it was, and the read answers 304 to it.
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    Given the org pack "base" with the file "missions/software-dev/enhancements/spdd-plan.prompt-fragment.md":
      """
      Fill the REASONS canvas before the concern map, and keep it current.
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/slots/0/fragments/0/text" contains "and keep it current."
    When I remember the header "ETag"
    Given the org pack "other" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X, edited
      intent: X
      enforcement: required
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the response header "ETag" is the remembered header
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt" with If-None-Match the remembered header "ETag"
    Then the response status is 304

  Scenario: FR-007 - the context of an action of a mission type in force links its prompt, and one not in force does not
    The context of software-dev/plan, in force for a-1, carries the relation prompt, which leads to the prompt read of the
    same action; the prompt links back to the context as effective-context and to the step under the activation as
    effective-artifact. The context of research/gather, not in force for a-1, carries no prompt link (a link is an
    available action, and that read would answer 409); its twin under a-2, which puts research in force, carries it.
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    When I follow the link "prompt"
    Then the response status is 200
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    And the field at "/_links/effective-context/href" is "/api/v1/activations/a-1/mission-types/software-dev/actions/plan/context"
    And the field at "/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/plan"
    When I follow the link "effective-context"
    Then the response status is 200
    When I GET "/activations/a-1/mission-types/research/actions/gather/context"
    Then the response status is 200
    And the answer has no link "prompt"
    When I GET "/activations/a-2/mission-types/research/actions/gather/context"
    Then the response status is 200
    When I follow the link "prompt"
    Then the response status is 200

  Scenario: FR-005, C-005 - a slot that still holds legacy text is listed with its fragments and a prompt_slot_not_empty diagnostic
    The warning path of the load rule (an error-handling test): base's descriptor declares no schema_version, so legacy
    text between plan's paired markers is loaded with a warning, not refused. The read lists the slot with its fragment,
    and no flag, and its diagnostics name the slot. The twin is the first read, of the same prompt with the slot empty:
    no diagnostic.
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the field at "/diagnostics" holds exactly 0 items
    Given the org pack "base" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # Plan

      Write the plan.

      <!-- spdd:reasons-block:start -->
      Keep the legacy guidance.
      <!-- spdd:reasons-block:end -->

      <!-- team:checklist:start -->
      <!-- team:checklist:end -->
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/software-dev/steps/plan.prompt.md" of the pack "base"
    And the prompt holds exactly the slots:
      """
      spdd:reasons-block org base "base" spdd-plan
      team:checklist (empty)
      """
    And the field at "/diagnostics" holds exactly 1 item
    And the field at "/diagnostics/0/code" is "prompt_slot_not_empty"
    And the field at "/diagnostics/0/detail" contains "spdd:reasons-block"
    And the field at "/diagnostics/0/detail" contains "missions/software-dev/steps/plan.prompt.md"

  Scenario: Research R10 - a closure whose enhancement in force names a slot the prompt in force does not declare is 500 before the 304
    C38 on the prompt read: proj replaces software-dev and its plan prompt with one that declares no slot, and a-3 puts
    spdd-plan in force, so the read is refused 500 prompt_slot_missing, also against If-None-Match: *. The twin, a-4,
    differs only in not selecting spdd-plan, and answers proj's prompt, with no slot.
    Given the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M774HB00000000000000PR0J
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
    And the stored file of the activation "a-3" holds:
      """
      {"packs":[{"packId":"base"},{"packId":"proj"}],"schemaVersion":2,"selected":{"mission_type":["software-dev"],"step_enhancement":["spdd-plan"]}}
      """
    And the stored file of the activation "a-4" holds:
      """
      {"packs":[{"packId":"base"},{"packId":"proj"}],"schemaVersion":2,"selected":{"mission_type":["software-dev"]}}
      """
    When I GET "/activations/a-3/mission-types/software-dev/actions/plan/prompt" with the header "If-None-Match": "*"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"
    And the field at "/detail" contains "spdd:reasons-block"
    When I GET "/activations/a-4/mission-types/software-dev/actions/plan/prompt"
    Then the response status is 200
    And the field at "/prompt" is the file "missions/software-dev/steps/plan.prompt.md" of the pack "proj"
    And the field at "/promptPackId" is "proj"
    And the field at "/slots" holds exactly 0 items
