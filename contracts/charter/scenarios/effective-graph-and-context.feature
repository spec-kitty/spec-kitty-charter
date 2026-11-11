# PR C of layer-merge-and-effective-reads, contract C (IC-11; WP28, T139): the effective graph and the effective action
# context. Written red first (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW): WP28 committed the scenarios with every step bound
# and recorded the assertion failures against the 501 the operations answered (a wrong status), then served the reads.
# Every When asserts 200 (or the refusal), and every absence (an edge, a step, an artifact, an override) is read beside a
# positive control in the same answer. The fixtures are authored inline, per rule (plan.md, Fixtures).
#
# Scenarios: US5 AS1 (an edge with an endpoint out of force is absent), AS2 (a layer override: the edge from the
# overridden version is gone, the override is listed; a held step's override is listed, a template's is not), AS3 (an
# enhancer in force is an edge from T's side, one out of force is not), AS4 (the context: in force, with enhancements,
# a suggestion only through suggests, entries in URN order), AS5 (a mission type not in force, the 404s), the step
# enhancer target as a NodeReference (#429), and the validators (ETag, 304, one tag per representation).
#
# Steps these scenarios add to the vocabulary of README.md (WP28 binds them in EffectiveGraphSteps):
# - `Then the graph holds exactly the nodes:` followed by a doc string, one URN per line, in the answer's order.
# - `Then the graph holds exactly the edges:` followed by a doc string, one `<source> <relation> <target>` per line.
# - `Then the graph holds exactly the layer overrides:` followed by a doc string, one `<artifact> won by <pack>@<level>
#   over <pack>@<level>` per line.
# - `Then the context holds exactly the artifacts:` followed by a doc string, one `<kind>:<id> <state>/<reason>` per line,
#   with ` suggested when "<when>" because "<reason>"` after an entry that carries a suggestion.
# - `Then the context holds exactly the enhancements:` followed by a doc string, one `<kind>:<id> is enhanced by
#   <kind>:<id>[, <kind>:<id>]` per line.
# - `Then the field at "<pointer>" holds exactly the ids:` is the existing step: an item with an `id` is read as
#   `<kind>:<id>`, as one with an `artifactId` is.

@contract @activations
Feature: The effective graph and the effective action context answer what the activation puts in force
  getEffectiveGraph answers the merged closure's edges among the nodes in force (the artifacts in force, and each held
  step whose mission type is in force), resolved through the layer overrides, with the overrides applied.
  getEffectiveActionContext answers ADR-0018's walk from the action's step over the artifacts in force, with the
  enhancers in force of each node, the artifacts reached only through suggests as suggestions, and the skipped report.
  Both are conditional reads with an ETag of their own representation (FR-020), and both refuse an unknown activation
  (FR-021); the context also refuses an unknown mission type or action (FR-019).

  Scenario: US5 AS1 - an edge whose endpoint is not in force is absent from the graph
    US5 AS1, FR-018: D requires R, and R is not selected, so R is out of force and the edge is gone. D2 requires R2, and
    both are selected: the control, the edge and both nodes are in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: R
      enforcement: required
      """
    And the org pack "main" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: D2
      enforcement: required
      """
    And the org pack "main" with the file "directives/R2.directive.yaml":
      """
      schema_version: '1.0'
      id: R2
      title: R2
      intent: R2
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D
        target: directive:R
        relation: requires
      - source: directive:D2
        target: directive:R2
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2", "R2"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      directive:D
      directive:D2
      directive:R2
      """
    And the graph holds exactly the edges:
      """
      directive:D2 requires directive:R2
      """
    And the field at "/layerOverrides" holds exactly 0 items

  Scenario: US5 AS2 - the edge from an overridden version is gone, the edge to it ends at the winner, and the override is listed
    US5 AS2, FR-018: core (built-in) holds D, R and X, with D requires R and X requires D. proj (project) holds its own D,
    which replaces core's whole. The edge from core's D is absent although D and R are both in force, and the edge X requires
    D stands and ends at the version in force. The graph lists the override: which artifact, which level won and which lost.
    The edge X requires R2, with R2 not selected, is not here either: the control that the graph still drops what is not in
    force while it keeps the rest.
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at built-in
      intent: D at built-in
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: R
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/R2.directive.yaml":
      """
      schema_version: '1.0'
      id: R2
      title: R2
      intent: R2
      enforcement: required
      """
    And the built-in pack "core" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D
        target: directive:R
        relation: requires
      - source: directive:X
        target: directive:D
        relation: requires
      - source: directive:X
        target: directive:R2
        relation: requires
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D at project
      intent: D at project
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"directive": ["D", "R", "X"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      directive:D
      directive:R
      directive:X
      """
    And the graph holds exactly the edges:
      """
      directive:X requires directive:D
      """
    And the graph holds exactly the layer overrides:
      """
      directive:D won by proj@project over core@built-in
      """

  Scenario: US5 AS2 - the override of a held step is listed, the override of a template and of an artifact out of force are not
    US5 AS2, FR-018, decision 01M702A71YF7WNDTG2AA89K8ZA: core (built-in) and proj (project) both hold the mission type
    software-dev and its step implement, and proj replaces core's. The mission type is selected, so it is in force and so
    is its held step: the graph lists both overrides. proj also replaces core's template writing/meeting-minutes.md and
    core's directive E, which the document does not select: a template is no node of the graph and E is out of force, so
    neither override is listed. The two listed are the controls in the same answer.
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the built-in pack "core" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges: []
      """
    And the built-in pack "core" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: E
      enforcement: required
      """
    And the built-in pack "core" with the file "templates/writing/meeting-minutes.md":
      """
      # Minutes at built-in
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
    And the project pack "proj" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges: []
      """
    And the project pack "proj" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E at project
      intent: E at project
      enforcement: required
      """
    And the project pack "proj" with the file "templates/writing/meeting-minutes.md":
      """
      # Minutes at project
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      action:software-dev/implement
      mission_type:software-dev
      """
    And the graph holds exactly the layer overrides:
      """
      action:software-dev/implement won by proj@project over core@built-in
      mission_type:software-dev won by proj@project over core@built-in
      """

  Scenario: US5 AS3 - an enhancer in force is an edge of the graph, read from T's side, and one out of force is not
    US5 AS3, FR-015, E12: the tactic e enhances the directive T, and both are selected, so the graph has the edge and T is
    enhanced by e. The tactic e2 enhances T2, which is selected, but e2 is not: an enhancer is returned only when it is
    itself in force, so its edge is absent and T2 stays a node. The context of the action that scopes T and T2 carries the
    same fact from the node's side: T is enhanced by e, and T2 by nothing.
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
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/T2.directive.yaml":
      """
      schema_version: '1.0'
      id: T2
      title: T2
      intent: T2
      enforcement: required
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: e
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: e2
      steps:
        - title: Only step
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
      - source: tactic:e
        target: directive:T
        relation: enhances
      - source: tactic:e2
        target: directive:T2
        relation: enhances
      - source: action:software-dev/implement
        target: directive:T
        relation: scope
      - source: action:software-dev/implement
        target: directive:T2
        relation: scope
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["T", "T2"], "tactic": ["e"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      action:software-dev/implement
      directive:T
      directive:T2
      mission_type:software-dev
      tactic:e
      """
    And the graph holds exactly the edges:
      """
      action:software-dev/implement scope directive:T
      action:software-dev/implement scope directive:T2
      tactic:e enhances directive:T
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:T active/selected
      directive:T2 active/selected
      """
    And the context holds exactly the enhancements:
      """
      directive:T is enhanced by tactic:e
      """

  Scenario: US5 AS4 - the context holds the artifacts in force the action reaches, with the suggestion only through suggests
    US5 AS4, FR-019, #408 ruling 3, decisions 01M6Y6VE6VZQ4MXD98N93JB3GJ and 01M7059T4K64TAVKXN3T1FKWYG: the step implement
    scopes D and D2, both in force, so both are entries, with D2 listed after D (URN order). D suggests S, which the
    document does not select and nothing else reaches: S is listed as a suggestion, inactive with reason not_selected,
    with the edge's when and reason. D2 suggests R2, which D also requires: R2 is reached through requires too, so it is
    not a suggestion and, out of force, not in the context at all. O is selected and in force but no step reaches it: the
    context does not list it. The tactic e enhances D and is in force: D is enhanced by e, and e is not pulled in as an
    entry. A context with no depth follows no suggests, so the suggestion is read with depth 1.
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
    And the org pack "main" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: D2
      enforcement: required
      """
    And the org pack "main" with the file "directives/S.directive.yaml":
      """
      schema_version: '1.0'
      id: S
      title: S
      intent: S
      enforcement: required
      """
    And the org pack "main" with the file "directives/R2.directive.yaml":
      """
      schema_version: '1.0'
      id: R2
      title: R2
      intent: R2
      enforcement: required
      """
    And the org pack "main" with the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O
      intent: O
      enforcement: required
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: e
      steps:
        - title: Only step
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
      - source: action:software-dev/implement
        target: directive:D2
        relation: scope
      - source: directive:D
        target: directive:S
        relation: suggests
        when: the change touches the schema
        reason: S covers the schema checks
      - source: directive:D
        target: directive:R2
        relation: requires
      - source: directive:D2
        target: directive:R2
        relation: suggests
        when: the change is risky
        reason: R2 is the safe path
      - source: tactic:e
        target: directive:D
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "D2", "O"], "tactic": ["e"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the response status is 200
    And the field at "/missionType" is "software-dev"
    And the field at "/action" is "implement"
    And the context holds exactly the artifacts:
      """
      directive:D active/selected
      directive:D2 active/selected
      directive:S inactive/not_selected suggested when "the change touches the schema" because "S covers the schema checks"
      """
    And the context holds exactly the enhancements:
      """
      directive:D is enhanced by tactic:e
      """
    And the field at "/skipped/missionTypeNotInForce" holds exactly 0 items
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:D active/selected
      directive:D2 active/selected
      """

  Scenario: The context holds every kind the action scopes and the activation selects, and nothing else (#484)
    #484: an action scopes a directive, a tactic, a procedure, a styleguide and a toolguide, each selected, so each is an
    entry, active and selected. Beside each sits a control the context must not list: the directive DX and the procedure
    px are selected but no step scopes them; the tactic t2 is scoped but not selected; the styleguide and the toolguide
    each have both controls, s2 and g2 scoped but not selected, s3 and g3 selected but not scoped. The answer lists the
    five in URN order and none of the seven.
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
    And the org pack "main" with the file "directives/DX.directive.yaml":
      """
      schema_version: '1.0'
      id: DX
      title: DX
      intent: DX
      enforcement: required
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: t
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/t2.tactic.yaml":
      """
      schema_version: '1.0'
      id: t2
      name: t2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "procedures/p.procedure.yaml":
      """
      schema_version: '1.0'
      id: p
      title: p
      description: p
      entry_condition: A defect is reported.
      exit_condition: The defect is fixed.
      steps:
        - title: Write the failing test
          role: implementer
      """
    And the org pack "main" with the file "procedures/px.procedure.yaml":
      """
      schema_version: '1.0'
      id: px
      title: px
      description: px
      entry_condition: A defect is reported.
      exit_condition: The defect is fixed.
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "styleguides/s.styleguide.yaml":
      """
      schema_version: '1.0'
      id: s
      title: s
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      """
    And the org pack "main" with the file "styleguides/s2.styleguide.yaml":
      """
      schema_version: '1.0'
      id: s2
      title: s2
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      """
    And the org pack "main" with the file "styleguides/s3.styleguide.yaml":
      """
      schema_version: '1.0'
      id: s3
      title: s3
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      """
    And the org pack "main" with the file "toolguides/g.toolguide.yaml":
      """
      schema_version: '1.0'
      id: g
      tool: git
      title: g
      guide_path: g.guide.md
      summary: g
      """
    And the org pack "main" with the file "toolguides/g.guide.md":
      """
      # g

      ## Overview

      Use git.
      """
    And the org pack "main" with the file "toolguides/g2.toolguide.yaml":
      """
      schema_version: '1.0'
      id: g2
      tool: git
      title: g2
      guide_path: g2.guide.md
      summary: g2
      """
    And the org pack "main" with the file "toolguides/g2.guide.md":
      """
      # g2

      ## Overview

      Use git.
      """
    And the org pack "main" with the file "toolguides/g3.toolguide.yaml":
      """
      schema_version: '1.0'
      id: g3
      tool: git
      title: g3
      guide_path: g3.guide.md
      summary: g3
      """
    And the org pack "main" with the file "toolguides/g3.guide.md":
      """
      # g3

      ## Overview

      Use git.
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
      - source: action:software-dev/implement
        target: tactic:t
        relation: scope
      - source: action:software-dev/implement
        target: tactic:t2
        relation: scope
      - source: action:software-dev/implement
        target: procedure:p
        relation: scope
      - source: action:software-dev/implement
        target: styleguide:s
        relation: scope
      - source: action:software-dev/implement
        target: styleguide:s2
        relation: scope
      - source: action:software-dev/implement
        target: toolguide:g
        relation: scope
      - source: action:software-dev/implement
        target: toolguide:g2
        relation: scope
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "DX"], "tactic": ["t"], "procedure": ["p", "px"], "styleguide": ["s", "s3"], "toolguide": ["g", "g3"]}}
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the context holds exactly the artifacts:
      """
      directive:D active/selected
      procedure:p active/selected
      styleguide:s active/selected
      tactic:t active/selected
      toolguide:g active/selected
      """
    And the field at "/enhancements" holds exactly 0 items
    And the field at "/artifacts/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/procedure/p"
    And the field at "/artifacts/1/_links/artifact/href" is "/api/v1/packs/main/artifacts/procedure/p"
    And the field at "/artifacts/2/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/styleguide/s"
    And the field at "/artifacts/4/_links/artifact/href" is "/api/v1/packs/main/artifacts/toolguide/g"
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the field at "/nodes/4/urn" is "procedure:p"
    And the field at "/nodes/4/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/procedure/p"
    When I GET "/activations/a-1/artifacts/styleguide/s"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/state" is "active"
    When I GET "/activations/a-1/artifacts/styleguide/s2"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    When I GET "/activations/a-1/artifacts/toolguide/g2"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    When I GET "/activations/a-1/artifacts?kind=styleguide&state=active"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items" holds exactly the ids:
      """
      styleguide:s
      styleguide:s3
      """
    When I GET "/activations/a-1/artifacts?kind=toolguide&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      toolguide:g
      toolguide:g3
      """

  Scenario: US5 AS5 - the context of a mission type not in force is empty and the skipped report says why
    US5 AS5, FR-019: research is not selected, so its step gather is not in force and the context of research/gather has no
    artifacts and no enhancements, although the step scopes D, which is selected. The skipped report lists the mission type
    research in missionTypeNotInForce: the reason, read in the same answer. software-dev is selected: the control, whose
    context lists its own scoped artifact, read in the same scenario.
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
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/S.directive.yaml":
      """
      schema_version: '1.0'
      id: S
      title: S
      intent: S
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
      - urn: action:research/gather
        kind: action
        label: gather
      edges:
      - source: action:research/gather
        target: directive:D
        relation: scope
      - source: action:software-dev/implement
        target: directive:S
        relation: scope
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "S"]}}
      """
    When I GET "/activations/a-1/mission-types/research/actions/gather/context"
    Then the response status is 200
    And the field at "/artifacts" holds exactly 0 items
    And the field at "/enhancements" holds exactly 0 items
    And the field at "/skipped/missionTypeNotInForce" holds exactly the ids:
      """
      mission_type:research
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the context holds exactly the artifacts:
      """
      directive:S active/selected
      """
    And the field at "/skipped/missionTypeNotInForce" holds exactly 0 items

  Scenario: US5 AS5 - an unknown mission type or action is 404, whether or not the mission type is in force, and a depth of 2 is 400
    US5 AS5, FR-019, E16: the merged closure holds software-dev with its step implement, and research (not selected) with
    its step gather. An unknown mission type is 404 mission_type_not_found, an unknown action of a held mission type is 404
    action_not_found, and so is one of research, which is not in force: the names are judged against the closure, not against
    what is in force. research/gather answers 200 in the same scenario: the control that the mission type is held.
    A depth of 2 is outside 0 to 1, so it is 400 (the operation declares the Problem).
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
    And the org pack "main" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:research/gather
        kind: action
        label: gather
      edges: []
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D"]}}
      """
    When I GET "/activations/a-1/mission-types/missing/actions/implement/context"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/missing/context"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/research/actions/missing/context"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/research/actions/gather/context"
    Then the response status is 200
    And the field at "/artifacts" holds exactly 0 items
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=2"
    Then the response status is 400

  Scenario Outline: FR-021 - an unknown activation is 404 activation_not_found on the graph and on the context
    FR-021: neither read answers for an activation nothing is stored under. The control is the stored activation a-1, which
    answers 200 on the same path.
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
    And the org pack "main" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges: []
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D"]}}
      """
    When I GET "/activations/a-1/<path>"
    Then the response status is 200
    When I GET "/activations/unknown/<path>"
    Then the response status is 404
    And the problem code is "activation_not_found"

    Examples:
      | path                                                 |
      | graph                                                |
      | mission-types/software-dev/actions/implement/context |

  Scenario: #429 - a target of an enhancer without a target in force is a NodeReference, on the report and on the context
    #429, D15: the tactic e enhances the directive T, which the document does not select, so e is not in force and the
    report names T as a node, {kind, id}, where an ArtifactReference said {kind, artifactId}. The tactic e2 enhances the
    selected directive T2: the control, in force in the same answer, so only e is listed. The context of the action reads the
    same bucket in its skipped report. A step as a target (kind action) is no shape a pack file can author before step
    enhancements are loaded (#424): ClosureViewSessionStepRefsTest pins it from the in-force resolution.
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
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/T2.directive.yaml":
      """
      schema_version: '1.0'
      id: T2
      title: T2
      intent: T2
      enforcement: required
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: e
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: e2
      steps:
        - title: Only step
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
      - source: tactic:e
        target: directive:T
        relation: enhances
      - source: tactic:e2
        target: directive:T2
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["T2"], "tactic": ["e", "e2"]}}
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/enhancedTargetNotInForce" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/report/enhancedTargetNotInForce/0/targets" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/enhancedTargetNotInForce/0/targets/0/id" is "T"
    And the response body has no field at "/report/enhancedTargetNotInForce/0/targets/0/artifactId"
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e2
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the field at "/skipped/enhancedTargetNotInForce/0/targets" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/skipped/enhancedTargetNotInForce/0/targets/0/id" is "T"

  Scenario: FR-020 - each read has an ETag of its own representation, and a matching If-None-Match is 304
    FR-020, E17, decisions 01M6Y11E55YP5TG1EJ1MDKCFRP and 01M6Y3GZFRC5WEQQ7G6GT0YMW3: the graph and the context of the same
    activation carry different tags, each answers 304 to its own tag, and a tag of one is not the tag of the other: the
    graph does not answer 304 to the tag of the context. A write to the activation's document moves the tag of the graph,
    which then answers 200 again. The 304 has no body.
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
    And the org pack "main" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: D2
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
      - source: action:software-dev/implement
        target: directive:D2
        relation: scope
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/activations/a-1/graph" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I remember the header "ETag"
    And I GET "/activations/a-1/mission-types/software-dev/actions/implement/context" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    When I GET "/activations/a-1/graph" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "D2"]}}
      """
    Then the response status is 200
    When I GET "/activations/a-1/graph" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

  Scenario: WP08 - a graph node and a context item of a served kind link their effective read, and a step links its step read
    #450 for the served kinds. The step implement scopes D, which requires R. The graph lists the step, D, R and the mission type:
    the two directives link effective-artifact (a node names no pack, so it has no artifact link), the step links its
    step read under the activation and the mission type its own read (#99 WP06). The context lists D and R as items, which link effective-artifact and artifact. Every link
    followed answers 200.
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
    And the org pack "main" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: R
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
        target: directive:R
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "directive": ["D", "R"]}}
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      action:software-dev/implement
      directive:D
      directive:R
      mission_type:software-dev
      """
    And the field at "/nodes/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement"
    And the field at "/nodes/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/directive/D"
    And the field at "/nodes/1/_links/artifact" is absent
    And the field at "/nodes/2/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/directive/R"
    And the field at "/nodes/3/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    When I GET "/activations/a-1/artifacts/directive/D"
    Then the response status is 200
    And the field at "/packId" is "main"
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the response status is 200
    And the field at "/artifacts/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/directive/D"
    And the field at "/artifacts/0/_links/artifact/href" is "/api/v1/packs/main/artifacts/directive/D"
    And following the link "effective-artifact" of every element of "artifacts" answers 200
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then following the link "artifact" of every element of "artifacts" answers 200
