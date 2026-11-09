# PR C of layer-merge-and-effective-reads, IC-13 (WP29, T144): a deny entry on a required artifact is accepted when an
# active override of it satisfies the requirement (operator ruling Q9, research R12). Written red first and committed
# with every step bound (ActivationSteps, RegistrationSteps): against the head that holds only the #399 write rule the
# cases that need the new rule fail on an assertion (the status is 409 where 200 or 201 is asserted), none on an
# undefined or pending step.
#
# Red first: the deactivate, the replace, the create and the unselect that follows a deactivate.
# Regression controls, green before this work package (#399 answered 409 already, and the answer must not change): an
# enhancer alone is 409 artifact_required, a locked artifact is 409 artifact_locked whatever overrides it, a replace
# whose document holds the deny entry and no override is 409. Their titles say "regression control, green before".
#
# The merge refusal is the one case that is neither: the write consults the merged closure for a deny entry on a
# required artifact, so a closure whose layer merge is refused (ADR-0063 D3) answers 500 pack_unreadable where #399
# answered 409 artifact_required. A deny entry on an artifact nothing requires never consults the merge.
#
# The fixture is one org pack, main. Its governing preset requires directives T and T2 and its policy locks LK. F
# overrides T, L overrides LK, and the tactic e enhances T; nothing overrides T2.

@contract @activations
Feature: A deny entry on a required artifact is accepted when an active override satisfies the requirement
  The floor of an activation write (ADR-0012 D7, ADR-0059 D4): a deny entry on an artifact a pack locks is 409
  artifact_locked, and one on an artifact a pack requires is 409 artifact_required, unless an artifact that overrides it
  is active in the document as it will be stored (selected or required, within the language filter, bound to a mission
  type in force, not denied and not hidden itself). The rule is the in-force resolution's own, so a write that is
  accepted is a deny entry the next read applies. An enhancement never satisfies, and a locked artifact is never
  satisfied by an override. A create judges every deny entry of the body; a replace, a deactivate, a preset applied with
  force and a select with includeDeactivated judge only the deny entries they add. A later write that removes the
  override is not refused, and the read lists the deny entry in denyNotApplied (DIV-204).

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
    And the org pack "main" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: T and T2 are required.
      activated_directives:
        - id: T
          must_have: true
        - id: T2
          must_have: true
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
    And the org pack "main" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
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
    And the org pack "main" with the file "directives/L.directive.yaml":
      """
      schema_version: '1.0'
      id: L
      title: L
      intent: L
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
    And the org pack "main" with the file "tactics/e.tactic.yaml":
      """
      schema_version: '1.0'
      id: e
      name: e
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:F
        target: directive:T
        relation: overrides
      - source: directive:L
        target: directive:LK
        relation: overrides
      - source: tactic:e
        target: directive:T
        relation: enhances
      """

  Scenario: IC-13 - deactivating a required T with an active override of it is 200 and the deny entry is applied
    Q9, R12: the governing preset requires T, and F, selected, overrides T. The deactivate adds a deny entry on T,
    which F satisfies, so it answers 200 and stores the entry. The read applies it: T is not in force (the in-force
    directives are F, the locked LK and the required T2, the controls in the same answer) and its deny entry is not in
    denyNotApplied.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/T" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    And the response header "ETag" differs from the stored ETag
    And the field at "/applied" holds exactly the ids:
      """
      directive:T
      """
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/directive" holds exactly the ids:
      """
      T
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      F
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:F
      directive:LK
      directive:T2
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/denyNotApplied" holds exactly 0 items
    And the field at "/report/requiredNotInForce" holds exactly 0 items

  Scenario: IC-13 regression control, green before - deactivating a required T with only an enhancer of it active is 409 artifact_required
    R11, Q8: the tactic e enhances T and is selected, and an enhancement never satisfies a requirement, so the deny
    entry on T is refused as #399 refused it, naming main, and nothing is stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/T" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the field at "/config/selected/tactic" holds exactly the ids:
      """
      e
      """
    And the response body has no field at "/config/deactivated"

  Scenario: IC-13 regression control, green before - deactivating a locked LK is 409 artifact_locked whatever overrides it
    R12: main locks LK and L, selected, overrides LK. A lock is never satisfied by an override (an override of a locked
    artifact is not applied, Q6), so the deny entry is refused with artifact_locked, as #399 refused it, and nothing is
    stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["L"]}}
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
    And the response body has no field at "/config/deactivated"

  Scenario: IC-13 - a replace that adds both the override and the deny entry is 200
    R12: the deny entry is judged on the document as it will be stored, not on the document it replaces, so a body
    that selects F and denies T together is accepted. The stored document holds neither, so the deny entry is added.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}, "deactivated": {"directive": ["T"]}}
      """
    Then the response status is 200
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      T
      """
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      F
      """
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/denyNotApplied" holds exactly 0 items

  Scenario: IC-13 regression control, green before - a replace with the deny entry and no override is 409 artifact_required
    R12: nothing overrides T2, so a body that denies it is refused, naming main, and the stored document is kept. The
    same body with T2 selected and no deny entry is 200 (the control that the body is otherwise fine).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}, "deactivated": {"directive": ["T2"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/revision" is the stored revision
    And the response body has no field at "/config/selected"
    When I PUT "/activations/a-1" with the stored ETag and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F", "T2"]}}
      """
    Then the response status is 200

  Scenario: IC-13 - a create judges every deny entry of the body: the override and the deny entry together are 201, the enhancer alone is 409
    DIV-204: a create has no document it replaces, so every deny entry of the body is judged on the body. F with the
    deny entry on T is stored. The control, in the same scenario, is the enhancer with the deny entry on T: 409
    artifact_required, and nothing is stored.
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}, "deactivated": {"directive": ["T"]}}
      """
    Then the response status is 201
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      T
      """
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"tactic": ["e"]}, "deactivated": {"directive": ["T"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"

  Scenario: IC-13 - unselecting the override afterwards is 200 and the read lists the deny entry in denyNotApplied
    R12, F1: a write judges only the deny entries it adds, so unselecting F is not refused although T's deny entry was
    accepted for F's sake. The deny entry is then not applied: T is back in force with reason required, and the
    skipped report lists the entry with the reason required.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/T" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    When I remember the ETag
    And I DELETE "/activations/a-1/selections/directive/F" with the remembered ETag
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:F
      """
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/directive" holds exactly the ids:
      """
      T
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:LK
      directive:T
      directive:T2
      """
    And the field at "/items/1/reason" is "required"
    When I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the field at "/report/denyNotApplied" holds exactly the ids:
      """
      directive:T
      """
    And the field at "/report/denyNotApplied/0/reason" is "required"

  Scenario: IC-13 - deactivating a locked LK whose deny entry is already stored is 409 artifact_locked
    The floor judges the named target, not only the entry the write adds: a stored document that already denies the
    locked LK (a pack upgrade locked it after the deny entry was stored) is refused as an empty one is, and nothing
    changes.
    Given the stored file of the activation "a-1" holds:
      """
      {"schemaVersion": 1, "packs": [{"packId": "main"}], "activatedKinds": ["directive"], "deactivated": {"directive": ["LK"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    When I remember the ETag
    And I PUT "/activations/a-1/exclusions/directive/LK" with the remembered ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "main"
    When I GET "/activations/a-1"
    Then the field at "/config/deactivated/directive" holds exactly the ids:
      """
      LK
      """

  Scenario: IC-13 - deactivating T again after the override was unselected is 409 artifact_required
    R12, F1: T's deny entry was accepted for F's sake and stays stored when F is unselected. Deactivating T once more
    judges T as the first write did: F no longer satisfies it, so the answer is 409 artifact_required naming main,
    although the entry is already stored.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["F"]}}
      """
    When I PUT "/activations/a-1/exclusions/directive/T" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200
    When I remember the ETag
    And I DELETE "/activations/a-1/selections/directive/F" with the remembered ETag
    Then the response status is 200
    When I remember the ETag
    And I PUT "/activations/a-1/exclusions/directive/T" with the remembered ETag and the body:
      """
      {}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "main"

  Scenario: IC-13 - a merge refusal reaches a write that adds a deny entry on a required artifact
    ADR-0063 D3, C21; the write consults the merged closure for a deny entry on a required artifact only. core
    (built-in) locks LK, leaves OP open and has a governing preset requiring OP. The project pack proj replaces LK,
    which a locked artifact does not allow, so the layer merge of core and proj is refused. A PUT that denies OP, which
    core requires, is therefore 500 pack_unreadable, naming LK, where #399 answered 409 artifact_required, and nothing
    is stored. The control, in the same scenario, denies X, which nothing requires: the merge is not consulted, and the
    document is stored (201).
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "core" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:LK: locked
      """
    And the built-in pack "core" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: OP is required.
      activated_directives:
        - id: OP
          must_have: true
      """
    And the built-in pack "core" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: LK
      intent: LK
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/OP.directive.yaml":
      """
      schema_version: '1.0'
      id: OP
      title: OP
      intent: OP
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
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: LK replaced
      intent: LK replaced
      enforcement: required
      """
    When I PUT "/activations/a-2" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "deactivated": {"directive": ["OP"]}}
      """
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "directive:LK"
    When I GET "/activations/a-2"
    Then the response status is 404
    When I PUT "/activations/a-3" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "deactivated": {"directive": ["X"]}}
      """
    Then the response status is 201
    And the field at "/config/deactivated/directive" holds exactly the ids:
      """
      X
      """
