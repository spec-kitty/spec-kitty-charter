# WP07 of agent-profile-reads: the read of one agent profile under an activation, getEffectiveAgentProfile (FR-007, FR-004,
# FR-010, NFR-002; plan fold A3, C1, C4, C6, W1, W4; operator decisions 01M7115WE6WQ7Q41HSS9ZHD1F3 and
# 01M710DQEMV7VPKDNQPWKPGD4F). Written red first: the scenarios were committed with every step bound and run against the
# service as it stood, where /activations/charter/artifacts/agent_profile/java-jenny was answered by the generic read of
# an artifact, which carries no body and no governance; the operation was then built in :profiles-edge.
#
# The read answers the stored winner of the layer merge with the facts the activation gives it, its profile as the
# winner's pack stores it and its governance walked under the activation: an artifact in force is listed, a suggestion
# out of force is listed with its state, and an ancestor that is not in force is dropped with what only it requires (W1).
# The profile the walk starts from is exempt from that predicate (W4). The scenarios follow the links: the artifact link
# of the answer and of every governance entry (artifact, and effective-artifact since WP08), and the activation.
#
# Steps these scenarios add to the vocabulary of README.md (bound in ProfileSteps):
# - `Then the effective governance holds exactly the entries:` followed by a doc string, one entry per line, in the
#   answer's order: `<urn> <step> via <urn> hops <n> <state>/<reason> in <packId> linked` (or `unlinked` for an entry
#   without an artifact link), then ` edge {}` for an entry whose edge states neither word, or ` edge {when "<when>" because
#   "<reason>"}` for one that states any (one word alone is written alone): every entry has an `edge`, whatever its relation
#   and whether it is in force or not.
#
# Fixture: built-in holds java-jenny (which specializes implementer-ivan, requires DIRECTIVE_010 and java-conventions and
# suggests DIRECTIVE_041, DIRECTIVE_037, DIRECTIVE_052 and DIRECTIVE_053) and implementer-ivan (which requires DIRECTIVE_024). The activation "charter"
# is built from a selection like this repository's charter.yaml: the directives and style guides it selects, and the two
# profiles; DIRECTIVE_037 and DIRECTIVE_053 are left out of the selection. The java-conventions requires edge and the two
# suggests edges of DIRECTIVE_041 and DIRECTIVE_037 state words; the edges to DIRECTIVE_052 and DIRECTIVE_053 state none.

@contract @activations @agent-profiles
Feature: An agent profile is read under an activation, with its governance and the state of each entry
  GET /activations/{activationId}/artifacts/agent_profile/{artifactId} answers the winner of the profile under the
  activation: the pack and level that hold it, its state and reason, the profile as that pack stores it and the artifacts
  its governance walk lists, each with the facts of its own winner. The walk is judged by the activation: what is in force
  is listed, a suggestion out of force is listed with its state, and an ancestor that is not in force is dropped. The
  activation is judged first, then whether a pack of the closure holds the profile, both before any If-None-Match. The ETag
  names the revision, the packs of the closure, the profile and the depth, and a matching If-None-Match is a 304.

  Background:
    Given the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000FG
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
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
      routing-priority: 80
      model: null
      purpose: Deliver idiomatic Java.
      specialization:
        primary-focus: Java implementation
      directive-references:
        - code: "034"
          name: Test-First Development
          rationale: Tests define the contract.
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
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_037.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_037
      title: Living documentation
      intent: Keep the documentation alive.
      enforcement: advisory
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
    And the built-in pack "built-in" with the file "directives/DIRECTIVE_053.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_053
      title: Small pull requests
      intent: Keep pull requests small.
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
        target: directive:DIRECTIVE_037
        relation: suggests
        when: when the change alters behaviour
        reason: Documentation should follow the behaviour.
      - source: agent_profile:java-jenny
        target: directive:DIRECTIVE_052
        relation: suggests
      - source: agent_profile:java-jenny
        target: directive:DIRECTIVE_053
        relation: suggests
      - source: agent_profile:implementer-ivan
        target: directive:DIRECTIVE_024
        relation: requires
      """
    And the activation "charter" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {
        "agent_profile": ["implementer-ivan", "java-jenny"],
        "directive": ["DIRECTIVE_010", "DIRECTIVE_024", "DIRECTIVE_041", "DIRECTIVE_052"],
        "styleguide": ["java-conventions"]}}
      """

  Scenario: java-jenny is read under a charter-like activation, with its body, its governance and every link resolving
    java-jenny is selected, and so is its parent. The answer names the pack and the level that hold the version in force
    and says the profile is in force and selected. Its governance lists the parent (lineage), what the profile requires
    and what only the parent requires, each in force with the pack that holds it. At the default depth no suggestion is
    walked. Every entry links the pack-scoped read of its winner, and the answer links the profile's own, the same body,
    and the activation. The answer carries the verified fingerprint and a strong ETag.
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/activationId" is "charter"
    And the field at "/kind" is "agent_profile"
    And the field at "/artifactId" is "java-jenny"
    And the field at "/packId" is "built-in"
    And the field at "/level" is "built-in"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/required" is false
    And the field at "/policy" is "open"
    And the field at "/satisfiedBy" is absent
    And the field at "/suggestion" is absent
    And the field at "/agentProfile/profileId" is "java-jenny"
    And the field at "/agentProfile/name" is "Java Jenny"
    And the field at "/agentProfile/routingPriority" is 80
    And the field at "/agentProfile/directiveReferences/0/code" is "034"
    And the field at "/_links/self/href" is "/api/v1/activations/charter/artifacts/agent_profile/java-jenny"
    And the field at "/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/agent_profile/java-jenny"
    And the field at "/_links/up/href" is "/api/v1/activations/charter"
    And the effective governance holds exactly the entries:
      """
      agent_profile:implementer-ivan lineage via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      directive:DIRECTIVE_024 requires via agent_profile:implementer-ivan hops 2 active/selected in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    And the field at "/governance/1/kind" is "directive"
    And the field at "/governance/1/artifactId" is "DIRECTIVE_010"
    And the field at "/governance/1/level" is "built-in"
    And the field at "/governance/1/policy" is "open"
    And the field at "/governance/1/relation" is "requires"
    And the field at "/governance/1/_links/artifact/href" is "/api/v1/packs/built-in/artifacts/directive/DIRECTIVE_010"
    And the field at "/governance/1/_links/effective-artifact/href" is "/api/v1/activations/charter/artifacts/directive/DIRECTIVE_010"
    And the field at "/governance/0/_links/effective-artifact/href" is "/api/v1/activations/charter/artifacts/agent_profile/implementer-ivan"
    And following the link "artifact" of every element of "governance" answers 200
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    Then following the link "effective-artifact" of every element of "governance" answers 200
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/kind" is "agent_profile"
    And the field at "/packId" is "built-in"
    And the field at "/agentProfile/name" is "Java Jenny"
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    And I follow the link "up"
    Then the response status is 200
    And the field at "/activationId" is "charter"

  Scenario: At depth 1 the suggestions are walked: both carry the words of their edge, one in force, one out of force
    DIRECTIVE_041 is selected, so it is a suggests entry in force, and it carries the when and the reason of its suggests
    edge (FR-004). DIRECTIVE_037 is not selected: it is listed as a suggestion that is inactive, not_selected, with the
    words of its edge inline. Both link the pack-scoped read. The self link repeats the depth the request sent.
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=1"
    Then the response status is 200
    And the field at "/_links/self/href" is "/api/v1/activations/charter/artifacts/agent_profile/java-jenny?depth=1"
    And the effective governance holds exactly the entries:
      """
      agent_profile:implementer-ivan lineage via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      directive:DIRECTIVE_024 requires via agent_profile:implementer-ivan hops 2 active/selected in built-in linked edge {}
      directive:DIRECTIVE_037 suggests via agent_profile:java-jenny hops 1 inactive/not_selected in built-in linked edge {when "when the change alters behaviour" because "Documentation should follow the behaviour."}
      directive:DIRECTIVE_041 suggests via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when keeping the tests a clear scaffold" because "A test-writing profile should reach the scaffold hub."}
      directive:DIRECTIVE_052 suggests via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      directive:DIRECTIVE_053 suggests via agent_profile:java-jenny hops 1 inactive/not_selected in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    And following the link "artifact" of every element of "governance" answers 200
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=1"
    Then following the link "effective-artifact" of every element of "governance" answers 200

  Scenario: Every entry carries the words of its edge: a requires entry, and {} for a wordless suggests entry in force or out of force
    The java-conventions requires edge states a when and a reason, so its entry carries them although it is not a suggests
    entry. DIRECTIVE_052 is selected and DIRECTIVE_053 is not, and neither suggests edge states a word: both entries carry
    the same empty object, whatever their state (the member is present, not null and not absent). The suggestion member of
    the effective list is not on an entry.
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=1"
    Then the response status is 200
    And the field at "/governance/1/edge" is an empty object
    And the field at "/governance/3/edge/when" is "when the change alters behaviour"
    And the field at "/governance/5/state" is "active"
    And the field at "/governance/5/edge" is an empty object
    And the field at "/governance/6/state" is "inactive"
    And the field at "/governance/6/reason" is "not_selected"
    And the field at "/governance/6/edge" is an empty object
    And the field at "/governance/6/suggestion" is absent
    And the field at "/governance/7/edge/reason" is "The house style is part of the work."

  Scenario: An ancestor that is not in force is dropped, with what only it requires, though that is in force itself
    The activation selects java-jenny but not its parent. The parent is not listed and seeds nothing: DIRECTIVE_024 is
    selected, so it is in force, and only the parent requires it, so the profile's governance does not reach it. What the
    profile itself requires is still listed. This is the walk under an activation (W1), not the cascade's pass-through.
    Given the activation "no-parent" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {
        "agent_profile": ["java-jenny"],
        "directive": ["DIRECTIVE_010", "DIRECTIVE_024"],
        "styleguide": ["java-conventions"]}}
      """
    When I GET "/activations/no-parent/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/state" is "active"
    And the effective governance holds exactly the entries:
      """
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/governance/0/artifactId" is "implementer-ivan"
    And the field at "/governance/2/artifactId" is "DIRECTIVE_024"

  Scenario: A profile the activation does not select is still answered, with its state, and its governance is walked
    The start of the walk is exempt from the in-force predicate (W4): the answer is not a 404, it says the profile is
    inactive and not selected, and it lists what the profile requires that is in force. The parent is not in force, so it
    is dropped. The artifact link still reads the profile in the pack that holds it.
    Given the activation "only-directives" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {
        "directive": ["DIRECTIVE_010"],
        "styleguide": ["java-conventions"]}}
      """
    When I GET "/activations/only-directives/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    And the field at "/packId" is "built-in"
    And the field at "/agentProfile/profileId" is "java-jenny"
    And the effective governance holds exactly the entries:
      """
      directive:DIRECTIVE_010 requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {}
      styleguide:java-conventions requires via agent_profile:java-jenny hops 1 active/selected in built-in linked edge {when "when writing Java" because "The house style is part of the work."}
      """
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/agentProfile/profileId" is "java-jenny"

  Scenario: The copy that won the layer merge is the one answered, and its links read the winner's pack
    The project pack replaces java-jenny at the project level. The answer names it, its body is the project copy's, the
    artifact link reads the project pack, and the governance of the winner is walked from the merged closure: the edges of
    the built-in pack still apply. The stored winner is answered; nothing of enhances is resolved.
    Given the project pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: house
      pack_level: project
      """
    And the project pack "house" with the file "agent_profiles/java-jenny.agent.yaml":
      """
      profile-id: java-jenny
      name: House Jenny
      role: implementer
      purpose: Java, the house way.
      specialization:
        primary-focus: Java
      """
    And the activation "layered" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}, {"packId": "house"}], "selected": {
        "agent_profile": ["java-jenny"],
        "directive": ["DIRECTIVE_010"],
        "styleguide": ["java-conventions"]}}
      """
    When I GET "/activations/layered/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/packId" is "house"
    And the field at "/level" is "project"
    And the field at "/agentProfile/name" is "House Jenny"
    And the field at "/_links/artifact/href" is "/api/v1/packs/house/artifacts/agent_profile/java-jenny"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/packId" is "house"
    And the field at "/agentProfile/name" is "House Jenny"

  Scenario: A matching If-None-Match is 304 with the same tag, and the tag of another depth or profile never matches
    The first answer, 200, is the positive control: the 304 is not an empty store. The same read at another depth has
    another tag, as the ETag names the profile and the depth, and so has another profile.
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the response header "ETag" appears once
    When I remember the header "ETag"
    And I GET "/activations/charter/artifacts/agent_profile/java-jenny" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=1"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/charter/artifacts/agent_profile/implementer-ivan"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/charter/artifacts/agent_profile/implementer-ivan" with If-None-Match the remembered header "ETag"
    Then the response status is 200

  Scenario: A depth outside 0 to 1 is a 400 without a code that names the bound
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=2"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The query parameter depth is \"2\", which is greater than 1."
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=-1"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The query parameter depth is \"-1\", which is less than 0."
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny?depth=0"
    Then the response status is 200

  Scenario: An activation that is not stored is activation_not_found, whatever the profile is
    When I GET "/activations/nope/artifacts/agent_profile/java-jenny"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/nope/artifacts/agent_profile/ghost"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A profile no pack of the closure holds is artifact_not_found, also against If-None-Match
    GHOST is held by no pack. An id of another kind is no match either: DIRECTIVE_010 is a directive. The refusal is judged
    before the If-None-Match, which `*` would otherwise match. java-jenny is the control: the same activation answers it.
    When I GET "/activations/charter/artifacts/agent_profile/ghost"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/charter/artifacts/agent_profile/DIRECTIVE_010"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/charter/artifacts/agent_profile/ghost" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "/activations/charter/artifacts/agent_profile/java-jenny"
    Then the response status is 200
