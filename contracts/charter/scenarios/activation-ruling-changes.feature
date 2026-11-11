# SC-005 for PR A0 of layer-merge-and-effective-reads (T147): one scenario per counter-example of the ruleset review
# that A0 changes, each pinning the ruled answer with a control in the same answer. The rulings and their decisions:
# A4 DM-01M6RA7XC5HC8915GZT2R3H83W, B1 DM-01M6RA9BKJY7X9BFV93QW1KM1T (writes; DM-01M6RN5Y406SB4KA13QYMQ2P8J),
# B5 DM-01M6RA9XQSH3E07C7VCPCVD6R4, A10 DM-01M6RA8MG55V8C2V9PXWDTRNQS, A14 DM-01M6RA92BS6GGCYTRCFQDYYY5S,
# A15 DM-01M6RA97220XBR7RR447FE2JXD, B8 DM-01M6RAA6Y7NVVGKVKQV05GZYES. The changed behaviour is listed in
# changes/2026-11-07-a0-ruling-changes.md and changes/2026-11-07-a0-ruling-breaking.md.
# - A4's first counter-example in the review denies a required T that an active override E satisfies. Accepting that
#   deny entry is IC-13's relaxed write rule, which PR B builds; the A0 scenario below reaches the same state (a deny
#   entry on a now-required artifact in the stored document) through a pack change instead.
# - A10's report of the unheld must-have item is a read of PR B (the skipped report's requiredNotHeld, WP22 and WP25),
#   and the DELETE of an artifact another pack's preset names is PR A's deletePackArtifact (WP19): both scenarios are
#   bound.

@contract @activations
Feature: The A0 rulings on activation writes and on the packs
  A0 changes seven answers of #399 (the ruleset review of 2026-11-06). A replace is judged against the document it
  replaces (A4): it drops what the packs no longer hold and judges only the deny entries it adds. A write other than
  a create drops a version pin no pack holds into droppedPins (B1). includeDeactivated clears only the deny entries of
  what the walk reaches and selects (B5). The floor holds only the must-have items a pack holds (A10). Two packs that
  enhance one artifact compose (A14). A pack's policy locks only its own artifacts and those of the packs it builds on
  (A15), and never binds the pack itself (B8).

  The fixture: the org pack main holds directives D1, LK, R and X, the tactic t and the profiles p and q (p
  specializes from q, q requires R); main's policy locks directive:LK. The project pack proj extends main, holds
  directive PD and locks every directive (kind_defaults). The org pack other, unrelated to both, holds directive OD.

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
    And the org pack "main" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: LK
      intent: The directive main locks
      enforcement: required
      """
    And the org pack "main" with the file "directives/R.directive.yaml":
      """
      schema_version: '1.0'
      id: R
      title: R
      intent: The directive q requires
      enforcement: required
      """
    And the org pack "main" with the file "directives/X.directive.yaml":
      """
      schema_version: '1.0'
      id: X
      title: X
      intent: The directive an upgrade removes
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
    And the org pack "main" with the file "lineage.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:p
        target: agent_profile:q
        relation: specializes_from
      - source: agent_profile:q
        target: directive:R
        relation: requires
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943R
      name: proj-pack
      pack_level: project
      mutability_policy: policy.yaml
      """
    And the project pack "proj" with the file "policy.yaml":
      """
      schema_version: 1
      kind_defaults:
        directive: locked
      """
    And the project pack "proj" with the file "directives/PD.directive.yaml":
      """
      schema_version: '1.0'
      id: PD
      title: PD
      intent: The project's directive
      enforcement: required
      """
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
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
      intent: A directive of an unrelated pack
      enforcement: required
      """

  # A4: a replace is judged against the document it replaces.

  Scenario: A4 - a GET then PUT of a body whose deny entry a pack now requires is 200 with the same ETag
    Before A4 the PUT was 409 artifact_required: the floor was judged on every deny entry of the body. Now it is judged
    on the deny entries the body adds, and this one is in the replaced document already, so the PUT changes nothing.
    The same deny entry on a new activation is added, so it is still 409 (the control).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D1"]}, "deactivated": {"directive": ["R"]}}
      """
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: R is required from now on.
      activated_directives:
        - id: R
          must_have: true
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    When I remember the ETag
    And I remember the field "revision" of the response body
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D1"]}, "deactivated": {"directive": ["R"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    And the field "revision" of the response body is the remembered one
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      R
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      D1
      """
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"directive": ["R"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"

  Scenario: A4 - a PUT of the body read before a pack upgrade removed an id drops the id and lists it in dropped
    Before A4 the PUT was 422 unknown_artifact. The id the replaced document named is dropped; D1, which the pack
    still holds, stays selected (the control in the same answer). The tag the GET answered is the closure tag
    (decision 01M6Y11E55YP5TG1EJ1MDKCFRP): the pack upgrade changed the closure, so that tag is stale and a write that
    sends it is 412 (decision 01M6Y3GZFRC5WEQQ7G6GT0YMW3); the same body with the stored revision is the write the
    scenario is about.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D1", "X"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    When I remember the ETag
    And the file "directives/X.directive.yaml" of the pack "main" is deleted
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D1", "X"]}}
      """
    Then the response status is 412
    When I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D1", "X"]}}
      """
    Then the response status is 200
    And the field at "/dropped" holds exactly the ids:
      """
      directive:X
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      D1
      """
    And the response header "ETag" differs from the stored ETag

  # B1: a write other than a create drops a version pin no pack holds.

  Scenario: B1 - a per-artifact write drops a pin no pack holds into droppedPins, and a create refuses it
    Before B1 the select was 422 version_not_held: main was upgraded to 1.1.0 after the document pinned 1.0.0. The
    select drops the pin and still applies (the control in the same answer); the same pin in a create body is 422.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main", "version": "1.0.0"}]}
      """
    And the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: main-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I PUT "/activations/a-1/selections/directive/D1" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/droppedPins" holds exactly 1 item
    And the field at "/droppedPins/0/packId" is "main"
    And the field at "/droppedPins/0/version" is "1.0.0"
    And the field at "/applied" holds exactly the ids:
      """
      directive:D1
      """
    When I GET "/activations/a-1"
    Then the field at "/config/packs/0/packId" is "main"
    And the response body has no field at "/config/packs/0/version"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main", "version": "1.0.0"}]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/packs/0/version"
    And the field at "/violations/0/code" is "version_not_held"

  # B5: includeDeactivated clears only what the walk reaches and selects.

  Scenario: B5 - includeDeactivated clears the deny entry of a reached artifact and keeps a passed ancestor's
    Before B5 the walk also cleared q, the lineage ancestor it passes through without selecting. R is reached and
    selected, so its deny entry is cleared (the control in the same answer); q keeps its own.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"agent_profile": ["q"], "directive": ["R"]}}
      """
    When I PUT "/activations/a-1/selections/agent_profile/p" with the stored ETag and the body:
      """
      {"cascade": ["all"], "includeDeactivated": true}
      """
    Then the response status is 200
    And the field at "/cleared" holds exactly the ids:
      """
      directive:R
      """
    And the field at "/applied" holds exactly the ids:
      """
      agent_profile:p
      directive:R
      """
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/agent_profile" holds exactly the ids:
      """
      q
      """
    And the response body has no field at "/config/deactivated/directive"

  # A10: the floor holds only the must-have items a pack holds.

  Scenario: A10 - a must-have item no pack holds is not on the floor, and a held one of the same preset is
    The governing preset names D1, which main holds, and GONE, which no pack holds. GONE leaves the floor (it is
    reported by PR B's reads, below), so a document of main is created; D1 stays required (the control).
    Given the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: D1 and an item no pack holds.
      activated_directives:
        - id: D1
          must_have: true
        - id: GONE
          must_have: true
      """
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["X"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      X
      """
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"

  # A14: enhances is not a sibling conflict.

  Scenario: A14 - two org packs that enhance one artifact compose
    Before A14 two sibling packs that both enhance an artifact contested it, so the compose was refused
    pack_sibling_conflict. An enhances edge is additive: the closure of both is served (the overrides contest stays
    refused, composition.feature).
    Given the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      """
    And the org pack "left" with the file "tactics/left-way.tactic.yaml":
      """
      schema_version: '1.0'
      id: left-way
      name: left-way
      steps:
        - title: Only step
      """
    And the org pack "left" with the file "enhancing.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: tactic:left-way
        kind: tactic
        label: left-way
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: tactic:left-way
        target: directive:software-dev/test-first
        relation: enhances
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      """
    And the org pack "right" with the file "tactics/right-way.tactic.yaml":
      """
      schema_version: '1.0'
      id: right-way
      name: right-way
      steps:
        - title: Only step
      """
    And the org pack "right" with the file "enhancing.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: tactic:right-way
        kind: tactic
        label: right-way
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: tactic:right-way
        target: directive:software-dev/test-first
        relation: enhances
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "left"}, {"packId": "right"}]}
      """
    When I GET "/activations/a-1/packs"
    Then the response status is 200
    And the answer holds exactly 2 packs
    When I GET "/offering"
    Then the response status is 200
    When I GET "/packs/left/artifacts"
    Then the response status is 200
    And the field at "/items/0/id" is "left-way"
    When I GET "/packs/right/artifacts"
    Then the response status is 200
    And the field at "/items/0/id" is "right-way"
    When I GET "/activations/a-1/artifacts?kind=tactic"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      tactic:left-way
      tactic:right-way
      """

  # A15: a pack's policy locks only its own artifacts and those of the packs it builds on.

  Scenario: A15 - a project pack's kind default locks its own and its ancestors' directives, not an unrelated pack's
    Before A15 proj's kind_defaults locked every directive of the closure, OD of the unrelated other included. Now a
    deny entry on OD is accepted, while PD (proj's own) and D1 (main's, which proj extends) stay locked (the controls).
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "proj"}, {"packId": "other"}], "deactivated": {"directive": ["OD"]}}
      """
    Then the response status is 201
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      OD
      """
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "proj"}, {"packId": "other"}], "deactivated": {"directive": ["PD"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "proj"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "proj"}, {"packId": "other"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "proj"

  # B8: a pack's own policy never binds the pack itself.

  Scenario: B8 - a pack's own lock does not refuse its own per-artifact PUT, and binds the pack built on it
    Before B8 main's own lock on LK refused main's re-registration of LK, 409 governance_policy_violation. Now it
    protects LK from the packs built on main only: the same write into proj, which extends main, is still refused (the
    control).
    When I PUT "/packs/main/artifacts/directive/LK" with the body:
      """
      {"schemaVersion": "1.0", "title": "LK", "intent": "The directive main locks, reworded", "enforcement": "required"}
      """
    Then the response status is 200
    And the field at "/directive/intent" is "The directive main locks, reworded"
    When I PUT "/packs/proj/artifacts/directive/LK" with the body:
      """
      {"schemaVersion": "1.0", "title": "LK", "intent": "proj's own LK", "enforcement": "required"}
      """
    Then the response status is 409
    And the problem code is "governance_policy_violation"

  # WP19 (PR A) builds deletePackArtifact. The preset item is spelled as a bare id (the preset item grammar refuses
  # main::D1), so the pack other builds on main, and the item names main's D1 as the compose resolves it.
  Scenario: A10 - an artifact another pack's preset names cannot be deleted
    The preset of the pack "other" names main's D1, so main's D1 cannot be deleted while it is named.
    Given registration is switched on
    And the org pack "other" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: other-pack
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0'
      """
    And the org pack "other" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: D1 of main.
      activated_directives:
        - id: D1
          must_have: true
      """
    When I DELETE "/packs/main/artifacts/directive/D1" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/packs/0/packId" is "other"
    When I GET "/packs/main/artifacts/directive/D1"
    Then the response status is 200

  # The skipped report (PR B) answers requiredNotHeld from floor.unheld(). The answer is ActivationSkippedReport: the
  # report is at /report.
  Scenario: A10 - the unheld must-have item is in requiredNotHeld
    The report of an unheld must-have item is the skipped report's requiredNotHeld. The preset item is spelled as a bare
    id, as the preset item grammar requires (a pack-qualified main::D1 is refused), and names main's D1. The held item
    D1 is the control: it is required and in force, and it is not in the bucket.
    Given the org pack "other" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: D1 of main and an item no pack holds.
      activated_directives:
        - id: D1
          must_have: true
        - id: GONE
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "other"}]}
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/requiredNotHeld" holds exactly the ids:
      """
      directive:GONE
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items/0/artifactId" is "D1"
    And the field at "/items/0/required" is true
