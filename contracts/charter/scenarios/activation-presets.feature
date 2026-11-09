@contract @activations
Feature: Applying a preset to an activation
  PUT …/activations/{activationId}/presets/{presetId} applies one preset the activation's closure holds
  (ADR-0059 D6, ADR-0020 D6, FR-016, User Story 5). Each key the preset lists replaces that key of the document:
  each selected.<kind> it lists, activatedKinds and missionTypes. A key the preset does not list is left as it is.
  A preset lists a key when its file holds at least one member for it; the packs read an absent list and an empty
  one alike, so an empty list counts as not listed (A7). Without force a member the document denies is not
  selected and its deny entry stays; with force true the deny entries of the preset's members are cleared and
  reported in cleared. applied lists every id the write added to or removed from selected, deactivated or
  missionTypes, so the ids a replaced key drops are listed too; a kind is never an entry. A preset id no pack of
  the closure holds is 404 preset_not_found; when two packs of the closure hold one preset id, the last in merge
  order wins. The write requires If-Match and answers an ActivationChange with the new revision as its ETag; a
  write that changes nothing answers 200 with the same revision and stores nothing.

  The fixture is one org pack, main, over a second, shared, and a third pack, other, the activation does not
  name. main holds directives A, B, C and M, tactic t and the mission types old-type and software-dev. Its
  presets: team (directives B and C, the kind gate [directives]), also held by shared with tactic u, where
  main's wins; force-me (directives B and M); no-kinds (directive A and no kind gate); mission-types
  (activated_mission_types [software-dev]). other holds the preset outside.

  Background:
    Given the org pack "shared" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: shared
      pack_level: org
      """
    And the org pack "shared" with the file "tactics/u.tactic.yaml":
      """
      schema_version: '1.0'
      id: u
      name: u
      steps:
        - title: Only step
      """
    And the org pack "shared" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The shared pack's team preset, which main's overrides.
      activated_tactics:
        - id: u
      """
    And the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
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
    And the org pack "main" with the file "directives/M.directive.yaml":
      """
      schema_version: '1.0'
      id: M
      title: M
      intent: M
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
    And the org pack "main" with the file "missions/old-type/old-type.mission-type.yaml":
      """
      schema_version: "1.0"
      id: old-type
      title: Old type
      description: The mission type the document starts with
      steps:
        - id: start
          title: Start
          role: implementer
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: Software development
      description: Build software
      steps:
        - id: implement
          title: Implement
          role: implementer
      """
    And the org pack "main" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The team's directives.
      activated_kinds:
        - directives
      activated_directives:
        - id: B
        - id: C
      """
    And the org pack "main" with the file "presets/force-me.yaml":
      """
      schema_version: '1.0'
      id: force-me
      description: B and the directive the document denies.
      activated_directives:
        - id: B
        - id: M
      """
    And the org pack "main" with the file "presets/no-kinds.yaml":
      """
      schema_version: '1.0'
      id: no-kinds
      description: A, with no kind gate.
      activated_directives:
        - id: A
      """
    And the org pack "main" with the file "presets/mission-types.yaml":
      """
      schema_version: '1.0'
      id: mission-types
      description: Software development only.
      activated_mission_types:
        - id: software-dev
      """
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
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
    And the org pack "other" with the file "presets/outside.yaml":
      """
      schema_version: '1.0'
      id: outside
      description: A preset outside the activation's closure.
      activated_directives:
        - id: X
      """

  # User Story 5: apply a preset.

  Scenario: A preset replaces each key it lists and leaves the others untouched
    US5 AS1 (ADR-0059 D6): team lists directives and the kind gate, so selected.directive becomes [B, C] and
    activatedKinds [directive]; selected.tactic is not listed and stays [t]. applied lists A, which the replaced
    key dropped, as well as B and C (A1), and no kind. main's team wins over shared's, which lists tactic u.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["A"], "tactic": ["t"]}}
      """
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:A
      directive:B
      directive:C
      """
    And the field at "/cleared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      B
      C
      """
    And the field at "/config/selected/tactic" holds exactly the ids:
      """
      t
      """
    And the field at "/config/activatedKinds" holds exactly the ids:
      """
      directive
      """

  Scenario: A preset that lists no kind leaves activatedKinds untouched
    A7: no-kinds has no activated_kinds, which the packs read as they read an empty list, so the key is not
    listed and the document's kind gate stays.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "activatedKinds": ["tactic"], "selected": {"directive": ["B"]}}
      """
    When I PUT "/activations/a-1/presets/no-kinds" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:A
      directive:B
      """
    When I GET "/activations/a-1"
    Then the field at "/config/activatedKinds" holds exactly the ids:
      """
      tactic
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      """

  Scenario: Without force a denied member is not selected and its deny entry stays
    US5 AS2, first half: M is denied, so force-me selects B alone; M is neither selected nor applied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"directive": ["M"]}}
      """
    When I PUT "/activations/a-1/presets/force-me" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:B
      """
    And the field at "/cleared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      B
      """
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      M
      """

  Scenario: With force the deny entries of the preset's members are cleared and the members selected
    US5 AS2, second half (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"directive": ["M"]}}
      """
    When I PUT "/activations/a-1/presets/force-me" with the stored ETag and the body:
      """
      {"force": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly the ids:
      """
      directive:M
      """
    And the field at "/applied" holds exactly the ids:
      """
      directive:B
      directive:M
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      B
      M
      """
    And the response body has no field at "/config/deactivated"

  Scenario: A preset that lists mission types replaces missionTypes
    FR-016, R7: the replaced key's dropped id is in applied too (A1).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "missionTypes": ["old-type"]}
      """
    When I PUT "/activations/a-1/presets/mission-types" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:old-type
      mission_type:software-dev
      """
    When I GET "/activations/a-1"
    Then the field at "/config/missionTypes" holds exactly the ids:
      """
      software-dev
      """

  Scenario: A preset that lists no mission types leaves missionTypes untouched
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "missionTypes": ["old-type"]}
      """
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    When I GET "/activations/a-1"
    Then the field at "/config/missionTypes" holds exactly the ids:
      """
      old-type
      """

  Scenario: Applying a preset that changes nothing answers the same revision
    The no-op: the document already holds what team sets, so the answer is 200 with the stored revision, an
    empty applied and cleared, no dropped (the write dropped nothing), and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "activatedKinds": ["directive"], "selected": {"directive": ["B", "C"]}}
      """
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items
    And the response body has no field at "/dropped"

  Scenario: A preset no pack holds is 404 preset_not_found
    US5 AS3.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/no-such-preset" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "preset_not_found"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  Scenario: A preset held only by a pack outside the activation's closure is 404 preset_not_found
    FR-010: other holds outside, but the activation builds on main alone.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/outside" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "preset_not_found"

  Scenario: A preset whose member the closure does not hold is 422 at the whole request
    FR-017: the preset is expanded into the document, and the document is judged as any write's is. A member no
    pack of the closure holds (a preset gone stale after an upgrade) is an unknown_artifact; a preset write has no
    body list for a pointer to index, so the violation points at the whole request. Nothing is stored.
    Given the org pack "main" with the file "presets/stale.yaml":
      """
      schema_version: '1.0'
      id: stale
      description: A member the pack no longer holds.
      activated_directives:
        - id: GONE
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/stale" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is ""
    And the field at "/violations/0/code" is "unknown_artifact"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  Scenario: A stale preset member the stored document already names is 422, never a no-op
    ADR-0059 amendment R5: a pack upgrade removed GONE, which the stored selected.directive and the preset still
    name. The write drops GONE from the stored document and the preset puts it back, so the computed document
    equals the stored one. A write that dropped a stale id is never a no-op, so the document is judged, and GONE
    is an unknown_artifact at the whole request. Nothing is stored.
    Given the org pack "main" with the file "directives/GONE.directive.yaml":
      """
      schema_version: '1.0'
      id: GONE
      title: Gone
      intent: GONE
      enforcement: required
      """
    And the org pack "main" with the file "presets/stale.yaml":
      """
      schema_version: '1.0'
      id: stale
      description: A member the pack no longer holds.
      activated_directives:
        - id: GONE
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["GONE"]}}
      """
    And the file "directives/GONE.directive.yaml" of the pack "main" is deleted
    When I PUT "/activations/a-1/presets/stale" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is ""
    And the field at "/violations/0/code" is "unknown_artifact"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      GONE
      """

  Scenario: A force of the wrong type is 422 at its pointer, never coerced
    PresetApplication.force is a boolean: a string or a number in its place is a fault of the body, judged after
    the preconditions (FR-010), and never read as true. Nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/team" with the stored ETag and the body:
      """
      {"force": "true"}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/force"
    And the field at "/violations/0/code" is "unreadable_body"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  # The preconditions are judged before the content (FR-010).

  Scenario: Applying a preset without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/team" with the body:
      """
      {}
      """
    Then the response status is 428

  Scenario: Applying a preset with a stale If-Match is 412, before the preset is judged
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/presets/no-such-preset" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the response status is 412

  Scenario: Applying a preset to an activation that does not exist is 404 activation_not_found
    When I PUT "/activations/no-such/presets/team" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "activation_not_found"
