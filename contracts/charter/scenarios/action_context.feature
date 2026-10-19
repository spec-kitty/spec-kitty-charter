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
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "impacts": null, "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"}
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
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:documentation/generate",
        "missionType": "mission_type:documentation",
        "depth": 1,
        "guidance": [
          {"urn": "directive:DIRECTIVE_042", "kind": "directive", "label": "Documentation Directive", "relation": "scope", "impacts": null, "via": "action:documentation/generate"}
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
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/review",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "impacts": null, "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "mission_type:software-dev"},
          {"urn": "styleguide:undeclared-guide", "kind": "styleguide", "label": null, "relation": "scope", "impacts": null, "via": "action:software-dev/review"}
        ]
      }
      """

  Scenario: An action that is not a node of the graph is refused, not answered empty
    Provisional: core falls back to its non-bootstrap context here; the shadow comparison maps
    this 404 to that fallback (roadmap step 7), and the conformance corpus decides (issue #9).
    When I GET "/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "action_not_found"
    And the response body is:
      """
      {"title": "Action not found", "status": 404, "code": "action_not_found", "detail": "action:software-dev/deploy is not a node of the merged graph."}
      """

  Scenario: An unknown mission type is refused
    When I GET "/mission-types/gardening/actions/plan/context"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    And the response body is:
      """
      {"title": "Mission type not found", "status": 404, "code": "mission_type_not_found", "detail": "mission_type:gardening is not a node of the merged graph."}
      """

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
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "built-in/broken.graph.yaml: unknown key 'surprise'"}
      """

  Scenario: The depth of the walk is echoed, and this version answers depth 1 only
    A later version raises the maximum depth, which is compatible: a request that asks for depth
    1 keeps its answer.
    When I GET "/mission-types/software-dev/actions/plan/context?depth=1"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "impacts": null, "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"}
        ]
      }
      """

  Scenario: A depth this version does not answer is a bad request, not a charter refusal
    When I GET "/mission-types/software-dev/actions/plan/context?depth=2"
    Then the response status is 400
    And the response body is:
      """
      {"title": "Bad Request", "status": 400}
      """

  Scenario: The governance fingerprint is the same while the governance is, and changes with it
    The fingerprint digests the packs in force and the merged graph; it does not depend on the
    order a document declares its nodes and edges in.
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the field "governanceFingerprint" of the response body is a sha256 digest
    When I remember the field "governanceFingerprint" of the response body
    And I GET "/mission-types/software-dev/actions/plan/context"
    Then the field "governanceFingerprint" of the response body is the remembered one
    Given the built-in pack "built-in" with the file "tension.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:DIRECTIVE_001
        target: directive:DIRECTIVE_010
        relation: impacts
        impacts: -0.5
      """
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And the field "governanceFingerprint" of the response body differs from the remembered one

  Scenario: An impacts edge brings nothing into scope
    An impacts edge records how one artifact affects another, with a signed strength; only scope
    edges bring an artifact into an action's guidance. A legacy in_tension_with edge is read as
    impacts with strength -1 until core migrates its graphs.
    Given the built-in pack "built-in" with the file "tension.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: action:software-dev/plan
        target: tactic:premortem
        relation: impacts
        impacts: 0.75
      - source: directive:DIRECTIVE_001
        target: directive:DIRECTIVE_010
        relation: in_tension_with
      """
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "guidance": [
          {"urn": "directive:DIRECTIVE_001", "kind": "directive", "label": "Architectural Integrity Standard", "relation": "scope", "impacts": null, "via": "mission_type:software-dev"},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan"}
        ]
      }
      """

  Scenario: An impacts edge without a strength in [-1, 1] is refused
    Given the built-in pack "built-in" with the file "tension.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: directive:DIRECTIVE_001
        target: directive:DIRECTIVE_010
        relation: impacts
        impacts: -2
      """
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "built-in/tension.graph.yaml: 'impacts' -2.0 is outside [-1, 1]"}
      """

  Scenario: A strength on an edge that is not impacts is refused
    Given the built-in pack "built-in" with the file "tension.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: action:software-dev/plan
        target: directive:DIRECTIVE_001
        relation: scope
        impacts: 0.5
      """
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "built-in/tension.graph.yaml: a scope edge has no 'impacts'"}
      """
