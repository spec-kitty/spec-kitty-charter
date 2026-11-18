# WP05 of slice G (#538): the selection read, selectAgentProfile (ruling D1; US1 to US3, FR-001, FR-002, FR-005, SC-001,
# SC-002). One scenario per row of the plan's example table, on charter's own example packs (ADR-0070), then the
# conflicts on test packs authored inline, the 304, and the 400 of the reserved role generic.
#
# The three contexts are the example packs' closures: built-in alone; org-base, which depends on built-in and adds
# reviewer-rita (a second root reviewer, so the org-base context shows the tie), human-operator (performer human, role
# operator) and reviewer-renata-org (an enhancer of reviewer-renata, never a candidate); and project, which depends on
# org-base, adds project-reviewer (primary reviewer, alt implementer) and pins reviewer to reviewer-renata in its
# policy.yaml. built-in declares the fallback generic-agent and locks it, so it is in force in every context; project
# names the same fallback, so there is no conflict. Every profile a scenario reads is selected by its activation.
#
# Steps this file adds to the vocabulary of README.md (bound in ProfileSteps):
# - `Then the selection answers exactly:` followed by a doc string, one candidate of the answer per line, in the
#   answer's order: `<id> in <pack> as <fit>`. Each candidate's `effective-artifact` link must be the read of that
#   profile under the activation of the request.

@contract @activations @agent-profiles
Feature: The profile that fills a role is selected under an activation
  GET /activations/{activationId}/roles/{role}/profile-selection answers which agent profile in force fills the role:
  the pin narrows the candidates first, then a human-performed profile serves only operator or a role pinned to it,
  then role fit, the context of a specialization, the tier, the longest specializes_from chain and routing-priority;
  every candidate left is answered, marked a tie when there is more than one; with none left, the fallback in force,
  never for operator. An enhancer is never a candidate. The refusals come in one order: the activation, the conflicts
  of the closure (the fallback's, then the pin's), then no_profile_matched, each before any If-None-Match.

  Scenario: Built-in - implementer is implementer-ivan, a primary fit; his language specializations do not fit an unasked context
    US1.1, ADR-0069 decision 4: implementer-ivan takes implementer as his primary role. java-jenny and python-pedro
    specialize him and declare languages, so with no language asked neither fits (ruling D2), and the root answers.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/implementer/profile-selection"
    Then the response status is 200
    And the response body is:
      """
      {
        "role": "implementer",
        "context": {},
        "candidates": [
          {"id": "implementer-ivan", "pack": "built-in", "fit": "primary",
           "_links": {"effective-artifact": {"href": "/api/v1/activations/b/artifacts/agent_profile/implementer-ivan"}}}
        ],
        "tie": false,
        "_links": {"self": {"href": "/api/v1/activations/b/roles/implementer/profile-selection"}}
      }
      """
    And the response header "Charter-Source-Fingerprint" is a sha256 digest
    And the response header "ETag" appears once

  Scenario: Built-in - implementer for Java work is java-jenny, the deeper specialization within the tier
    US1.2, ruling D2: java-jenny declares java and specializes implementer-ivan, so she is one specializes_from hop
    deeper than him; python-pedro declares python and does not fit.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/implementer/profile-selection?language=java"
    Then the response status is 200
    And the selection answers exactly:
      """
      java-jenny in built-in as primary
      """
    And the field at "/tie" is false
    And the field at "/context/language" is "java"
    And the field at "/context/framework" is absent
    And the field at "/_links/self/href" is "/api/v1/activations/b/roles/implementer/profile-selection?language=java"

  Scenario: Built-in - implementer for Kotlin work is implementer-ivan: a root fits any context
    Ruling D2: both specializations declare a language other than kotlin, so neither fits; the root declares languages
    too, and a root is never judged by the context.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/implementer/profile-selection?language=kotlin"
    Then the response status is 200
    And the selection answers exactly:
      """
      implementer-ivan in built-in as primary
      """
    And the field at "/context/language" is "kotlin"

  Scenario: Built-in - facilitator, a role no profile fills, is the fallback generic-agent
    US1.4, ADR-0068: no profile in force takes facilitator, so the fallback in force answers, marked fallback. built-in
    locks generic-agent, so the activation puts it in force without selecting it.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/facilitator/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      generic-agent in built-in as fallback
      """
    And the field at "/tie" is false

  Scenario: Built-in - operator with no human-performed profile is no_profile_matched, never the fallback
    US1.5, US3.2, ruling D4: the role operator is served only by a human-performed profile, and it never falls back, so
    the fallback in force is not answered. The same activation answers facilitator with the fallback (the control).
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/operator/profile-selection"
    Then the response status is 404
    And the problem code is "no_profile_matched"
    And the response has no "ETag" header
    When I GET "/activations/b/roles/facilitator/profile-selection"
    Then the response status is 200

  Scenario: Org-base - reviewer is the tie of reviewer-renata and reviewer-rita; the enhancer reviewer-renata-org is no candidate
    US1.3, SC-001: both are roots that take reviewer as their primary role at the default routing-priority, so both are
    answered, built-in's first (closure position, then id), and the answer is marked a tie. reviewer-renata-org takes
    reviewer too and is in force, but it enhances reviewer-renata, so it is never a candidate.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the activation "o" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"agent_profile": ["reviewer-renata", "reviewer-renata-org", "reviewer-rita", "human-operator"]}}
      """
    When I GET "/activations/o/roles/reviewer/profile-selection"
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
        "_links": {"self": {"href": "/api/v1/activations/o/roles/reviewer/profile-selection"}}
      }
      """
    And following the link "effective-artifact" of every element of "candidates" answers 200

  Scenario: Org-base - operator is human-operator, a human-performed primary fit
    US3.1, ruling D4: human-operator is performer human and takes operator, the one role a human serves unpinned.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the activation "o" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "org-base"}], "selected": {"agent_profile": ["reviewer-renata", "reviewer-renata-org", "reviewer-rita", "human-operator"]}}
      """
    When I GET "/activations/o/roles/operator/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      human-operator in org-base as primary
      """
    And the field at "/tie" is false

  Scenario: Project - reviewer is reviewer-renata, by the project's pin; project-reviewer and reviewer-rita are no candidates
    US2.1, ruling D3: project pins reviewer to reviewer-renata in its policy.yaml, so only she and her specializations
    remain; project-reviewer and reviewer-rita take reviewer as their primary role too, and the org-base tie is gone.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the example pack "org-alt" of the charter
    And the example pack "project" of the charter
    And the activation "p" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata", "reviewer-rita", "project-reviewer"]}}
      """
    When I GET "/activations/p/roles/reviewer/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      reviewer-renata in built-in as primary
      """
    And the field at "/tie" is false

  Scenario: Project - implementer is implementer-ivan: project-reviewer's alt fit loses to a primary fit
    US1.1, ADR-0069 decision 4: project-reviewer lists implementer among its alt-roles, and an alt fit never outranks a
    primary one.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the example pack "org-alt" of the charter
    And the example pack "project" of the charter
    And the activation "p" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata", "reviewer-rita", "project-reviewer"]}}
      """
    When I GET "/activations/p/roles/implementer/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      implementer-ivan in built-in as primary
      """

  Scenario: Project - with every primary implementer out of force, project-reviewer's alt fit answers, not the fallback
    US1.7 (squad M1): the activation selects project-reviewer alone, so the alt fit is the best tier left and it is
    answered marked alt; the fallback answers only when no candidate is left.
    Given the example pack "built-in" of the charter
    And the example pack "org-base" of the charter
    And the example pack "org-alt" of the charter
    And the example pack "project" of the charter
    And the activation "p" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "project"}], "selected": {"agent_profile": ["project-reviewer"]}}
      """
    When I GET "/activations/p/roles/implementer/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      project-reviewer in project as alt
      """

  Scenario: A matching If-None-Match is a 304 with the same tag, and the tag of another role or context never matches
    The first answer, 200, is the positive control. The same request with its own ETag is a 304 without a body; the tag
    names the role and the asked context, so another role and another language each answer 200 under another tag.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan", "java-jenny", "python-pedro", "reviewer-renata"]}}
      """
    When I GET "/activations/b/roles/implementer/profile-selection"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "/activations/b/roles/implementer/profile-selection" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response body is empty
    And the response header "ETag" is the remembered header
    When I GET "/activations/b/roles/implementer/profile-selection?language=java" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/b/roles/reviewer/profile-selection" with If-None-Match the remembered header "ETag"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header

  Scenario: The reserved role generic is a 400 without a code; an unknown activation is activation_not_found before any 304
    generic marks a fallback profile and fits no role (ADR-0068), so the path pattern refuses it. An unknown activation
    is judged first, also against If-None-Match: *, which matches any tag.
    Given the example pack "built-in" of the charter
    And the activation "b" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "built-in"}], "selected": {"agent_profile": ["implementer-ivan"]}}
      """
    When I GET "/activations/b/roles/generic/profile-selection"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/b/roles/Reviewer/profile-selection"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/unknown/roles/reviewer/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/b/roles/operator/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "no_profile_matched"

  Scenario: Two unrelated packs that declare different fallbacks are fallback_profile_conflict, even when a profile matches
    US1.6, ADR-0068 decision 5: team-a and team-b build on nothing, so neither is derived from the other, and each
    declares its own fallback. The conflict is judged before matching, so the role reviewer, which reviewer-ada fills,
    is refused too, and so is a request with If-None-Match: *. The control activation holds team-a alone and answers.
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
    When I GET "/activations/both/roles/facilitator/profile-selection"
    Then the response status is 422
    And the problem code is "fallback_profile_conflict"
    And the field at "/detail" contains "team-a, team-b"
    When I GET "/activations/both/roles/reviewer/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 422
    And the problem code is "fallback_profile_conflict"
    When I GET "/activations/one/roles/facilitator/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      fallback-a in team-a as fallback
      """

  Scenario: Two packs that pin the asked role to different profiles are role_pin_conflict; another role is answered
    US2.4, ruling D3: team-a pins reviewer to reviewer-ada and team-b to reviewer-bo, and the two meet in one activation.
    Pins are tighten-only, so the selection for reviewer is refused, even against If-None-Match: *. The pins name
    reviewer alone, so implementer, which implementer-ida fills, is answered (the control).
    Given the org pack "team-a" with the file "pack.yaml":
      """
      pack_id: 01M7DFBF00000000000000TMA1
      pack_version: 1.0.0
      parent_pack: null
      name: team-a
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "team-a" with the file "policy.yaml":
      """
      schema_version: 1
      role_pins:
        reviewer: reviewer-ada
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
    And the org pack "team-a" with the file "agent_profiles/implementer-ida.agent.yaml":
      """
      profile-id: implementer-ida
      name: Implementer Ida
      role: implementer
      purpose: Implement team A's work.
      specialization:
        primary-focus: Implementation
      """
    And the org pack "team-b" with the file "pack.yaml":
      """
      pack_id: 01M7DFBF00000000000000TMB1
      pack_version: 1.0.0
      parent_pack: null
      name: team-b
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "team-b" with the file "policy.yaml":
      """
      schema_version: 1
      role_pins:
        reviewer: reviewer-bo
      """
    And the org pack "team-b" with the file "agent_profiles/reviewer-bo.agent.yaml":
      """
      profile-id: reviewer-bo
      name: Reviewer Bo
      role: reviewer
      purpose: Review team B's work.
      specialization:
        primary-focus: Review
      """
    And the activation "both" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "team-a"}, {"packId": "team-b"}], "selected": {"agent_profile": ["reviewer-ada", "reviewer-bo", "implementer-ida"]}}
      """
    When I GET "/activations/both/roles/reviewer/profile-selection" with the header "If-None-Match": "*"
    Then the response status is 422
    And the problem code is "role_pin_conflict"
    And the field at "/detail" contains "team-a, team-b"
    And the field at "/detail" contains "reviewer"
    When I GET "/activations/both/roles/implementer/profile-selection"
    Then the response status is 200
    And the selection answers exactly:
      """
      implementer-ida in team-a as primary
      """
