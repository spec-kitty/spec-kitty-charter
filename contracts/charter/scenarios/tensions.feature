@contract @activations
Feature: A tension between two artifacts in force is answered by the graph, the context and the tensions read
  ADR-0045 decisions 8 to 12 (#602) over the charter's tensions example pack (ADR-0070): a tension in force is two
  guidance artifacts in force that pull against each other, written once from the smaller URN. The effective graph
  answers it as one tension edge with its first writer's words and the artifacts in force that reconcile both sides; the
  action context answers it when it lists both sides in force; and the tensions read (getActivationTensions) answers
  every tension in force and one unreconciled_tension finding per tension nothing in force reconciles, with reconcile
  always and deactivate_one_side with an exclude link for each side the exclusion write would accept (ruling G3). The
  pack holds one case of each: reconciled, unreconciled, one side out of force, registered in reverse, and a tension
  beside a requires on the same pair. Each activation below is scenario configuration, written inline; the context of
  software-dev's implement lists the pack's directives project-wide, since no action and no mission type scopes them.

  # Step vocabulary (README.md): `the graph's tension edges are exactly:`, `the context's tensions are exactly:` and
  # `the activation's tensions are exactly:` read one line per tension, `<source> tension <target> reconciledBy
  # [<urns>] when <when|-> reason <reason|->`; `the activation's findings are exactly:` reads `<source> <target>
  # resolutions [<tokens>] links [<relations>]`; `the edge "<s>" "<r>" "<t>" has no tension words`; and `I follow the
  # link "<relation>" of the finding <n> with the answer's revision and an empty body`.

  Background:
    Given the example pack "built-in" of the charter
    And the example pack "tensions" of the charter

  Scenario: Case 1 - a reconciled tension is one edge with its words and its reconciler, in every read, with no finding
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_REC_A", "TENSION_REC_B"], "tactic": ["reconcile-rec"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph's tension edges are exactly:
      """
      directive:TENSION_REC_A tension directive:TENSION_REC_B reconciledBy [tactic:reconcile-rec] when when a change is large enough to need a design reason Small increments ship sooner; a settled design avoids rework.
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context's tensions are exactly:
      """
      directive:TENSION_REC_A tension directive:TENSION_REC_B reconciledBy [tactic:reconcile-rec] when when a change is large enough to need a design reason Small increments ship sooner; a settled design avoids rework.
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's tensions are exactly:
      """
      directive:TENSION_REC_A tension directive:TENSION_REC_B reconciledBy [tactic:reconcile-rec] when when a change is large enough to need a design reason Small increments ship sooner; a settled design avoids rework.
      """
    And the field at "/findings" holds exactly 0 items

  Scenario: Case 2 - an unreconciled tension has a finding with both resolutions, and its exclude link resolves it
    The tactic reconcile-half reconciles one side only, so it reconciles nothing. Following the finding's exclude-source
    with the answer's revision as If-Match and an empty body puts the source out of force: the tension is gone.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_UNREC_A", "TENSION_UNREC_B"], "tactic": ["reconcile-half"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph's tension edges are exactly:
      """
      directive:TENSION_UNREC_A tension directive:TENSION_UNREC_B reconciledBy [] when - reason -
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's tensions are exactly:
      """
      directive:TENSION_UNREC_A tension directive:TENSION_UNREC_B reconciledBy [] when - reason -
      """
    And the activation's findings are exactly:
      """
      directive:TENSION_UNREC_A directive:TENSION_UNREC_B resolutions [reconcile, deactivate_one_side] links [effective-source, effective-target, exclude-source, exclude-target]
      """
    When I follow the link "effective-source" of the finding 0 with the answer's revision and an empty body
    Then the response status is 200
    And the field at "/artifactId" is "TENSION_UNREC_A"
    When I GET "/activations/a-1/tensions"
    And I follow the link "exclude-source" of the finding 0 with the answer's revision and an empty body
    Then the response status is 200
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    And the field at "/findings" holds exactly 0 items

  Scenario: Case 3 - a tension with one side out of force is in no read, and in each once the side is back
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_ONE_A", "TENSION_ONE_B"], "mission_type": ["software-dev"]}, "deactivated": {"directive": ["TENSION_ONE_B"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph's tension edges are exactly:
      """
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    And the field at "/findings" holds exactly 0 items
    Given the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_ONE_A", "TENSION_ONE_B"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-2/graph"
    Then the graph's tension edges are exactly:
      """
      directive:TENSION_ONE_A tension directive:TENSION_ONE_B reconciledBy [] when - reason -
      """
    When I GET "/activations/a-2/mission-types/software-dev/actions/implement/context"
    Then the context's tensions are exactly:
      """
      directive:TENSION_ONE_A tension directive:TENSION_ONE_B reconciledBy [] when - reason -
      """
    When I GET "/activations/a-2/tensions"
    Then the activation's findings are exactly:
      """
      directive:TENSION_ONE_A directive:TENSION_ONE_B resolutions [reconcile, deactivate_one_side] links [effective-source, effective-target, exclude-source, exclude-target]
      """

  Scenario: Case 4 - a tension registered larger first is stored and answered from the smaller URN
    Given registration is switched on
    When I GET "/packs/tensions"
    And I remember the ETag
    And I PUT "/packs/tensions/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:TENSION_REG_B", "to": "directive:TENSION_REG_A", "type": "tension"}
      """
    Then the response status is 201
    And the field at "/relationship/type" is "tension"
    And the file "registered.graph.yaml" of the pack "tensions" contains "source: directive:TENSION_REG_A"
    And the file "registered.graph.yaml" of the pack "tensions" contains "target: directive:TENSION_REG_B"
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_REG_A", "TENSION_REG_B"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's tensions are exactly:
      """
      directive:TENSION_REG_A tension directive:TENSION_REG_B reconciledBy [] when - reason -
      """
    And the activation's findings are exactly:
      """
      directive:TENSION_REG_A directive:TENSION_REG_B resolutions [reconcile, deactivate_one_side] links [effective-source, effective-target, exclude-source, exclude-target]
      """

  Scenario: Case 5 - a requires and a tension on one pair are two edges, and only the tension has words
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_REQ_A", "TENSION_REQ_B"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the edge "directive:TENSION_REQ_B" "requires" "directive:TENSION_REQ_A" has no tension words
    And the graph's tension edges are exactly:
      """
      directive:TENSION_REQ_A tension directive:TENSION_REQ_B reconciledBy [] when - reason -
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's tensions are exactly:
      """
      directive:TENSION_REQ_A tension directive:TENSION_REQ_B reconciledBy [] when - reason -
      """

  Scenario: A side a pack locks has no exclude link, and when both are locked only reconcile is offered (ruling G3)
    Given the org pack "tensions" with the file "policy.yaml":
      """
      schema_version: 1
      kind_defaults:
        directive: open
        tactic: open
      artifacts:
        directive:TENSION_UNREC_A: locked
      """
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_UNREC_A", "TENSION_UNREC_B"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's findings are exactly:
      """
      directive:TENSION_UNREC_A directive:TENSION_UNREC_B resolutions [reconcile, deactivate_one_side] links [effective-source, effective-target, exclude-target]
      """
    When I follow the link "exclude-target" of the finding 0 with the answer's revision and an empty body
    Then the response status is 200
    Given the org pack "tensions" with the file "policy.yaml":
      """
      schema_version: 1
      kind_defaults:
        directive: open
        tactic: open
      artifacts:
        directive:TENSION_UNREC_A: locked
        directive:TENSION_UNREC_B: locked
      """
    Given the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_UNREC_A", "TENSION_UNREC_B"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-2/tensions"
    Then the response status is 200
    And the activation's findings are exactly:
      """
      directive:TENSION_UNREC_A directive:TENSION_UNREC_B resolutions [reconcile] links [effective-source, effective-target]
      """
    When I PUT "/activations/a-2/exclusions/directive/TENSION_UNREC_A" with the header "If-Match": "*" and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"

  Scenario: A side the context lists only as a suggestion is no side of a tension, at depth 0 or 1
    TENSION_REC_B is not selected, so it is out of force: reached through a suggests edge of TENSION_REC_A it is listed as
    a suggestion, and the tension is in no read. Case 1's context, with both selected, has it.
    Given the org pack "tensions" with the file "suggests.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:TENSION_REC_A
        target: directive:TENSION_REC_B
        relation: suggests
      """
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"directive": ["TENSION_REC_A"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=0"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the response status is 200
    And the context lists "directive:TENSION_REC_B" as "listed / project"
    And the field at "/tensions" holds exactly 0 items
    When I GET "/activations/a-1/tensions"
    Then the field at "/tensions" holds exactly 0 items

  Scenario: A mission type not in force answers no tension in its context while the tensions read lists it
    The built-in pack requires software-dev, so the scenario adds a mission type of its own, solo, that the activation
    does not select; the context of solo's step answers no tension while the activation has one in force.
    Given the org pack "tensions" with the file "missions/solo/solo.mission-type.yaml":
      """
      schema_version: "1.0"
      id: solo
      title: solo
      description: solo
      steps:
        - id: run
          title: run
          role: implementer
      """
    And the org pack "tensions" with the file "solo.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:solo/run
        kind: action
        label: run
      edges:
      - source: mission_type:solo
        target: action:solo/run
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "tensions"}], "selected": {"mission_type": ["software-dev"], "directive": ["TENSION_REC_A", "TENSION_REC_B"], "tactic": ["reconcile-rec"]}}
      """
    When I GET "/activations/a-1/mission-types/solo/actions/run/context"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    And the field at "/skipped/missionTypeNotInForce/0/artifactId" is "solo"
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the context's tensions are exactly:
      """
      directive:TENSION_REC_A tension directive:TENSION_REC_B reconciledBy [tactic:reconcile-rec] when when a change is large enough to need a design reason Small increments ship sooner; a settled design avoids rework.
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the activation's tensions are exactly:
      """
      directive:TENSION_REC_A tension directive:TENSION_REC_B reconciledBy [tactic:reconcile-rec] when when a change is large enough to need a design reason Small increments ship sooner; a settled design avoids rework.
      """
