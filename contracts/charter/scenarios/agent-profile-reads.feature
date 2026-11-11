# WP04 of agent-profile-reads: the pack-scoped read of one agent profile, getPackAgentProfile (FR-002, FR-003, FR-004, FR-010,
# NFR-001, NFR-002, SC-003, SC-004; plan fold C1 to C3, C6, C9 to C11). Written red first: the scenarios were committed
# with every step bound and run against the service as it stood, where a read of an agent_profile answered the generic
# route's 404 kind_not_found; the operation was then built. profiles.feature (#380) is left alone: it is @WIP and describes
# the effective profile, which this read is not.
#
# The governance walk lists an artifact once, at its earliest step, and depth counts the suggests hops it follows: at the
# default depth 0 no suggestion is listed, at depth=1 the suggested artifacts and what they require are. An entry links
# (`artifact`) only when its target is held and the artifact list serves its kind. WP05 opened the list to agent_profile, so
# a lineage entry links to the pack-scoped read of the ancestor, and the last scenarios follow the profile read's own
# `collection` and `references` links and every governance entry's `artifact` link and expect 200 (FR-004).
#
# Steps these scenarios add to the vocabulary of README.md (bound in ProfileSteps):
# - `Then the field at "<json-pointer>" is null` — the member is there and is JSON null (a member that is left out is absent).
# - `Then the governance holds exactly the entries:` followed by a doc string, one entry per line, in the answer's order:
#   `<urn> <step> via <urn> hops <n>`, then ` in <packId> linked` for a held entry with an `artifact` link to the pack
#   that holds it, ` in <packId> unlinked` for a held entry without one, or ` not held unlinked` for an entry no pack of
#   the closure holds (a null packId and no link); then ` edge {}` for an entry whose edge states neither word, or ` edge
#   {when "<when>" because "<reason>"}` (either word alone is written alone) for an entry whose edge states any: every
#   entry has an `edge`, whatever its relation.

@contract @agent-profiles
Feature: A pack's agent profile is read with its governance
  GET /packs/{packId}/artifacts/agent_profile/{artifactId} answers the Artifact envelope with the profile as the pack stores
  it (agentProfile) and the artifacts its governance walk lists over the pack's composed closure (governance), sorted by
  urn: the lineage through specializes_from, then what the profile and its ancestors require, suggest and use as
  vocabulary. The read is one verified read: it carries the Charter-Source-Fingerprint it verified and the pack revision as
  its ETag. A pack that is not configured is pack_not_found; a profile the pack does not hold is artifact_not_found; a depth
  other than 0 or 1 is a 400 Problem without a code.

  Background:
    Given the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000FG
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
      """
    And the built-in pack "built-in" with the file "agent_profiles/lone.agent.yaml":
      """
      profile-id: lone
      name: Lone
      role: implementer
      purpose: Work alone.
      specialization:
        primary-focus: Nothing else.
      """
    And the built-in pack "built-in" with the file "agent_profiles/java-jenny.agent.yaml":
      """
      profile-id: java-jenny
      name: Java Jenny
      description: Java-specialist implementer
      schema-version: "1.0"
      role: implementer
      applies_to_languages:
        - java
      capabilities:
        - java-implementation
        - junit-testing
      routing-priority: 80
      max-concurrent-tasks: 5
      model: null
      purpose: Deliver idiomatic Java.
      specialization:
        primary-focus: Java implementation
        avoidance-boundary: Architectural decisions
      collaboration:
        handoff-to:
          - reviewer
        handoff-from:
          - architect
          - planner
      mode-defaults:
        - mode: ping-pong
          description: Alternate a failing test and the code that passes it.
          use-case: Driving a feature test first.
      initialization-declaration: I am Java Jenny.
      sentinel: false
      specialization-context:
        languages:
          - java
        writing-style:
          - pragmatic
        complexity-preference:
          - low
          - medium
      directive-references:
        - code: "034"
          name: Test-First Development
          rationale: Tests define the contract.
      tactic-references:
        - id: tdd-red-green-refactor
          rationale: Red, green, refactor.
      self-review-protocol:
        steps:
          - name: compile
            command: mvn compile
            gate: zero compilation errors
          - name: locality-review
            gate: only files of the task are modified
      """
    And the built-in pack "built-in" with the file "agent_profiles/implementer-ivan.agent.yaml":
      """
      profile-id: implementer-ivan
      name: Implementer Ivan
      role: implementer
      purpose: Implement.
      specialization:
        primary-focus: Implementation
      """
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_010.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_010
      title: Specification fidelity
      intent: Follow the specification.
      enforcement: required
      """
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_024.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_024
      title: Locality of change
      intent: Keep the change local.
      enforcement: required
      """
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_041.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_041
      title: Tests as scaffold
      intent: Keep tests a scaffold.
      enforcement: advisory
      """
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_052.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_052
      title: Reviewable commits
      intent: Keep commits reviewable.
      enforcement: advisory
      """
    And the built-in pack "built-in" with the file "styleguides/java-conventions.styleguide.yaml":
      """
      schema_version: '1.0'
      id: java-conventions
      title: Java conventions
      scope: code
      principles:
        - name: Name things plainly
          description: Name things plainly, in one sentence.
      """
    And the built-in pack "built-in" with the file "agent_profile.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:java-jenny
        target: agent_profile:implementer-ivan
        relation: specializes_from
      - source: agent_profile:java-jenny
        target: styleguide:java-conventions
        relation: requires
        when: when writing Java
        reason: The house style is part of the work.
      - source: agent_profile:java-jenny
        target: directive:DIRECTIVE_010
        relation: requires
      - source: agent_profile:java-jenny
        target: directive:DIRECTIVE_041
        relation: suggests
        when: when keeping the tests a clear scaffold
        reason: A test-writing profile should reach the scaffold hub.
      - source: agent_profile:java-jenny
        target: directive:DIRECTIVE_052
        relation: suggests
      - source: agent_profile:implementer-ivan
        target: directive:DIRECTIVE_024
        relation: requires
      """

  Scenario: A profile is read in the envelope, with the revision as its ETag and the verified fingerprint
    When I GET "/packs/built-in/artifacts/agent_profile/lone"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "agent_profile",
        "id": "lone",
        "packId": "built-in",
        "agentProfile": {
          "profileId": "lone",
          "name": "Lone",
          "role": "implementer",
          "altRoles": [],
          "avatarImage": null,
          "capabilities": [],
          "model": null,
          "effort": null,
          "purpose": "Work alone.",
          "specialization": {"primaryFocus": "Nothing else."},
          "collaboration": {"handoffTo": [], "handoffFrom": [], "worksWith": [], "outputArtifacts": [], "operatingProcedures": [], "canonicalVerbs": []},
          "modeDefaults": [],
          "specializationContext": null,
          "appliesToLanguages": [],
          "directiveReferences": [],
          "tacticReferences": [],
          "toolguideReferences": [],
          "styleguideReferences": [],
          "selfReviewProtocol": null,
          "excluding": null
        },
        "governance": [],
        "_links": {
          "self": {"href": "/api/v1/packs/built-in/artifacts/agent_profile/lone"},
          "collection": {"href": "/api/v1/packs/built-in/artifacts?kind=agent_profile"},
          "references": {"href": "/api/v1/packs/built-in/artifacts/agent_profile/lone/references"}
        }
      }
      """

  Scenario: A profile with lineage and edges is read with its body, its governance and its links
    The lineage seeds the later steps: DIRECTIVE_024 is required by the parent, not by the profile, and is listed through
    it, two hops from the profile. At the default depth the suggested directive is not walked.
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/kind" is "agent_profile"
    And the field at "/agentProfile/profileId" is "java-jenny"
    And the field at "/agentProfile/routingPriority" is 80
    And the field at "/agentProfile/specializationContext/complexityPreference/1" is "medium"
    And the field at "/agentProfile/selfReviewProtocol/steps/0/command" is "mvn compile"
    And the field at "/agentProfile/directiveReferences/0/code" is "034"
    And the field at "/_links/self/href" is "/api/v1/packs/built-in/artifacts/agent_profile/java-jenny"
    And the governance holds exactly the entries:
      """
      agent_profile:implementer-ivan lineage via agent_profile:java-jenny hops 1 in built-in linked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 in built-in linked edge {}
      directive:DIRECTIVE_024 requires via agent_profile:implementer-ivan hops 2 in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    And the field at "/governance/0/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/agent_profile/implementer-ivan"
    And the field at "/governance/3/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/styleguide/java-conventions"

  Scenario: Every entry carries the words of its edge, a requires entry as a suggests entry, and {} where the edge states none
    The java-conventions requires edge and the DIRECTIVE_041 suggests edge state a when and a reason; the DIRECTIVE_010
    requires edge and the DIRECTIVE_052 suggests edge state neither, so their edge is an empty object (it is present, not
    null and not absent).
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny?depth=1"
    Then the response status is 200
    And the field at "/governance/1/edge" is an empty object
    And the field at "/governance/3/edge/when" is "when keeping the tests a clear scaffold"
    And the field at "/governance/4/edge" is an empty object
    And the field at "/governance/5/edge/reason" is "The house style is part of the work."
    And the field at "/governance/5/suggestion" is absent

  Scenario: At depth 1 the profile's suggestions are walked, each with the condition and the reason of its edge
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny?depth=1"
    Then the response status is 200
    And the field at "/_links/self/href" is "/api/v1/packs/built-in/artifacts/agent_profile/java-jenny?depth=1"
    And the governance holds exactly the entries:
      """
      agent_profile:implementer-ivan lineage via agent_profile:java-jenny hops 1 in built-in linked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 in built-in linked edge {}
      directive:DIRECTIVE_024 requires via agent_profile:implementer-ivan hops 2 in built-in linked edge {}
      directive:DIRECTIVE_041 suggests via agent_profile:java-jenny hops 1 in built-in linked edge {when "when keeping the tests a clear scaffold" because "A test-writing profile should reach the scaffold hub."}
      directive:DIRECTIVE_052 suggests via agent_profile:java-jenny hops 1 in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """

  Scenario: The links of a profile read lead to reads that answer 200: its collection, its references and every governance entry
    The collection link is the list of the pack's profiles, the references link is the edges the pack writes from the
    profile, and the artifact link of a governance entry, a lineage entry included, is the pack-scoped read of that
    artifact. Each is followed from the profile read, not built by hand.
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/_links/collection/href" is "/api/v1/packs/built-in/artifacts?kind=agent_profile"
    And following the link "artifact" of every element of "governance" answers 200
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny"
    And I follow the link "collection"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items/0/kind" is "agent_profile"
    And the field at "/items/0/id" is "implementer-ivan"
    And the field at "/items/1/id" is "java-jenny"
    And the field at "/items/2/id" is "lone"
    And the field at "/items/3/id" is absent
    And following the link "self" of every element of "items" answers 200
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny"
    And I follow the link "references"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items/0/reference" is "agent_profile:implementer-ivan"
    And the field at "/items/0/relation" is "specializes_from"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/agent_profile/implementer-ivan"
    And following the link "artifact" of every element of "items" answers 200

  Scenario: A parent no pack of the closure holds is listed as not held, with no pack and no link
    Given the built-in pack "built-in" with the file "agent_profiles/orphan.agent.yaml":
      """
      profile-id: orphan
      name: Orphan
      role: implementer
      purpose: Lost its parent.
      specialization:
        primary-focus: Waiting
      """
    And the built-in pack "built-in" with the file "orphan.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:orphan
        target: agent_profile:ghost
        relation: specializes_from
      - source: agent_profile:orphan
        target: directive:DIRECTIVE_010
        relation: requires
      - source: agent_profile:ghost
        target: directive:DIRECTIVE_024
        relation: requires
      """
    When I GET "/packs/built-in/artifacts/agent_profile/orphan"
    Then the response status is 200
    And the governance holds exactly the entries:
      """
      agent_profile:ghost lineage via agent_profile:orphan hops 1 not held unlinked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:orphan hops 1 in built-in linked edge {}
      """
    And the field at "/governance/0/held" is false
    And the field at "/governance/0/packId" is null

  Scenario: The governance walk reaches a parent that another pack of the closure holds, and names the pack
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: house
      pack_level: org
      depends_on:
        - pack_id: 01ARWG13C000000000000000FG
          range: ">=1.0,<2"
      """
    And the org pack "house" with the file "agent_profiles/house-jenny.agent.yaml":
      """
      profile-id: house-jenny
      name: House Jenny
      role: implementer
      purpose: Java, the house way.
      specialization:
        primary-focus: Java
      """
    And the org pack "house" with the file "house.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:house-jenny
        target: built-in::agent_profile:java-jenny
        relation: specializes_from
      """
    When I GET "/packs/house/artifacts/agent_profile/house-jenny"
    Then the response status is 200
    And the field at "/packId" is "house"
    And the governance holds exactly the entries:
      """
      agent_profile:implementer-ivan lineage via agent_profile:java-jenny hops 2 in built-in linked edge {}
      agent_profile:java-jenny lineage via agent_profile:house-jenny hops 1 in built-in linked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 2 in built-in linked edge {}
      directive:DIRECTIVE_024 requires via agent_profile:implementer-ivan hops 3 in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 2 in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    And the field at "/governance/0/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/agent_profile/implementer-ivan"
    And the field at "/governance/1/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/agent_profile/java-jenny"
    And the field at "/governance/2/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/directive/DIRECTIVE_010"

  Scenario: A project pack that overrides a built-in directive the profile requires names its own copy, whose edges are walked
    Given the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      depends_on:
        - pack_id: 01ARWG13C000000000000000FG
          range: ">=1.0,<2"
      """
    And the project pack "proj" with the file "agent_profiles/proj-jenny.agent.yaml":
      """
      profile-id: proj-jenny
      name: Project Jenny
      role: implementer
      purpose: Java, the project way.
      specialization:
        primary-focus: Java
      """
    And the project pack "proj" with the file "directives/DIRECTIVE_010.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_010
      title: Specification fidelity, the project way
      intent: Follow the project's specification.
      enforcement: required
      """
    And the project pack "proj" with the file "directives/PROJ_001.directive.yaml":
      """
      schema_version: '1.0'
      id: PROJ_001
      title: Project review
      intent: Review the project way.
      enforcement: required
      """
    And the project pack "proj" with the file "procedures/project-flow.procedure.yaml":
      """
      schema_version: '1.0'
      id: project-flow
      title: Project flow
      description: The way this project ships a change.
      entry_condition: A change is ready.
      exit_condition: The change is shipped.
      steps:
        - title: Ship it
      """
    And the project pack "proj" with the file "proj.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: agent_profile:proj-jenny
        target: directive:DIRECTIVE_010
        relation: requires
      - source: agent_profile:proj-jenny
        target: procedure:project-flow
        relation: requires
      - source: directive:DIRECTIVE_010
        target: directive:PROJ_001
        relation: requires
      """
    When I GET "/packs/proj/artifacts/agent_profile/proj-jenny"
    Then the response status is 200
    And the governance holds exactly the entries:
      """
      directive:DIRECTIVE_010 requires via agent_profile:proj-jenny hops 1 in proj linked edge {}
      directive:PROJ_001 requires via directive:DIRECTIVE_010 hops 2 in proj linked edge {}
      procedure:project-flow requires via agent_profile:proj-jenny hops 1 in proj linked edge {}
      """
    And the field at "/governance/0/packId" is "proj"
    And the field at "/governance/0/_links/artifact/href" is "/api/v1/packs/proj/artifacts/directive/DIRECTIVE_010"
    And following the link "artifact" of every element of "governance" answers 200

  Scenario: A profile only a pack of the closure holds is not the pack's own and is artifact_not_found
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: house
      pack_level: org
      depends_on:
        - pack_id: 01ARWG13C000000000000000FG
          range: ">=1.0,<2"
      """
    When I GET "/packs/house/artifacts/agent_profile/java-jenny"
    Then the response status is 404
    And the problem code is "artifact_not_found"

  Scenario: A profile is read with both authored forms of excluding, as written
    Given the built-in pack "built-in" with the file "agent_profiles/drops-fields.agent.yaml":
      """
      profile-id: drops-fields
      name: Drops fields
      role: implementer
      purpose: Drop whole fields.
      specialization:
        primary-focus: Dropping
      excluding:
        - tactic-references
        - styleguide-references
      """
    And the built-in pack "built-in" with the file "agent_profiles/drops-values.agent.yaml":
      """
      profile-id: drops-values
      name: Drops values
      role: implementer
      purpose: Drop values.
      specialization:
        primary-focus: Dropping
      excluding:
        alt-roles:
          - reviewer
        capabilities: []
      """
    When I GET "/packs/built-in/artifacts/agent_profile/drops-fields"
    Then the response status is 200
    And the field at "/agentProfile/excluding/wholeFields/1" is "styleguide-references"
    And the field at "/agentProfile/excluding/fieldValues" is null
    When I GET "/packs/built-in/artifacts/agent_profile/drops-values"
    Then the response status is 200
    And the field at "/agentProfile/excluding/wholeFields" is null
    And the field at "/agentProfile/excluding/fieldValues/0/field" is "alt-roles"
    And the field at "/agentProfile/excluding/fieldValues/0/values/0" is "reviewer"
    And the field at "/agentProfile/excluding/fieldValues/1/field" is "capabilities"
    And the field at "/agentProfile/excluding/fieldValues/1/values/0" is absent

  Scenario: A profile the pack does not hold is artifact_not_found
    When I GET "/packs/built-in/artifacts/agent_profile/nobody"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    And the field at "/detail" is "Pack built-in has no agent_profile nobody."

  Scenario: A pack that is not configured is pack_not_found, whatever the profile
    When I GET "/packs/nope/artifacts/agent_profile/java-jenny"
    Then the response status is 404
    And the problem code is "pack_not_found"

  Scenario Outline: A depth other than 0 or 1 is a 400 Problem without a code
    When I GET "/packs/built-in/artifacts/agent_profile/java-jenny?depth=<depth>"
    Then the response status is 400
    And the problem has no code

    Examples:
      | depth |
      | 2     |
      | -1    |

  Scenario: A directive is still read by the generic route, with no profile member and no references link
    When I GET "/packs/built-in/artifacts/directive/DIRECTIVE_010"
    Then the response status is 200
    And the field at "/kind" is "directive"
    And the field at "/agentProfile" is absent
    And the answer has no link "references"
