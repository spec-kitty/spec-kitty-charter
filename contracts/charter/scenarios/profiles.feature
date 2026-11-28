# PR-CHARTER-REFUSAL-001 of #374 (#380; rulings D5, D6 and D7 of slice G, ADR-0078), on the effective profile read
# getEffectiveAgentProfile (R8, #102). An agent profile that enhances another is merged into it: its identity fields are
# its own, and every other top-level field it declares replaces the base's and must keep all of it. Steps are enhanced by
# additions only (ADR-0053 decision 22), so a drop cannot happen to a step: this concerns agent profiles alone, the one
# kind whose enhances keeps its field-merge meaning.
#
# The approved scenario is read on content stored before the rule: its packs are configured as files, the way a pack
# held before the registration refused such an enhancement is, so the effective profile read, not the registration,
# judges it. The read answers 500 profile_enhancement_drops_field, the packs being at fault, with the enhancing pack first
# in packs; a registration of the same content answers 422 with the same code
# (registration/whole-pack-registration.feature).
#
# The positive controls read charter's own example packs (ADR-0070): org-base's reviewer-renata-org, an enhancer of the
# built-in reviewer-renata that restates what it replaces and adds DIRECTIVE_038, and the built-in java-jenny, who
# specializes implementer-ivan. java-jenny declares every field implementer-ivan does, so the inherited field is shown on
# a scenario test pack of the same two profiles, in which java-jenny leaves fields absent.
#
# Steps this file adds to the vocabulary of README.md (bound in ProfileSteps):
# - `Given the <level> pack "<name>" holds the agent profile "<id>" with the specialization "<text>"` — configures the
#   pack, with a descriptor of its own, holding one reviewer profile whose specialization's primary-focus is the text.
# - `Given the <level> pack "<name>" holds an agent profile that enhances "<id>" and leaves out its specialization` —
#   configures the pack, depending on the pack that holds the profile, with the profile `<id>-<name>` whose
#   specialization's primary-focus is another text and an enhances edge to `<id>` qualified by its pack.
# - `When I read the effective agent profile "<id>"` — stores an activation over every pack those steps configured,
#   selecting every profile they wrote, and reads the profile under it.
# - `Then the request is refused naming the pack "<name>" and the field "<field>"` — 500
#   profile_enhancement_drops_field, the pack first in packs and named in the detail with the field.

@contract @activations @agent-profiles
Feature: An agent profile is only ever added to, never quietly weakened
  A pack's agent profile that enhances another profile may add to it, never silently drop part of it:
  governance that would quietly weaken a profile is refused, naming the pack and the fields it would drop.

  @promise:PR-CHARTER-REFUSAL-001
  Scenario: An agent profile enhancement that would drop fields of the profile it enhances is refused
    Given the org pack "base" holds the agent profile "reviewer" with the specialization "review"
    And the org pack "team" holds an agent profile that enhances "reviewer" and leaves out its specialization
    When I read the effective agent profile "reviewer"
    Then the request is refused naming the pack "team" and the field "specialization"

  Scenario: An enhancement in force that keeps the profile it enhances is merged into it, and one out of force is not
    US4.1, US4.3 and US5.4, rulings D5 and D7: reviewer-renata-org restates reviewer-renata's specialization and directive
    references and adds DIRECTIVE_038. Under an activation that selects both, the effective reviewer-renata holds her
    six directive references and the addition, and her own name, never the enhancer's; her capabilities, which the
    enhancer leaves absent, stay hers. The pack-scoped read the artifact link names is the stored profile, without the
    addition. The enhancer's own read answers its stored body and links the profile it enhances. Under an activation
    that leaves the enhancer out, nothing of it is merged.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the activation "o" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"agent_profile": ["reviewer-renata", "reviewer-renata-org"]}}
      """
    And the activation "plain" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"agent_profile": ["reviewer-renata"]}}
      """
    When I GET "/activations/o/artifacts/agent_profile/reviewer-renata"
    Then the response status is 200
    And the field at "/packId" is "built-in"
    And the field at "/agentProfile/profileId" is "reviewer-renata"
    And the field at "/agentProfile/name" is "Reviewer Renata"
    And the field at "/agentProfile/capabilities/0" is "code-review"
    And the field at "/agentProfile/directiveReferences" holds exactly 7 items
    And the field at "/agentProfile/directiveReferences/5/code" is "DIRECTIVE_051"
    And the field at "/agentProfile/directiveReferences/6/code" is "org-base::DIRECTIVE_038"
    And the field at "/lineage" holds exactly 0 items
    And the answer has no link "enhances"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/agentProfile/directiveReferences" holds exactly 6 items
    When I GET "/activations/o/artifacts/agent_profile/reviewer-renata-org"
    Then the response status is 200
    And the field at "/packId" is "org-base"
    And the field at "/agentProfile/name" is "Reviewer Renata (org)"
    And the field at "/agentProfile/capabilities" holds exactly 0 items
    And the field at "/_links/enhances/href" is "/api/v1/activations/o/artifacts/agent_profile/reviewer-renata"
    When I follow the link "enhances"
    Then the response status is 200
    And the field at "/artifactId" is "reviewer-renata"
    When I GET "/activations/plain/artifacts/agent_profile/reviewer-renata"
    Then the response status is 200
    And the field at "/agentProfile/directiveReferences" holds exactly 6 items

  Scenario: java-jenny's lineage is implementer-ivan of built-in at depth 1, and what she declares is her own
    US4.2, ruling D6: under an activation that selects both, java-jenny's lineage is her parent, held by built-in, one
    hop away. She declares every field implementer-ivan does, so her languages stay java alone and her name is her own.
    Under an activation that leaves her parent out, her lineage is empty: an ancestor that is not in force is not listed.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny"]}}
      """
    And the activation "alone" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["java-jenny"]}}
      """
    When I GET "/activations/b/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/lineage" holds exactly 1 item
    And the field at "/lineage/0/id" is "implementer-ivan"
    And the field at "/lineage/0/pack" is "built-in"
    And the field at "/lineage/0/depth" is 1
    And the field at "/agentProfile/name" is "Java Jenny"
    And the field at "/agentProfile/specializationContext/languages" holds exactly 1 item
    And the field at "/agentProfile/specializationContext/languages/0" is "java"
    When I GET "/activations/alone/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/lineage" holds exactly 0 items

  Scenario: A field java-jenny leaves absent is inherited from implementer-ivan; identity fields never are
    Ruling D6 on a scenario test pack of the two profiles: implementer-ivan declares a description, capabilities and
    max-concurrent-tasks; java-jenny declares her own description and none of the other two. Her effective profile
    inherits the capabilities and max-concurrent-tasks, keeps her description, and keeps her identity: her name, purpose
    and routing-priority are hers, and implementer-ivan's routing-priority is not inherited where she declares none. The
    pack-scoped read is the stored profile, without what she inherits.
    Given the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000FG
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
      """
    And the built-in pack "built-in" with the file "agent_profiles/implementer-ivan.agent.yaml":
      """
      profile-id: implementer-ivan
      name: Implementer Ivan
      description: General-purpose implementer
      role: implementer
      capabilities:
        - code-implementation
      routing-priority: 50
      max-concurrent-tasks: 5
      purpose: Implement.
      specialization:
        primary-focus: Implementation
      """
    And the built-in pack "built-in" with the file "agent_profiles/java-jenny.agent.yaml":
      """
      profile-id: java-jenny
      name: Java Jenny
      description: Java-specialist implementer
      role: implementer
      purpose: Deliver idiomatic Java.
      specialization:
        primary-focus: Java implementation
      specialization-context:
        languages:
          - java
      """
    And the built-in pack "built-in" with the file "agent_profile.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'agent_profile:java-jenny', target: 'agent_profile:implementer-ivan', relation: specializes_from}
      """
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny"]}}
      """
    When I GET "/activations/b/artifacts/agent_profile/java-jenny"
    Then the response status is 200
    And the field at "/lineage/0/id" is "implementer-ivan"
    And the field at "/lineage/0/pack" is "built-in"
    And the field at "/lineage/0/depth" is 1
    And the field at "/agentProfile/capabilities/0" is "code-implementation"
    And the field at "/agentProfile/maxConcurrentTasks" is 5
    And the field at "/agentProfile/description" is "Java-specialist implementer"
    And the field at "/agentProfile/name" is "Java Jenny"
    And the field at "/agentProfile/purpose" is "Deliver idiomatic Java."
    And the field at "/agentProfile/routingPriority" is absent
    And the field at "/agentProfile/specialization/primaryFocus" is "Java implementation"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/agentProfile/capabilities" holds exactly 0 items
    And the field at "/agentProfile/maxConcurrentTasks" is absent
