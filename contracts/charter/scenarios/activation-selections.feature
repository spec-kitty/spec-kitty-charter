@contract @activations
Feature: Selecting, unselecting, deactivating and re-activating one artifact of an activation
  The per-artifact writes of an activation (ADR-0020 D6, FR-010 to FR-015): PUT and DELETE on
  …/selections/{kind}/{artifactId} select and unselect one artifact (or, with kind mission_type, one mission
  type), and PUT and DELETE on …/exclusions/{kind}/{artifactId} deactivate and re-activate one. Each requires
  If-Match and answers an ActivationChange with the new revision as its ETag: applied lists every id the write
  added to or removed from selected, deactivated or missionTypes, and cleared the deny entries it removed. A
  write that changes nothing answers 200 with the same revision and stores nothing.

  A cascading select walks requires, suggests, refines, scope and instantiates forward from the target, follows
  <pack>:: targets, passes through a profile's specializes_from ancestors (requires and scope only), stops at a
  deny entry and reports it (ADR-0059 D2). The enhances pull runs at read time: a select never adds an enhancer
  to selected (D2.2), and with includeDeactivated it clears the deny entries of what the pull reaches from what
  the walk selects. A cascading deactivate unselects what only the target kept, keeps what another active
  artifact reaches or an active enhancer references (skippedShared), and denies the target alone (ADR-0013 D2-D5).

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
      roles:
        - implementer
      purpose: Implements.
      specialization:
        primary-focus: Implementation.
      """
    And the org pack "main" with the file "agent_profiles/q.agent.yaml":
      """
      profile-id: q
      name: q
      roles:
        - implementer
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
    US3 AS1: x is denied, so the walk stops there and reports it; e enhances t, which the walk selects, but the
    enhances pull runs at read time (ADR-0059 D2.2), so e is neither selected nor applied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
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

  Scenario: includeDeactivated clears the walk's deny entries and the enhancer's, and selects no enhancer
    US3 AS3 (R2): x and e are denied. The walk selects D, t, x, t2, t3 and xt; the enhances pull from them reaches
    e (it enhances t), so e's deny entry is cleared too: e is in cleared and in applied (removed from
    deactivated), and absent from selected.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["e", "x"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the stored ETag and the body:
      """
      {"cascade": ["all"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly the ids:
      """
      tactic:e
      tactic:x
      """
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      tactic:e
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
    And the response body has no field at "/config/deactivated"

  Scenario: A cascade passes through a profile's ancestor without putting it in force
    US3 AS4: p specializes from q; q requires R and suggests S. The lineage passes requires through, so R is
    selected; q is an ancestor, never in force, and S is reached only over suggests from it.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"agent_profile": ["q"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D2"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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

  Scenario: Selecting a mission type adds it to missionTypes
    US3 AS7.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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
    Then the field at "/config/missionTypes" holds exactly the ids:
      """
      research
      """

  Scenario: Selecting a mission type the closure does not hold is 404 mission_type_not_found
    US3 AS7, FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/mission_type/no-such-type" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: Selecting an artifact the closure does not hold is 404 artifact_not_found
    US3 AS8, FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/NO_SUCH" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  Scenario: A cascade that mixes all with kinds is refused
    US3 AS8.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D2"]}}
      """
    When I PUT "/activations/a-1/selections/directive/D2" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items

  # The preconditions are judged before the content (FR-010).

  Scenario: A select without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/D" with the body:
      """
      {}
      """
    Then the response status is 428

  Scenario: An unselect without If-Match is 428
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D"]}}
      """
    When I send DELETE "/activations/a-1/selections/directive/D"
    Then the response status is 428

  Scenario: A select with a stale If-Match is 412, before the path id is judged
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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

  Scenario: A path kind outside the selectable kinds is 422 with a violation at the path
    FR-010: template is never activated.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D", "D2"], "tactic": ["t", "t2"]}}
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
    US4 AS2 (R6): e2 is selected and enhances t3, which only D reaches, so t3 stays, naming e2.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["e2", "t3"]}}
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

  Scenario: A candidate kept by an enhancer keeps the candidate it enhances, on a second shared step
    US4 AS2, FR-015: K requires c, c3 and c5, and c3 requires c4; the selected e2 enhances c, and c enhances c3.
    e2 keeps c; only once c is kept does it keep c3, and c3 keeps c4. c5 leaves selected.
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["K"], "tactic": ["c", "c3", "c4", "c5", "e2"]}}
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

  Scenario: Deactivating without a cascade denies and unselects the target alone
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "selected": {"directive": ["D"], "tactic": ["t"]}}
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
    And the field at "/config/selected/directive" holds exactly 0 items

  Scenario: Deactivating a locked artifact is 409 artifact_locked and stores nothing
    US4 AS3, FR-007.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/LK" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  Scenario: Deactivating a required artifact is 409 artifact_required and stores nothing
    US4 AS3, FR-007: main's governing preset requires RQ.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/RQ" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the response header "ETag" is the stored ETag

  Scenario: Re-activating a denied artifact clears its deny entry and omits the emptied list
    US4 AS4.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the stored ETag
    Then the response status is 200
    And the response header "ETag" is the stored ETag
    And the field at "/applied" holds exactly 0 items
    And the field at "/cleared" holds exactly 0 items

  Scenario: Deactivating what is already denied and not selected changes nothing
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
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
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/directive/D" with the body:
      """
      {}
      """
    Then the response status is 428

  Scenario: A re-activate with a stale If-Match is 412
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "deactivated": {"tactic": ["x"]}}
      """
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the response status is 412

  Scenario: Deactivating a mission type is 422 with a violation at the path
    FR-010: missionTypes holds mission types, so the exclusions path takes no mission_type kind.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/mission_type/research" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/kind"

  Scenario: Deactivating an artifact the closure does not hold is 404 artifact_not_found
    FR-010.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/tactic/NO_SUCH" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 404
    And the problem code is "artifact_not_found"
