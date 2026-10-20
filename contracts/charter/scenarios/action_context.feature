@contract @provisional
Feature: Action-scoped context
  The offering's walk for one action of one mission type: what the merged graph says governs the
  action, each artifact once. Five steps, each breadth-first and cycle-safe:

  1. scope: the artifacts the action node scopes, one hop. The mission type node's scope edges
     are not walked: they are the entry point of the activation cascade, which a later operation
     owns (issue #103).
  2. requires: the scoped artifacts' hard dependencies, transitively.
  3. suggests: from the scoped artifacts only, up to "depth" suggests hops. An artifact that a
     scoped artifact reaches through requires is not a seed, as in core.
  4. requires of suggested: the hard dependencies of the suggested artifacts, transitively. The
     suggested artifacts' own suggestions are not walked, so "depth" bounds the answer.
  5. vocabulary: one hop from everything listed.

  A step never lists an artifact an earlier step listed, but still walks on through it. Each
  entry carries its relation, its parent "via" (the action node, or another entry), and "hops":
  how many edges away from the action it is. The resolution rules are provisional: the
  conformance corpus decides them (issue #9).

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
      """
    # The mission type scopes DIRECTIVE_001 and nothing else reaches it: no scenario below may list it.
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
        target: tactic:cycle-a
        relation: scope
      """
    # DIRECTIVE_010 is scoped and required (scope wins). writing-style is required and suggested (requires wins).
    And the built-in pack "built-in" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: directive:DIRECTIVE_001
        kind: directive
        label: Architectural Integrity Standard
      - urn: directive:DIRECTIVE_003
        kind: directive
        label: Decision Documentation Requirement
      - urn: directive:DIRECTIVE_010
        kind: directive
        label: Specification Fidelity Requirement
      - urn: directive:DIRECTIVE_030
        kind: directive
        label: Test and Typecheck Quality Gate
      edges:
      - source: directive:DIRECTIVE_010
        target: styleguide:writing-style
        relation: suggests
      - source: directive:DIRECTIVE_010
        target: tactic:tie-target
        relation: suggests
      - source: directive:DIRECTIVE_003
        target: styleguide:writing-style
        relation: requires
      - source: directive:DIRECTIVE_003
        target: tactic:review-checklist
        relation: suggests
      - source: directive:DIRECTIVE_003
        target: glossary_pack:architecture-terms
        relation: vocabulary
      - source: directive:DIRECTIVE_030
        target: styleguide:risk-log
        relation: requires
      """
    # A diamond: x-shared is one suggests hop from adr-drafting-workflow and two through premortem.
    # DIRECTIVE_003 is required and suggests review-checklist: only scoped artifacts seed suggests, so it is never listed.
    And the built-in pack "built-in" with the file "tactic.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: tactic:adr-drafting-workflow
        kind: tactic
        label: ADR Drafting Workflow
      - urn: tactic:cycle-a
        kind: tactic
        label: Cycle A
      - urn: tactic:cycle-b
        kind: tactic
        label: Cycle B
      - urn: tactic:premortem
        kind: tactic
        label: Premortem
      - urn: tactic:x-shared
        kind: tactic
        label: Shared Suggestion
      - urn: tactic:z-child
        kind: tactic
        label: Suggestion Child
      - urn: tactic:tie-target
        kind: tactic
        label: Tie Target
      - urn: tactic:hops-tie
        kind: tactic
        label: Hops Tie
      - urn: tactic:review-checklist
        kind: tactic
        label: Review Checklist
      edges:
      - source: tactic:adr-drafting-workflow
        target: directive:DIRECTIVE_010
        relation: requires
      - source: tactic:adr-drafting-workflow
        target: directive:DIRECTIVE_003
        relation: requires
      - source: tactic:adr-drafting-workflow
        target: tactic:premortem
        relation: suggests
      - source: tactic:adr-drafting-workflow
        target: tactic:x-shared
        relation: suggests
      - source: tactic:adr-drafting-workflow
        target: tactic:tie-target
        relation: suggests
      - source: tactic:adr-drafting-workflow
        target: tactic:hops-tie
        relation: suggests
      - source: tactic:cycle-a
        target: tactic:cycle-b
        relation: requires
      - source: tactic:cycle-b
        target: tactic:cycle-a
        relation: requires
      - source: tactic:premortem
        target: directive:DIRECTIVE_030
        relation: requires
      - source: tactic:premortem
        target: tactic:x-shared
        relation: suggests
      - source: tactic:x-shared
        target: tactic:z-child
        relation: suggests
      """
    And the built-in pack "built-in" with the file "styleguide.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: styleguide:writing-style
        kind: styleguide
        label: Writing Style
      - urn: styleguide:risk-log
        kind: styleguide
        label: Risk Log
      edges:
      - source: styleguide:writing-style
        target: tactic:hops-tie
        relation: suggests
      - source: styleguide:risk-log
        target: glossary_pack:risk-terms
        relation: vocabulary
      """
    And the built-in pack "built-in" with the file "glossary.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: glossary_pack:architecture-terms
        kind: glossary_pack
        label: Architecture Terms
      - urn: glossary_pack:risk-terms
        kind: glossary_pack
        label: Risk Terms
      edges: []
      """

  Scenario: Depth counts suggests hops; scope and requires do not depend on it
    At depth 0 no suggested artifact is listed: not premortem, and not what a suggested artifact
    requires or its vocabulary. At depth 1 the artifacts one suggests hop from a scoped artifact are
    listed, with the requires of the suggested (premortem requires DIRECTIVE_030, and so on), but not
    what a required artifact suggests (DIRECTIVE_003 suggests review-checklist). At depth 2
    the walk reaches z-child, a child of x-shared, which is one hop from adr-drafting-workflow and
    two through premortem: the walk is breadth-first, so the shorter path counts and z-child is not
    hidden. DIRECTIVE_001, scoped by the mission type only, is never listed.
    When I GET "/mission-types/software-dev/actions/plan/context?depth=0"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 0,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2}
        ]
      }
      """
    When I GET "/mission-types/software-dev/actions/plan/context?depth=1"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "directive:DIRECTIVE_030", "kind": "directive", "label": "Test and Typecheck Quality Gate", "relation": "requires", "impacts": null, "via": "tactic:premortem", "hops": 3},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "glossary_pack:risk-terms", "kind": "glossary_pack", "label": "Risk Terms", "relation": "vocabulary", "impacts": null, "via": "styleguide:risk-log", "hops": 5},
          {"urn": "styleguide:risk-log", "kind": "styleguide", "label": "Risk Log", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_030", "hops": 4},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2},
          {"urn": "tactic:hops-tie", "kind": "tactic", "label": "Hops Tie", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:premortem", "kind": "tactic", "label": "Premortem", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:tie-target", "kind": "tactic", "label": "Tie Target", "relation": "suggests", "impacts": null, "via": "directive:DIRECTIVE_010", "hops": 2},
          {"urn": "tactic:x-shared", "kind": "tactic", "label": "Shared Suggestion", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2}
        ]
      }
      """
    When I GET "/mission-types/software-dev/actions/plan/context?depth=2"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "directive:DIRECTIVE_030", "kind": "directive", "label": "Test and Typecheck Quality Gate", "relation": "requires", "impacts": null, "via": "tactic:premortem", "hops": 3},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "glossary_pack:risk-terms", "kind": "glossary_pack", "label": "Risk Terms", "relation": "vocabulary", "impacts": null, "via": "styleguide:risk-log", "hops": 5},
          {"urn": "styleguide:risk-log", "kind": "styleguide", "label": "Risk Log", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_030", "hops": 4},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2},
          {"urn": "tactic:hops-tie", "kind": "tactic", "label": "Hops Tie", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:premortem", "kind": "tactic", "label": "Premortem", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:tie-target", "kind": "tactic", "label": "Tie Target", "relation": "suggests", "impacts": null, "via": "directive:DIRECTIVE_010", "hops": 2},
          {"urn": "tactic:x-shared", "kind": "tactic", "label": "Shared Suggestion", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:z-child", "kind": "tactic", "label": "Suggestion Child", "relation": "suggests", "impacts": null, "via": "tactic:x-shared", "hops": 3}
        ]
      }
      """

  Scenario: Without a depth the walk follows two suggests hops, and says so
    The default depth is 2. The answer echoes the depth the walk followed. A later version may raise
    the request maximum, which is compatible: the echoed depth has no maximum.
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "directive:DIRECTIVE_030", "kind": "directive", "label": "Test and Typecheck Quality Gate", "relation": "requires", "impacts": null, "via": "tactic:premortem", "hops": 3},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "glossary_pack:risk-terms", "kind": "glossary_pack", "label": "Risk Terms", "relation": "vocabulary", "impacts": null, "via": "styleguide:risk-log", "hops": 5},
          {"urn": "styleguide:risk-log", "kind": "styleguide", "label": "Risk Log", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_030", "hops": 4},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2},
          {"urn": "tactic:hops-tie", "kind": "tactic", "label": "Hops Tie", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:premortem", "kind": "tactic", "label": "Premortem", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:tie-target", "kind": "tactic", "label": "Tie Target", "relation": "suggests", "impacts": null, "via": "directive:DIRECTIVE_010", "hops": 2},
          {"urn": "tactic:x-shared", "kind": "tactic", "label": "Shared Suggestion", "relation": "suggests", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "tactic:z-child", "kind": "tactic", "label": "Suggestion Child", "relation": "suggests", "impacts": null, "via": "tactic:x-shared", "hops": 3}
        ]
      }
      """

  Scenario: Requires is followed transitively, and a requires cycle ends
    Chain: adr-drafting-workflow (scoped) requires DIRECTIVE_003, which requires writing-style.
    Cycle: cycle-a requires cycle-b and cycle-b requires cycle-a; each is listed once and the walk
    ends. DIRECTIVE_010 is scoped and also required by adr-drafting-workflow: the scope entry stays.
    When I GET "/mission-types/software-dev/actions/plan/context?depth=0"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 0,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2}
        ]
      }
      """

  Scenario: The action's scope is walked; the mission type's is not
    The mission type scopes DIRECTIVE_001, and that is its activation entry point, not part of this
    walk (core: offering/drg/models.py:205-218; query.py:218-220 seeds from the action node only).
    The first slice of this contract walked it; that rule is withdrawn.
    Given the built-in pack "built-in" with the file "specify.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges:
      - source: action:software-dev/specify
        target: directive:DIRECTIVE_010
        relation: scope
      """
    When I GET "/mission-types/software-dev/actions/specify/context?depth=0"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/specify",
        "missionType": "mission_type:software-dev",
        "depth": 0,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/specify", "hops": 1}
        ]
      }
      """

  Scenario: A suggested artifact reached by a short and a long path keeps the short one
    d-x is one suggests hop from d-root, and two through d-b (declared first). The shorter path
    counts, so d-x is listed at hops 2 via d-root and its child d-z is listed at depth 2.
    Given the built-in pack "built-in" with the file "diamond.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/diamond
        kind: action
        label: diamond
      - urn: tactic:d-root
        kind: tactic
        label: Root
      - urn: tactic:d-b
        kind: tactic
        label: Branch
      - urn: tactic:d-x
        kind: tactic
        label: Shared
      - urn: tactic:d-z
        kind: tactic
        label: Child
      edges:
      - source: action:software-dev/diamond
        target: tactic:d-root
        relation: scope
      - source: tactic:d-root
        target: tactic:d-b
        relation: suggests
      - source: tactic:d-root
        target: tactic:d-x
        relation: suggests
      - source: tactic:d-b
        target: tactic:d-x
        relation: suggests
      - source: tactic:d-x
        target: tactic:d-z
        relation: suggests
      """
    When I GET "/mission-types/software-dev/actions/diamond/context?depth=1"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/diamond",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "diagnostics": [],
        "guidance": [
          {"urn": "tactic:d-b", "kind": "tactic", "label": "Branch", "relation": "suggests", "impacts": null, "via": "tactic:d-root", "hops": 2},
          {"urn": "tactic:d-root", "kind": "tactic", "label": "Root", "relation": "scope", "impacts": null, "via": "action:software-dev/diamond", "hops": 1},
          {"urn": "tactic:d-x", "kind": "tactic", "label": "Shared", "relation": "suggests", "impacts": null, "via": "tactic:d-root", "hops": 2}
        ]
      }
      """
    When I GET "/mission-types/software-dev/actions/diamond/context?depth=2"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/diamond",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "tactic:d-b", "kind": "tactic", "label": "Branch", "relation": "suggests", "impacts": null, "via": "tactic:d-root", "hops": 2},
          {"urn": "tactic:d-root", "kind": "tactic", "label": "Root", "relation": "scope", "impacts": null, "via": "action:software-dev/diamond", "hops": 1},
          {"urn": "tactic:d-x", "kind": "tactic", "label": "Shared", "relation": "suggests", "impacts": null, "via": "tactic:d-root", "hops": 2},
          {"urn": "tactic:d-z", "kind": "tactic", "label": "Child", "relation": "suggests", "impacts": null, "via": "tactic:d-x", "hops": 3}
        ]
      }
      """

  Scenario: What a required artifact suggests is not listed, as in core
    Core walks suggests "from scoped artifacts" (offering/drg/query.py:196-197), so t-sugg, suggested
    only by a required artifact, is not listed. Seeding suggests from the required artifacts too was
    measured at about 30% more governance over open-packs and rejected (research D2). Unlike the
    requires of suggested artifacts below, this is core's intent, so it is no divergence.
    Given the built-in pack "built-in" with the file "implement.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: tactic:t-req
        kind: tactic
        label: Required Holder
      - urn: directive:DIRECTIVE_100
        kind: directive
        label: Hard Dependency
      - urn: tactic:t-sugg
        kind: tactic
        label: Suggested By Dependency
      edges:
      - source: action:software-dev/implement
        target: tactic:t-req
        relation: scope
      - source: tactic:t-req
        target: directive:DIRECTIVE_100
        relation: requires
      - source: directive:DIRECTIVE_100
        target: tactic:t-sugg
        relation: suggests
      """
    When I GET "/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/implement",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_100", "kind": "directive", "label": "Hard Dependency", "relation": "requires", "impacts": null, "via": "tactic:t-req", "hops": 2},
          {"urn": "tactic:t-req", "kind": "tactic", "label": "Required Holder", "relation": "scope", "impacts": null, "via": "action:software-dev/implement", "hops": 1}
        ]
      }
      """

  Scenario: A suggests walk passes through an artifact an earlier step listed
    t-s suggests DIRECTIVE_100, which step 2 already listed as required: it keeps its requires entry,
    but the suggests walk goes on through it, so its suggestion t-c is listed at depth 2 (via
    DIRECTIVE_100, hops 3) and not at depth 1.
    Given the built-in pack "built-in" with the file "through.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/through
        kind: action
        label: through
      - urn: tactic:t-s
        kind: tactic
        label: Scoped
      - urn: directive:DIRECTIVE_100
        kind: directive
        label: Required And Suggested
      - urn: tactic:t-c
        kind: tactic
        label: Child
      edges:
      - source: action:software-dev/through
        target: tactic:t-s
        relation: scope
      - source: tactic:t-s
        target: directive:DIRECTIVE_100
        relation: requires
      - source: tactic:t-s
        target: directive:DIRECTIVE_100
        relation: suggests
      - source: directive:DIRECTIVE_100
        target: tactic:t-c
        relation: suggests
      """
    When I GET "/mission-types/software-dev/actions/through/context?depth=1"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/through",
        "missionType": "mission_type:software-dev",
        "depth": 1,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_100", "kind": "directive", "label": "Required And Suggested", "relation": "requires", "impacts": null, "via": "tactic:t-s", "hops": 2},
          {"urn": "tactic:t-s", "kind": "tactic", "label": "Scoped", "relation": "scope", "impacts": null, "via": "action:software-dev/through", "hops": 1}
        ]
      }
      """
    When I GET "/mission-types/software-dev/actions/through/context?depth=2"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/through",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_100", "kind": "directive", "label": "Required And Suggested", "relation": "requires", "impacts": null, "via": "tactic:t-s", "hops": 2},
          {"urn": "tactic:t-c", "kind": "tactic", "label": "Child", "relation": "suggests", "impacts": null, "via": "directive:DIRECTIVE_100", "hops": 3},
          {"urn": "tactic:t-s", "kind": "tactic", "label": "Scoped", "relation": "scope", "impacts": null, "via": "action:software-dev/through", "hops": 1}
        ]
      }
      """

  Scenario: What a suggested artifact requires is listed, and the walk ends there (intentional difference from core, DIV-110)
    Core lists a suggested artifact without its requires (offering/drg/query.py:226), although its
    own docstring calls requires "hard dependencies" (query.py:194-195): delivering an artifact
    without what it depends on contradicts that. The charter lists the requires of the suggested
    artifacts (step 4), registered as DIV-110. The step-4 artifacts are not walked for suggests: t-c
    is not listed, so depth bounds the answer.
    Given the built-in pack "built-in" with the file "implement.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: tactic:t-a
        kind: tactic
        label: Scoped
      - urn: tactic:t-b
        kind: tactic
        label: Suggested
      - urn: directive:DIRECTIVE_100
        kind: directive
        label: Suggested One's Hard Dependency
      - urn: tactic:t-c
        kind: tactic
        label: Not Walked
      edges:
      - source: action:software-dev/implement
        target: tactic:t-a
        relation: scope
      - source: tactic:t-a
        target: tactic:t-b
        relation: suggests
      - source: tactic:t-b
        target: directive:DIRECTIVE_100
        relation: requires
      - source: directive:DIRECTIVE_100
        target: tactic:t-c
        relation: suggests
      """
    When I GET "/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/implement",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_100", "kind": "directive", "label": "Suggested One's Hard Dependency", "relation": "requires", "impacts": null, "via": "tactic:t-b", "hops": 3},
          {"urn": "tactic:t-a", "kind": "tactic", "label": "Scoped", "relation": "scope", "impacts": null, "via": "action:software-dev/implement", "hops": 1},
          {"urn": "tactic:t-b", "kind": "tactic", "label": "Suggested", "relation": "suggests", "impacts": null, "via": "tactic:t-a", "hops": 2}
        ]
      }
      """

  Scenario: An artifact reached by several walks keeps its strongest entry
    The built-in graph carries DIRECTIVE_042 scoped by action:documentation/generate and by
    mission_type:documentation; core tolerates it, so it is no refusal. The first step that reaches an
    artifact lists it: scope, then requires, then suggests, then the requires of suggested, then
    vocabulary. So DIRECTIVE_042 is a scope entry (also required and suggested by doc-a) and doc-b,
    required, suggested and listed in the vocabulary of doc-a, is a requires entry.
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
      - urn: tactic:doc-a
        kind: tactic
        label: Doc A
      - urn: tactic:doc-b
        kind: tactic
        label: Doc B
      edges:
      - source: mission_type:documentation
        target: directive:DIRECTIVE_042
        relation: scope
      - source: action:documentation/generate
        target: directive:DIRECTIVE_042
        relation: scope
      - source: action:documentation/generate
        target: tactic:doc-a
        relation: scope
      - source: tactic:doc-a
        target: directive:DIRECTIVE_042
        relation: requires
      - source: tactic:doc-a
        target: directive:DIRECTIVE_042
        relation: suggests
      - source: tactic:doc-a
        target: tactic:doc-b
        relation: suggests
      - source: tactic:doc-a
        target: tactic:doc-b
        relation: requires
      - source: tactic:doc-a
        target: tactic:doc-b
        relation: vocabulary
      """
    When I GET "/mission-types/documentation/actions/generate/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:documentation/generate",
        "missionType": "mission_type:documentation",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_042", "kind": "directive", "label": "Documentation Directive", "relation": "scope", "impacts": null, "via": "action:documentation/generate", "hops": 1},
          {"urn": "tactic:doc-a", "kind": "tactic", "label": "Doc A", "relation": "scope", "impacts": null, "via": "action:documentation/generate", "hops": 1},
          {"urn": "tactic:doc-b", "kind": "tactic", "label": "Doc B", "relation": "requires", "impacts": null, "via": "tactic:doc-a", "hops": 2}
        ]
      }
      """

  Scenario: Among parents at the same distance the one with fewer hops wins, then the smaller URN
    t-x is reached at the second suggests step from DIRECTIVE_100 (listed as required, hops 3, and its
    URN sorts first) and from t-b (new, hops 2): t-b wins on hops. t-tie is suggested by t-p and t-q,
    both at hops 1: t-p wins on the URN.
    Given the built-in pack "built-in" with the file "tie.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/tie
        kind: action
        label: tie
      - urn: tactic:t-p
        kind: tactic
        label: Parent P
      - urn: tactic:t-q
        kind: tactic
        label: Parent Q
      - urn: tactic:t-tie
        kind: tactic
        label: Smaller URN
      - urn: tactic:t-s
        kind: tactic
        label: Scoped
      - urn: tactic:t-m
        kind: tactic
        label: Middle
      - urn: directive:DIRECTIVE_100
        kind: directive
        label: Required Deep
      - urn: tactic:t-b
        kind: tactic
        label: New Suggestion
      - urn: tactic:t-x
        kind: tactic
        label: Target
      edges:
      - source: action:software-dev/tie
        target: tactic:t-q
        relation: scope
      - source: action:software-dev/tie
        target: tactic:t-p
        relation: scope
      - source: action:software-dev/tie
        target: tactic:t-s
        relation: scope
      - source: tactic:t-q
        target: tactic:t-tie
        relation: suggests
      - source: tactic:t-p
        target: tactic:t-tie
        relation: suggests
      - source: tactic:t-s
        target: tactic:t-m
        relation: requires
      - source: tactic:t-m
        target: directive:DIRECTIVE_100
        relation: requires
      - source: tactic:t-s
        target: directive:DIRECTIVE_100
        relation: suggests
      - source: tactic:t-s
        target: tactic:t-b
        relation: suggests
      - source: directive:DIRECTIVE_100
        target: tactic:t-x
        relation: suggests
      - source: tactic:t-b
        target: tactic:t-x
        relation: suggests
      """
    When I GET "/mission-types/software-dev/actions/tie/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/tie",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_100", "kind": "directive", "label": "Required Deep", "relation": "requires", "impacts": null, "via": "tactic:t-m", "hops": 3},
          {"urn": "tactic:t-b", "kind": "tactic", "label": "New Suggestion", "relation": "suggests", "impacts": null, "via": "tactic:t-s", "hops": 2},
          {"urn": "tactic:t-m", "kind": "tactic", "label": "Middle", "relation": "requires", "impacts": null, "via": "tactic:t-s", "hops": 2},
          {"urn": "tactic:t-p", "kind": "tactic", "label": "Parent P", "relation": "scope", "impacts": null, "via": "action:software-dev/tie", "hops": 1},
          {"urn": "tactic:t-q", "kind": "tactic", "label": "Parent Q", "relation": "scope", "impacts": null, "via": "action:software-dev/tie", "hops": 1},
          {"urn": "tactic:t-s", "kind": "tactic", "label": "Scoped", "relation": "scope", "impacts": null, "via": "action:software-dev/tie", "hops": 1},
          {"urn": "tactic:t-tie", "kind": "tactic", "label": "Smaller URN", "relation": "suggests", "impacts": null, "via": "tactic:t-p", "hops": 2},
          {"urn": "tactic:t-x", "kind": "tactic", "label": "Target", "relation": "suggests", "impacts": null, "via": "tactic:t-b", "hops": 3}
        ]
      }
      """

  Scenario: Following via from any entry reaches the action in exactly its hops
    The deepest entry here is glossary_pack:risk-terms: its chain is risk-log, DIRECTIVE_030,
    premortem, adr-drafting-workflow, and the action. A via is always the action or another entry of
    the answer, and an entry's hops is its via's hops plus one.
    When I GET "/mission-types/software-dev/actions/plan/context"
    Then the response status is 200
    And following "via" from every guidance entry of "/mission-types/software-dev/actions/plan/context" reaches the action in exactly "hops" edges

  Scenario: A vocabulary edge lists its glossary pack as a vocabulary entry
    Core computes the same set and discards it (glossary_scopes, offering/drg/query.py:232-233). The
    charter lists it, with relation vocabulary; the seam drops these entries when it builds core's
    delivery (DIV-106).
    Given the built-in pack "built-in" with the file "vocab.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/vocab
        kind: action
        label: vocab
      - urn: tactic:v-root
        kind: tactic
        label: Root
      - urn: glossary_pack:v-terms
        kind: glossary_pack
        label: Terms
      edges:
      - source: action:software-dev/vocab
        target: tactic:v-root
        relation: scope
      - source: tactic:v-root
        target: glossary_pack:v-terms
        relation: vocabulary
      """
    When I GET "/mission-types/software-dev/actions/vocab/context"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/vocab",
        "missionType": "mission_type:software-dev",
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "glossary_pack:v-terms", "kind": "glossary_pack", "label": "Terms", "relation": "vocabulary", "impacts": null, "via": "tactic:v-root", "hops": 2},
          {"urn": "tactic:v-root", "kind": "tactic", "label": "Root", "relation": "scope", "impacts": null, "via": "action:software-dev/vocab", "hops": 1}
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
        "depth": 2,
        "diagnostics": [],
        "guidance": [
          {"urn": "styleguide:undeclared-guide", "kind": "styleguide", "label": null, "relation": "scope", "impacts": null, "via": "action:software-dev/review", "hops": 1}
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

  Scenario: A depth outside 0 to 2 is a bad request, not a charter refusal
    A later version may raise the maximum, which is compatible: a request inside 0 to 2 keeps its
    answer.
    When I GET "/mission-types/software-dev/actions/plan/context?depth=3"
    Then the response status is 400
    And the response body is:
      """
      {"title": "Bad Request", "status": 400}
      """
    When I GET "/mission-types/software-dev/actions/plan/context?depth=-1"
    Then the response status is 400
    And the response body is:
      """
      {"title": "Bad Request", "status": 400}
      """

  Scenario: The answer carries the load findings of the offering, as the pack listing does
    An org pack without a pack.yaml loads at its configured level, with the diagnostic that
    GET /packs reports for it. The hot read shows it without a second call. The pack carries the
    drg/fragment.yaml an org pack needs.
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
      """
    When I GET "/mission-types/software-dev/actions/plan/context?depth=0"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 0,
        "diagnostics": [{"code": "pack_level_missing", "detail": "org pack qa has no pack.yaml; the configured level org is used"}],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2}
        ]
      }
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
    An impacts edge records how one artifact affects another, with a signed strength; only the
    scope, requires, suggests and vocabulary edges bring an artifact into the context. A legacy
    in_tension_with edge is read as impacts with strength -1 until core migrates its graphs.
    premortem is listed at depth 1 or more, but not by the impacts edge: at depth 0 it is absent.
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
    When I GET "/mission-types/software-dev/actions/plan/context?depth=0"
    Then the response status is 200
    And the response body, apart from "governanceFingerprint", is:
      """
      {
        "action": "action:software-dev/plan",
        "missionType": "mission_type:software-dev",
        "depth": 0,
        "diagnostics": [],
        "guidance": [
          {"urn": "directive:DIRECTIVE_003", "kind": "directive", "label": "Decision Documentation Requirement", "relation": "requires", "impacts": null, "via": "tactic:adr-drafting-workflow", "hops": 2},
          {"urn": "directive:DIRECTIVE_010", "kind": "directive", "label": "Specification Fidelity Requirement", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "glossary_pack:architecture-terms", "kind": "glossary_pack", "label": "Architecture Terms", "relation": "vocabulary", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "styleguide:writing-style", "kind": "styleguide", "label": "Writing Style", "relation": "requires", "impacts": null, "via": "directive:DIRECTIVE_003", "hops": 3},
          {"urn": "tactic:adr-drafting-workflow", "kind": "tactic", "label": "ADR Drafting Workflow", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-a", "kind": "tactic", "label": "Cycle A", "relation": "scope", "impacts": null, "via": "action:software-dev/plan", "hops": 1},
          {"urn": "tactic:cycle-b", "kind": "tactic", "label": "Cycle B", "relation": "requires", "impacts": null, "via": "tactic:cycle-a", "hops": 2}
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
