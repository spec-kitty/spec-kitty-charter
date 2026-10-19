@contract @provisional
Feature: Action-scoped context
  The guidance for one action of one mission type: the artifacts one scope edge away from the
  action node and those one scope edge away from the mission type node, each artifact once. The
  resolution rules are provisional: the conformance corpus decides them (issue #9).

  Background:
    Given the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000FG
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
      """
    And the built-in pack "built-in" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      edges:
      - source: mission_type:software-dev
        target: action:software-dev/plan
        relation: requires
      - source: mission_type:software-dev
        target: directive:DIRECTIVE_001
        relation: scope
      - source: mission_type:software-dev
        target: directive:DIRECTIVE_010
        relation: scope
      """
    And the built-in pack "built-in" with the file "action.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges:
      - source: action:software-dev/plan
        target: directive:DIRECTIVE_010
        relation: scope
      - source: action:software-dev/plan
        target: tactic:adr-drafting-workflow
        relation: scope
      - source: action:software-dev/plan
        target: tactic:premortem
        relation: suggests
      """
    And the built-in pack "built-in" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: directive:DIRECTIVE_001
        kind: directive
        label: Architectural Integrity Standard
      - urn: directive:DIRECTIVE_010
        kind: directive
        label: Specification Fidelity Requirement
      edges: []
      """
    And the built-in pack "built-in" with the file "tactic.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: tactic:adr-drafting-workflow
        kind: tactic
        label: ADR Drafting Workflow
      - urn: tactic:premortem
        kind: tactic
        label: Premortem
      edges: []
      """

  Scenario: The scope of the action and of its mission type is answered once each, sorted by URN
    The order is core's: entries are sorted by their full URN string, <kind>:<id>, as core sorts
    the resolved artifact URNs of an action. An artifact both nodes scope keeps its action entry.
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And the response body is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "via": "action:software-dev/plan"},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "via": "action:software-dev/plan"}
        ]
      }
      """

  Scenario: A directive scoped by both an action and its mission type is answered once
    The built-in graph carries this case: DIRECTIVE_042 is scoped by action:documentation/generate
    and by mission_type:documentation. Core tolerates it in the graph, so it is no refusal.
    Given the built-in pack "built-in" with the file "documentation.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:documentation
        kind: mission_type
        label: documentation
      - urn: action:documentation/generate
        kind: action
        label: generate
      - urn: directive:DIRECTIVE_042
        kind: directive
        label: Documentation Directive
      edges:
      - source: mission_type:documentation
        target: directive:DIRECTIVE_042
        relation: scope
      - source: action:documentation/generate
        target: directive:DIRECTIVE_042
        relation: scope
      """
    When I GET "/mission-types/documentation/actions/generate/context"
    Then the response status is 200
    And the response body is:
      """
      {
        "action": "action:documentation/generate",
        "missionType": "mission_type:documentation",
        "guidance": [
          {"urn": "directive:DIRECTIVE_042", "kind": "directive", "label": "Documentation Directive", "relation": "scope", "via": "action:documentation/generate"}
        ]
      }
      """

  Scenario: A scope edge to a node the graph does not declare is answered without a label
    Provisional: core omits such an artifact; the conformance corpus decides (issue #9).
    Given the built-in pack "built-in" with the file "review.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/review
        kind: action
        label: review
      edges:
      - source: action:software-dev/review
        target: styleguide:undeclared-guide
        relation: scope
      """
    When I GET "/mission-types/software-dev/actions/review/context"
    Then the response status is 200
    And the response body is:
      """
      {
        "action": "action:software-dev/review",
        "missionType": "mission_type:software-dev",
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "via": "mission_type:software-dev"},
          {"urn": "styleguide:undeclared-guide", "kind": "styleguide", "label": null, "relation": "scope", "via": "action:software-dev/review"}
        ]
      }
      """

  Scenario: An action that is not a node of the graph is refused, not answered empty
    Provisional: core falls back to its non-bootstrap context here; the shadow comparison maps
    this 404 to that fallback (roadmap step 7), and the conformance corpus decides (issue #9).
    When I GET "/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "action_not_found"

  Scenario: An unknown mission type is refused
    When I GET "/mission-types/gardening/actions/plan/context"
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: A context read over a pack whose graph shard is malformed is refused
    Given the built-in pack "built-in" with the file "broken.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      surprise: true
      """
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 500
    And the problem code is "drg_schema_error"
