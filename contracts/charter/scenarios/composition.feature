@contract @composition
Feature: The compose resolves the configured packs' dependency graph
  One compose is one depends_on closure rooted at every configured pack, across levels (ADR-0053
  decision 6, as amended): GET /offering answers
  the closure in the resolver's order, dependencies before the packs that need them, each pack once
  (FR-019), and every entry carries the dependencies its pack.yaml declared, each with the range it
  declared and the version of the depended-on pack the compose resolved for it (FR-020, NFR-002).
  A graph the resolver cannot serve refuses the whole offering, never a partial compose: the refusal
  names the pack that broke the graph and why (ADR-0048 decisions 1 and 10, ADR-0053 decisions 4, 7, 8 and
  9). Every refusal code here has a negative control on the same fixture shape — the happy path that
  would break if the refusal fired wrongly (NFR-005) — so a no-op resolver cannot pass this file.

  Scenario: A shared dependency appears once, before the packs that need it
    The diamond of User Story 1: top depends on left and right, and both depend on apex. Apex is
    answered once, before left and right; the two ranges on apex overlap; every entry carries the
    resolved edges, in the descriptor's declared order.
    Given the org pack "apex" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: apex-pack
      pack_level: org
      """
    And the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 2.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '>=1.0,<2'
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 3.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '>=1.0'
      """
    And the org pack "top" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 4.0.0
      parent_pack: null
      name: top-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=2.0'
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943T
        range: '<4.0'
      """
    When I GET "/offering"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "packs": [
          {"packId": "apex", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "level": "org", "name": "apex-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/apex/artifacts"}}},
          {"packId": "left", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943S", "level": "org", "name": "left-pack", "version": "2.0.0", "dependsOn": [{"descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "range": ">=1.0,<2", "version": "1.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/left/artifacts"}}},
          {"packId": "right", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943T", "level": "org", "name": "right-pack", "version": "3.0.0", "dependsOn": [{"descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "range": ">=1.0", "version": "1.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/right/artifacts"}}},
          {"packId": "top", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943V", "level": "org", "name": "top-pack", "version": "4.0.0", "dependsOn": [{"descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943S", "range": ">=2.0", "version": "2.0.0"}, {"descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943T", "range": "<4.0", "version": "3.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/top/artifacts"}}}
        ],
        "_links": {"self": {"href": "/api/v1/offering"}}
      }
      """

  Scenario: The single built-in tree still loads
    A built-in pack with one graph shard resolves as before (FR-018's scenario level; the
    byte-level ratchet is WP04's).
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      edges: []
      """
    When I GET "/offering"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {"packs": [{"packId": "core", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "level": "built-in", "name": "core-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/core/artifacts"}}}], "_links": {"self": {"href": "/api/v1/offering"}}}
      """

  Scenario: A dependency cycle refuses the compose naming a pack on it
    Alpha depends on beta and beta depends on alpha. Two org packs may depend on each other's
    artifacts (a same-level edge is allowed), so the refusal is the cycle, not a level violation;
    the pack named is the first in the stable order that still has outstanding dependencies.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '>=1.0.0'
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_dependency_cycle"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) is on a dependency cycle: `alpha-pack` -> `beta-pack` -> `alpha-pack`"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A dependency no configured pack declares refuses the compose
    Alpha depends on an id no configured pack declares; the refusal names alpha and the missing id.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943X
        range: '>=1.0.0'
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_dependency_unknown"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) depends on pack `01M4P01D5TWB5FKZGNNQG3943X`, which no configured pack declares"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A broken compose refuses the composed read, while a pack-scoped read of one artifact still answers
    A broken compose never reaches a composed read (operator ruling of 2026-11-03): the offering and the
    references of an artifact are both refused with the composition code. The pack-scoped read of one artifact by
    id resolves no compose, so an author can inspect a broken pack. The control of the refusal is
    the scenario below: the same descriptor with its dependency configured is served.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943X
        range: '>=1.0.0'
      """
    And the org pack "alpha" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_dependency_unknown"
    When I GET "/packs/alpha/artifacts/directive/SHARE/references"
    Then the response status is 500
    And the problem code is "pack_dependency_unknown"
    And the field at "/packs/0/name" is "alpha-pack"
    When I GET "/packs/alpha/artifacts/directive/SHARE"
    Then the response status is 200
    And the field "id" of the response body is "SHARE"
    And the field at "/directive/title" is "Share the load"

  Scenario: A declared dependency a configured pack declares resolves
    The negative control of the unknown-dependency and the cycle refusals: the same descriptor with
    the target configured, in range, and one edge instead of two. The dependency comes before the
    pack that needs it even though its pack_id sorts after the dependent's, so the order is the
    resolution's, not the directory listing's. The entry carries the resolved edge: the target's pack_id, the declared
    range and the version that satisfied it.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/1/dependsOn/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/dependsOn/0/range" is ">=1.0.0"
    And the field at "/packs/1/dependsOn/0/version" is "1.0.0"

  Scenario: Two ranges on one pack that share no version refuse the compose
    Left asks apex for <2 and right asks apex for >=2: the compose holds one copy of apex, so no
    version can satisfy both, and the refusal comes before the held version is checked against either
    range. It names both declaring packs and both ranges. The control is the diamond above, whose two
    ranges on apex overlap.
    Given the org pack "apex" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: apex-pack
      pack_level: org
      """
    And the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '<2'
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '>=2'
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_dependency_out_of_range"
    And the field "detail" of the response body is "packs `left-pack` (01M4P01D5TWB5FKZGNNQG3943S) and `right-pack` (01M4P01D5TWB5FKZGNNQG3943T) require pack `apex-pack` (01M4P01D5TWB5FKZGNNQG3943R) in ranges `<2` and `>=2`, which no version satisfies together"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943T"
    And the field at "/packs/0/packId" is "right"
    And the field at "/packs/0/name" is "right-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "left"
    And the field at "/packs/1/name" is "left-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A depends_on range that is no PEP 440 specifier refuses the offering as unreadable
    A malformed range answers the same code whichever path reads the pack: the descriptor rules of
    validate mode run on the offering too, so the answer is pack_unreadable (500), never a
    composition code. The control is the scenario above that resolves a declared dependency in range.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: banana
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field "detail" of the response body is "pack.yaml of alpha: depends_on[0] range 'banana' is not a PEP 440 version specifier"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An exact range the held version misses refuses as out-of-range, not unknown
    The target is configured and holds 1.9.0; alpha declares ==2.0.0. The refusal is the range,
    naming alpha, the target and the held version — an unheld version of a configured pack is
    out-of-range, never an unknown dependency.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '==2.0.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.9.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_dependency_out_of_range"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) requires pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S) in range `==2.0.0`, but `beta-pack` is at version 1.9.0"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "beta"
    And the field at "/packs/1/name" is "beta-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A range the held version satisfies resolves
    The negative control of the out-of-range refusal: the same fixture with a range 1.9.0 satisfies,
    and the resolved edge reports the satisfying version.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.9.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.9.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/1/dependsOn/0/range" is ">=1.9.0"
    And the field at "/packs/1/dependsOn/0/version" is "1.9.0"

  Scenario: A parent_pack no configured pack declares refuses the compose
    The parent is refused rather than degraded to a root pack; the refusal names the pack and the
    parent id it declared.
    Given the org pack "orphan" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943X
      name: orphan-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_parent_unresolved"
    And the field "detail" of the response body is "pack `orphan-pack` (01M4P01D5TWB5FKZGNNQG3943R) names parent_pack `01M4P01D5TWB5FKZGNNQG3943X`, which no configured pack declares"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "orphan"
    And the field at "/packs/0/name" is "orphan-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A parent_pack a configured pack declares resolves
    The negative control of the unresolved-parent refusal: the same child with its parent
    configured. The parent imposes no order of its own; the stable order — the configured
    order — serves, and child is configured before root.
    Given the org pack "root" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: root-pack
      pack_level: org
      """
    And the org pack "child" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943R
      name: child-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "child"
    And the field at "/packs/1/packId" is "root"

  Scenario: An org pack depending on a project pack refuses the compose
    A pack may take a dependency or a parent only at its own level or one nearer built-in (ADR-0048
    decision 7), so an org pack
    leaning on a project pack is refused, naming both packs and both levels. A same-level edge is
    allowed (spec.md User Story 2 scenario 5 as corrected in 33cbb7d0, data-model.md invariant 3 and
    the resolver agree), and the same-level case is proven by the cycle and diamond scenarios above.
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943W
        range: '>=1.0.0'
      """
    And the project pack "team" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: team-pack
      pack_level: project
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_level_violation"
    And the field "detail" of the response body is "pack `house-pack` (01M4P01D5TWB5FKZGNNQG3943R) at org depends on pack `team-pack` (01M4P01D5TWB5FKZGNNQG3943W) at project; a pack may take a dependency or a parent only at its own level or one nearer built-in"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "house"
    And the field at "/packs/0/name" is "house-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943W"
    And the field at "/packs/1/packId" is "team"
    And the field at "/packs/1/name" is "team-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A project pack depending on an org pack resolves
    The negative control of the level-violation refusal: the same two packs with the dependency
    pointing from the project pack onto the org pack, one level nearer built-in. The org pack is answered first.
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      """
    And the project pack "team" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: team-pack
      pack_level: project
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
        range: '>=1.0.0'
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "house"
    And the field at "/packs/1/packId" is "team"

  Scenario: An org pack naming a project pack as its parent_pack refuses the compose
    The parent edge follows the same level rule as depends_on (ADR-0048 decision 7): a pack may take
    a parent only at its own level or one nearer built-in. The negative control is the scenario above
    that resolves an org child under an org parent.
    Given the org pack "child" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943W
      name: child-pack
      pack_level: org
      """
    And the project pack "team" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: team-pack
      pack_level: project
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_level_violation"
    And the field "detail" of the response body is "pack `child-pack` (01M4P01D5TWB5FKZGNNQG3943S) at org names pack `team-pack` (01M4P01D5TWB5FKZGNNQG3943W) at project as its parent_pack; a pack may take a dependency or a parent only at its own level or one nearer built-in"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/0/packId" is "child"
    And the field at "/packs/0/name" is "child-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943W"
    And the field at "/packs/1/packId" is "team"
    And the field at "/packs/1/name" is "team-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Two packs declaring one name refuse the compose
    A name is unique inside a compose (data-model invariant 2), so a pack-qualified reference is
    never ambiguous. The refusal names the second pack in the stable order and both pack ids.
    Given the org pack "one" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: shared-house-rules
      pack_level: org
      """
    And the org pack "two" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: shared-house-rules
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_name_conflict"
    And the field "detail" of the response body is "packs `shared-house-rules` (01M4P01D5TWB5FKZGNNQG3943R) and `shared-house-rules` (01M4P01D5TWB5FKZGNNQG3943S) both declare the name `shared-house-rules`"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/0/packId" is "two"
    And the field at "/packs/0/name" is "shared-house-rules"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/1/packId" is "one"
    And the field at "/packs/1/name" is "shared-house-rules"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Two configured packs declaring one pack_id refuse the compose
    A pack_id appears once in a compose (data-model invariant 2); two configured directories
    declaring the same id is a misconfiguration, and the refusal names the id.
    Given the org pack "one" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: one-pack
      pack_level: org
      """
    And the org pack "two" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: two-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_duplicate_id"
    And the field "detail" of the response body is "packs `one-pack` (01M4P01D5TWB5FKZGNNQG3943R) and `two-pack` (01M4P01D5TWB5FKZGNNQG3943R) both declare the pack_id 01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "two"
    And the field at "/packs/0/name" is "two-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/1/packId" is "one"
    And the field at "/packs/1/name" is "one-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Two configured packs with distinct pack_ids resolve
    The negative control of the duplicate-id and the name-conflict refusals: the same two configured
    packs, each declaring its own id and its own name, served in the stable order. A descriptor that
    declares no depends_on is served with dependsOn [] (offering.feature's ordering scenario).
    Given the org pack "one" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: one-pack
      pack_level: org
      """
    And the org pack "two" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: two-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "one"
    And the field at "/packs/1/packId" is "two"

  Scenario: Two siblings changing one artifact, unsettled, refuse the compose
    WP01's contest (FR-012): left and right, both at org, each declare an enhances edge on the same
    directive in their graphs, and no pack of the compose declares an overrides edge settling it. A
    conflict must be a decision, not load order, so the compose is refused (500, like every composition refusal) naming both packs
    and the artifact.
    Given the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      """
    And the org pack "left" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: action:software-dev/implement
        target: directive:software-dev/test-first
        relation: enhances
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      """
    And the org pack "right" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: action:software-dev/implement
        target: directive:software-dev/test-first
        relation: enhances
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    And the field "detail" of the response body is "packs `left-pack` (01M4P01D5TWB5FKZGNNQG3943R) and `right-pack` (01M4P01D5TWB5FKZGNNQG3943S) at org both change the artifact `directive:software-dev/test-first`, and no pack of the compose declares an overrides edge settling it"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/0/packId" is "right"
    And the field at "/packs/0/name" is "right-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/1/packId" is "left"
    And the field at "/packs/1/name" is "left-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An overrides edge settling the contest serves the compose
    The negative control of the sibling refusal (User Story 2, scenario 7): the same two siblings
    and the same two graphs, with a third pack of the compose declaring the overrides edge that
    names the winning member. The compose is served, all three packs in the stable order.
    Given the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      """
    And the org pack "left" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: action:software-dev/implement
        target: directive:software-dev/test-first
        relation: enhances
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      """
    And the org pack "right" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: directive:software-dev/test-first
        kind: directive
        label: test-first
      edges:
      - source: action:software-dev/implement
        target: directive:software-dev/test-first
        relation: enhances
      """
    And the org pack "settler" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: settler-pack
      pack_level: org
      overrides:
      - artifact: directive:software-dev/test-first
        member: 01M4P01D5TWB5FKZGNNQG3943R
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "left"
    And the field at "/packs/1/packId" is "right"
    And the field at "/packs/2/packId" is "settler"

  Scenario: Two siblings defining one artifact, unsettled, refuse the compose
    Two packs at one configured level that both hold the directive SHARE contest it (ruling 13): the
    compose is refused, naming both packs and the artifact, as for two siblings changing one artifact.
    Given the org pack "left" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: left-pack
      pack_level: org
      """
    And the org pack "left" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the org pack "right" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: right-pack
      pack_level: org
      """
    And the org pack "right" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_sibling_conflict"
    And the field "detail" of the response body is "packs `left-pack` (01M4P01D5TWB5FKZGNNQG3943R) and `right-pack` (01M4P01D5TWB5FKZGNNQG3943S) at org both define the artifact `directive:SHARE`, and no pack of the compose declares an overrides edge settling it"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/0/packId" is "right"
    And the field at "/packs/0/name" is "right-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/1/packId" is "left"
    And the field at "/packs/1/name" is "left-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An org pack and a project pack defining one artifact compose as a layer override
    The negative control of the duplicate-definition refusal: the same directive held at two
    different levels is the layer override of ADR-0053 decision 6, not a sibling conflict. Both packs are
    served, the org pack first.
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      """
    And the org pack "house" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the project pack "team" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: team-pack
      pack_level: project
      """
    And the project pack "team" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "house"
    And the field at "/packs/1/packId" is "team"

  # Deferred to #360: roots come from the activation set (operator ruling 2026-11-03).
  @WIP
  Scenario: The compose is the closure of the activated packs, not every available pack
    FR-011: the catalog is the set of available packs and the activated packs are the closure's roots.
    A pack that is neither activated nor depended on is available but is not in the compose.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the offering holds exactly 2 packs

  # Deferred to #380 (operator ruling 2026-11-05, decision 01M6NE62FJ43XT0VC78YJ128NP): the service does not
  # resolve an agent profile's enhances yet (ADR-0032; AgentProfile), so the effective profile this refusal
  # judges does not exist. PR-CHARTER-REFUSAL-001 of #374. Steps are enhancement adds only (ADR-0053 decision 22),
  # so this concerns profiles alone.
  @WIP
  Scenario: An agent profile enhancement that would drop fields of the profile it enhances is refused
    A pack's profile that enhances another profile may add to it, never silently drop part of it: governance
    that would quietly weaken a profile is refused, naming the profile and the fields it would drop.
    Given the org pack "base" holds the agent profile "reviewer" with the specialization "review"
    And the org pack "team" holds an agent profile that enhances "reviewer" and leaves out its specialization
    When I read the effective agent profile "reviewer"
    Then the request is refused naming the pack "team" and the field "specialization"

  Scenario: A bare cross-pack reference is refused naming the holding pack
    SC-004's refusal half (FR-015), on the two-pack fixture shared with the two scenarios below: alpha's
    graph writes the bare reference directive:SHARE, which only beta holds. A reference to another
    pack's artifact must be written pack-qualified, so the compose is refused, naming the holding
    pack, rather than resolving by load order.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: directive:SHARE
        relation: requires
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_unqualified"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) writes the bare reference `directive:SHARE`, which resolves only in pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S); a cross-pack reference is written pack-qualified"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "beta"
    And the field at "/packs/1/name" is "beta-pack"
    And the response has no "Charter-Source-Fingerprint" header


  Scenario: A pack-qualified reference outside the referring pack's closure is refused naming the missing pack
    SC-004's second refusal (FR-017): the same fixture, with alpha writing beta-pack::directive:SHARE but
    declaring no depends_on. Beta is in the compose, yet not in alpha's own depends_on closure, so
    alpha may not rely on it: the compose is refused, naming beta.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: beta-pack::directive:SHARE
        relation: requires
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) writes the reference `beta-pack::directive:SHARE`, but pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S) is outside its depends_on closure"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "beta"
    And the field at "/packs/1/name" is "beta-pack"
    And the response has no "Charter-Source-Fingerprint" header


  Scenario: A pack-qualified reference inside the closure resolves
    SC-004's control (FR-013, FR-017), on the same fixture: alpha now declares depends_on beta, so the
    reference beta-pack::directive:SHARE is inside its closure and the compose is served, beta before alpha.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: beta-pack::directive:SHARE
        relation: requires
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"

  Scenario: A pack-qualified reference to an artifact the named pack does not hold is refused naming both packs
    The control is the scenario above (ADR-0048 decision 2). Alpha depends on beta and writes
    beta-pack::directive:SHARE, so beta is inside its closure, but beta holds no directive SHARE: the compose is
    refused, naming alpha, the pack that wrote the reference, then beta, the pack that does not hold it.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: beta-pack::directive:SHARE
        relation: requires
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) writes the reference `beta-pack::directive:SHARE`, but pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S) does not hold `directive:SHARE`"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "beta"
    And the field at "/packs/1/name" is "beta-pack"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario Outline: A relationship whose target the compose cannot resolve is refused 422 before anything is written
    The registration judges its own to against the compose of the pack it writes into (FR-017), so an edge
    the next offering read would refuse never lands. alpha depends on beta and not on gamma: a qualifier naming
    gamma is outside its closure, a bare SHARE only beta holds is unqualified, and beta-pack::NOPE names a pack
    of the closure that does not hold it. Each of the three reference codes answers 422 on putPackRelationship
    (the contract's x-refusal operations map) where the reads answer 500, and the packs member names alpha
    first. The refused write changed nothing: the same remembered ETag still registers a target no pack holds,
    which is external.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/MINE.directive.yaml":
      """
      schema_version: '1.0'
      id: MINE
      title: Mine
      intent: Mine
      enforcement: required
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    Then the contract says "<code>" is answered with status 422 by the operation "putPackRelationship"
    And the contract says "<code>" is answered with status 500
    When I GET "/packs/alpha/artifacts/directive/MINE"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/alpha/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:MINE", "to": "<to>", "type": "requires"}
      """
    Then the response status is 422
    And the problem code is "<code>"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/1/descriptorPackId" is "<second descriptor>"
    And the field at "/packs/1/packId" is "<second>"
    When I PUT "/packs/alpha/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:MINE", "to": "directive:ELSEWHERE", "type": "requires"}
      """
    Then the response status is 201

    Examples:
      | code                            | to                      | second | second descriptor          |
      | pack_reference_pack_missing     | gamma-pack::directive:SHARE | gamma  | 01M4P01D5TWB5FKZGNNQG3943T |
      | pack_reference_unqualified      | directive:SHARE         | beta   | 01M4P01D5TWB5FKZGNNQG3943S |
      | pack_reference_artifact_missing | beta-pack::directive:NOPE | beta   | 01M4P01D5TWB5FKZGNNQG3943S |

  Scenario: The references of an artifact are read across packs, each resolved through the compose
    A composed read (operator ruling of 2026-11-03). Alpha depends on beta, and alpha's directive USE writes
    beta-pack::SHARE as a typed reference: the read answers it as written, under the relation the artifact mints for
    it, held by beta, resolved to the bare URN, and its artifact link reads beta's directive. The artifact the
    read is asked for must exist, exactly as for the pack-scoped read.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/USE.directive.yaml":
      """
      schema_version: '1.0'
      id: USE
      title: Use the shared load
      intent: Use it
      enforcement: required
      references:
      - type: directive
        id: beta-pack::SHARE
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    When I GET "/packs/alpha/artifacts/directive/USE/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "beta-pack::directive:SHARE"
    And the field at "/items/0/relation" is "suggests"
    And the field at "/items/0/urn" is "directive:SHARE"
    And the field at "/items/0/holdingPack/packId" is "beta"
    And the field at "/items/0/holdingPack/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/items/0/holdingPack/name" is "beta-pack"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/beta/artifacts/directive/SHARE"
    And following the link "artifact" of every element of "items" answers 200
    When I GET "/packs/alpha/artifacts/directive/NOPE/references"
    Then the response status is 404
    And the problem code is "directive_not_found"

  Scenario: A bare reference is held by the pack that writes it, which shadows another pack's artifact of that id
    FR-014: alpha (project) and beta (org) both hold directive SHARE, and alpha's USE writes it bare. The read
    answers alpha as the holding pack, with no refusal, and its artifact link reads alpha's directive. alpha's own
    SHARE writes beta-pack::SHARE, a qualified reference to another pack's artifact of the same id: legal (R2), not
    a self-loop, and it resolves to beta.
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the project pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: project
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Beta's share
      intent: Share it
      enforcement: required
      """
    And the project pack "alpha" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Alpha's share
      intent: Share it
      enforcement: required
      references:
      - type: directive
        id: beta-pack::SHARE
      """
    And the project pack "alpha" with the file "directives/USE.directive.yaml":
      """
      schema_version: '1.0'
      id: USE
      title: Use the shared load
      intent: Use it
      enforcement: required
      references:
      - type: directive
        id: SHARE
      """
    When I GET "/packs/alpha/artifacts/directive/USE/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "directive:SHARE"
    And the field at "/items/0/holdingPack/packId" is "alpha"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/alpha/artifacts/directive/SHARE"
    When I GET "/packs/alpha/artifacts/directive/SHARE"
    Then the response status is 200
    And the field at "/directive/title" is "Alpha's share"
    When I GET "/packs/alpha/artifacts/directive/SHARE/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "beta-pack::directive:SHARE"
    And the field at "/items/0/holdingPack/packId" is "beta"

  Scenario: A qualified reference naming a pack absent from the compose refuses the composed read
    The refusal is the compose's, raised when it resolves (FR-017): the references read is refused with the same
    pack_reference_pack_missing as the offering, naming alpha, the pack that wrote the reference. The pack-scoped read
    of the same directive still answers.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/USE.directive.yaml":
      """
      schema_version: '1.0'
      id: USE
      title: Use the shared load
      intent: Use it
      enforcement: required
      references:
      - type: directive
        id: ghost-pack::SHARE
      """
    When I GET "/packs/alpha/artifacts/directive/USE/references"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) writes the reference `ghost-pack::directive:SHARE`, but the compose holds no pack named `ghost-pack`"
    And the field at "/packs/0/name" is "alpha-pack"
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/alpha/artifacts/directive/USE"
    Then the response status is 200

  Scenario: A bare cross-pack reference in a tactic's typed references is refused naming the holding pack
    The same refusal as above (FR-015), through the other carrier of a reference: alpha's tactic
    references directive SHARE bare, and only beta holds it. One check path serves both carriers.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the org pack "alpha" with the file "uses-share.tactic.yaml":
      """
      id: uses-share
      schema_version: '1.0'
      name: Uses share
      steps:
        - title: Only step
      references:
        - name: Share
          type: directive
          id: SHARE
          when: always
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_unqualified"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943R) writes the bare reference `directive:SHARE`, which resolves only in pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S); a cross-pack reference is written pack-qualified"
    And the field at "/packs/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943R"
    And the field at "/packs/0/packId" is "alpha"
    And the field at "/packs/0/name" is "alpha-pack"
    And the field at "/packs/1/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/packId" is "beta"
    And the field at "/packs/1/name" is "beta-pack"

  Scenario: A pack-qualified typed reference inside the closure resolves
    The control of the typed carrier (FR-013, FR-017): the same tactic writes the reference
    pack-qualified, and alpha declares depends_on beta, so the compose is served, beta before alpha.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the org pack "alpha" with the file "uses-share.tactic.yaml":
      """
      id: uses-share
      schema_version: '1.0'
      name: Uses share
      steps:
        - title: Only step
      references:
        - name: Share
          type: directive
          id: beta-pack::SHARE
          when: always
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
