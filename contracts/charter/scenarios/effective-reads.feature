# PR B of layer-merge-and-effective-reads, contract B (IC-08, IC-17; WP22, T113): the effective artifact list, the
# skipped report and the write warnings. Written red first and committed with a WIP tag (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW);
# WP25 bound the glue, recorded the red assertion failure (every step bound, a wrong status or body) and removed the
# tag. Every When asserts 200 and a non-empty answer, and every absence (X absent, E absent) is read
# beside a positive control in the same answer. Fixtures are authored inline, per rule. "In force" is state=active
# (operator ruling Q1), so an absence is asserted on ?state=active and its reason on ?state=inactive or in the report.
#
# Scenarios: US3 AS2 to AS7 (AS7: the ETag cases), US4 AS1 and AS2, a required T hidden by an active override is
# satisfied, an enhancer alone does not satisfy T (which is put in force), an enhancer whose target is out of force is
# inactive, an override of a locked T is not applied, a chain F -> E -> T leaves F (A3), an enhancer ring is not in
# force (A8), a step of a denied mission type is not in force (binding), a required step whose mission type is not in
# force and a required enhancer with no target in force are dropped and reported (DM-01M6RFGFW3S0YJ222QMHD2W11B,
# DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ, A9), a pin and a must-have item no pack holds (B1, A10), the language filter at read
# (#408 ruling 2), a suggestion (#408 ruling 3), A13, the keyset cursor, and the writes of IC-17 (B6/B7, B4).
#
# An enhancer ring cannot be registered (A8, enhances_cycle), so the A8 scenario writes its pack files past the API.
#
# The suggestion scenario reads the carrier of a suggests edge's `when` and `reason` as the optional
# EffectiveArtifact.suggestion {when, reason} (runner decision 01M6Y0X5E9ANR25MC8RPFXYM72).
# getActivation's ETag covers the revision and the closure's fingerprint (operator decision 01M6Y11E55YP5TG1EJ1MDKCFRP).
#
# Steps these scenarios add to the vocabulary of README.md (WP25 binds them in EffectiveReadSteps):
# - `When I GET "<path>" with If-None-Match the remembered header "ETag"` sends the `ETag` last remembered with
#   `When I remember the header "ETag"` as `If-None-Match`; `When I follow the link "<relation>" with If-None-Match
#   the remembered header "ETag"` does the same for a followed link.
# - `When I PUT "<path>" with If-Match the remembered header "ETag" and the body:` sends the remembered `ETag` as `If-Match`.
# - `When I GET "<path>" with the remembered cursor` appends `pageCursor=<value>` (with `?` or `&`) where the value is
#   the field last remembered with `When I remember the field "/pageInfo/nextPageCursor" of the response body`.
# - `Then the field at "<pointer>" is "<text>"` and `... holds exactly the ids:` are the existing steps: an item of the
#   list is read as `<kind>:<artifactId>`, in the answer's order (kind, then id in code-point order).
# - `Then the field at "<pointer>" is the stored revision` compares a body field with the revision the activation was
#   stored with (the stored ETag without its quotes); `Then the field at "<pointer>" is the remembered header "<name>"
#   without its quotes` compares it with a header remembered with `When I remember the header "<name>"`. They state what
#   getActivation's closure ETag no longer is: the revision.

@contract @activations
Feature: The effective reads answer what an activation puts in force, and why the rest is not
  listEffectiveArtifacts answers every artifact of the activation's merged closure with its state, so "in force" is
  state=active (operator ruling Q1), and getActivationSkippedReport answers each artifact not in force once, in the bucket
  of the rule that decided it. The effective set is the activation document's rules over the merged closure: locked
  artifacts are always in force; a required artifact is a requirement, satisfied by itself or by an active override and
  never by an enhancement (R11); an enhancer is in force only with a target in force (Q8); overrides hide their targets
  except a locked one (Q6); a step or an artifact bound to a mission type is in force only while the mission type is; the
  language filter applies at read only (#408 ruling 2). Both reads carry an ETag per representation and answer 304 on a
  matching If-None-Match (FR-020). The writes of IC-17 warn what the read will not put in force (B6/B7), and the activation
  lists the deny entries the floor or a lock overrides (B4).

  Scenario: US3 AS2 - a denied required directive stays in force with reason required, and the deny entry is not applied
    US3 AS2, FR-013: the governing preset requires D and the stored document denies D, as a pack change after the
    write would leave it. D is in force with reason required and its deny entry is in denyNotApplied. X, a denied
    directive nothing requires, is the control: it is excluded and its deny entry is applied, so it is not listed.
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
    And the org pack "main" with the file "directives/E1.directive.yaml":
      """
      schema_version: '1.0'
      id: E1
      title: E1
      intent: E1
      enforcement: required
      """
    And the org pack "main" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      """
    And the stored file of the activation "a-1" holds:
      """
      {"deactivated":{"directive":["D","X"]},"packs":[{"packId":"main"}],"schemaVersion":2,"selected":{"directive":["E1"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      directive:E1
      directive:X
      """
    And the field at "/items/0/state" is "active"
    And the field at "/items/0/reason" is "required"
    And the field at "/items/0/required" is true
    And the field at "/items/1/state" is "active"
    And the field at "/items/2/state" is "excluded"
    And the field at "/items/2/reason" is "deactivated"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/denyNotApplied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/report/denyNotApplied/0/reason" is "required"

  Scenario: WP08 - each item of the list links its effective read and the pack-scoped read of the pack that holds it
    #450 for the served kinds: a client follows an item without building a path. D is held by the org pack and
    selected, rehearse is a tactic, selected too, and software-dev is a mission type, listed and linked like
    them (#99 WP06). The two links of an item
    answer 200 and name the same artifact (the effective read names the pack that holds the version in force, the
    artifact link reads that pack). The activation's artifacts link is the way to the list: it is templated, takes the
    kind and the state, and answers the same list as the path it expands to. A replace (PUT) answers the same link.
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
    And the org pack "main" with the file "tactics/rehearse.tactic.yaml":
      """
      schema_version: '1.0'
      id: rehearse
      name: Rehearse
      steps:
        - title: Only step
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
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["rehearse"], "mission_type": ["software-dev"]}}
      """
    When I GET "/activations/a-1"
    Then the field at "/_links/artifacts/href" is "/api/v1/activations/a-1/artifacts{?kind,state}"
    And the field at "/_links/artifacts/templated" is true
    When I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["rehearse"], "mission_type": ["software-dev"]}}
      """
    Then the response status is 200
    And the field at "/_links/artifacts/href" is "/api/v1/activations/a-1/artifacts{?kind,state}"
    And the field at "/_links/artifacts/templated" is true
    When I follow the templated link "artifacts" with:
      | kind  | directive |
      | state | active    |
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/items/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/directive/D"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/main/artifacts/directive/D"
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1"
    And I follow the templated link "artifacts" with:
      | kind | directive |
    Then following the link "artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      mission_type:software-dev
      tactic:rehearse
      """
    And the field at "/items/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/directive/D"
    And the field at "/items/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/items/1/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev"
    And the field at "/items/2/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/tactic/rehearse"
    And the field at "/items/2/_links/artifact/href" is "/api/v1/packs/main/artifacts/tactic/rehearse"

  Scenario: US3 AS3 - a selected id no pack holds is absent and reported as stale
    US3 AS3, ADR-0059 D4: the document selects D, which main holds, and GHOST, which no pack holds any longer. GHOST
    is absent from the artifacts in force and is in staleIds; D is the control, present in the same answer.
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
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"main"}],"schemaVersion":2,"selected":{"directive":["D","GHOST"]}}
      """
    When I GET "/activations/a-1/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/summary/active" is 1
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/staleIds" holds exactly the ids:
      """
      directive:GHOST
      """

  Scenario: US3 AS4 - the steps of a mission type are in force only while the mission type is
    US3 AS4, FR-040, FR-041, A7 Q1: software-dev is selected and its step contract is in force; research is not
    selected, so its step contract, though selected, is not in force: inactive with mission_type_not_in_force. The
    step contract of software-dev is the control, active in the same answer. There is no activatedKinds.
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
    And the org pack "main" with the file "mission_step_contracts/sd-implement.step-contract.yaml":
      """
      id: sd-implement
      schema_version: '1.0'
      action: implement
      mission: software-dev
      steps:
        - id: first
          description: The first step
      """
    And the org pack "main" with the file "mission_step_contracts/research-gather.step-contract.yaml":
      """
      id: research-gather
      schema_version: '1.0'
      action: gather
      mission: research
      steps:
        - id: first
          description: The first step
      """
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"main"}],"schemaVersion":2,"selected":{"mission_step_contract":["research-gather","sd-implement"],"mission_type":["software-dev"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:sd-implement
      """
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """
    And the field at "/items/0/reason" is "mission_type_not_in_force"

  Scenario: US3 AS5 - an active enhancer and its target in force are both in force, and an enhancer that is not active is absent
    US3 AS5, FR-015: E enhances T. The activation a-1 selects both, so both are in force. The activation a-2 selects
    T alone: E is absent from the artifacts in force, inactive with not_selected, and T, the control, is in force in
    the same answer. Nothing is pulled in.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e
        target: tactic:t
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["t", "e"]}}
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["t"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e
      tactic:t
      """
    When I GET "/activations/a-2/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:t
      """
    When I GET "/activations/a-2/artifacts?kind=tactic&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/items/0/reason" is "not_selected"

  Scenario: US3 AS6 - an active override hides its target with reason overridden
    US3 AS6, FR-016: F overrides T and both are selected. T is absent from the artifacts in force: inactive with
    overridden, listed in the report's overridden bucket with F as the overriding artifact. F and U are the controls,
    in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "main" with the file "directives/U.directive.yaml":
      """
      schema_version: '1.0'
      id: U
      title: U
      intent: U
      enforcement: required
      """
    And the org pack "main" with the file "override.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:F
        target: directive:T
        relation: overrides
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["T", "F", "U"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:F
      directive:U
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/items/0/reason" is "overridden"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/overridden/0/overriddenBy" holds exactly the ids:
      """
      directive:F
      """

  Scenario: A3 - in a chain F overrides E overrides T only F stays in force
    A3, R13 stratum 2: F overrides E and E overrides T, all selected. Overrides apply from the active set before any
    is removed, so both E and T leave force, hidden, and only F stays. U, which no override reaches, is the control.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "main" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: E
      enforcement: required
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/U.directive.yaml":
      """
      schema_version: '1.0'
      id: U
      title: U
      intent: U
      enforcement: required
      """
    And the org pack "main" with the file "chain.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:F
        target: directive:E
        relation: overrides
      - source: directive:E
        target: directive:T
        relation: overrides
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F", "E", "T", "U"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:F
      directive:U
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:E
      directive:T
      """
    And the field at "/items/0/reason" is "overridden"
    And the field at "/items/1/reason" is "overridden"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:E
      directive:T
      """

  Scenario: A required T hidden by an active override is satisfied by it
    R11, operator ruling Q6: the governing preset requires T, and F, selected, overrides T, which the document selects
    too. The requirement is satisfied by the override: T stays out of force (inactive, overridden) and carries
    satisfiedBy F, and it is not put in force and not reported as unsatisfied. F, in force, is the control in the same
    answer. A required T the document does not select is not_selected, the row that comes before overridden in E9 and in
    EffectiveArtifactReason, and carries satisfiedBy F all the same: a-2 selects F alone.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
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
    And the org pack "main" with the file "override.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:F
        target: directive:T
        relation: overrides
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F", "T"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:F
      directive:T
      """
    And the field at "/items/0/state" is "active"
    And the field at "/items/1/state" is "inactive"
    And the field at "/items/1/reason" is "overridden"
    And the field at "/items/1/required" is true
    And the field at "/items/1/satisfiedBy" holds exactly the ids:
      """
      directive:F
      """
    And the field at "/items/0/satisfiedBy" is absent
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/denyNotApplied" holds exactly 0 items
    And the field at "/report/requiredNotInForce" holds exactly 0 items
    Given the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}}
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
      directive:F
      """
    When I GET "/activations/a-2/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotInForce" holds exactly 0 items

  Scenario: An enhancer alone does not satisfy a required T, which is put in force
    R11, operator ruling Q8: the governing preset requires T, the document denies T and selects E, which enhances T.
    An enhancement never satisfies a requirement, so T is put in force with reason required and its deny entry is in
    denyNotApplied. E, whose target T is now in force, is in force as well: the control that the enhancer works only
    beside its target.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: E
      steps:
        - title: Only step
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
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e
        target: directive:T
        relation: enhances
      """
    And the stored file of the activation "a-1" holds:
      """
      {"deactivated":{"directive":["T"]},"packs":[{"packId":"main"}],"schemaVersion":2,"selected":{"tactic":["e"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/items/0/reason" is "required"
    And the field at "/items/0/satisfiedBy" is absent
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/denyNotApplied" holds exactly the ids:
      """
      directive:T
      """

  Scenario: An enhancer whose target is out of force is inactive with enhanced_target_not_in_force
    R11, operator ruling Q8: E enhances T, T is not selected, so E, though selected, is inactive with
    enhanced_target_not_in_force and is in enhancedTargetNotInForce with its target. E2 enhances T2, which is
    selected: the control, in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
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
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
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
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e", "e2"], "directive": ["T2"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e2
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/items/0/reason" is "enhanced_target_not_in_force"
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

  Scenario: An override of a locked T is not applied
    R11, operator ruling Q6: T is locked, E overrides T and is selected. The override is not applied: T stays in force
    with reason locked, E is in force too, and the pair is in overrideNotApplied. The control is the same override of
    an open T2 by E2, which is applied: T2 is hidden and in overridden.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "main" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:T: locked
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: E
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
    And the org pack "main" with the file "directives/E2.directive.yaml":
      """
      schema_version: '1.0'
      id: E2
      title: E2
      intent: E2
      enforcement: required
      """
    And the org pack "main" with the file "override.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:E
        target: directive:T
        relation: overrides
      - source: directive:E2
        target: directive:T2
        relation: overrides
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["E", "E2", "T2"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:E
      directive:E2
      directive:T
      """
    And the field at "/items/2/reason" is "locked"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/overrideNotApplied" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/overrideNotApplied/0/overriddenBy" holds exactly the ids:
      """
      directive:E
      """
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:T2
      """

  Scenario: A8 - an enhancer ring is not in force, and an enhancer of a target in force is
    A8, R13 stratum 4 (a least fixed point): E1 enhances E2 and E2 enhances E1, so neither has a non-enhancer in force
    to stand on and a ring cannot hold itself up: both are inactive with enhanced_target_not_in_force. E3 enhances T,
    which is selected: the control, in force in the same answer. A registration refuses the ring (enhances_cycle), so
    the pack files are written past the API.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "tactics/e1.tactic.yaml":
      """
      schema_version: '1.0'
      id: e1
      name: E1
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e3.tactic.yaml":
      """
      schema_version: '1.0'
      id: e3
      name: E3
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "ring.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e1
        target: tactic:e2
        relation: enhances
      - source: tactic:e2
        target: tactic:e1
        relation: enhances
      - source: tactic:e3
        target: tactic:t
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e1", "e2", "e3", "t"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e3
      tactic:t
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e1
      tactic:e2
      """
    And the field at "/items/0/reason" is "enhanced_target_not_in_force"
    And the field at "/items/1/reason" is "enhanced_target_not_in_force"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/enhancedTargetNotInForce" holds exactly the ids:
      """
      tactic:e1
      tactic:e2
      """

  Scenario: A step of a denied mission type is not in force (the binding)
    A7 Q1, research R13 stratum 4: a step, and any artifact bound to a mission type, is in force only while the mission
    type is. research is denied and its step contract is selected, so the step contract is inactive with
    mission_type_not_in_force and is in missionTypeNotInForce. The step contract of software-dev, which is selected,
    is the control, in force in the same answer.
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
    And the org pack "main" with the file "mission_step_contracts/sd-implement.step-contract.yaml":
      """
      id: sd-implement
      schema_version: '1.0'
      action: implement
      mission: software-dev
      steps:
        - id: first
          description: The first step
      """
    And the org pack "main" with the file "mission_step_contracts/research-gather.step-contract.yaml":
      """
      id: research-gather
      schema_version: '1.0'
      action: gather
      mission: research
      steps:
        - id: first
          description: The first step
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"], "mission_step_contract": ["sd-implement", "research-gather"]}, "deactivated": {"mission_type": ["research"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:sd-implement
      """
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """
    And the field at "/items/0/reason" is "mission_type_not_in_force"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/missionTypeNotInForce" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """

  Scenario: A required step whose mission type is not in force is dropped and reported (DM-01M6RFGFW3S0YJ222QMHD2W11B)
    Decision DM-01M6RFGFW3S0YJ222QMHD2W11B: the governing preset requires the step contract research-gather and the
    directive D. research is not in force, so the step contract stays out (inactive, mission_type_not_in_force, with
    required true) and is in requiredNotInForce. D, required and unbound, is the control: in force with reason
    required in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
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
    And the org pack "main" with the file "mission_step_contracts/research-gather.step-contract.yaml":
      """
      id: research-gather
      schema_version: '1.0'
      action: gather
      mission: research
      steps:
        - id: first
          description: The first step
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      activated_mission_step_contracts:
        - id: research-gather
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/items/0/reason" is "required"
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """
    And the field at "/items/0/reason" is "mission_type_not_in_force"
    And the field at "/items/0/required" is true
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotInForce" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """
    And the field at "/report/requiredNotInForce/0/reason" is "mission_type_not_in_force"

  Scenario: A required enhancer with no target in force is dropped and reported (DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ, A9)
    Decision DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ, A9: the governing preset requires the tactic RE, which enhances T, and T is
    not selected. The enhancer rule wins over the requirement: RE is inactive with enhanced_target_not_in_force and
    required true, and is in requiredNotInForce. The required directive D is the control, in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "tactics/re.tactic.yaml":
      """
      schema_version: '1.0'
      id: re
      name: RE
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:re
        target: directive:T
        relation: enhances
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      activated_tactics:
        - id: re
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:re
      """
    And the field at "/items/0/reason" is "enhanced_target_not_in_force"
    And the field at "/items/0/required" is true
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotInForce" holds exactly the ids:
      """
      tactic:re
      """
    And the field at "/report/requiredNotInForce/0/reason" is "enhanced_target_not_in_force"

  Scenario: A denied enhancer whose target is out of force is inactive with enhanced_target_not_in_force, and is not excluded
    E9, decision 01M6YM02EG3X5V8G33T1HT5N0K: the enhancer row comes before the deny row, so the document's deny entry on E
    does not make E excluded. E enhances T, which is not selected, so E is inactive with enhanced_target_not_in_force; it
    is in enhancedTargetNotInForce with its target, it is not in skippedDeactivated, and summary.excluded does not count
    it. E2 enhances T2, which is selected: the control, in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
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
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
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
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e2"], "directive": ["T2"]}, "deactivated": {"tactic": ["e"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=tactic"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:e
      tactic:e2
      """
    And the field at "/items/0/state" is "inactive"
    And the field at "/items/0/reason" is "enhanced_target_not_in_force"
    And the field at "/items/1/state" is "active"
    And the field at "/summary/excluded" is 0
    And the field at "/summary/skipped" is 1
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/enhancedTargetNotInForce" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/report/skippedDeactivated" holds exactly 0 items

  Scenario: A required enhancer the document selects is listed once, in requiredNotInForce only
    Decision 01M6YM02EG3X5V8G33T1HT5N0K: the governing preset requires the tactic RE, which enhances T, T is not
    selected, and the document selects RE. The enhancer rule wins over the requirement and over the selection: RE is
    inactive with enhanced_target_not_in_force and is in requiredNotInForce, and not in enhancedTargetNotInForce as well,
    so summary.skipped counts it once. The required directive D is the control, in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "tactics/re.tactic.yaml":
      """
      schema_version: '1.0'
      id: re
      name: RE
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "enhance.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:re
        target: directive:T
        relation: enhances
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      activated_tactics:
        - id: re
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["re"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=tactic"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:re
      """
    And the field at "/items/0/state" is "inactive"
    And the field at "/items/0/reason" is "enhanced_target_not_in_force"
    And the field at "/items/0/required" is true
    And the field at "/summary/skipped" is 1
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotInForce" holds exactly the ids:
      """
      tactic:re
      """
    And the field at "/report/enhancedTargetNotInForce" holds exactly 0 items

  Scenario: B1 - a version pin no pack holds is in pinNotHeld, and a held pin of the same document is not
    B1, FR-026: the document pins main at 9.9.9, which main does not hold, and other at 1.0.0, which it does. A read
    reports the unheld pin (a write would drop it): pinNotHeld holds main's pin alone. The directive OD of other is the
    control, in force in the same answer. The document is stored past the API, which would drop the pin.
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
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other-pack
      pack_level: org
      """
    And the org pack "other" with the file "directives/OD.directive.yaml":
      """
      schema_version: '1.0'
      id: OD
      title: OD
      intent: OD
      enforcement: required
      """
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"main","version":"9.9.9"},{"packId":"other","version":"1.0.0"}],"schemaVersion":2,"selected":{"directive":["OD"]}}
      """
    When I GET "/activations/a-1/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:OD
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/pinNotHeld" holds exactly 1 item
    And the field at "/report/pinNotHeld/0/packId" is "main"
    And the field at "/report/pinNotHeld/0/version" is "9.9.9"

  Scenario: A10 - a must-have item no pack holds is in requiredNotHeld, and a held one is required and in force
    A10, FR-028: the governing preset names D, which main holds, and GONE, which no pack holds. GONE is not on the
    floor and is in requiredNotHeld; D is the control, required and in force in the same answer. The same case, with a
    preset of a second pack, is in activation-ruling-changes.feature.
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
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
        - id: GONE
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/items/0/required" is true
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotHeld" holds exactly the ids:
      """
      directive:GONE
      """

  Scenario: The language filter applies at read only: no languages filters nothing, and what it filtered is in the bucket
    #408 ruling 2, FR-013, FR-017: py-style applies to python and js-style to javascript, D is untagged. The activation
    a-1 sends no languages: nothing is filtered and the report's languageFiltered is empty (all three in force is the
    positive control). The activation a-2 sends languages [python]: js-style is inactive with language_filtered and is
    in languageFiltered, and py-style and D stay in force in the same answer.
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
    And the org pack "main" with the file "styleguides/py-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: py-style
      title: py-style
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      applies_to_languages:
        - python
      """
    And the org pack "main" with the file "styleguides/js-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: js-style
      title: js-style
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      applies_to_languages:
        - javascript
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "styleguide": ["py-style", "js-style"]}}
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "languages": ["python"], "selected": {"directive": ["D"], "styleguide": ["py-style", "js-style"]}}
      """
    When I GET "/activations/a-1/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      styleguide:js-style
      styleguide:py-style
      """
    And the field at "/summary/active" is 3
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/languageFiltered" holds exactly 0 items
    When I GET "/activations/a-2/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      styleguide:py-style
      """
    When I GET "/activations/a-2/artifacts?kind=styleguide&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      styleguide:js-style
      """
    And the field at "/items/0/reason" is "language_filtered"
    When I GET "/activations/a-2/skipped"
    Then the response status is 200
    And the field at "/report/languageFiltered" holds exactly the ids:
      """
      styleguide:js-style
      """

  Scenario: An artifact reached only through suggests is listed as a suggestion with its when and reason
    #408 ruling 3, FR-013: D suggests S, with a when and a reason, and the document selects D alone. S is reached only
    through the suggests edge, so it is listed as a suggestion carrying the edge's when and reason (not as in force).
    R, which D requires and which is selected, is the control: a plain entry, with no suggestion member.
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
      nodes: []
      edges:
      - source: directive:D
        target: directive:R
        relation: requires
      - source: directive:D
        target: directive:S
        relation: suggests
        when: the change touches the schema
        reason: S covers the schema checks
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "R"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      directive:R
      """
    And the field at "/items/1/suggestion" is absent
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:S
      """
    And the field at "/items/0/suggestion/when" is "the change touches the schema"
    And the field at "/items/0/suggestion/reason" is "S covers the schema checks"

  Scenario: A13 - a cascading deactivate after a cascading select leaves the artifact the select reached unselected
    A13, decision DM-01M6RA8XSM9CZZX1ADX6G80T3X: T requires Y, the document selects Y and Z, and a cascading select of T
    then a cascading deactivate of T remove T and what its walk reached unless something else active keeps it. Prior
    selections are not remembered, so Y is no longer selected (inactive, not_selected); Z, which the walk never
    reached, is the control, still in force in the same answer.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/Y.directive.yaml":
      """
      schema_version: '1.0'
      id: Y
      title: Y
      intent: Y
      enforcement: required
      """
    And the org pack "main" with the file "directives/Z.directive.yaml":
      """
      schema_version: '1.0'
      id: Z
      title: Z
      intent: Z
      enforcement: required
      """
    And the org pack "main" with the file "requires.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:T
        target: directive:Y
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["Y", "Z"]}}
      """
    When I PUT "/activations/a-1/selections/directive/T" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    When I PUT "/activations/a-1/exclusions/directive/T" with the header "If-Match": "*" and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:Z
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:Y
      """
    And the field at "/items/0/reason" is "not_selected"

  Scenario: US4 AS1 - each artifact not in force is in exactly one bucket, and the report carries the revision
    US4 AS1, FR-017: one document that has a stale id (GHOST), a deny entry the floor does not apply (D1), a language-
    filtered styleguide, an overridden directive (OV), a step of a mission type not in force, an override of a locked
    artifact (LKO of LK), an enhancer without a target (ENH), a pin and a must-have item no pack holds, and a required
    enhancer that cannot stand in force (RENH). Each is in exactly its bucket. The controls are in the same answer: the
    artifacts that are in force (D1 required, LK locked, LKO and OVR selected) are listed once as active.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "main" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:LK: locked
      """
    And the org pack "main" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: D1
      intent: D1
      enforcement: required
      """
    And the org pack "main" with the file "directives/OV.directive.yaml":
      """
      schema_version: '1.0'
      id: OV
      title: OV
      intent: OV
      enforcement: required
      """
    And the org pack "main" with the file "directives/OVR.directive.yaml":
      """
      schema_version: '1.0'
      id: OVR
      title: OVR
      intent: OVR
      enforcement: required
      """
    And the org pack "main" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: LK
      intent: LK
      enforcement: required
      """
    And the org pack "main" with the file "directives/LKO.directive.yaml":
      """
      schema_version: '1.0'
      id: LKO
      title: LKO
      intent: LKO
      enforcement: required
      """
    And the org pack "main" with the file "directives/TG.directive.yaml":
      """
      schema_version: '1.0'
      id: TG
      title: TG
      intent: TG
      enforcement: required
      """
    And the org pack "main" with the file "styleguides/js-style.styleguide.yaml":
      """
      schema_version: '1.0'
      id: js-style
      title: js-style
      scope: code
      principles:
        - name: Keep it small
          description: Keep it small, in one sentence.
      applies_to_languages:
        - javascript
      """
    And the org pack "main" with the file "tactics/enh.tactic.yaml":
      """
      schema_version: '1.0'
      id: enh
      name: ENH
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/renh.tactic.yaml":
      """
      schema_version: '1.0'
      id: renh
      name: RENH
      steps:
        - title: Only step
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
    And the org pack "main" with the file "mission_step_contracts/research-gather.step-contract.yaml":
      """
      id: research-gather
      schema_version: '1.0'
      action: gather
      mission: research
      steps:
        - id: first
          description: The first step
      """
    And the org pack "main" with the file "all.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:OVR
        target: directive:OV
        relation: overrides
      - source: directive:LKO
        target: directive:LK
        relation: overrides
      - source: tactic:enh
        target: directive:TG
        relation: enhances
      - source: tactic:renh
        target: directive:TG
        relation: enhances
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D1
          must_have: true
        - id: GONE
          must_have: true
      activated_tactics:
        - id: renh
          must_have: true
      """
    And the stored file of the activation "a-1" holds:
      """
      {"deactivated":{"directive":["D1"]},"languages":["python"],"packs":[{"packId":"main","version":"9.9.9"}],"schemaVersion":2,"selected":{"directive":["GHOST","LKO","OV","OVR"],"mission_step_contract":["research-gather"],"styleguide":["js-style"],"tactic":["enh"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D1
      directive:LK
      directive:LKO
      directive:OVR
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field "revision" of the response body is a sha256 digest
    And the field "governanceFingerprint" of the response body is a sha256 digest
    And the field at "/report/staleIds" holds exactly the ids:
      """
      directive:GHOST
      """
    And the field at "/report/denyNotApplied" holds exactly the ids:
      """
      directive:D1
      """
    And the field at "/report/languageFiltered" holds exactly the ids:
      """
      styleguide:js-style
      """
    And the field at "/report/overridden" holds exactly the ids:
      """
      directive:OV
      """
    And the field at "/report/missionTypeNotInForce" holds exactly the ids:
      """
      mission_step_contract:research-gather
      """
    And the field at "/report/overrideNotApplied" holds exactly the ids:
      """
      directive:LK
      """
    And the field at "/report/enhancedTargetNotInForce" holds exactly the ids:
      """
      tactic:enh
      """
    And the field at "/report/pinNotHeld" holds exactly 1 item
    And the field at "/report/requiredNotHeld" holds exactly the ids:
      """
      directive:GONE
      """
    And the field at "/report/requiredNotInForce" holds exactly the ids:
      """
      tactic:renh
      """

  Scenario: US4 AS2 - an unknown activation is 404 activation_not_found on both reads
    US4 AS2, FR-021: neither read answers an activation the service does not hold. The activation a-1, which it holds,
    is the control: both reads answer 200 with its directive in the same scenario.
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
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"]}}
      """
    When I GET "/activations/unknown/artifacts"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/unknown/skipped"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/a-1/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/staleIds" holds exactly 0 items

  Scenario: The list is paged by a keyset cursor, and summary is identical on every page
    FR-013, plan IC-09: three directives, a page of two. The first page holds A and B and the second holds C, in
    (kind, id) order, and summary counts the whole set on both pages (three in force). The control is that the two
    pages together hold each artifact exactly once.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "main" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the org pack "main" with the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C
      intent: C
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["A", "B", "C"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=2"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      directive:B
      """
    And the field at "/summary/active" is 3
    And the field at "/pageInfo/hasNextPage" is true
    When I follow the link "next"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:C
      """
    And the field at "/summary/active" is 3
    And the field at "/pageInfo/hasNextPage" is false

  Scenario: A cursor from another tag is 400, and a fresh cursor of the new tag pages on
    Plan IC-09, research R5: the cursor binds the tag of the set it pages. After the document changes (the revision, so
    the tag, changes) the old cursor is 400 without a code. The control is the same read without a cursor, which answers
    200 with the new set, and the cursor it carries, which pages on and answers 200.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "main" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the org pack "main" with the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C
      intent: C
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["A", "B", "C"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=2"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      directive:B
      """
    When I remember the field "/pageInfo/nextPageCursor" of the response body
    And I PUT "/activations/a-1/exclusions/directive/C" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=2" with the remembered cursor
    Then the response status is 400
    And the field at "/detail" starts with "The query parameter pageCursor is \""
    And the field at "/detail" ends with "\", which belongs to another set of effective artifacts; read the first page again."
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=1"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    When I follow the link "next"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:B
      """

  Scenario: A cursor taken from state=active and replayed with state=inactive is 400
    Plan IC-09, research R5: the cursor binds the query of the set it pages, not only the document's tag. A cursor from
    the active list, replayed against the inactive list of the same, unchanged document, is 400. The control is the same
    cursor on the active list it came from, which pages on and answers 200 with the last active artifact, and the inactive
    list without a cursor, which answers 200 with the artifact that is not selected.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "main" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the org pack "main" with the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C
      intent: C
      enforcement: required
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
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["A", "B", "C"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=2"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      directive:B
      """
    When I remember the field "/pageInfo/nextPageCursor" of the response body
    And I GET "/activations/a-1/artifacts?kind=directive&state=inactive&pageSize=2" with the remembered cursor
    Then the response status is 400
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=2" with the remembered cursor
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:C
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive&pageSize=2"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """

  Scenario: US3 AS7 - a matching If-None-Match is 304 with the current tag
    US3 AS7, FR-020: the list answers an ETag, and the same request with that tag as If-None-Match is 304 with no body and
    the same tag. The first answer, 200 with the artifact A, is the positive control: the 304 is not an empty store.
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/other" with the archive "other-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    And the response header "ETag" appears once
    When I remember the header "ETag"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header

  Scenario: US3 AS7 - after a re-registration that changes a pack of the closure the old tag is 200 with a new ETag
    US3 AS7, research R5: the tag covers the packs of the activation's closure. acme is in the closure and its version 1.1.0
    is registered over 1.0.0, so the old tag no longer matches: 200 with the list and a new ETag. The 304 with the old tag
    before the re-registration is the control that the tag matched until then.
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "acme-v2" is a copy of the archive "acme-v1"
    And the archive "acme-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v2" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 2
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/other" with the archive "other-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    When I remember the header "ETag"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    When I PUT "/packs/acme" with the archive "acme-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """

  Scenario: US3 AS7 - a change to a pack outside the closure keeps the tag
    US3 AS7, research R5: the activation is rooted at acme alone, and other is registered again with another version. The
    closure of the activation does not hold other, so the tag is unchanged and the old tag is 304. The pack other is held and
    listed (the positive control: GET /packs/other answers 200), so the re-registration did change something.
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    And the archive "other-v2" is a copy of the archive "other-v1"
    And the archive "other-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.1.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v2" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 2
      intent: O
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/other" with the archive "other-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    When I remember the header "ETag"
    When I PUT "/packs/other" with the archive "other-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/packs/other"
    Then the response status is 200
    And the field at "/version" is "1.1.0"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header

  Scenario: US3 AS7 - a state, a kind and a cursor page never answer 304 to the tag of another representation
    US3 AS7, WP24 T123: an entity tag names one representation, so two queries never share a tag. The tag of the first
    page (directives in force, pageSize 1) matches its own request (304, the control) and no other: another state, another
    kind and the next page (a cursor) each answer 200 with their own non-empty list and a tag that differs. The activation
    selects the directives A and B and the tactic T, and C is not selected.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A
      enforcement: required
      """
    And the org pack "main" with the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: B
      enforcement: required
      """
    And the org pack "main" with the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C
      intent: C
      enforcement: required
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["A", "B"], "tactic": ["t"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=1"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    When I remember the header "ETag"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=1" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive&pageSize=1" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/items" holds exactly the ids:
      """
      directive:C
      """
    When I GET "/activations/a-1/artifacts?kind=tactic&state=active&pageSize=1" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/items" holds exactly the ids:
      """
      tactic:t
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active&pageSize=1"
    Then the response status is 200
    When I follow the link "next" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/items" holds exactly the ids:
      """
      directive:B
      """

  Scenario: B4 - the ETag of getActivation changes when a pack of the closure changes, and not for an unrelated pack
    Decision 01M6Y11E55YP5TG1EJ1MDKCFRP: denyNotApplied is judged against the packs, so the strong tag of GET
    /activations/{id} covers the revision and the closure's fingerprint. acme is in the closure and its version 1.1.0 is
    registered over 1.0.0, so the old tag is 200 with a new ETag and the activation. The control is the unrelated pack
    other, registered again before: the tag then still matched (304), and the 200 body is not empty.
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "acme-v2" is a copy of the archive "acme-v1"
    And the archive "acme-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v2" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 2
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    And the archive "other-v2" is a copy of the archive "other-v1"
    And the archive "other-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.1.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v2" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 2
      intent: O
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/other" with the archive "other-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      """
    When I remember the header "ETag"
    And I PUT "/packs/other" with the archive "other-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/activations/a-1" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    When I PUT "/packs/acme" with the archive "acme-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/activations/a-1" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      """

  Scenario: B4 - a write takes the getActivation tag or the revision as If-Match, and a stale closure tag is 412
    Decision 01M6Y3GZFRC5WEQQ7G6GT0YMW3: an activation write accepts If-Match carrying the quoted revision or the current
    getActivation tag (the revision and the closure's fingerprint); a closure tag that no longer matches is 412. The write's
    answer keeps the revision as its ETag and carries no denyNotApplied. A GET, then a PUT with the GET's tag, is 200; the
    revision the PUT answered is accepted as well (the control that the revision still works). After acme, a pack of the
    closure, is registered again, the old GET tag on a PUT is 412, and a fresh GET tag is 200 (the positive control).
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "acme-v2" is a copy of the archive "acme-v1"
    And the archive "acme-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v2" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 2
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      """
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with If-Match the remembered header "ETag" and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field at "/denyNotApplied" is absent
    When I remember the ETag
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 200
    When I PUT "/packs/acme" with the archive "acme-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with If-Match the remembered header "ETag" and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 412
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      """
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with If-Match the remembered header "ETag" and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 200

  Scenario: B4 - a registration that changes an activation's in-force set lists it in activationsAffected, and not an unrelated one
    FR-027, B4: a-1 is rooted at acme and selects A; acme version 1.2.0 drops A and holds B, so the in-force set of a-1
    changes and the succeeded registration lists a-1 in notices.activationsAffected. a-2, rooted at the unrelated pack
    other, is the control: it is not listed, though it exists and is in force (the same answer lists a-1, so the list is
    not empty by accident).
    Given registration is switched on
    And the archive "acme-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: A
      enforcement: required
      """
    And the archive "other-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: other
      pack_level: org
      """
    And the archive "other-v1" holds the file "directives/O.directive.yaml":
      """
      schema_version: '1.0'
      id: O
      title: O at version 1
      intent: O
      enforcement: required
      """
    And the archive "acme-v3" is a copy of the archive "acme-v1"
    And the archive "acme-v3" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.2.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-v3" lacks the file "directives/A.directive.yaml"
    And the archive "acme-v3" holds the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B at version 3
      intent: B
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/other" with the archive "other-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "acme"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "other"}], "selected": {"directive": ["O"]}}
      """
    Then the response status is 201
    When I PUT "/packs/acme" with the archive "acme-v3"
    And I poll the registration until it ends
    Then the response status is 200
    And the field at "/state" is "succeeded"
    And the field at "/result/notices/activationsAffected" holds exactly the ids:
      """
      a-1
      """
    When I GET "/activations/a-2/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:O
      """

  Scenario: B4 - a registration that changes only the version in force of a selected artifact lists the activation
    FR-027, B4: the built-in pack core holds A. a-1 is rooted at core and house and selects A, which is in force from
    core. house version 1.1.0 holds A as well, so the org version replaces the built-in one: the artifacts in force are
    the same (A), but the version in force is another pack's at another level, and the effective set differs. The
    succeeded registration lists a-1 in notices.activationsAffected. a-2, rooted at core alone, is the control: its closure
    does not hold house, so it is not listed, though A is in force there too.
    Given registration is switched on
    And the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A in core
      intent: A
      enforcement: required
      """
    And the archive "house-v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the archive "house-v2" is a copy of the archive "house-v1"
    And the archive "house-v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.1.0
      parent_pack: null
      name: house-pack
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the archive "house-v2" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A in house
      intent: A
      enforcement: required
      """
    When I PUT "/packs/house" with the archive "house-v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "house"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}], "selected": {"directive": ["A"]}}
      """
    Then the response status is 201
    When I PUT "/packs/house" with the archive "house-v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/notices/activationsAffected" holds exactly the ids:
      """
      a-1
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A
      """
    And the field at "/items/0/packId" is "house"
    When I GET "/activations/a-2/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items/0/packId" is "core"

  Scenario: US3 AS7 - after the v1 to v2 write the tag changes once
    US3 AS7, FR-042, A7 Q4: a stored version 1 document is read as its version 2 upgrade and a read writes nothing. A
    write stores version 2 and the tag changes (the old tag is 200 with a new ETag); further reads then keep the new tag
    (a 304, the control that it changed once and is stable). The stored file keeps schemaVersion 1 until the write.
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
    And the org pack "main" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: D2
      enforcement: required
      """
    And the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "activatedKinds": ["directive"], "selected": {"directive": ["D"]}}
      """
    When I remember the stored file of the activation "a-1"
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      """
    When I remember the header "ETag"
    Then the stored file of the activation "a-1" is the remembered one
    When I PUT "/activations/a-1/selections/directive/D2" with the header "If-Match": "*" and the body:
      """
      {}
      """
    Then the response status is 200
    And the stored file of the activation "a-1" has the field "/schemaVersion" equal to 2
    When I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      directive:D2
      """
    When I remember the header "ETag"
    And I GET "/activations/a-1/artifacts?kind=directive&state=active" with If-None-Match the remembered header "ETag"
    Then the response status is 304

  Scenario: B6/B7 - a select of an enhancer whose target is denied is 200 with the warning enhanced_target_not_in_force
    IC-17, FR-032: T is denied, so selecting E, which enhances T, is accepted and the answer warns that the read will not
    put it in force. Selecting E2, whose target T2 is selected and so in force, is the control: 200 with E2 applied and
    no warning, in the same scenario. (An enhancer is in force only with a target in force, Q8: a target that is merely
    not denied is not enough.)
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/t2.tactic.yaml":
      """
      schema_version: '1.0'
      id: t2
      name: T2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "main" with the file "directives/U.directive.yaml":
      """
      schema_version: '1.0'
      id: U
      title: U
      intent: U
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e
        target: tactic:t
        relation: enhances
      - source: tactic:e2
        target: tactic:t2
        relation: enhances
      - source: directive:F
        target: directive:U
        relation: overrides
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["t2"]}, "deactivated": {"tactic": ["t"]}}
      """
    When I PUT "/activations/a-1/selections/tactic/e" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/warnings" holds exactly 1 item
    And the field at "/warnings/0/code" is "enhanced_target_not_in_force"
    And the field at "/warnings/0/artifact/artifactId" is "e"
    And the field at "/warnings/0/artifact/kind" is "tactic"
    When I PUT "/activations/a-1/selections/tactic/e2" with the header "If-Match": "*" and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:e2
      """
    And the field at "/warnings" holds exactly 0 items

  Scenario: B6/B7 - a select of an artifact an active override hides is 200 with the warning overridden
    IC-17, FR-032: F is selected and overrides U, so selecting U is accepted and the answer warns overridden. Selecting X,
    which nothing overrides, is the control: 200 with X applied and no warning.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/t2.tactic.yaml":
      """
      schema_version: '1.0'
      id: t2
      name: T2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "main" with the file "directives/U.directive.yaml":
      """
      schema_version: '1.0'
      id: U
      title: U
      intent: U
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e
        target: tactic:t
        relation: enhances
      - source: tactic:e2
        target: tactic:t2
        relation: enhances
      - source: directive:F
        target: directive:U
        relation: overrides
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}}
      """
    When I PUT "/activations/a-1/selections/directive/U" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:U
      """
    And the field at "/warnings" holds exactly 1 item
    And the field at "/warnings/0/code" is "overridden"
    And the field at "/warnings/0/artifact/artifactId" is "U"
    When I PUT "/activations/a-1/selections/directive/X" with the header "If-Match": "*" and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:X
      """
    And the field at "/warnings" holds exactly 0 items

  Scenario: B4 - GET /activations/{id} lists a deny entry the floor overrides in denyNotApplied, and not an applied one
    IC-17, FR-027, B4: the governing preset requires D, and the stored document denies D (a pack change after the write
    would leave it so) and X. D's deny entry is not applied, so the activation lists directive:D in denyNotApplied. X is
    the control: its deny entry is applied, so it is not listed, and the document in config still carries both.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "tactics/t.tactic.yaml":
      """
      schema_version: '1.0'
      id: t
      name: T
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: E
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/t2.tactic.yaml":
      """
      schema_version: '1.0'
      id: t2
      name: T2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e2.tactic.yaml":
      """
      schema_version: '1.0'
      id: e2
      name: E2
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: X
      enforcement: required
      """
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "main" with the file "directives/U.directive.yaml":
      """
      schema_version: '1.0'
      id: U
      title: U
      intent: U
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: tactic:e
        target: tactic:t
        relation: enhances
      - source: tactic:e2
        target: tactic:t2
        relation: enhances
      - source: directive:F
        target: directive:U
        relation: overrides
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D
          must_have: true
      """
    And the stored file of the activation "a-1" holds:
      """
      {"deactivated":{"directive":["D","X"]},"packs":[{"packId":"main"}],"schemaVersion":2}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/denyNotApplied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      D
      X
      """

  Scenario Outline: A filter or cursor the effective list cannot read is a 400 Problem without a code that says why (<query>)
    #411: the detail names the parameter, the value it carried and what it accepts.
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I GET "/activations/a-1/artifacts?<query>"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "<detail>"

    Examples:
      | query           | detail                                                                                                                                                                                                                  |
      | kind=template   | The query parameter kind is \"template\", which is not one of directive, tactic, styleguide, toolguide, paradigm, procedure, agent_profile, mission_step_contract, glossary_pack, skill, anti_pattern, mission_type or step_enhancement. |
      | state=retired   | The query parameter state is \"retired\", which is not one of active, inactive or excluded.                                                                                                                             |
      | pageCursor=YQ   | The query parameter pageCursor is \"YQ\", which is not a cursor this list gave.                                                                                                                                         |
