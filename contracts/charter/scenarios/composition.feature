@contract @composition
Feature: The compose resolves the configured packs' dependency graph
  One compose is one depends_on closure at one level (ADR-0048 decision 6): GET /offering answers
  the closure in the resolver's order, dependencies before the packs that need them, each pack once
  (FR-019), and every entry carries the dependencies its pack.yaml declared, each with the range it
  declared and the version of the depended-on pack the compose resolved for it (FR-020, NFR-002).
  A graph the resolver cannot serve refuses the whole offering, never a partial compose: the refusal
  names the pack that broke the graph and why (ADR-0048 decision 10, ADR-0053 decisions 4, 7, 8 and
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
          {"packId": "left", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943S", "level": "org", "name": "left-pack", "version": "2.0.0", "dependsOn": [{"packId": "01M4P01D5TWB5FKZGNNQG3943R", "range": ">=1.0,<2", "version": "1.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/left/artifacts"}}},
          {"packId": "right", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943T", "level": "org", "name": "right-pack", "version": "3.0.0", "dependsOn": [{"packId": "01M4P01D5TWB5FKZGNNQG3943R", "range": ">=1.0", "version": "1.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/right/artifacts"}}},
          {"packId": "top", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943V", "level": "org", "name": "top-pack", "version": "4.0.0", "dependsOn": [{"packId": "01M4P01D5TWB5FKZGNNQG3943S", "range": ">=2.0", "version": "2.0.0"}, {"packId": "01M4P01D5TWB5FKZGNNQG3943T", "range": "<4.0", "version": "3.0.0"}], "_links": {"artifacts": {"href": "/api/v1/packs/top/artifacts"}}}
        ],
        "_links": {"self": {"href": "/api/v1/offering"}}
      }
      """

  Scenario: A descriptor that declares no depends_on resolves exactly as today
    The negative control of the whole family (User Story 1, scenario 3): a descriptor without
    depends_on changes nothing but the new member, which the read always computes.
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    When I GET "/offering"
    Then the response status is 200
    And the response body is:
      """
      {"packs": [{"packId": "qa", "descriptorPackId": "01M4P01D5TWB5FKZGNNQG3943R", "level": "org", "name": "qa-pack", "version": "1.0.0", "dependsOn": [], "_links": {"artifacts": {"href": "/api/v1/packs/qa/artifacts"}}}], "_links": {"self": {"href": "/api/v1/offering"}}}
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
    Then the response status is 422
    And the problem code is "pack_dependency_cycle"
    And the field "detail" of the response body is "the dependency graph contains a cycle at 01M4P01D5TWB5FKZGNNQG3943R"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A chain with no cycle resolves, the dependency first
    The negative control of the cycle refusal: the same two packs with one edge instead of two.
    The dependency comes before the pack that needs it even though its pack_id sorts after the
    dependent's, so the order is the resolution's, not the directory listing's.
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
    Then the response status is 422
    And the problem code is "pack_dependency_unknown"
    And the field "detail" of the response body is "pack 01M4P01D5TWB5FKZGNNQG3943R depends on 01M4P01D5TWB5FKZGNNQG3943X, which no configured pack declares"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A declared dependency a configured pack declares resolves
    The negative control of the unknown-dependency refusal: the same descriptor with the target
    configured, in range. The entry carries the resolved edge: the target's pack_id, the declared
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
    And the field at "/packs/1/dependsOn/0/packId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/dependsOn/0/range" is ">=1.0.0"
    And the field at "/packs/1/dependsOn/0/version" is "1.0.0"

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
    Then the response status is 422
    And the problem code is "pack_dependency_out_of_range"
    And the field "detail" of the response body is "pack 01M4P01D5TWB5FKZGNNQG3943R requires 01M4P01D5TWB5FKZGNNQG3943S in range ==2.0.0, and the held version is 1.9.0"
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
    Then the response status is 422
    And the problem code is "pack_parent_unresolved"
    And the field "detail" of the response body is "pack 01M4P01D5TWB5FKZGNNQG3943R names parent_pack 01M4P01D5TWB5FKZGNNQG3943X, which no configured pack declares"
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
    A pack may depend only on its own level or a lower one (ADR-0048 decision 6), so an org pack
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
    Then the response status is 422
    And the problem code is "pack_level_violation"
    And the field "detail" of the response body is "pack 01M4P01D5TWB5FKZGNNQG3943R at org depends on 01M4P01D5TWB5FKZGNNQG3943W at project; a pack may depend only on its own level or a lower one"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A project pack depending on an org pack resolves
    The negative control of the level-violation refusal: the same two packs with the dependency
    pointing downward, project onto org. The org pack is answered first.
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
    Then the response status is 422
    And the problem code is "pack_name_conflict"
    And the field "detail" of the response body is "pack name shared-house-rules is declared by packs 01M4P01D5TWB5FKZGNNQG3943R and 01M4P01D5TWB5FKZGNNQG3943S"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Two packs with distinct names resolve
    The negative control of the name-conflict refusal: the same two configured packs, each with its
    own name, served in the stable order.
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
    Then the response status is 422
    And the problem code is "pack_duplicate_id"
    And the field "detail" of the response body is "pack_id 01M4P01D5TWB5FKZGNNQG3943R is declared by two configured packs"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Two configured packs with distinct pack_ids resolve
    The negative control of the duplicate-id refusal: the same two configured packs, each declaring
    its own id.
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
    conflict must be a decision, not load order, so the compose is refused (409) naming both packs
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
    Then the response status is 409
    And the problem code is "pack_sibling_refinement_conflict"
    And the field "detail" of the response body is "packs 01M4P01D5TWB5FKZGNNQG3943R and 01M4P01D5TWB5FKZGNNQG3943S at org both change the artifact directive:software-dev/test-first, and no pack of the compose declares an overrides edge settling it"
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
    Then the response status is 422
    And the problem code is "pack_reference_unqualified"
    And the field "detail" of the response body is "the bare reference directive:SHARE of pack 01M4P01D5TWB5FKZGNNQG3943R resolves only in pack beta-pack; a cross-pack reference is written pack-qualified"
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
    Then the response status is 422
    And the problem code is "pack_reference_pack_missing"
    And the field "detail" of the response body is "the reference beta-pack::directive:SHARE of pack 01M4P01D5TWB5FKZGNNQG3943R names pack beta-pack, which is outside the depends_on closure of pack 01M4P01D5TWB5FKZGNNQG3943R"
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
    Then the response status is 422
    And the problem code is "pack_reference_unqualified"
    And the field "detail" of the response body is "the bare reference directive:SHARE of pack 01M4P01D5TWB5FKZGNNQG3943R resolves only in pack beta-pack; a cross-pack reference is written pack-qualified"

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
