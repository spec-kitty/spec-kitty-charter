# PR A0 of layer-merge-and-effective-reads (IC-20, FR-040, FR-041, FR-042):
# - The contract changed first (C-001, changes/2026-11-07-a0-one-kind-model.md); the service follows in the same PR.
# - Bound in WP08 (ActivationSteps), which recorded the red (an assertion failure with every step bound, decision
#   DM-01M6RN6GC7PJS8BGHMAYM4JSSW) and took the @WIP tag off. An undefined step fails the suite (UndefinedStepGuard).
# - Steps this file adds to the vocabulary of README.md:
#   - `Given the stored file of the activation "<id>" holds:` writes the activation's file as the store keeps it,
#     bypassing the API, so a schemaVersion 1 document can be seeded;
#   - `When I remember the stored file of the activation "<id>"`, then `Then the stored file of the activation "<id>"
#     is the remembered one` (the same bytes; the scenarios store in memory, so there is no modification time) or
#     `... differs from the remembered one`;
#   - `Then the stored file of the activation "<id>" has the field "<pointer>" equal to <n>`;
#   - `Then the field at "<pointer>" contains "<text>"`, a substring of a string field (a detail's named replacement).
# - The four effective reads still answer 501 (PR B builds them, WP22): the read-only scenario below reads the
#   activation and its closure only; PR B adds the four reads to it as they are built.

@contract @activations
Feature: One kind model on the activation document
  The activation document is packs, selected, deactivated and languages (IC-20; ruling A7). Mission types, steps and
  agent profiles are ordinary kinds of it: a mission type is selected under selected.mission_type and denied under
  deactivated.mission_type, except when a pack requires or locks it (Q2). Selecting a mission type with a cascade
  reaches the steps it holds, crossed like requires (Q1, rule W20), and a cascading deactivate of it unselects what
  only it kept (rule W23). activatedKinds and missionTypes are not part of the document: a write that sends either
  is 422 validation_failed, one violation per member naming its replacement (Q4). A preset may name any kind (Q3). The
  stored document is at schemaVersion 2: a version 1 document is answered as its upgrade and stored as version 2 on
  the next write, never by a read. A client that sends [] for a selected kind it read as absent registers the same
  document (FR-042).

  The fixture is one org pack, main. Its mission types: software-dev, whose step implement requires directive SD;
  research; required-type, which main's governing preset requires; locked-type, which main's policy locks. It also
  holds directive OTHER, which nothing reaches, and the skill land-pr, which the preset with-skill names.

  Background:
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
        mission_type:locked-type: locked
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: required-type is required.
      activated_mission_types:
        - id: required-type
          must_have: true
      """
    And the org pack "main" with the file "presets/with-skill.yaml":
      """
      schema_version: '1.0'
      id: with-skill
      description: Selects the skill land-pr.
      activated_skills:
        - id: land-pr
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
    And the org pack "main" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Find things out
      steps:
        - id: explore
          title: Explore
          role: researcher
      """
    And the org pack "main" with the file "missions/required-type/required-type.mission-type.yaml":
      """
      schema_version: "1.0"
      id: required-type
      title: Required type
      description: The mission type the governing preset requires
      steps:
        - id: start
          title: Start
          role: implementer
      """
    And the org pack "main" with the file "missions/locked-type/locked-type.mission-type.yaml":
      """
      schema_version: "1.0"
      id: locked-type
      title: Locked type
      description: The mission type the policy locks
      steps:
        - id: start
          title: Start
          role: implementer
      """
    And the org pack "main" with the file "directives/SD.directive.yaml":
      """
      schema_version: '1.0'
      id: SD
      title: SD
      intent: The directive the implement step requires
      enforcement: required
      """
    And the org pack "main" with the file "directives/OTHER.directive.yaml":
      """
      schema_version: '1.0'
      id: OTHER
      title: OTHER
      intent: A directive nothing reaches
      enforcement: required
      """
    And the org pack "main" with the file "skills/land-pr.skill.yaml":
      """
      schema_version: "1.0"
      id: land-pr
      title: Land a PR
      description: Lands a pull request.
      form: prompt
      body_path: land-pr.skill.md
      """
    And the org pack "main" with the file "skills/land-pr.skill.md":
      """
      Land the pull request.
      """
    And the org pack "main" with the file "steps.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: directive:SD
        relation: requires
      """

  # Q1: a mission type cascades to the steps it holds.

  Scenario: Selecting a mission type with a cascade selects what its steps reach
    Q1, rule W20: the walk crosses from software-dev to its step implement like requires, and from the step to SD.
    A step is not an activatable kind, so it is neither stored nor listed; what it reaches is.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/software-dev" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:SD
      mission_type:software-dev
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/mission_type" holds exactly the ids:
      """
      software-dev
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      SD
      """
    And the response body has no field at "/config/missionTypes"

  Scenario: A denied directive a step requires is reported as required by the mission type
    Q1, rule W20: the via from the step of software-dev to SD is the mission type's own, so SD, denied, stays denied and
    is reported in skippedDeactivated as required by software-dev, not as a plain suggestion.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"directive": ["SD"]}}
      """
    When I PUT "/activations/a-1/selections/mission_type/software-dev" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:software-dev
      """
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      directive:SD
      """
    And the field at "/skipped/skippedDeactivated/0/viaRequires" is true
    And the field at "/skipped/skippedDeactivated/0/requiredBy" holds exactly the ids:
      """
      mission_type:software-dev
      """

  Scenario: Selecting a mission type without a cascade selects the mission type alone
    The negative control of the cascade: no cascade means the walk reaches the target only (rule W21).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/software-dev" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:software-dev
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/mission_type" holds exactly the ids:
      """
      software-dev
      """
    And the response body has no field at "/config/selected/directive"

  Scenario: A cascading deactivate of a mission type unselects what only it kept
    Rule W23: SD was reached through the step of software-dev and is unselected with it; OTHER, which the walk does
    not reach, stays selected (the positive control in the same answer).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["OTHER", "SD"], "mission_type": ["software-dev"]}}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/software-dev" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:SD
      mission_type:software-dev
      """
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/mission_type" holds exactly the ids:
      """
      software-dev
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      OTHER
      """

  # Q2: a mission type is deniable unless it is required or locked.

  Scenario: A deny entry on a mission type is accepted
    Q2: research is neither required nor locked, so it is denied like any kind.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["research", "software-dev"]}}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/research" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:research
      """
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/mission_type" holds exactly the ids:
      """
      research
      """
    And the field at "/config/selected/mission_type" holds exactly the ids:
      """
      software-dev
      """

  Scenario: A deny entry on a required mission type is 409 artifact_required and stores nothing
    Q2: main's governing preset requires required-type.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/required-type" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag
    When I PUT "/activations/a-1/exclusions/mission_type/research" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200

  Scenario: A deny entry on a locked mission type is 409 artifact_locked and stores nothing
    Q2: main's policy locks locked-type.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/locked-type" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A document that denies a required mission type is 409 on create, and one that denies another is 201
    Q2 on the document write, with its positive control.
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"mission_type": ["required-type"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"mission_type": ["research"]}}
      """
    Then the response status is 201
    And the field at "/config/deactivated/mission_type" holds exactly the ids:
      """
      research
      """

  # Q4: the remnant members are refused now.

  Scenario: activatedKinds in a create body is 422 validation_failed naming selected.<kind>
    Q4: no deprecation window. The same body without the member is created (the positive control).
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "activatedKinds": ["directive"], "selected": {"directive": ["SD"]}}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations" holds exactly 1 item
    And the field at "/violations/0/pointer" is "/activatedKinds"
    And the field at "/violations/0/detail" contains "selected.<kind>"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["SD"]}}
      """
    Then the response status is 201

  Scenario: missionTypes in a create body is 422 validation_failed naming selected.mission_type
    Q4. The same mission type under selected.mission_type is created (the positive control).
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "missionTypes": ["software-dev"]}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations" holds exactly 1 item
    And the field at "/violations/0/pointer" is "/missionTypes"
    And the field at "/violations/0/detail" contains "selected.mission_type"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/mission_type" holds exactly the ids:
      """
      software-dev
      """

  Scenario: A replace that sends both remnant members is 422 with one violation per member and stores nothing
    Q4 on replaceActivation.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "activatedKinds": [], "missionTypes": []}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field at "/violations" holds exactly 2 items
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  # Q3: a preset may name any kind.

  Scenario: A preset naming a skill applies it
    Q3: the skill exclusion of presets is dropped; selected.skill becomes the preset's members.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["OTHER"]}}
      """
    When I PUT "/activations/a-1/presets/with-skill" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      skill:land-pr
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/skill" holds exactly the ids:
      """
      land-pr
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      OTHER
      """

  # FR-042, the server half: absent and [] register the same document.

  Scenario: A PUT that sends [] for the selected kinds a GET answered as absent keeps the ETag and the stored bytes
    Decision DM-01M6RN62PPCMSH6M54D2HXYZEQ: the generated client sends an absent list back as []. For a selected kind
    both mean none, so the canonical form leaves an empty selected.<kind> out and the write changes nothing.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["SD"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      SD
      """
    And the response body has no field at "/config/selected/tactic"
    And the response body has no field at "/config/selected/mission_type"
    When I remember the ETag
    And I remember the field "revision" of the response body
    And I remember the stored file of the activation "a-1"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["SD"], "tactic": [], "mission_type": []}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field "revision" of the response body is the remembered one
    And the stored file of the activation "a-1" is the remembered one
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["OTHER"]}}
      """
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

  # Storage: schemaVersion 2, upgraded on the next write, never by a read.

  Scenario: A stored version 1 document reads back as its version 2 upgrade
    Decision DM-01M6RMJHDKNTXAM055Q8WR6SDN: missionTypes become selected.mission_type, every selection is kept and
    activatedKinds is discarded.
    Given the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "activatedKinds": ["directive"], "missionTypes": ["software-dev"], "selected": {"directive": ["SD"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/schemaVersion" is 2
    And the field at "/config/selected/mission_type" holds exactly the ids:
      """
      software-dev
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      SD
      """
    And the response body has no field at "/config/missionTypes"
    And the response body has no field at "/config/activatedKinds"

  Scenario: Reads leave a stored version 1 document untouched, and the next conditional write stores version 2
    Decision DM-01M6RFGMDH859CJP3RHHF9YC3H: reads upgrade in memory only. The ETag a read answers is the upgrade's,
    so a conditional PUT with it is accepted and stores the document at version 2.
    Given the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "missionTypes": ["software-dev"], "selected": {"directive": ["SD"]}}
      """
    When I remember the stored file of the activation "a-1"
    And I GET "/activations/a-1"
    Then the response status is 200
    When I remember the field "revision" of the response body
    And I remember the ETag
    And I GET "/activations/a-1/packs"
    Then the response status is 200
    And the stored file of the activation "a-1" is the remembered one
    When I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["SD"], "mission_type": ["software-dev"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field "revision" of the response body is the remembered one
    And the stored file of the activation "a-1" differs from the remembered one
    And the stored file of the activation "a-1" has the field "/schemaVersion" equal to 2
