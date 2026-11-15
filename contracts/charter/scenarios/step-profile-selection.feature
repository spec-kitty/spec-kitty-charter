# WP08 of slice G (#538): the step-keyed selection read, selectAgentProfileForStep (US6, FR-012), and the
# profile-selection link of the effective step read and the action context. On charter's own example packs (ADR-0070):
# built-in's software-dev names implementer on implement, reviewer on review and no role on specify; org-base, which
# depends on built-in, adds reviewer-rita, a second root reviewer, so the reviewer selection of the org-base context is a
# tie. built-in declares the fallback generic-agent and locks it, so it is in force in every context.
#
# A step names a role and never a profile, and a substep never delegates to one (slice F), so the step's answer is the
# role read's answer for that role: each scenario that selects for a step asks the role read too, in the same context,
# and both must answer the same candidates.

@contract @activations @agent-profiles
Feature: The profile of a step is selected by the role it names under an activation
  GET /activations/{activationId}/mission-types/{missionType}/actions/{action}/profile-selection answers what
  selectAgentProfile answers for the role the step names, in the same context: a client never reads the step's role
  to ask again. A step that names no role is one any role may act on, so the fallback in force answers it. An unknown
  mission type or step is the 404 of the other step reads, and the effective step read and the action context link
  the step's selection as profile-selection.

  Scenario: Org-base - the review step answers the reviewer selection of the context, the tie of renata and rita
    US6, FR-012: software-dev's review names reviewer, and the reviewer selection of org-base's closure is the tie of
    reviewer-renata and reviewer-rita. The role read in the same context answers the same candidates.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the activation "o" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"agent_profile": ["reviewer-renata", "reviewer-renata-org", "reviewer-rita", "human-operator"]}}
      """
    When I GET "/activations/o/mission-types/software-dev/actions/review/profile-selection"
    Then the response status is 200
    And the response body is:
      """
      {
        "role": "reviewer",
        "context": {},
        "candidates": [
          {"id": "reviewer-renata", "pack": "built-in", "fit": "primary",
           "_links": {"effective-artifact": {"href": "/api/v1/activations/o/artifacts/agent_profile/reviewer-renata"}}},
          {"id": "reviewer-rita", "pack": "org-base", "fit": "primary",
           "_links": {"effective-artifact": {"href": "/api/v1/activations/o/artifacts/agent_profile/reviewer-rita"}}}
        ],
        "tie": true,
        "_links": {"self": {"href": "/api/v1/activations/o/mission-types/software-dev/actions/review/profile-selection"}}
      }
      """
    And the response header "Charter-Source-Fingerprint" is a sha256 digest
    And the response header "ETag" appears once
    When I GET "/activations/o/roles/reviewer/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      reviewer-renata in built-in as primary
      reviewer-rita in org-base as primary
      """

  Scenario: Built-in - the implement step answers implementer-ivan, and for Java work java-jenny, as the role read does
    software-dev's implement names implementer. With no language asked, the root implementer-ivan answers; the asked
    context reaches the selection unchanged, so java-jenny answers for Java work, as the role read answers.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      implementer-ivan in built-in as primary
      """
    And the field at "/role" is "implementer"
    And the field at "/tie" is false
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection?language=java"
    Then the response status is 200
    And the selection answers exactly:
      """
      java-jenny in built-in as primary
      """
    And the field at "/context/language" is "java"
    And the field at "/_links/self/href" is "/api/v1/activations/b/mission-types/software-dev/actions/implement/profile-selection?language=java"
    When I GET "/activations/b/roles/implementer/profile-selection?language=java"
    Then the response status is 200
    And the selection answers exactly:
      """
      java-jenny in built-in as primary
      """

  Scenario: Built-in - a step that names no role answers the fallback in force, and carries no role
    US6: software-dev's specify names no role, so any role may act on it; no role fit narrows the candidates, and the
    fallback generic-agent answers with fit fallback. The answer has no role.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/mission-types/software-dev/actions/specify/profile-selection"
    Then the response status is 200
    And the response body is:
      """
      {
        "context": {},
        "candidates": [
          {"id": "generic-agent", "pack": "built-in", "fit": "fallback",
           "_links": {"effective-artifact": {"href": "/api/v1/activations/b/artifacts/agent_profile/generic-agent"}}}
        ],
        "tie": false,
        "_links": {"self": {"href": "/api/v1/activations/b/mission-types/software-dev/actions/specify/profile-selection"}}
      }
      """

  Scenario: An unknown step or mission type is the 404 of the other step reads, judged before any If-None-Match
    The step is judged against the merged closure as getEffectiveActionContext judges it: an unknown step is
    action_not_found and an unknown mission type mission_type_not_found, also against If-None-Match: *, which matches
    any tag. An unknown activation is judged first.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "reviewer-renata"]}}
      """
    When I GET "/activations/b/mission-types/software-dev/actions/deploy/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "action_not_found"
    And the response has no "ETag" header
    When I GET "/activations/b/mission-types/research-only/actions/review/profile-selection"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/unknown/mission-types/software-dev/actions/deploy/profile-selection"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/b/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "action_not_found"

  Scenario: A matching If-None-Match is a 304; the tag names the step and the context, never the role read's
    The first answer, 200, is the positive control. The same request with its own ETag is a 304 without a body; another
    step, another context and the role read of the same role each answer 200 under another tag.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response body is empty
    And the response header "ETag" is the remembered header
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection?language=java" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/b/mission-types/software-dev/actions/review/profile-selection" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/b/roles/implementer/profile-selection" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

  Scenario: The effective step read links the step's profile selection, and following it answers the step's role
    getEffectiveMissionStep links profile-selection, the step-keyed selection under the same activation.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/artifacts/mission_type/software-dev/steps/review"
    Then the response status is 200
    And the field at "/_links/profile-selection/href" is "/api/v1/activations/b/mission-types/software-dev/actions/review/profile-selection"
    When I follow the link "profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      reviewer-renata in built-in as primary
      """
    And the field at "/role" is "reviewer"

  Scenario: The action context links the step's profile selection, whether or not the mission type is in force
    getEffectiveActionContext links profile-selection on every answer: the selection reads the profiles in force,
    whatever the mission type's state, so the link is present where prompt, which needs the mission type in force, is
    not. A pack-scoped step read selects no profile and has no such link.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/mission-types/software-dev/actions/specify/context"
    Then the response status is 200
    And the field at "/_links/profile-selection/href" is "/api/v1/activations/b/mission-types/software-dev/actions/specify/profile-selection"
    When I follow the link "profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      generic-agent in built-in as fallback
      """
    And the field at "/role" is absent
    When I GET "/packs/built-in/artifacts/mission_type/software-dev/steps/review"
    Then the response status is 200
    And the answer has no link "profile-selection"

  Scenario: A mission type, step or language outside its pattern is a 400 without a code
    The path and query patterns refuse before the activation is read: a mission type or step is a lower-case slug, and
    a language a lower-case token.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan"]}}
      """
    When I GET "/activations/b/mission-types/Software-Dev/actions/implement/profile-selection"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/b/mission-types/software-dev/actions/Implement/profile-selection"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection?language=Java"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/b/mission-types/software-dev/actions/implement/profile-selection"
    Then the response status is 200

  Scenario: Two unrelated packs that declare different fallbacks refuse a step's selection with fallback_profile_conflict
    As for selectAgentProfile (ADR-0068 decision 5): team-a and team-b build on nothing, and each declares its own
    fallback, so the conflict is judged after the step and before any profile is matched, also against
    If-None-Match: *. The step sort of team-a's triage names reviewer, which reviewer-ada fills; the control
    activation holds team-a alone and answers her.
    Given the org pack "team-a" with the file "pack.yaml":
      """
      pack_id: 01M7DFBF00000000000000TMA1
      pack_version: 1.0.0
      parent_pack: null
      name: team-a
      pack_level: org
      fallback_profile: fallback-a
      """
    And the org pack "team-a" with the file "agent_profiles/fallback-a.agent.yaml":
      """
      profile-id: fallback-a
      name: Fallback A
      role: generic
      purpose: Serve team A when no profile fits.
      specialization:
        primary-focus: Any work
      """
    And the org pack "team-a" with the file "agent_profiles/reviewer-ada.agent.yaml":
      """
      profile-id: reviewer-ada
      name: Reviewer Ada
      role: reviewer
      purpose: Review team A's work.
      specialization:
        primary-focus: Review
      """
    And the org pack "team-a" with the file "missions/triage/triage.mission-type.yaml":
      """
      schema_version: "1.0"
      id: triage
      title: Triage
      description: Sort the incoming work
      steps:
        - id: sort
          title: Sort
          role: reviewer
      """
    And the org pack "team-a" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:triage/sort
        kind: action
        label: sort
      edges:
      - source: mission_type:triage
        target: action:triage/sort
        relation: requires
      """
    And the org pack "team-b" with the file "pack.yaml":
      """
      pack_id: 01M7DFBF00000000000000TMB1
      pack_version: 1.0.0
      parent_pack: null
      name: team-b
      pack_level: org
      fallback_profile: fallback-b
      """
    And the org pack "team-b" with the file "agent_profiles/fallback-b.agent.yaml":
      """
      profile-id: fallback-b
      name: Fallback B
      role: generic
      purpose: Serve team B when no profile fits.
      specialization:
        primary-focus: Any work
      """
    And the activation "both" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "team-a"}, {"packId": "team-b"}], "selected": {"agent_profile": ["reviewer-ada", "fallback-a", "fallback-b"]}}
      """
    And the activation "one" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "team-a"}], "selected": {"agent_profile": ["reviewer-ada", "fallback-a"]}}
      """
    When I GET "/activations/both/mission-types/triage/actions/sort/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 422
    And the problem code is "fallback_profile_conflict"
    And the field at "/detail" contains "team-a, team-b"
    When I GET "/activations/both/mission-types/triage/actions/gather/profile-selection"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/one/mission-types/triage/actions/sort/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      reviewer-ada in team-a as primary
      """
    And the field at "/role" is "reviewer"

  Scenario: A step that names no role, with no fallback in force, is no_profile_matched
    US6: any role may act on the step intake of team-c's triage, so only the fallback in force could answer it, and
    team-c declares none; the refusal comes before If-None-Match: *. The step sort of the same type names reviewer,
    which reviewer-cy fills (the control).
    Given the org pack "team-c" with the file "pack.yaml":
      """
      pack_id: 01M7DFBF00000000000000TMC1
      pack_version: 1.0.0
      parent_pack: null
      name: team-c
      pack_level: org
      """
    And the org pack "team-c" with the file "agent_profiles/reviewer-cy.agent.yaml":
      """
      profile-id: reviewer-cy
      name: Reviewer Cy
      role: reviewer
      purpose: Review team C's work.
      specialization:
        primary-focus: Review
      """
    And the org pack "team-c" with the file "missions/triage/triage.mission-type.yaml":
      """
      schema_version: "1.0"
      id: triage
      title: Triage
      description: Sort the incoming work
      steps:
        - id: intake
          title: Intake
        - id: sort
          title: Sort
          role: reviewer
      """
    And the org pack "team-c" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:triage/intake
        kind: action
        label: intake
      - urn: action:triage/sort
        kind: action
        label: sort
      edges:
      - source: mission_type:triage
        target: action:triage/intake
        relation: requires
      - source: mission_type:triage
        target: action:triage/sort
        relation: requires
      """
    And the activation "c" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "team-c"}], "selected": {"agent_profile": ["reviewer-cy"]}}
      """
    When I GET "/activations/c/mission-types/triage/actions/intake/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "no_profile_matched"
    And the field at "/detail" contains "triage/intake"
    And the response has no "ETag" header
    When I GET "/activations/c/mission-types/triage/actions/sort/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      reviewer-cy in team-c as primary
      """
