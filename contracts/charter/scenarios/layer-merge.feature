# PR B of layer-merge-and-effective-reads, contract B (IC-08; WP22, T113): the layer merge as the effective reads
# answer it. Written red first and committed with a WIP tag (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW); WP25 bound the glue,
# recorded the red assertion failure (every step bound, a wrong status or body) and removed the tag.
# Every When asserts 200 (or the refusal) and a non-empty answer, and every absence is read beside a positive control in
# the same answer. The fixture packs are authored inline, per rule (plan.md, Fixtures).
#
# Scenarios: US3 AS1, the A1 union, US6 AS1 (the #357 settlement), US6 AS2, and the refusal of a layer override the
# mutability level forbids (500 pack_unreadable, ADR-0063 D3, C21).
#
# Steps these scenarios add to the vocabulary of README.md (WP25 binds them in EffectiveReadSteps):
# - `When I GET "<path>" with If-None-Match the remembered header "ETag"` sends the `ETag` last remembered with
#   `When I remember the header "ETag"` as `If-None-Match`; `When I follow the link "<relation>" with If-None-Match
#   the remembered header "ETag"` does the same for a followed link.
# - `When I GET "<path>" with the remembered cursor` appends `pageCursor=<value>` (with `?` or `&`) where the value is
#   the field last remembered with `When I remember the field "/pageInfo/nextPageCursor" of the response body`.
# - `Then the field at "<pointer>" is "<text>"` and `... holds exactly the ids:` are the existing steps: an item of the
#   list is read as `<kind>:<artifactId>`, in the answer's order (kind, then id in code-point order).

@contract @activations
Feature: The layer merge decides which version of an artifact the effective reads answer
  The activation's closure is merged per artifact (ADR-0059 D3, ADR-0053 d6): the version from the level that applies, in
  merge order (built-in, then org, then project), replaces the others whole, and a sibling contest at one level is settled
  only by a distribution pack's overrides entry (#357). The effective list answers the winner with its provenance (packId
  and level, FR-013). An override the overridden artifact's mutability level forbids refuses the read (ADR-0063 D3, C21).

  Scenario: US3 AS1 - a directive held at built-in and at project level is listed once, with the project version's provenance
    US3 AS1, FR-013: core (built-in) holds D and X, proj (project) holds its own D. The layer merge keeps the project
    version of D whole (ADR-0059 D3), so D appears once with packId proj and level project. X, which no later level
    replaces, is the control: it keeps its built-in provenance in the same answer.
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
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"directive": ["D", "X"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D
      directive:X
      """
    And the field at "/items/0/packId" is "proj"
    And the field at "/items/0/level" is "project"
    And the field at "/items/0/state" is "active"
    And the field at "/items/0/reason" is "selected"
    And the field at "/items/1/packId" is "core"
    And the field at "/items/1/level" is "built-in"
    And the field at "/summary/active" is 2

  Scenario: A1 - the requirement of a preset id is the union of every governing copy across levels
    A1, FR-014, FR-028: the built-in pack's governing preset requires D1 and the org pack's preset of the same id
    requires D2. The copy at the later level does not replace the earlier one's requirement: both D1 and D2 are
    required and in force with no selection at all. D3, which no copy requires, is the control: it is not in force,
    and the same read says why.
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: D1
      intent: D1
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/D2.directive.yaml":
      """
      schema_version: '1.0'
      id: D2
      title: D2
      intent: D2
      enforcement: required
      """
    And the built-in pack "core" with the file "directives/D3.directive.yaml":
      """
      schema_version: '1.0'
      id: D3
      title: D3
      intent: D3
      enforcement: required
      """
    And the built-in pack "core" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D1
          must_have: true
      """
    And the org pack "house" with the file "pack.yaml":
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
    And the org pack "house" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The governing preset.
      activated_directives:
        - id: D2
          must_have: true
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "house"}]}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D1
      directive:D2
      """
    And the field at "/items/0/required" is true
    And the field at "/items/0/reason" is "required"
    And the field at "/items/1/required" is true
    And the field at "/items/1/reason" is "required"
    When I GET "/activations/a-1/artifacts?kind=directive&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:D3
      """
    And the field at "/items/0/required" is false
    And the field at "/items/0/reason" is "not_selected"

  Scenario: US6 AS1 - a distribution pack's settlement puts the named member's version in force (#357)
    US6 AS1, #357, ADR-0053 d5: the org packs a and b both define D, and the distribution pack x declares
    overrides: [{artifact: directive:D, member: <b>}]. The settlement is applied before the levels merge, so b's D
    is in force and no pack_sibling_conflict is raised. A1, which only a holds, is the control: it keeps a's
    provenance in the same answer.
    Given the org pack "a" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: a-pack
      pack_level: org
      """
    And the org pack "a" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D of a
      intent: D of a
      enforcement: required
      """
    And the org pack "a" with the file "directives/A1.directive.yaml":
      """
      schema_version: '1.0'
      id: A1
      title: A1
      intent: A1
      enforcement: required
      """
    And the org pack "b" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: b-pack
      pack_level: org
      """
    And the org pack "b" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D of b
      intent: D of b
      enforcement: required
      """
    And the org pack "x" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: x-pack
      pack_level: org
      overrides:
      - artifact: directive:D
        member: 01M4P01D5TWB5FKZGNNQG3943S
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "a"}, {"packId": "b"}, {"packId": "x"}], "selected": {"directive": ["A1", "D"]}}
      """
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A1
      directive:D
      """
    And the field at "/items/0/packId" is "a"
    And the field at "/items/0/level" is "org"
    And the field at "/items/1/packId" is "b"
    And the field at "/items/1/level" is "org"

  Scenario: US6 AS2 - the same conflict without a settlement is refused, and an activation that avoids it still answers
    US6 AS2: the same two siblings with no distribution pack. A read of an activation whose closure holds both
    refuses 500 with the composition code pack_sibling_conflict (the read never answers part of a conflict). The
    control is an activation of the pack a alone, whose closure composes: it answers 200 with its directive.
    The conflicting activation is stored past the API, as a pack change after the write would leave it.
    Given the org pack "a" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: a-pack
      pack_level: org
      """
    And the org pack "a" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D of a
      intent: D of a
      enforcement: required
      """
    And the org pack "a" with the file "directives/A1.directive.yaml":
      """
      schema_version: '1.0'
      id: A1
      title: A1
      intent: A1
      enforcement: required
      """
    And the org pack "b" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: b-pack
      pack_level: org
      """
    And the org pack "b" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D of b
      intent: D of b
      enforcement: required
      """
    And the activation "a-2" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "a"}], "selected": {"directive": ["A1"]}}
      """
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"a"},{"packId":"b"}],"schemaVersion":2,"selected":{"directive":["D"]}}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    And the field at "/detail" contains "directive:D"
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/activations/a-1/skipped"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    When I GET "/activations/a-2/artifacts?state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:A1
      """

  Scenario: A layer override the overridden artifact's level forbids is 500 pack_unreadable, and one of an open artifact is served
    FR-011, ADR-0063 D3, C21: core (built-in) locks LK and leaves OP open. The project pack proj replaces LK, which a
    locked artifact does not allow, so the read of an activation over core and proj refuses 500 pack_unreadable,
    naming the artifact, both packs and the level. The project pack proj-ok replaces the open OP: the control, an
    activation over core and proj-ok, answers 200 and lists OP with proj-ok's provenance in the same scenario, beside LK,
    which core's lock keeps in force whatever the activation selects. Both refused and served activations are stored
    past the API.
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
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
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
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
    And the project pack "proj-ok" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj-ok-pack
      pack_level: project
      """
    And the project pack "proj-ok" with the file "directives/OP.directive.yaml":
      """
      schema_version: '1.0'
      id: OP
      title: OP replaced
      intent: OP replaced
      enforcement: required
      """
    And the stored file of the activation "a-1" holds:
      """
      {"packs":[{"packId":"core"},{"packId":"proj"}],"schemaVersion":2,"selected":{"directive":["LK"]}}
      """
    And the stored file of the activation "a-2" holds:
      """
      {"packs":[{"packId":"core"},{"packId":"proj-ok"}],"schemaVersion":2,"selected":{"directive":["OP"]}}
      """
    When I GET "/activations/a-1/artifacts"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "directive:LK"
    And the field at "/detail" contains "proj-pack"
    And the field at "/detail" contains "core-pack"
    And the field at "/detail" contains "locked"
    And the field at "/packs/0/name" is "proj-pack"
    And the field at "/packs/1/name" is "core-pack"
    When I GET "/activations/a-1/skipped"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/activations/a-2/artifacts?kind=directive&state=active"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      directive:LK
      directive:OP
      """
    And the field at "/items/0/reason" is "locked"
    And the field at "/items/0/packId" is "core"
    And the field at "/items/1/packId" is "proj-ok"
    And the field at "/items/1/level" is "project"
