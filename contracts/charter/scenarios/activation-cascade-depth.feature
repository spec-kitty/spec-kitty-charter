@contract @activations
Feature: Cascade depth: how many suggests hops a cascading select or deactivate may cross
  A cascading select and a cascading deactivate take an optional depth, the number of suggests hops the walk may
  cross (IC-21, FR-043; issue #408 ruling 1, decision DM-01M6TDH0SY5Q224GX709DXWG8K). Only suggests is counted, as
  ADR-0018 D4's depth counts it: requires and the other hard relations, enhances, the specializes_from pass-through
  and a mission type's steps are walked in full. depth 0 follows no suggests edge, depth 1 follows one, and so on.
  Absent means unlimited, exactly as before (ADR-0059 D2). With depth, the skipped report adds skippedByDepth: the
  suggests targets one hop past the bound, so a client knows whether a deeper depth would reach more.

  A cascading deactivate honours depth in the target's own walk, so a deactivate at depth N undoes a select at depth N
  in what it removes. The walk of the other active artifacts stays unlimited (decision DM-01M6TF3BA808JMW0W0A0CPXBNW),
  so the round trip is not exact when another active artifact reaches a candidate over suggests: that candidate is
  kept and reported in skippedShared.

  The fixture is one org pack, main. In the graph: the profile lead requires CORE and suggests HA; HA requires HAB and
  suggests HB; HB requires HBB (a chain of two suggests hops, each with its own requires). O suggests H and T requires
  H (the round-trip gap). DM requires DMB and suggests DMA, and DMB requires DMA (a diamond: DMA is reached over
  requires, so it is selected at any depth).

  Background:
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/CORE.directive.yaml":
      """
      schema_version: '1.0'
      id: CORE
      title: CORE
      intent: CORE
      enforcement: required
      """
    And the org pack "main" with the file "directives/HA.directive.yaml":
      """
      schema_version: '1.0'
      id: HA
      title: HA
      intent: HA
      enforcement: required
      """
    And the org pack "main" with the file "directives/HAB.directive.yaml":
      """
      schema_version: '1.0'
      id: HAB
      title: HAB
      intent: HAB
      enforcement: required
      """
    And the org pack "main" with the file "directives/HB.directive.yaml":
      """
      schema_version: '1.0'
      id: HB
      title: HB
      intent: HB
      enforcement: required
      """
    And the org pack "main" with the file "directives/HBB.directive.yaml":
      """
      schema_version: '1.0'
      id: HBB
      title: HBB
      intent: HBB
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
    And the org pack "main" with the file "directives/T.directive.yaml":
      """
      schema_version: '1.0'
      id: T
      title: T
      intent: T
      enforcement: required
      """
    And the org pack "main" with the file "directives/H.directive.yaml":
      """
      schema_version: '1.0'
      id: H
      title: H
      intent: H
      enforcement: required
      """
    And the org pack "main" with the file "directives/DM.directive.yaml":
      """
      schema_version: '1.0'
      id: DM
      title: DM
      intent: DM
      enforcement: required
      """
    And the org pack "main" with the file "directives/DMB.directive.yaml":
      """
      schema_version: '1.0'
      id: DMB
      title: DMB
      intent: DMB
      enforcement: required
      """
    And the org pack "main" with the file "directives/DMA.directive.yaml":
      """
      schema_version: '1.0'
      id: DMA
      title: DMA
      intent: DMA
      enforcement: required
      """
    And the org pack "main" with the file "agent_profiles/lead.agent.yaml":
      """
      profile-id: lead
      name: lead
      role: implementer
      purpose: Leads.
      specialization:
        primary-focus: Leadership.
      """
    And the org pack "main" with the file "depth.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:lead
        target: directive:CORE
        relation: requires
      - source: agent_profile:lead
        target: directive:HA
        relation: suggests
      - source: directive:HA
        target: directive:HAB
        relation: requires
      - source: directive:HA
        target: directive:HB
        relation: suggests
      - source: directive:HB
        target: directive:HBB
        relation: requires
      - source: directive:O
        target: directive:H
        relation: suggests
      - source: directive:T
        target: directive:H
        relation: requires
      - source: directive:DM
        target: directive:DMB
        relation: requires
      - source: directive:DM
        target: directive:DMA
        relation: suggests
      - source: directive:DMB
        target: directive:DMA
        relation: requires
      """

  Scenario: A cascading select without depth walks the whole suggests chain
    The control: an absent depth is unlimited, and the skipped report has no skippedByDepth.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      directive:HA
      directive:HAB
      directive:HB
      directive:HBB
      """
    And the response body has no field at "/skipped/skippedByDepth"

  Scenario: A cascading select at depth 0 selects the target and its hard closure only
    lead requires CORE and suggests HA: depth 0 follows no suggests edge, and skippedByDepth names HA.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      """
    And the field at "/skipped/skippedByDepth" holds exactly the ids:
      """
      directive:HA
      """
    And the field at "/skipped/skippedByScope" holds exactly 0 items
    And the field at "/skipped/skippedDeactivated" holds exactly 0 items
    When I GET "/activations/a-1"
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      CORE
      """

  Scenario: A cascading select at depth 1 adds the first suggests target and its hard closure
    HA is one hop away and brings HAB over requires; HB is the next frontier.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 1}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      directive:HA
      directive:HAB
      """
    And the field at "/skipped/skippedByDepth" holds exactly the ids:
      """
      directive:HB
      """

  Scenario: A cascading select at a depth the chain does not reach has an empty skippedByDepth
    depth 2 selects the whole chain, and the bound cut nothing: skippedByDepth is present and empty.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 2}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      directive:HA
      directive:HAB
      directive:HB
      directive:HBB
      """
    And the field at "/skipped/skippedByDepth" holds exactly 0 items

  Scenario: An artifact reached over requires is selected at depth 0 although a suggests path also reaches it
    DM suggests DMA and requires DMB, and DMB requires DMA: DMA comes with the hard closure, so nothing is cut.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/dm" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:DM
      directive:DMA
      directive:DMB
      """
    And the field at "/skipped/skippedByDepth" holds exactly 0 items

  Scenario: A depth without a cascade is accepted and has no effect
    There is no walk, so the target alone is selected and no skippedByDepth is reported.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      """
    And the response body has no field at "/skipped/skippedByDepth"

  Scenario: A cascading deactivate at depth 1 undoes a cascading select at depth 1
    The select at depth 1 brought lead, CORE, HA and HAB; the deactivate at depth 1 removes exactly those and leaves nothing selected.
    HB lay past the bound but was never selected, so a deeper deactivate would remove nothing there and skippedByDepth is empty.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 1}
      """
    Then the response status is 200
    When I remember the ETag
    When I PUT "/activations/a-1/exclusions/agent_profile/lead" with the remembered ETag and the body:
      """
      {"cascade": ["all"], "depth": 1}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      directive:HA
      directive:HAB
      """
    And the field at "/skipped/skippedByDepth" holds exactly 0 items
    And the field at "/skipped/skippedShared" holds exactly 0 items
    When I GET "/activations/a-1"
    Then the response body has no field at "/config/selected/directive"
    And the response body has no field at "/config/selected/agent_profile"
    And the field at "/config/deactivated/agent_profile" holds exactly the ids:
      """
      lead
      """

  Scenario: A cascading deactivate at depth 0 leaves what lies past the bound selected
    Everything is selected; the deactivate at depth 0 removes lead and CORE, and HA, HAB, HB and HBB stay.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"agent_profile": ["lead"], "directive": ["CORE", "HA", "HAB", "HB", "HBB"]}}
      """
    When I PUT "/activations/a-1/exclusions/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      """
    And the field at "/skipped/skippedByDepth" holds exactly the ids:
      """
      directive:HA
      """
    When I GET "/activations/a-1"
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      HA
      HAB
      HB
      HBB
      """

  Scenario: A cascading deactivate without depth removes the whole chain
    The control: an absent depth is unlimited, and the skipped report has no skippedByDepth.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"agent_profile": ["lead"], "directive": ["CORE", "HA", "HAB", "HB", "HBB"]}}
      """
    When I PUT "/activations/a-1/exclusions/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"]}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:lead
      directive:CORE
      directive:HA
      directive:HAB
      directive:HB
      directive:HBB
      """
    And the response body has no field at "/skipped/skippedByDepth"

  Scenario: The round trip is not exact when another active artifact suggests a candidate
    Decision DM-01M6TF3BA808JMW0W0A0CPXBNW: O is selected at depth 0 (O suggests H), then T at depth 0 (T requires H). Deactivating T at depth 0 removes T, but the shared walk is unlimited, so it still reaches H over O's suggests edge: H is kept in skippedShared, referenced by O.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/directive/O" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:O
      """
    And the field at "/skipped/skippedByDepth" holds exactly the ids:
      """
      directive:H
      """
    When I remember the ETag
    When I PUT "/activations/a-1/selections/directive/T" with the remembered ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:H
      directive:T
      """
    When I remember the ETag
    When I PUT "/activations/a-1/exclusions/directive/T" with the remembered ETag and the body:
      """
      {"cascade": ["all"], "depth": 0}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/skipped/skippedShared" holds exactly the ids:
      """
      directive:H
      """
    And the field at "/skipped/skippedShared/0/referencedBy/kind" is "directive"
    And the field at "/skipped/skippedShared/0/referencedBy/artifactId" is "O"
    When I GET "/activations/a-1"
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      H
      O
      """

  Scenario: A select whose depth is a string is 422 at /depth and stores nothing
    The refusal is judged after the preconditions (FR-010), and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": "1"}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "unreadable_body"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A select whose depth is a decimal is 422 at /depth and stores nothing
    The refusal is judged after the preconditions (FR-010), and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 1.5}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "unreadable_body"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A select whose depth is a negative number is 422 at /depth and stores nothing
    The refusal is judged after the preconditions (FR-010), and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": -1}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "below_minimum"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A select whose depth is a number past the int32 range is 422 at /depth and stores nothing
    The refusal is judged after the preconditions (FR-010), and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/selections/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 99999999999}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "above_maximum"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A deactivate whose depth is negative is 422 at /depth and stores nothing
    The exclusion body is judged as the selection body is.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": -1}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "below_minimum"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag

  Scenario: A deactivate whose depth is a decimal is 422 at /depth and stores nothing
    The exclusion body is judged as the selection body is.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1/exclusions/agent_profile/lead" with the stored ETag and the body:
      """
      {"cascade": ["all"], "depth": 1.5}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/depth"
    And the field at "/violations/0/code" is "unreadable_body"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response header "ETag" differs from the stored ETag
