# A refusal of the closure checks names every fault (#556, ADR-0075 slice 7; US1 AS1 and AS2, US2 AS1 to AS3 and US4
# AS1 of closure-checks-behind-ports). A registration that breaks several rules answers one problem: the first fault's
# code and status, a detail that counts the faults, and a header violation per fault (its code and detail), so the
# author fixes them all in one round. A single fault answers exactly as before (SC-002). The packs are inline archives,
# as in whole-pack-registration.feature, and the steps are its registration steps.
#
# State: bound, with no @WIP tag; the operations are built.

@contract @registration
Feature: A registration names every fault of its closure in one refusal
  A pack author registers a pack, PUT /packs/{packId} with the archive as the body, and polls the registration until
  it ends. When the closure checks refuse it, the refused status carries one problem for every fault they found: the
  relation rules, the fallback profile and role-pin rules of ADR-0068 and ADR-0030 ruling D3, and the mutability level of each
  write (ADR-0012). Each refusal is paired with the pack that registers once its faults are fixed.

  Background:
    Given registration is switched on
    And the archive "acme" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the archive "acme" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:A: locked
        directive:B: locked
        directive:C: open
      """
    And the archive "acme" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A
      intent: A directive acme locks
      enforcement: required
      """
    And the archive "acme" holds the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B
      intent: Another directive acme locks
      enforcement: required
      """
    And the archive "acme" holds the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C
      intent: A directive acme leaves open
      enforcement: required
      """
    And the archive "derived" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: null
      name: derived
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "derived" holds the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: A directive derived holds
      enforcement: required
      """
    And the archive "derived" holds the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: Another directive derived holds
      enforcement: required
      """
    When I PUT "/packs/acme" with the archive "acme"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # US1 AS1: a self-override and an enhances cycle, in the order the relation rules judge them
  Scenario: A pack whose relations break two rules is refused once, naming both faults
    Given the archive "two-faults" is a copy of the archive "derived"
    And the archive "two-faults" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'acme::directive:C', target: 'acme::directive:C', relation: overrides}
      - {source: 'directive:D', target: 'directive:E', relation: enhances}
      - {source: 'directive:E', target: 'directive:D', relation: enhances}
      """
    And the archive "sound" is a copy of the archive "derived"
    And the archive "sound" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:D', target: 'directive:E', relation: enhances}
      """
    When I PUT "/packs/derived" with the archive "two-faults"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "self_override"
    And the field at "/problem/detail" contains "(2 fault(s); every one is in violations)"
    And the field at "/problem/violations" holds exactly 2 item(s)
    And the field at "/problem/violations/0/code" is "self_override"
    And the field at "/problem/violations/1/code" is "enhances_cycle"
    When I GET "/packs/derived"
    Then the response status is 404
    When I PUT "/packs/derived" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # US1 AS2 (SC-002): one fault answers as it did before, with no violation list
  Scenario: A pack whose relations break one rule is refused as before
    Given the archive "one-fault" is a copy of the archive "derived"
    And the archive "one-fault" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:D', target: 'directive:E', relation: enhances}
      - {source: 'directive:E', target: 'directive:D', relation: enhances}
      """
    When I PUT "/packs/derived" with the archive "one-fault"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "enhances_cycle"
    And the field at "/problem/violations" is absent
    And the field at "/problem/packs/0/packId" is "derived"

  # US2 AS1 and AS3: a fallback profile that is neither locked nor role-neutral, and a role pin to an unknown profile
  Scenario: A pack whose fallback profile breaks two checks and whose role pin breaks one is refused once, naming all three
    Given the archive "fallback" is a copy of the archive "derived"
    And the archive "fallback" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: null
      name: derived
      pack_level: org
      mutability_policy: policy.yaml
      fallback_profile: generic-agent
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "fallback" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        agent_profile:generic-agent: extendable
      role_pins:
        reviewer: reviewer-rex
      """
    And the archive "fallback" holds the file "agent_profiles/generic-agent.agent.yaml":
      """
      profile-id: generic-agent
      name: Generic agent
      role: implementer
      purpose: Serve when no profile fits the asked role.
      specialization:
        primary-focus: Any work
      """
    And the archive "sound" is a copy of the archive "fallback"
    And the archive "sound" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        agent_profile:generic-agent: locked
      """
    And the archive "sound" holds the file "agent_profiles/generic-agent.agent.yaml":
      """
      profile-id: generic-agent
      name: Generic agent
      role: generic
      purpose: Serve when no profile fits the asked role.
      specialization:
        primary-focus: Any work
      """
    When I PUT "/packs/derived" with the archive "fallback"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "fallback_profile_not_locked"
    And the field at "/problem/detail" contains "(3 fault(s); every one is in violations)"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "fallback_profile_not_locked"
    And the field at "/problem/violations/1/code" is "fallback_profile_not_role_neutral"
    And the field at "/problem/violations/2/code" is "role_pin_unknown"
    When I GET "/packs/derived"
    Then the response status is 404
    When I PUT "/packs/derived" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # US2 AS2: nothing more is judged of a fallback profile the closure does not hold
  Scenario: A fallback profile no pack holds is its only fallback fault, beside a role pin's
    Given the archive "unknown" is a copy of the archive "derived"
    And the archive "unknown" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: null
      name: derived
      pack_level: org
      mutability_policy: policy.yaml
      fallback_profile: nobody
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "unknown" holds the file "policy.yaml":
      """
      schema_version: 1
      role_pins:
        reviewer: reviewer-rex
      """
    When I PUT "/packs/derived" with the archive "unknown"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "fallback_profile_unknown"
    And the field at "/problem/violations" holds exactly 2 item(s)
    And the field at "/problem/violations/0/code" is "fallback_profile_unknown"
    And the field at "/problem/violations/1/code" is "role_pin_unknown"

  # US4 AS1: every write the levels forbid is one violation of the one 409, in the order of the diff
  Scenario: A pack that writes two artifacts its dependency locks is refused once, naming both writes
    Given the archive "locked-writes" is a copy of the archive "derived"
    And the archive "locked-writes" holds the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B, rewritten
      intent: A rewrite of a directive acme locks
      enforcement: required
      """
    And the archive "locked-writes" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A, rewritten
      intent: A rewrite of a directive acme locks
      enforcement: required
      """
    When I PUT "/packs/derived" with the archive "locked-writes"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "governance_policy_violation"
    And the field at "/problem/violations" holds exactly 2 item(s)
    And the field at "/problem/violations/0/code" is "governance_policy_violation"
    And the field at "/problem/violations/0/detail" contains "directive:A"
    And the field at "/problem/violations/1/code" is "governance_policy_violation"
    And the field at "/problem/violations/1/detail" contains "directive:B"
    When I GET "/packs/derived"
    Then the response status is 404
    When I PUT "/packs/derived" with the archive "derived"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # The edge-index faults (#639, ADR-0082 slice 4, operator rulings D2, Q-A, Q-B and Q-C): a registration refuses each
  # fault of the closure's layer merge with the code a composing read of the closure answers, here as 422 (ADR-0056).
  # The pack proj, at project level, holds acme's open directive C: a layer override.

  Scenario: An overrides edge that the layer merge re-points to its own source is refused self_override, as the reads refuse it
    Given the archive "proj" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "proj" holds the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C, replaced
      intent: The project's version of acme's open directive
      enforcement: required
      """
    And the archive "self" is a copy of the archive "proj"
    And the archive "self" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:C', target: 'acme::directive:C', relation: overrides}
      """
    When I PUT "/packs/proj" with the archive "self"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "self_override"
    And the field at "/problem/detail" contains "In the closure rooted at proj"
    And the field at "/problem/detail" contains "which resolves to its own source in the merged closure"
    And the field at "/problem/packs/0/packId" is "proj"
    When I GET "/packs/proj"
    Then the response status is 404
    When I PUT "/packs/proj" with the archive "proj"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # #667: the fallback rule reads its profile's winner, which runs the layer merge. The edge-index rules judge the merge
  # first, so the registration answers the fault's 422, never the merge's 500 pack_unreadable.
  Scenario: A pack whose closure declares a fallback profile and a re-pointed self-override is refused 422 self_override
    Given the archive "falling-back" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      mutability_policy: policy.yaml
      fallback_profile: generic-agent
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "falling-back" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        agent_profile:generic-agent: locked
      """
    And the archive "falling-back" holds the file "agent_profiles/generic-agent.agent.yaml":
      """
      profile-id: generic-agent
      name: Generic agent
      role: generic
      purpose: Serve when no profile fits the asked role.
      specialization:
        primary-focus: Any work
      """
    And the archive "falling-back" holds the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C, replaced
      intent: The project's version of acme's open directive
      enforcement: required
      """
    And the archive "sound" is a copy of the archive "falling-back"
    And the archive "falling-back" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:C', target: 'acme::directive:C', relation: overrides}
      """
    When I PUT "/packs/proj" with the archive "falling-back"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "self_override"
    When I PUT "/packs/proj" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # Ruling Q-B (C21): only an open artifact may be replaced by a later level. A new version of acme that locks C, which
  # proj replaces, is refused for proj's closure, as a read of that closure is.
  Scenario: A pack that locks an artifact a later level replaces is refused override_not_permitted for the replacing pack's closure
    Given the archive "proj" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "proj" holds the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C, replaced
      intent: The project's version of acme's open directive
      enforcement: required
      """
    And the archive "acme-locking" is a copy of the archive "acme"
    And the archive "acme-locking" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the archive "acme-open" is a copy of the archive "acme-locking"
    And the archive "acme-locking" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:A: locked
        directive:B: locked
        directive:C: locked
      """
    When I PUT "/packs/proj" with the archive "proj"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/acme" with the archive "acme-locking"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "override_not_permitted"
    And the field at "/problem/detail" contains "In the closure rooted at proj"
    And the field at "/problem/detail" contains "only an open artifact may be replaced by a later level"
    And the field at "/problem/packs/0/packId" is "proj"
    And the field at "/problem/packs/1/packId" is "acme"
    When I PUT "/packs/acme" with the archive "acme-open"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # Ruling Q-C: another pack's requires edge at a mission type that names no step of it is the reference's fault, with
  # the violation mission_step_unlinked, as a read of the closure refuses it.
  Scenario: Another pack's requires edge at a mission type to an action that is no step of it is refused pack_reference_artifact_missing
    Given the archive "rxbase" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXB01
      pack_version: 1.0.0
      parent_pack: null
      name: rxbase
      pack_level: org
      """
    And the archive "rxbase" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      steps:
      - id: a
        title: A
      """
    And the archive "rxbase" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rx/a
        kind: action
        label: a
      - urn: action:rx/c
        kind: action
        label: c
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    And the archive "rxext" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXE01
      pack_version: 1.0.0
      parent_pack: null
      name: rxext
      pack_level: org
      depends_on:
      - pack_id: 01M7B19K0000000000000RXB01
        range: '>=1.0'
      """
    And the archive "linked" is a copy of the archive "rxext"
    And the archive "rxext" holds the file "extra.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'rxbase::mission_type:rx', target: 'rxbase::action:rx/c', relation: requires}
      """
    And the archive "linked" holds the file "extra.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'rxbase::mission_type:rx', target: 'rxbase::action:rx/a', relation: requires}
      """
    When I PUT "/packs/rxbase" with the archive "rxbase"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/rxext" with the archive "rxext"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "pack_reference_artifact_missing"
    And the field at "/problem/detail" contains "names no step of the type"
    And the field at "/problem/violations/0/code" is "mission_step_unlinked"
    And the field at "/problem/packs/0/packId" is "rxbase"
    When I PUT "/packs/rxext" with the archive "linked"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-009: the relation rules gate the edge-index rules, so a closure that breaks both is refused for the relation
  # fault alone, the root cause, and not for its knock-on fault.
  Scenario: A pack that breaks a relation rule and an edge-index rule is refused for the relation fault alone
    Given the archive "proj" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: proj
      pack_level: project
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "proj" holds the file "directives/C.directive.yaml":
      """
      schema_version: '1.0'
      id: C
      title: C, replaced
      intent: The project's version of acme's open directive
      enforcement: required
      """
    And the archive "proj" holds the file "directives/F.directive.yaml":
      """
      schema_version: '1.0'
      id: F
      title: F
      intent: A directive proj holds
      enforcement: required
      """
    And the archive "proj" holds the file "directives/G.directive.yaml":
      """
      schema_version: '1.0'
      id: G
      title: G
      intent: Another directive proj holds
      enforcement: required
      """
    And the archive "proj" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:C', target: 'acme::directive:C', relation: overrides}
      - {source: 'directive:F', target: 'directive:G', relation: enhances}
      - {source: 'directive:G', target: 'directive:F', relation: enhances}
      """
    When I PUT "/packs/proj" with the archive "proj"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "enhances_cycle"
    And the field at "/problem/violations" is absent
