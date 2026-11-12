# Mission effective-step-and-action-context (WP10, FR-013, SC-006; research in-force-rulings-439-447.md): the operator
# ruling of 2026-11-12 on #439, option (b). satisfiedBy and overriddenBy list every overrider in force that satisfies or
# hides an artifact, sorted by the level of the pack that holds the version in force of each overrider (built-in, then
# org, then project), then by the full URN in Unicode code point order. The state and the reason do not change: they
# never depended on which overrider was named. Written red first: the scenarios were committed while the service named
# the overrider with the smallest URN alone.
#
# Fixture: the org pack "main" holds T, which its governing preset requires, and E3, which overrides T; the project pack
# "proj" holds E1 and E2, which both override T. The ids are chosen so that the order by URN alone (E1, E2, E3) differs
# from the ruled order (E3 at org, then E1 and E2 at project, by URN within the level). PW is scoped by nothing and
# suggests T, so the action context lists T as a suggestion when the activation does not select it; jenny requires T.

@contract @activations
Feature: Every overrider in force that satisfies or hides an artifact is named, by level then URN
  satisfiedBy, on the effective list's items, the effective artifact read, the action context's items and the governance
  entries of the effective agent profile read, and overriddenBy, on the skipped report's overridden and
  overrideNotApplied, are lists of ArtifactReference: every artifact in force that overrides the artifact, directly or
  through a chain, by the level of its winning pack and then by URN.

  Background:
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
    And the org pack "main" with the file "directives/E3.directive.yaml":
      """
      schema_version: '1.0'
      id: E3
      title: E3
      intent: E3
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
    And the org pack "main" with the file "agent_profiles/jenny.agent.yaml":
      """
      profile-id: jenny
      name: Jenny
      role: implementer
      purpose: Implement.
      specialization:
        primary-focus: Implementation
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: T
          must_have: true
      """
    And the org pack "main" with the file "main.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:E3
        target: directive:T
        relation: overrides
      - source: directive:PW
        target: directive:T
        relation: suggests
      - source: agent_profile:jenny
        target: directive:T
        relation: requires
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: ">=1.0,<2"
      """
    And the project pack "proj" with the file "directives/E1.directive.yaml":
      """
      schema_version: '1.0'
      id: E1
      title: E1
      intent: E1
      enforcement: required
      """
    And the project pack "proj" with the file "directives/E2.directive.yaml":
      """
      schema_version: '1.0'
      id: E2
      title: E2
      intent: E2
      enforcement: required
      """
    And the project pack "proj" with the file "proj.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:E1
        target: main-pack::directive:T
        relation: overrides
      - source: directive:E2
        target: main-pack::directive:T
        relation: overrides
      """

  Scenario: A selected T hidden by three overriders in force names them all, org before project, by URN within a level
    The document selects T and its three overriders. T is out of force with the reason overridden and required true, as
    before the ruling; the listing's satisfiedBy and the skipped report's overriddenBy both name E3 (org) first, then E1
    and E2 (project) by URN. The order by URN alone would put E3 last. The action context carries the activation's
    report, so its overridden entry names the same three in the same order.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {
        "mission_type": ["software-dev"], "directive": ["E1", "E2", "E3", "PW", "T"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/items/0/state" is "inactive"
    And the field at "/items/0/reason" is "overridden"
    And the field at "/items/0/required" is true
    And the field at "/items/0/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/overridden/0/overriddenBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    And the field at "/report/requiredNotInForce" holds exactly 0 items
    When I GET "/activations/a-1/artifacts/directive/T"
    Then the response status is 200
    And the field at "/reason" is "overridden"
    And the field at "/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the field at "/skipped/overridden/0/overriddenBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """

  Scenario: A required T the document does not select names every overrider wherever it is carried
    T is not selected, so its reason is not_selected, the row before overridden, and it carries satisfiedBy all the same:
    on the effective list, on its effective read, on the action context, which lists it as a suggestion of PW, and on the
    governance entry of jenny, which requires it. Each names E3, E1, E2.
    Given the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {
        "mission_type": ["software-dev"], "directive": ["E1", "E2", "E3", "PW"]}}
      """
    When I GET "/activations/a-2/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/items/0/reason" is "not_selected"
    And the field at "/items/0/required" is true
    And the field at "/items/0/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    When I GET "/activations/a-2/artifacts/directive/T"
    Then the response status is 200
    And the field at "/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    When I GET "/activations/a-2/mission-types/software-dev/actions/implement/context?depth=1"
    Then the response status is 200
    And the field at "/artifacts" holds exactly the ids:
      """
      directive:E1
      directive:E2
      directive:E3
      directive:PW
      directive:T
      """
    And the field at "/artifacts/4/reason" is "not_selected"
    And the field at "/artifacts/4/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    And the field at "/artifacts/3/satisfiedBy" is absent
    When I GET "/activations/a-2/artifacts/agent_profile/jenny"
    Then the response status is 200
    And the field at "/governance" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/governance/0/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      directive:E1
      directive:E2
      """
    And the field at "/satisfiedBy" is absent

  Scenario: One overrider in force is named in a one-element list
    The twin of the first scenario: the document selects T and E3 alone, so E1 and E2 are not in force and are not named.
    satisfiedBy and overriddenBy are lists of one.
    Given the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"directive": ["E3", "T"]}}
      """
    When I GET "/activations/a-3/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:E1
      directive:E2
      directive:PW
      directive:T
      """
    And the field at "/items/3/reason" is "overridden"
    And the field at "/items/3/satisfiedBy" holds exactly the ids:
      """
      directive:E3
      """
    When I GET "/activations/a-3/skipped"
    Then the response status is 200
    And the field at "/report/overridden/0/overriddenBy" holds exactly the ids:
      """
      directive:E3
      """
