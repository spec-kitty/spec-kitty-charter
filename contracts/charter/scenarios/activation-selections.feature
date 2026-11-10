@contract @activations
Feature: Selecting, unselecting, deactivating and re-activating one artifact of an activation
  The per-artifact writes of an activation (ADR-0020 D6, FR-010 to FR-015): PUT and DELETE on
  …/selections/{kind}/{artifactId} select and unselect one artifact (or, with kind mission_type, one mission
  type), and PUT and DELETE on …/exclusions/{kind}/{artifactId} deactivate and re-activate one. Each requires
  If-Match and answers an ActivationChange with the new revision as its ETag: applied lists every id the write
  added to or removed from selected or deactivated (mission types included, under mission_type: one kind model,
  IC-20), and cleared the deny entries it removed. A
  write that changes nothing answers 200 with the same revision and stores nothing.

  A cascading select walks requires, suggests, refines, scope, instantiates and enhances from the target, each in
  its declared direction (an enhancer to what it enhances), follows <pack>:: targets, passes through a profile's
  specializes_from ancestors (requires and scope only), stops at a deny entry and reports it (ADR-0059 D2, as
  amended on 2026-11-06). There is no enhances pull at write time. A cascading deactivate walks the same way,
  unselects what only the target kept, keeps what another active artifact reaches (skippedShared), and denies the
  target alone (ADR-0013 D2-D5).

  The fixture is one org pack, main, over a second, shared. In the graph: D requires t, x, t2, t3 and shared::xt;
  e enhances t (the enhancer of User Story 3); D2 requires t2; e2 enhances t3 (the enhancer of User Story 4);
  profile p specializes from q, q requires R and suggests S; A requires B and B requires A. main's policy locks
  directive LK, and its governing preset requires directive RQ.

  Background:
    Given the org pack "shared" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: shared
      pack_level: org
      """
    And the org pack "shared" with the file "tactics/xt.tactic.yaml":
      """
      schema_version: '1.0'
      id: xt
      name: Shared tactic
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      mutability_policy: policy.yaml
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "main" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:LK: locked
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: RQ is required.
      activated_directives:
        - id: RQ
          must_have: true
      """
    And the org pack "main" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: "1.0"
      id: research
      title: Research
      description: Conduct systematic research
      steps:
        - id: scoping
          title: Research Scoping
          role: researcher
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
    And the org pack "main" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: Locked
      intent: LK
      enforcement: required
      """
    And the org pack "main" with the file "directives/RQ.directive.yaml":
      """
      schema_version: '1.0'
      id: RQ
      title: Required
      intent: RQ
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
    And the org pack "main" with the file "tactics/x.tactic.yaml":
      """
      schema_version: '1.0'
      id: x
      name: x
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: e
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
    And the org pack "main" with the file "tactics/t3.tactic.yaml":
      """
      schema_version: '1.0'
      id: t3
      name: t3
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
    And the org pack "main" with the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: p
      role: implementer
      purpose: Implements.
      specialization:
        primary-focus: Implementation.
      """
    And the org pack "main" with the file "agent_profiles/q.agent.yaml":
      """
      profile-id: q
      name: q
      role: implementer
      purpose: Implements in general.
      specialization:
        primary-focus: Implementation in general.
      """
    And the org pack "main" with the file "cascade.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D
        target: tactic:t
        relation: requires
      - source: directive:D
        target: tactic:x
        relation: requires
      - source: tactic:e
        target: tactic:t
        relation: enhances
      - source: directive:D2
        target: tactic:t2
        relation: requires
      - source: directive:D
        target: tactic:t2
        relation: requires
      - source: tactic:e2
        target: tactic:t3
        relation: enhances
      - source: directive:D
        target: tactic:t3
        relation: requires
      - source: directive:D
        target: shared::tactic:xt
        relation: requires
      - source: agent_profile:p
        target: agent_profile:q
        relation: specializes_from
      - source: agent_profile:q
        target: directive:R
        relation: requires
      - source: agent_profile:q
        target: directive:S
        relation: suggests
      - source: directive:A
        target: directive:B
        relation: requires
      - source: directive:B
        target: directive:A
        relation: requires
      """

  # User Story 3: select and unselect one artifact, with cascade.

  Scenario: A cascading select selects what the target requires, skips a denied one and never selects an enhancer
    US3 AS1: x is denied, so the walk stops there and reports it; e enhances t, which the walk selects, but
    enhances runs from e to t (ADR-0059 D2, as amended on 2026-11-06), so e is neither selected nor applied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      tactic:t
      tactic:t2
      tactic:t3
      tactic:xt
      """
    And the field at "/cleared" holds exactly 0 items
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      tactic:x
      """
    And the field at "/skipped/skippedDeactivated/0/viaRequires" is true
    And the field at "/skipped/skippedDeactivated/0/requiredBy" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/skipped/skippedByScope" holds exactly 0 items
    And the field at "/skipped/unreachedBehindDeactivated" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D
      """
    And the field at "/config/selected/tactic" holds exactly the ids:
      """
      t
      t2
      t3
      xt
      """
    And the field at "/config/deactivated/tactic" holds exactly the ids:
      """
      x
      """

  Scenario: A cascade scoped to directives selects the target alone and reports the tactics it reached
    US3 AS2.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["directive"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/skipped/skippedByScope" holds exactly the ids:
      """
      tactic:t
      tactic:t2
      tactic:t3
      tactic:xt
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D
      """
    And the response body has no field at "/config/selected/tactic"

  Scenario: includeDeactivated clears the deny entries the walk passes through, and no enhancer's
    US3 AS3, ADR-0059 D2 (operator ruling of 2026-11-06): x and e are denied. The walk selects D, t, x, t2, t3 and
    xt, and clears x. e enhances t, but enhances runs from e to t, so the walk never reaches e and there is no
    enhances pull at write time: e stays denied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["e", "x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly the ids:
      """
      tactic:x
      """
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      tactic:t
      tactic:t2
      tactic:t3
      tactic:x
      tactic:xt
      """
    And the field at "/skipped/skippedDeactivated" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/selected/tactic" holds exactly the ids:
      """
      t
      t2
      t3
      x
      xt
      """
    And the field at "/config/deactivated/tactic" holds exactly the ids:
      """
      e
      """

  Scenario: includeDeactivated keeps the deny entry of an artifact outside the scope, where the walk stops
    FR-013, ADR-0059 (operator ruling of 2026-11-06): includeDeactivated takes back only the deny entries the walk
    passes through within the cascade's scope. x is a tactic, outside a cascade into directives, so the walk stops
    at x as it would without includeDeactivated: x keeps its deny entry and is reported in skippedDeactivated.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["directive"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly 0 items
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/skipped/skippedByScope" holds exactly the ids:
      """
      tactic:t
      tactic:t2
      tactic:t3
      tactic:xt
      """
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      tactic:x
      """
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/selected/tactic"
    And the field at "/config/deactivated/tactic" holds exactly the ids:
      """
      x
      """

  Scenario: includeDeactivated keeps a denied ancestor outside the scope, which stops the lineage
    FR-013, FR-014, ADR-0059 (operator ruling of 2026-11-06): q is a profile, outside a cascade into directives, so
    the lineage stops at q as it would without includeDeactivated; q keeps its deny entry and R lies behind it.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"agent_profile": ["q"]}}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["directive"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly 0 items
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      """
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      agent_profile:q
      """
    And the field at "/skipped/unreachedBehindDeactivated" holds exactly the ids:
      """
      directive:R
      """

  Scenario: includeDeactivated passes through a denied ancestor inside the scope and keeps its deny entry
    FR-013, FR-014, B5 (decision DM-01M6RA9XQSH3E07C7VCPCVD6R4): includeDeactivated clears the deny entries of the
    artifacts the walk reaches and selects only. With every kind in scope the lineage passes through the denied q,
    which it does not select, so q keeps its deny entry; R is selected through it (the positive control in the same
    answer). Before B5 the walk cleared q's deny entry as it passed.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"agent_profile": ["q"]}}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly 0 items
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      directive:R
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/agent_profile" holds exactly the ids:
      """
      p
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      R
      """
    And the field at "/config/deactivated/agent_profile" holds exactly the ids:
      """
      q
      """

  Scenario: A stop reached through a template is required by what instantiates it
    ArtifactReference.yaml: requiredBy names artifacts an ArtifactReference can name, so a template on the way to
    a stop leads back to the directive that instantiates it, and the answer is 200.
    Given the org pack "main" with the file "templates/tpl.md":
      """
      # A template
      """
    And the org pack "main" with the file "template.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:D2
        target: template:tpl.md
        relation: instantiates
      - source: template:tpl.md
        target: tactic:x
        relation: requires
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      tactic:x
      """
    And the field at "/skipped/skippedDeactivated/0/requiredBy" holds exactly the ids:
      """
      directive:D2
      """

  Scenario: A cascade passes through a profile's ancestor without putting it in force
    US3 AS4: p specializes from q; q requires R and suggests S. The lineage passes requires through, so R is
    selected; q is an ancestor, never in force, and S is reached only over suggests from it.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      directive:R
      """
    And the field at "/skipped/skippedByScope" holds exactly 0 items
    And the field at "/skipped/skippedDeactivated" holds exactly 0 items

  Scenario: A denied ancestor stops the lineage and is reported, with what lies behind it
    US3 AS4, second half: q is denied, so R is not reached.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"agent_profile": ["q"]}}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      """
    And the field at "/skipped/skippedDeactivated" holds exactly the ids:
      """
      agent_profile:q
      """
    And the field at "/skipped/unreachedBehindDeactivated" holds exactly the ids:
      """
      directive:R
      """

  Scenario: Unselecting a selected artifact removes it, lists it in applied and clears nothing
    US3 AS5.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2"]}}
      """
    When I DELETE "/activations/a-1/selections/directive/D" with the stored ETag
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/cleared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """

  Scenario: Unselecting an artifact that is not selected changes nothing
    US3 AS5, the no-op: 200 with the same revision, empty applied and cleared.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D2"]}}
      """
    When I DELETE "/activations/a-1/selections/directive/D" with the stored ETag
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items

  Scenario: Selecting into an absent list makes the list of that one artifact
    US3 AS6: absent means none, so the select adds a list holding exactly the artifact.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D2
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """

  Scenario: Selecting a mission type adds it to selected.mission_type
    US3 AS7, one kind model (IC-20): a mission type is selected like any kind.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/research" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:research
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/mission_type" holds exactly the ids:
      """
      research
      """
    And the response body has no field at "/config/missionTypes"

  Scenario: Selecting a mission type the closure does not hold is 404 mission_type_not_found
    US3 AS7, FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/no-such-type" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: A mission type path id names it in any known spelling and stores the declared id, as any kind's does
    Rewritten for the one kind model (IC-20): a mission type is a kind of selected like any other, so its path id is
    normalized as every kind's is (ADR-0020 D4): another case or its full URN names the declared mission type. It
    was matched exactly while missionTypes was a member of its own. A spelling of no mission type is still 404.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/RESEARCH" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:research
      """
    When I remember the ETag
    And I PUT "/activations/a-1/selections/mission_type/mission_type:research" with the remembered ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly 0 items
    When I PUT "/activations/a-1/selections/mission_type/mission_type:no-such-type" with the remembered ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/mission_type" holds exactly the ids:
      """
      research
      """

  Scenario: Selecting an artifact the closure does not hold is 404 artifact_not_found
    US3 AS8, FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/NO_SUCH" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "artifact_not_found"

  Scenario: A select names its artifact in any known spelling and stores the declared id
    ADR-0020 D4 (operator ruling of 2026-11-06): the path id is read as a document's id is, so the full URN of the
    path's kind and another case select the declared D2.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/directive:d2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D2
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """

  Scenario: An unselect in another case removes the declared id
    ADR-0020 D4 (operator ruling of 2026-11-06).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2"]}}
      """
    When I DELETE "/activations/a-1/selections/directive/d" with the stored ETag
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """

  Scenario: A path id spelled as a URN of another kind is 404 artifact_not_found
    ADR-0020 D4: a URN of another kind than the path's names no artifact of that kind.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/tactic:D" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A cascade that mixes all with kinds is refused
    US3 AS8.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all", "tactic"]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/cascade"
    And the field at "/violations/0/code" is "all_mixed_with_kinds"

  Scenario: A cascade follows a reference qualified by another pack
    FR-012 (R7): D requires shared::xt, which the shared pack holds, so the cascade selects xt.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied/4/kind" is "tactic"
    And the field at "/applied/4/artifactId" is "xt"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/tactic/3" is "xt"

  Scenario: A cascade over a cycle answers once and selects each artifact once
    A requires B and B requires A.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/A" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:A
      directive:B
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      A
      B
      """

  Scenario: Selecting what is already selected changes nothing
    The no-op: 200 with the same revision, empty applied and cleared, and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D2"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items

  Scenario: A cascading select of an enhancer selects the artifact it enhances
    ADR-0059 D2 (operator ruling of 2026-11-06): enhances is a cascade relation like the others, walked in its
    declared direction, from e to t.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/tactic/e" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:e
      tactic:t
      """

  Scenario: A cascading select of an enhanced artifact selects no enhancer
    ADR-0059 D2 (operator ruling of 2026-11-06): e enhances t runs from e to t, so a walk from t never reaches e.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/tactic/t" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:t
      """

  Scenario: A cascading deactivate of an enhancer unselects the artifact it enhances
    ADR-0059 D2 (operator ruling of 2026-11-06): the deactivate walks enhances from e to t, and nothing else keeps t.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e", "t"]}}
      """
    When I PUT "/activations/a-1/exclusions/tactic/e" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:e
      tactic:t
      """
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/selected/tactic"

  Scenario: A cascading deactivate of an enhancer keeps the artifact it enhances when another needs it
    ADR-0059 D2 (operator ruling of 2026-11-06): D is selected and requires t, so t is shared and stays, naming D.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["e", "t"]}}
      """
    When I PUT "/activations/a-1/exclusions/tactic/e" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:e
      """
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      tactic:t
      """
    And the field at "/skipped/skippedShared/0/referencedBy/kind" is "directive"
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "D"

  # The preconditions are judged before the content (FR-010).

  Scenario: A select without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the body:
      """
      {}
      """
    Then the response status is 428

  Scenario: An unselect without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"]}}
      """
    When I send DELETE "/activations/a-1/selections/directive/D"
    Then the response status is 428

  Scenario: A select with a stale If-Match is 412, before the path id is judged
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/NO_SUCH" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the response status is 412

  Scenario: A select on an activation that does not exist is 404 activation_not_found
    When I PUT "/activations/no-such/selections/directive/D" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A malformed body without If-Match is 428, before the body is judged
    FR-010: the preconditions come before any 422. A member the schema does not declare and a cascade token
    outside its kinds are faults of the body, judged only once the preconditions hold.
    When I PUT "/activations/no-such/selections/directive/D" with the body:
      """
      {"cascade": ["bogus"], "favourite": 1}
      """
    Then the response status is 428

  Scenario: A malformed body on an activation that does not exist is 404 activation_not_found
    When I PUT "/activations/no-such/exclusions/directive/D" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"cascade": null}
      """
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A malformed body is 422 once the preconditions hold, a cascade token at its index
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["directive", "bogus"]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/cascade/1"
    And the field at "/violations/0/code" is "unknown_kind"

  Scenario: A malformed If-Match on a select is 400
    J10: an If-Match that is neither * nor one quoted revision breaks its pattern.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D" with If-Match "not-a-revision" and the body:
      """
      {}
      """
    Then the response status is 400

  Scenario: A path kind outside the selectable kinds is 422 with a violation at the path
    FR-010: template is never activated.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/template/D" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/kind"

  # User Story 4: deactivate and re-activate one artifact.

  Scenario: A cascading deactivate unselects what only the target kept and keeps what another selected one reaches
    US4 AS1: t2 is also reached from D2, which stays selected; only D gets a deny entry.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2"], "tactic": ["t", "t2"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      tactic:t
      """
    And the field at "/cleared" holds exactly 0 items
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      tactic:t2
      """
    And the field at "/skipped/skippedShared/0/referencedBy/kind" is "directive"
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "D2"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """
    And the field at "/config/selected/tactic" holds exactly the ids:
      """
      t2
      """
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      D
      """
    And the response body has no field at "/config/deactivated/tactic"

  Scenario: A cascading deactivate keeps what a selected enhancer references
    US4 AS2: e2 is selected and enhances t3, which only D reaches; the shared walk from e2 crosses enhances to t3,
    so t3 stays, naming e2.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["e2", "t3"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      tactic:t3
      """
    And the field at "/skipped/skippedShared/0/referencedBy/kind" is "tactic"
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "e2"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/tactic" holds exactly the ids:
      """
      e2
      t3
      """

  Scenario: A candidate kept by an enhancer keeps the candidate it enhances, in the same shared walk
    US4 AS2, FR-015: K requires c, c3 and c5, and c3 requires c4; the selected e2 enhances c, and c enhances c3.
    The shared walk from e2 crosses enhances to c, goes on through c to c3 and c3 keeps c4. c5 leaves selected.
    Given the org pack "main" with the file "directives/K.directive.yaml":
      """
      schema_version: '1.0'
      id: K
      title: K
      intent: K
      enforcement: required
      """
    And the org pack "main" with the file "tactics/c.tactic.yaml":
      """
      schema_version: '1.0'
      id: c
      name: c
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/c3.tactic.yaml":
      """
      schema_version: '1.0'
      id: c3
      name: c3
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/c4.tactic.yaml":
      """
      schema_version: '1.0'
      id: c4
      name: c4
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/c5.tactic.yaml":
      """
      schema_version: '1.0'
      id: c5
      name: c5
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "chain.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:K
        target: tactic:c
        relation: requires
      - source: directive:K
        target: tactic:c3
        relation: requires
      - source: directive:K
        target: tactic:c5
        relation: requires
      - source: tactic:c3
        target: tactic:c4
        relation: requires
      - source: tactic:e2
        target: tactic:c
        relation: enhances
      - source: tactic:c
        target: tactic:c3
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["K"], "tactic": ["c", "c3", "c4", "c5", "e2"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/K" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:K
      tactic:c5
      """
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      tactic:c
      tactic:c3
      tactic:c4
      """
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "e2"
    And the field at "/skipped/skippedShared/1/referencedBy/artifactId" is "c"
    And the field at "/skipped/skippedShared/2/referencedBy/artifactId" is "c3"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/tactic" holds exactly the ids:
      """
      c
      c3
      c4
      e2
      """

  Scenario: A cascading deactivate follows the lineage, so it undoes a cascading select
    FR-015, ADR-0059 D2 (operator ruling of 2026-11-06): selecting p with a cascade selects R through its ancestor
    q; deactivating p with a cascade walks the same lineage, so R leaves selected again and only p is denied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      directive:R
      """
    When I remember the ETag
    And I PUT "/activations/a-1/exclusions/agent_profile/p" with the remembered ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      directive:R
      """
    And the field at "/skipped/skippedShared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/selected/agent_profile"
    And the response body has no field at "/config/selected/directive"
    And the field at "/config/deactivated/agent_profile" holds exactly the ids:
      """
      p
      """

  Scenario: A cascading deactivate keeps what the lineage reached when another active artifact needs it
    FR-015, ADR-0059 D2 (operator ruling of 2026-11-06): q is selected on its own and requires R, so deactivating p
    with a cascade keeps R and names q in skippedShared.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"agent_profile": ["q"]}}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    When I remember the ETag
    And I PUT "/activations/a-1/exclusions/agent_profile/p" with the remembered ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      """
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      directive:R
      """
    And the field at "/skipped/skippedShared/0/referencedBy/kind" is "agent_profile"
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "q"
    When I GET "/activations/a-1"
    Then the field at "/config/selected/agent_profile" holds exactly the ids:
      """
      q
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      R
      """

  Scenario: Deactivating without a cascade denies and unselects the target alone
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["t"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/D" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/tactic" holds exactly the ids:
      """
      t
      """
    And the response body has no field at "/config/selected/directive"

  Scenario: Deactivating a locked artifact is 409 artifact_locked and stores nothing
    US4 AS3, FR-007.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/LK" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: Deactivating a required artifact is 409 artifact_required and stores nothing
    US4 AS3, FR-007: main's governing preset requires RQ.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/RQ" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A stored deny entry on the floor is tolerated by a write of another id
    ADR-0059 D4, data model ("a stored one is tolerated but not applied"): D2 was denied before main's governing
    preset came to require it. Only a deny entry the write adds is judged against the floor, so selecting t
    answers 200 and the stored entry stays as it is.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"directive": ["D2"]}}
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: RQ and D2 are required.
      activated_directives:
        - id: RQ
          must_have: true
        - id: D2
          must_have: true
      """
    When I PUT "/activations/a-1/selections/tactic/t" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      tactic:t
      """
    And the field at "/cleared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/directive" holds exactly the ids:
      """
      D2
      """

  # Stale ids are dropped on write (ADR-0059 amendment A5, operator ruling of 2026-11-06).

  Scenario: A write drops the ids the closure no longer declares and reports them in dropped
    ADR-0059 amendment A5: a pack upgrade removed directive OLD, tactic old and mission type research, which the
    stored document still names. Selecting D2 first drops them from selected and deactivated, then
    selects D2; dropped names them, and applied and cleared report only the select.
    Given the org pack "main" with the file "directives/OLD.directive.yaml":
      """
      schema_version: '1.0'
      id: OLD
      title: Old
      intent: OLD
      enforcement: required
      """
    And the org pack "main" with the file "tactics/old.tactic.yaml":
      """
      schema_version: '1.0'
      id: old
      name: old
      steps:
        - title: Only step
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["OLD"], "mission_type": ["research"]}, "deactivated": {"tactic": ["old"]}}
      """
    And the file "directives/OLD.directive.yaml" of the pack "main" is deleted
    And the file "tactics/old.tactic.yaml" of the pack "main" is deleted
    And the file "missions/research/research.mission-type.yaml" of the pack "main" is deleted
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:D2
      """
    And the field at "/cleared" holds exactly 0 items
    And the field at "/dropped" holds exactly the ids:
      """
      directive:OLD
      mission_type:research
      tactic:old
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """
    And the response body has no field at "/config/selected/mission_type"
    And the response body has no field at "/config/deactivated"

  Scenario: A write that only drops stale ids is not a no-op and stores a new revision
    ADR-0059 amendment A5: D2 is already selected, so the select changes nothing but the drop of OLD; the dropped id
    counts as a change.
    Given the org pack "main" with the file "directives/OLD.directive.yaml":
      """
      schema_version: '1.0'
      id: OLD
      title: Old
      intent: OLD
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D2", "OLD"]}}
      """
    And the file "directives/OLD.directive.yaml" of the pack "main" is deleted
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the response header "ETag" is the quoted field "/revision"
    And the field at "/applied" holds exactly 0 items
    And the field at "/dropped" holds exactly the ids:
      """
      directive:OLD
      """

  Scenario: An unselect that names a stale id drops it and stores a new revision
    FR-010, ADR-0059 amendment A5: the closure no longer holds OLD, but the stored document still selects it, so the
    unselect proceeds; OLD is dropped with every other stale id and reported in dropped only.
    Given the org pack "main" with the file "directives/OLD.directive.yaml":
      """
      schema_version: '1.0'
      id: OLD
      title: Old
      intent: OLD
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D2", "OLD"]}}
      """
    And the file "directives/OLD.directive.yaml" of the pack "main" is deleted
    When I DELETE "/activations/a-1/selections/directive/OLD" with the stored ETag
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the response header "ETag" is the quoted field "/revision"
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items
    And the field at "/dropped" holds exactly the ids:
      """
      directive:OLD
      """
    When I GET "/activations/a-1"
    Then the field at "/config/selected/directive" holds exactly the ids:
      """
      D2
      """

  Scenario: A re-activate that names a stale id drops its deny entry and stores a new revision
    FR-010, ADR-0059 amendment A5: the closure no longer holds the tactic old, but the stored document still denies it,
    so the re-activate proceeds; old is dropped, reported in dropped only, and not in cleared.
    Given the org pack "main" with the file "tactics/old.tactic.yaml":
      """
      schema_version: '1.0'
      id: old
      name: old
      steps:
        - title: Only step
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["old"]}}
      """
    And the file "tactics/old.tactic.yaml" of the pack "main" is deleted
    When I DELETE "/activations/a-1/exclusions/tactic/old" with the stored ETag
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items
    And the field at "/dropped" holds exactly the ids:
      """
      tactic:old
      """
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/deactivated"

  Scenario: An unselect or a re-activate of an id neither the closure nor the document names is 404 and drops nothing
    FR-010: GONE is in no pack and in no list of the stored document, so both deletes are artifact_not_found, and the
    stale OLD is not dropped by them.
    Given the org pack "main" with the file "directives/OLD.directive.yaml":
      """
      schema_version: '1.0'
      id: OLD
      title: Old
      intent: OLD
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["OLD"]}}
      """
    And the file "directives/OLD.directive.yaml" of the pack "main" is deleted
    When I DELETE "/activations/a-1/selections/directive/GONE" with the stored ETag
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I DELETE "/activations/a-1/exclusions/directive/GONE" with the stored ETag
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: Re-activating a denied artifact clears its deny entry and omits the emptied list
    US4 AS4.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the stored ETag
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the field at "/cleared" holds exactly the ids:
      """
      tactic:x
      """
    And the field at "/applied" holds exactly the ids:
      """
      tactic:x
      """
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/deactivated"

  Scenario: Re-activating an artifact that is not denied changes nothing
    US4 AS4, the no-op.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the stored ETag
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items

  Scenario: Deactivating what is already denied and not selected changes nothing
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I PUT "/activations/a-1/exclusions/tactic/x" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items

  Scenario: A deactivate without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/D" with the body:
      """
      {}
      """
    Then the response status is 428

  Scenario: A re-activate with a stale If-Match is 412
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the response status is 412

  Scenario: Deactivating a mission type denies it, and one the closure does not hold is 404 mission_type_not_found
    Rewritten for the one kind model (IC-20, Q2): the exclusions path takes kind mission_type, and a mission type
    neither required nor locked is denied like any kind. It was 422 at the path while missionTypes held mission
    types apart.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/no-such-type" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I PUT "/activations/a-1/exclusions/mission_type/research" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      mission_type:research
      """

  Scenario: Deactivating an artifact the closure does not hold is 404 artifact_not_found
    FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/tactic/NO_SUCH" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "artifact_not_found"
