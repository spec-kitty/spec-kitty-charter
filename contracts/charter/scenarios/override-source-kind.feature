@contract @registration
Feature: An overrides edge comes from an activatable artifact
  An overrides edge says that one governance artifact stands in for another (rule C27). Only an artifact of an
  activatable kind can stand in: a template, an asset, a guard or a lifecycle has no activation state (rule D16).
  The operator ruled on #447 on 2026-11-12 (option 1, FR-014): an overrides edge whose source is not of an
  activatable kind is refused wherever it can enter. Then the read, which drops such an overrider, and the
  deactivation, which finds no satisfier in it, can never disagree.

  - A registration lists one violation per such edge inside 422 validation_failed. Each violation has the code
    overrides_source_not_activatable and the pack-relative graph document as its pointer.
  - putPackRelationship answers 422 validation_failed with the same violation code at /from and writes nothing.
  - A composing read of a closure that holds such a pack answers 500 pack_unreadable and names the graph document.

  The rule judges the source kind only. An overrides edge from an activatable kind is unchanged, and so is an edge
  of another relation from a template. Each refusal sits beside its passing twin, which differs only in the source
  of the edge.

  Every fixture is two org packs: a base pack holds a directive, and a second pack depends on it and holds another
  directive and the template t.md. The registrations use the archives obase (directive D) and ovr (directive E); the
  relationship writes and the reads plant pbase (directive P) and povr (directive F) below the service, with their own
  ids, so that they never conflict with the packs the background registers.

  Background:
    Given registration is switched on
    And the archive "obase" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000QB
      pack_version: 1.0.0
      parent_pack: null
      name: obase
      pack_level: org
      """
    And the archive "obase" holds the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: D
      enforcement: required
      """
    And the archive "ovr" holds the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000QV
      pack_version: 1.0.0
      parent_pack: null
      name: ovr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000QB
        range: '>=1.0'
      """
    And the archive "ovr" holds the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: E
      enforcement: required
      """
    And the archive "ovr" holds the file "templates/t.md":
      """
      # A template
      """
    And the archive "ovr" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:E
        target: obase::directive:D
        relation: overrides
      """
    When I PUT "/packs/obase" with the archive "obase"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-014: the overrides edge from the template is one violation at its graph document.
  Scenario: A registration whose graph document has an overrides edge from a template is refused, beside the pack whose edge comes from a directive
    Given the archive "bad" is a copy of the archive "ovr"
    And the archive "bad" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: template:t.md
        target: obase::directive:D
        relation: overrides
      """
    When I PUT "/packs/ovr" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "overrides_source_not_activatable"
    And the field at "/problem/violations/0/pointer" is "overrides.graph.yaml"
    And the field at "/problem/violations/0/detail" contains "template:t.md"
    When I PUT "/packs/ovr" with the archive "ovr"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-014: one violation per edge. A source written with the pack's own name is judged like a bare one.
  Scenario: Two overrides edges from the template, one of them qualified with the pack's name, are two violations
    Given the archive "bad" is a copy of the archive "ovr"
    And the archive "bad" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: template:t.md
        target: obase::directive:D
        relation: overrides
      - source: ovr::template:t.md
        target: directive:E
        relation: overrides
      """
    When I PUT "/packs/ovr" with the archive "bad"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 2 item(s)
    And the field at "/problem/violations/0/code" is "overrides_source_not_activatable"
    And the field at "/problem/violations/0/pointer" is "overrides.graph.yaml"
    And the field at "/problem/violations/1/code" is "overrides_source_not_activatable"
    And the field at "/problem/violations/1/pointer" is "overrides.graph.yaml"

  # C27: the rule judges the overrides relation only; a template may still be the source of another relation.
  Scenario: A requires edge from the template is registered
    Given the archive "requires" is a copy of the archive "ovr"
    And the archive "requires" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: template:t.md
        target: obase::directive:D
        relation: requires
      """
    When I PUT "/packs/ovr" with the archive "requires"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-014 on the relationship write: validation_failed with the violation at /from, and nothing written. The read of
  # the pack keeps its ETag.
  Scenario: putPackRelationship of an overrides edge from a template is refused and nothing is written
    Given the org pack "pbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PB
      pack_version: 1.0.0
      parent_pack: null
      name: pbase
      pack_level: org
      """
    And the org pack "pbase" with the file "directives/P.directive.yaml":
      """
      schema_version: '1.0'
      id: P
      title: P
      intent: P
      enforcement: required
      """
    And the org pack "povr" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PV
      pack_version: 1.0.0
      parent_pack: null
      name: povr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000PB
        range: '>=1.0'
      """
    And the org pack "povr" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "povr" with the file "templates/t.md":
      """
      # A template
      """
    When I GET "/packs/povr/artifacts/directive/F"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/povr/relationships" with the remembered ETag and the body:
      """
      {"from": "template:t.md", "to": "pbase::directive:P", "type": "overrides"}
      """
    Then the response status is 422
    And the field at "/code" is "validation_failed"
    And the field at "/violations" holds exactly 1 item(s)
    And the field at "/violations/0/code" is "overrides_source_not_activatable"
    And the field at "/violations/0/pointer" is "/from"
    And the field at "/violations/0/detail" contains "template:t.md"
    When I GET "/packs/povr/artifacts/directive/F"
    Then the response header "ETag" is the remembered header

  # The twin of the relationship refusal: the same edge from the directive is registered.
  Scenario: putPackRelationship of the same overrides edge from a directive is registered
    Given the org pack "pbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PB
      pack_version: 1.0.0
      parent_pack: null
      name: pbase
      pack_level: org
      """
    And the org pack "pbase" with the file "directives/P.directive.yaml":
      """
      schema_version: '1.0'
      id: P
      title: P
      intent: P
      enforcement: required
      """
    And the org pack "povr" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PV
      pack_version: 1.0.0
      parent_pack: null
      name: povr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000PB
        range: '>=1.0'
      """
    And the org pack "povr" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "povr" with the file "templates/t.md":
      """
      # A template
      """
    When I GET "/packs/povr/artifacts/directive/F"
    Then the response status is 200
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/packs/povr/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:F", "to": "pbase::directive:P", "type": "overrides"}
      """
    Then the response status is 201
    When I GET "/packs/povr/artifacts/directive/F"
    Then the response header "ETag" differs from the remembered header

  # FR-014 on a composing read: a stored pack that holds the edge, planted below the service, is unreadable and the
  # refusal names the graph document.
  Scenario: A closure holding a pack with an overrides edge from a template is unreadable and names the graph document
    Given the org pack "pbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PB
      pack_version: 1.0.0
      parent_pack: null
      name: pbase
      pack_level: org
      """
    And the org pack "pbase" with the file "directives/P.directive.yaml":
      """
      schema_version: '1.0'
      id: P
      title: P
      intent: P
      enforcement: required
      """
    And the org pack "povr" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PV
      pack_version: 1.0.0
      parent_pack: null
      name: povr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000PB
        range: '>=1.0'
      """
    And the org pack "povr" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "povr" with the file "templates/t.md":
      """
      # A template
      """
    And the org pack "povr" with the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: template:t.md
        target: pbase::directive:P
        relation: overrides
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "overrides.graph.yaml"

  # The 200 beside the 500 above: the closure differs only in the source of the edge.
  Scenario: The positive control: the same closure with the overrides edge from the directive is read
    Given the org pack "pbase" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PB
      pack_version: 1.0.0
      parent_pack: null
      name: pbase
      pack_level: org
      """
    And the org pack "pbase" with the file "directives/P.directive.yaml":
      """
      schema_version: '1.0'
      id: P
      title: P
      intent: P
      enforcement: required
      """
    And the org pack "povr" with the file "pack.yaml":
      """
      pack_id: 01M6ZRYK0000000000000000PV
      pack_version: 1.0.0
      parent_pack: null
      name: povr
      pack_level: org
      depends_on:
      - pack_id: 01M6ZRYK0000000000000000PB
        range: '>=1.0'
      """
    And the org pack "povr" with the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: F
      enforcement: required
      """
    And the org pack "povr" with the file "templates/t.md":
      """
      # A template
      """
    And the org pack "povr" with the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:F
        target: pbase::directive:P
        relation: overrides
      """
    When I GET "/offering"
    Then the response status is 200
