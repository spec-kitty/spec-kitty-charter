# Mission effective-step-and-action-context (WP02, FR-008 to FR-010, SC-004; research R1 to R4; plan squad R2, R3, R7,
# R8): the ownership rule of the effective action context, operator ruling of 2026-11-12 (Decision Moment
# 01M774HQ8N5EVYDRTP16MBJCN3, option B). An in-force directive, tactic or paradigm applies to the action mt/a when the
# action's walk reaches it (appliesThrough action), when mission_type:mt scopes it (mission_type), or when no action and no
# mission type scopes it (project); what ADR-0018's steps 2 to 5 reach from it comes with it, under the same appliesThrough.
# One scoped only by other actions or other mission types is absent, whatever is in force (research R2). Written red first:
# the scenarios were committed before the context carried appliesThrough.
#
# The example table of the first scenario is the one home of the evidence of 2026-11-12 (squad R8): the 16 directives of
# software-dev/implement, as spec.md's section "Evidence (2026-11-12)" lists them; the docs link to it. The fixture mirrors
# the evidence's shape: the org pack holds fourteen of them and requires NO_FULL_HEAVY_SUITES_IN_MISSION through its
# governing preset, the project pack holds PROJECT_001 and PROJECT_002, and DIRECTIVE_003 is scoped by three other actions.
#
# Steps this file adds to the vocabulary of README.md (bound in EffectiveGraphSteps):
# - `Then the context lists "<kind>:<id>" as "<expected>"`: `listed / <appliesThrough>` when exactly one item of the
#   context's artifacts is that artifact and carries that appliesThrough, `absent` when no item is.

@contract @activations
Feature: The effective action context lists what applies to the action under the ownership rule
  getEffectiveActionContext lists, beside what the action's walk reaches, the in-force directives, tactics and paradigms
  its mission type scopes and those no action and no mission type scopes, with what ADR-0018's steps 2 to 5 reach from
  them. Each item says which rule brought it in (appliesThrough). An artifact scoped only by other actions or other
  mission types is not listed, and neither is an unscoped artifact of another kind that nothing listed reaches.

  Scenario Outline: The 16 directives of software-dev/implement: <directive> is <expected>
    SC-004, FR-008, FR-010: the 2026-11-12 evidence. Seven directives are scoped by action:software-dev/implement and are
    listed through the action. DIRECTIVE_003 is scoped by plan, review and specify, never by implement: it is in force and
    absent, scoped only by other actions. The eight others are scoped by no action and no mission type, so they are listed
    project-wide, the org-required one and the two of the project pack among them. The context lists 15 of the 16.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: software-dev
      description: software-dev
      steps:
        - id: specify
          title: specify
          role: implementer
        - id: plan
          title: plan
          role: implementer
        - id: implement
          title: implement
          role: implementer
        - id: review
          title: review
          role: implementer
      """
    And the org pack "main" with the file "directives/DIRECTIVE_001.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_001
      title: DIRECTIVE_001
      intent: DIRECTIVE_001
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_003.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_003
      title: DIRECTIVE_003
      intent: DIRECTIVE_003
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_010.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_010
      title: DIRECTIVE_010
      intent: DIRECTIVE_010
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_025.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_025
      title: DIRECTIVE_025
      intent: DIRECTIVE_025
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_030.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_030
      title: DIRECTIVE_030
      intent: DIRECTIVE_030
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_032.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_032
      title: DIRECTIVE_032
      intent: DIRECTIVE_032
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_034.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_034
      title: DIRECTIVE_034
      intent: DIRECTIVE_034
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_037.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_037
      title: DIRECTIVE_037
      intent: DIRECTIVE_037
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_041.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_041
      title: DIRECTIVE_041
      intent: DIRECTIVE_041
      enforcement: required
      """
    And the org pack "main" with the file "directives/DIRECTIVE_051.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_051
      title: DIRECTIVE_051
      intent: DIRECTIVE_051
      enforcement: required
      """
    And the org pack "main" with the file "directives/NO_FULL_HEAVY_SUITES_IN_MISSION.directive.yaml":
      """
      schema_version: '1.0'
      id: NO_FULL_HEAVY_SUITES_IN_MISSION
      title: NO_FULL_HEAVY_SUITES_IN_MISSION
      intent: NO_FULL_HEAVY_SUITES_IN_MISSION
      enforcement: required
      """
    And the org pack "main" with the file "directives/QA_003_NO_FAKE_GREEN.directive.yaml":
      """
      schema_version: '1.0'
      id: QA_003_NO_FAKE_GREEN
      title: QA_003_NO_FAKE_GREEN
      intent: QA_003_NO_FAKE_GREEN
      enforcement: required
      """
    And the org pack "main" with the file "directives/RECONCILE_CHANGE_SCOPE_TENSIONS.directive.yaml":
      """
      schema_version: '1.0'
      id: RECONCILE_CHANGE_SCOPE_TENSIONS
      title: RECONCILE_CHANGE_SCOPE_TENSIONS
      intent: RECONCILE_CHANGE_SCOPE_TENSIONS
      enforcement: required
      """
    And the org pack "main" with the file "directives/USE_C4_MODEL_TECHNIQUES.directive.yaml":
      """
      schema_version: '1.0'
      id: USE_C4_MODEL_TECHNIQUES
      title: USE_C4_MODEL_TECHNIQUES
      intent: USE_C4_MODEL_TECHNIQUES
      enforcement: required
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: NO_FULL_HEAVY_SUITES_IN_MISSION
          must_have: true
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:software-dev/plan
        kind: action
        label: plan
      - urn: action:software-dev/review
        kind: action
        label: review
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges:
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_001
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_010
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_025
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_030
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_034
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_037
        relation: scope
      - source: action:software-dev/implement
        target: directive:DIRECTIVE_051
        relation: scope
      - source: action:software-dev/plan
        target: directive:DIRECTIVE_003
        relation: scope
      - source: action:software-dev/review
        target: directive:DIRECTIVE_003
        relation: scope
      - source: action:software-dev/specify
        target: directive:DIRECTIVE_003
        relation: scope
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "directives/PROJECT_001.directive.yaml":
      """
      schema_version: '1.0'
      id: PROJECT_001
      title: PROJECT_001
      intent: PROJECT_001
      enforcement: required
      """
    And the project pack "proj" with the file "directives/PROJECT_002.directive.yaml":
      """
      schema_version: '1.0'
      id: PROJECT_002
      title: PROJECT_002
      intent: PROJECT_002
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "directive": ["DIRECTIVE_001", "DIRECTIVE_003", "DIRECTIVE_010", "DIRECTIVE_025", "DIRECTIVE_030", "DIRECTIVE_032", "DIRECTIVE_034", "DIRECTIVE_037", "DIRECTIVE_041", "DIRECTIVE_051", "PROJECT_001", "PROJECT_002", "QA_003_NO_FAKE_GREEN", "RECONCILE_CHANGE_SCOPE_TENSIONS", "USE_C4_MODEL_TECHNIQUES"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the field at "/artifacts" holds exactly 15 items
    And the context lists "directive:<directive>" as "<expected>"
    When I GET "/activations/a-1/artifacts/directive/<directive>"
    Then the response status is 200
    And the field at "/state" is "active"

    Examples:
      | directive                       | scoped by                                            | expected         |
      | DIRECTIVE_001                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_010                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_025                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_030                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_034                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_037                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_051                   | action:software-dev/implement                        | listed / action  |
      | DIRECTIVE_003                   | action:software-dev/plan, review and specify         | absent           |
      | DIRECTIVE_032                   | none                                                 | listed / project |
      | DIRECTIVE_041                   | none                                                 | listed / project |
      | NO_FULL_HEAVY_SUITES_IN_MISSION | none (org-required)                                  | listed / project |
      | QA_003_NO_FAKE_GREEN            | none                                                 | listed / project |
      | RECONCILE_CHANGE_SCOPE_TENSIONS | none                                                 | listed / project |
      | USE_C4_MODEL_TECHNIQUES         | none                                                 | listed / project |
      | PROJECT_001                     | none (project pack)                                  | listed / project |
      | PROJECT_002                     | none (project pack)                                  | listed / project |

  Scenario Outline: A type-scoped directive is listed through the mission type, and another type's stays out while research is <research>
    FR-008, research R1 and R2, plan squad R2: mission_type:software-dev scopes TW, so the context of software-dev/implement
    lists it through the mission type. mission_type:research scopes RT: it is in force, the read of the artifact says so,
    and it is absent from software-dev/implement whether research is in force or not, because ownership is judged on the
    authored scope edges and not on what is in force (turning research off does not make RT project-wide). REQ is required
    by the governing preset and scoped only by action:software-dev/plan: in force and absent. PW is scoped by nothing and is
    listed project-wide, with the procedure pq it requires. The procedure pr is in force and scoped by nothing, but a
    procedure is no root of the ownership rule and nothing listed reaches it, so it is absent: pq is its twin, which differs
    only in the requires edge. A is the action's own entry, the control of the first pass. The context of research/gather
    lists RT through its mission type while research is in force, and nothing while it is not.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: software-dev
      description: software-dev
      steps:
        - id: implement
          title: implement
          role: implementer
        - id: plan
          title: plan
          role: implementer
      """
    And the org pack "main" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: research
      description: research
      steps:
        - id: gather
          title: gather
          role: implementer
      """
    And the org pack "main" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "main" with the file "directives/PW.directive.yaml":
      """
      schema_version: '1.0'
      id: PW
      title: PW
      intent: PW
      enforcement: required
      """
    And the org pack "main" with the file "directives/REQ.directive.yaml":
      """
      schema_version: '1.0'
      id: REQ
      title: REQ
      intent: REQ
      enforcement: required
      """
    And the org pack "main" with the file "directives/RT.directive.yaml":
      """
      schema_version: '1.0'
      id: RT
      title: RT
      intent: RT
      enforcement: required
      """
    And the org pack "main" with the file "directives/TW.directive.yaml":
      """
      schema_version: '1.0'
      id: TW
      title: TW
      intent: TW
      enforcement: required
      """
    And the org pack "main" with the file "procedures/pq.procedure.yaml":
      """
      schema_version: '1.0'
      id: pq
      title: pq
      description: pq
      entry_condition: A defect is reported.
      exit_condition: The defect is fixed.
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "procedures/pr.procedure.yaml":
      """
      schema_version: '1.0'
      id: pr
      title: pr
      description: pr
      entry_condition: A defect is reported.
      exit_condition: The defect is fixed.
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: REQ
          must_have: true
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:software-dev/plan
        kind: action
        label: plan
      - urn: action:research/gather
        kind: action
        label: gather
      edges:
      - source: action:software-dev/implement
        target: directive:A
        relation: scope
      - source: action:software-dev/plan
        target: directive:REQ
        relation: scope
      - source: mission_type:software-dev
        target: directive:TW
        relation: scope
      - source: mission_type:research
        target: directive:RT
        relation: scope
      - source: directive:PW
        target: procedure:pq
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": [<mission types>], "directive": ["A", "PW", "RT", "TW"], "procedure": ["pq", "pr"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:A active/selected action
      directive:PW active/selected project
      directive:TW active/selected mission_type
      procedure:pq active/selected project
      """
    When I GET "/activations/a-1/artifacts/directive/RT"
    Then the response status is 200
    And the field at "/state" is "active"
    When I GET "/activations/a-1/artifacts/directive/REQ"
    Then the response status is 200
    And the field at "/state" is "active"
    And the field at "/reason" is "required"
    When I GET "/activations/a-1/artifacts/procedure/pr"
    Then the response status is 200
    And the field at "/state" is "active"
    When I GET "/activations/a-1/mission-types/research/actions/gather/context"
    Then the response status is 200
    And the field at "/artifacts" holds exactly <gather items> items
    And the context lists "directive:RT" as "<RT in gather>"

    Examples:
      | research     | mission types              | gather items | RT in gather          |
      | in force     | "software-dev", "research" | 3            | listed / mission_type |
      | not in force | "software-dev"             | 0            | absent                |

  Scenario: A suggestion carries the appliesThrough of the pass whose suggests edge reached it
    FR-009, research R3 and R4: D is the action's own entry and suggests S1; PW is scoped by nothing and suggests S2.
    Neither S1 nor S2 is selected, so each is listed as a suggestion with depth 1: S1 through the action and S2
    project-wide, as the entry that suggests it. With no depth the context follows no suggests, and lists D and PW alone.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: software-dev
      description: software-dev
      steps:
        - id: implement
          title: implement
          role: implementer
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/PW.directive.yaml":
      """
      schema_version: '1.0'
      id: PW
      title: PW
      intent: PW
      enforcement: required
      """
    And the org pack "main" with the file "directives/S1.directive.yaml":
      """
      schema_version: '1.0'
      id: S1
      title: S1
      intent: S1
      enforcement: required
      """
    And the org pack "main" with the file "directives/S2.directive.yaml":
      """
      schema_version: '1.0'
      id: S2
      title: S2
      intent: S2
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: directive:D
        relation: scope
      - source: directive:D
        target: directive:S1
        relation: suggests
        when: the change touches the schema
        reason: S1 covers the schema checks
      - source: directive:PW
        target: directive:S2
        relation: suggests
        when: the change is risky
        reason: S2 is the safe path
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "PW"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:D active/selected action
      directive:PW active/selected project
      directive:S1 inactive/not_selected action suggested when "the change touches the schema" because "S1 covers the schema checks"
      directive:S2 inactive/not_selected project suggested when "the change is risky" because "S2 is the safe path"
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:D active/selected action
      directive:PW active/selected project
      """
