Feature: Action-scoped context
  The guidance for one action of one mission type: the artifacts one scope edge away from the
  action node, then those one scope edge away from the mission type node.

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

  Scenario: Action scope comes first, then mission-type scope, without repeats
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And the response body is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "guidance": [
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "via": "action:software-dev/plan"},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "via": "action:software-dev/plan"},
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "via": "mission_type:software-dev"}
        ]
      }
      """

  Scenario: An action the mission type does not have is refused, not answered empty
    When I GET "/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "ACTION_NOT_FOUND"

  Scenario: An unknown mission type is refused
    When I GET "/mission-types/gardening/actions/plan/context"
    Then the response status is 404
    And the problem code is "MISSION_TYPE_NOT_FOUND"
