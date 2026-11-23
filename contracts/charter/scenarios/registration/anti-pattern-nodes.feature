# The anti-pattern node at registration (ADR-0052 decisions 11 and 14, ADR-0053 decision 31; #154; mission
# pack-doctor-coverage-01M7YMQK, WP11, FR-014). An anti-pattern is held by its file,
# anti_patterns/<id>.anti-pattern.yaml; an anti_pattern node in a graph document of the same pack only declares it. A
# pack whose shard or fragment declares an anti_pattern node with no file is refused drg_schema_error at the node, as a
# node of any kind a pack may not declare is. pack-doctor validate answers the same code, pointer and detail for the
# same planted node (AntiPatternNodeCheckTest, ADR-0075 decision 9). A registration violation names the document as its
# pointer; the node's place, /nodes/<n>, is the finding's pointer within it, which pack-doctor reports.
#
# Another pack's anti-pattern is no node of the rejecting pack: a rejects edge names it as a pack-qualified endpoint,
# which needs the target's pack in the rejecting pack's depends_on closure (ADR-0052 decision 11). The compose judges
# that reference (pack_reference_pack_missing), so the last two scenarios stage two packs with the org-pack steps and
# read GET /offering, as composition.feature does.
#
# Steps: the existing whole-pack registration steps and the org-pack compose steps only (scenarios/README.md).

@contract @registration
Feature: An anti_pattern node needs its file in the same pack, and a rejects edge to another pack needs depends_on
  A graph node of the anti_pattern kind declares an anti-pattern the pack holds as a file. A node with no file refuses
  the pack, which then stores nothing; the same node beside its file registers. A rejects edge to another pack's
  anti-pattern is refused unless that pack is in the rejecting pack's depends_on closure.

  Background:
    Given registration is switched on
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: The directive that stays
      enforcement: required
      """
    And the archive "v1" holds the file "anti_patterns/x.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: x
      title: X
      description: The anti-pattern the graph node declares.
      severity: smell
      bad_example: the bad example
      """
    And the archive "v1" holds the file "styleguide.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: anti_pattern:x
        kind: anti_pattern
        label: X
      edges:
      - source: directive:A
        target: anti_pattern:x
        relation: rejects
      """

  Scenario: An anti_pattern node beside its file registers
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/diagnostics" holds exactly 0 item(s)

  Scenario: A shard anti_pattern node with no file refuses the pack at the node
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" lacks the file "anti_patterns/x.anti-pattern.yaml"
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "drg_schema_error"
    And the field at "/problem/violations/0/pointer" is "styleguide.graph.yaml"
    And the field at "/problem/violations/0/detail" is "styleguide.graph.yaml: anti_pattern:x is declared with no file; an anti-pattern is held by anti_patterns/x.anti-pattern.yaml in the same pack"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario: A fragment anti_patterns node with no file refuses the pack at the node
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" lacks the file "anti_patterns/x.anti-pattern.yaml"
    And the archive "bad" lacks the file "styleguide.graph.yaml"
    And the archive "bad" holds the file "drg/fragment.yaml":
      """
      nodes:
      - {id: x, kind: anti_patterns, title: X}
      edges: []
      """
    When I PUT "/packs/acme" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "drg_schema_error"
    And the field at "/problem/violations/0/pointer" is "drg/fragment.yaml"
    And the field at "/problem/violations/0/detail" is "drg/fragment.yaml: anti_pattern:x is declared with no file; an anti-pattern is held by anti_patterns/x.anti-pattern.yaml in the same pack"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario: A rejects edge to another pack's anti-pattern outside the depends_on closure refuses the compose
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "anti_patterns/x.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: x
      title: X
      description: The anti-pattern another pack rejects.
      severity: smell
      bad_example: the bad example
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: The directive that rejects
      enforcement: required
      """
    And the org pack "alpha" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:A
        target: beta-pack::anti_pattern:x
        relation: rejects
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field "detail" of the response body is "pack `alpha-pack` (01M4P01D5TWB5FKZGNNQG3943T) writes the reference `beta-pack::anti_pattern:x`, but pack `beta-pack` (01M4P01D5TWB5FKZGNNQG3943S) is outside its depends_on closure"

  Scenario: A rejects edge to another pack's anti-pattern inside the depends_on closure resolves
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "anti_patterns/x.anti-pattern.yaml":
      """
      schema_version: '1.0'
      id: x
      title: X
      description: The anti-pattern another pack rejects.
      severity: smell
      bad_example: the bad example
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "alpha" with the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: The directive that rejects
      enforcement: required
      """
    And the org pack "alpha" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:A
        target: beta-pack::anti_pattern:x
        relation: rejects
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
