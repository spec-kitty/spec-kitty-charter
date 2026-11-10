@contract
Feature: Failures outside the charter's refusals
  A request the service cannot answer for a reason that is not a charter refusal is answered with
  a Problem body too, never with a framework's own error document. A 4xx Problem carries the
  title and the status of the HTTP status and nothing else: no code, because no refusal applies.
  Every 500 is a CharterRefusal: an unexpected failure is refused with internal_error and a
  generic detail that says nothing about the failure. A read whose packs change while it is
  answered is refused with stale_input, never answered from a mix of old and new files, and no
  refusal carries a Charter-Source-Fingerprint header.

  Background:
    Given the built-in pack "built-in" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: mission_type:software-dev
        kind: mission_type
        label: software-dev
      - urn: action:software-dev/plan
        kind: action
        label: plan
      edges: []
      """

  Scenario: A path the contract does not define is answered with a not-found problem
    When I GET "/pack"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Not Found", "status": 404}
      """

  Scenario: A request that accepts no representation the operation has is answered with a not-acceptable problem
    The problem is served as application/problem+json although the request does not accept it,
    as RFC 9457 allows for an error response.
    When I GET "/info" accepting "text/csv"
    Then the response status is 406
    And the response body is:
      """
      {"title": "Not Acceptable", "status": 406}
      """

  Scenario: An unexpected failure while the packs are read is refused with internal_error
    Given the service fails unexpectedly while it reads the packs
    When I GET "/info"
    Then the response status is 500
    And the problem code is "internal_error"
    And the response body is:
      """
      {"title": "Internal error", "status": 500, "code": "internal_error", "detail": "The charter service failed unexpectedly; the failure is logged."}
      """

  Scenario: An input the service cannot read is refused with pack_unreadable
    A directory stands where config.yaml should be a file, so reading it fails with an I/O error
    whoever runs the service (a file made unreadable by its permissions would still be readable to
    root). The input is one the service digests, not a seam: nothing about it changes while the
    read is answered, so the refusal is the first digest's own and permanent, not stale_input. It
    names the file relative to the project configuration directory and says nothing of the host.
    Given the project configuration file "config.yaml" is a directory
    When I GET "/info"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response body, apart from "detail", is:
      """
      {"title": "Pack unreadable", "status": 500, "code": "pack_unreadable"}
      """
    And the field "detail" of the response body starts with "project-config/config.yaml cannot be read"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A service info whose inputs change while it is answered is refused with stale_input
    /info digests twice and answers only the digest both reads agree on. A pack file rewritten between the two
    digests, or one the second digest can no longer read, makes the answer stale. The same read, asked again,
    answers.
    Given a pack file changes while the next read is answered
    When I GET "/info"
    Then the response status is 500
    And the problem code is "stale_input"
    And the response body is:
      """
      {"title": "Stale input", "status": 500, "code": "stale_input", "detail": "The inputs changed while the service info was read; read it again."}
      """
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/info"
    Then the response status is 200

  Scenario: A read that watches for a change and sees none answers with the digest it verified
    The negative control of the stale_input scenario: the same seam, armed to change nothing.
    Given no pack file changes while the next read is answered
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "Charter-Source-Fingerprint" is the remembered "sourceFingerprint"

  Scenario: governance_policy_violation is raised by the registration writes (#293)
    A declaration, not behavior. The registration writes refuse a write the mutability level of the
    pack that owns its target does not permit (ADR-0012, epic #55). Each pack's policy.yaml is read
    (ADR-0063); no open pack ships one yet, so every target here is open and no request here can provoke
    the refusal; the unit tests of PackMutabilityPolicy and of the registration services pin the behavior, and
    ReservedRefusalCodesTest fails while the contract marks a code not raised that a main class names.
    Then the contract declares the refusal code "governance_policy_violation"
    And the contract says "governance_policy_violation" is answered with status 409
    And the contract says "governance_policy_violation" may not be retried
    And the contract says "governance_policy_violation" is raised by an operation

  Scenario: activation_not_found is raised by the activation register
    A declaration pin: the activation register raises the code (#125, #360), and the scenarios of
    activation-register.feature pin the behavior. ReservedRefusalCodesTest fails when the contract marks
    it raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "activation_not_found"
    And the contract says "activation_not_found" is answered with status 404
    And the contract says "activation_not_found" may not be retried
    And the contract says "activation_not_found" is raised by an operation

  Scenario: artifact_not_found is raised by the per-artifact activation writes
    A declaration pin: the select, unselect, deactivate and re-activate of an activation raise the code when
    the path names an artifact the activation's closure does not hold (#291), and the scenarios of
    activation-selections.feature pin the behavior. ReservedRefusalCodesTest fails when the contract marks it
    raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "artifact_not_found"
    And the contract says "artifact_not_found" is answered with status 404
    And the contract says "artifact_not_found" may not be retried
    And the contract says "artifact_not_found" is raised by an operation

  Scenario: mission_type_not_found is raised by the select and unselect of a mission type
    A declaration pin: a select or an unselect of kind mission_type raises the code when the path names a
    mission type the activation's closure does not hold (#291), and activation-selections.feature pins the
    behavior. Nothing here calls the service.
    Then the contract declares the refusal code "mission_type_not_found"
    And the contract says "mission_type_not_found" is answered with status 404
    And the contract says "mission_type_not_found" may not be retried
    And the contract says "mission_type_not_found" is raised by an operation

  Scenario: preset_not_found is raised by the preset application
    A declaration pin: applying a preset raises the code when the path names a preset no pack of the
    activation's closure holds, and activation-presets.feature pins the behavior. ReservedRefusalCodesTest
    fails when the contract marks it raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "preset_not_found"
    And the contract says "preset_not_found" is answered with status 404
    And the contract says "preset_not_found" may not be retried
    And the contract says "preset_not_found" is raised by an operation

  Scenario: artifact_required is raised by the activation register
    A declaration pin: the activation register raises the code (#125, #360), and the scenarios of
    activation-register.feature pin the behavior. ReservedRefusalCodesTest fails when the contract marks
    it raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "artifact_required"
    And the contract says "artifact_required" is answered with status 409
    And the contract says "artifact_required" may not be retried
    And the contract says "artifact_required" is raised by an operation

  Scenario: artifact_locked is raised by the activation register
    A declaration pin: the activation register raises the code (#125, #360), and the scenarios of
    activation-register.feature pin the behavior. ReservedRefusalCodesTest fails when the contract marks
    it raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "artifact_locked"
    And the contract says "artifact_locked" is answered with status 409
    And the contract says "artifact_locked" may not be retried
    And the contract says "artifact_locked" is raised by an operation

  Scenario: activation_invalid is raised by the activation register
    A declaration pin: the activation register raises the code (#125, #360), and the scenarios of
    activation-register.feature pin the behavior. ReservedRefusalCodesTest fails when the contract marks
    it raised and no main class names it. Nothing here calls the service.
    Then the contract declares the refusal code "activation_invalid"
    And the contract says "activation_invalid" is answered with status 422
    And the contract says "activation_invalid" may not be retried
    And the contract says "activation_invalid" is raised by an operation

  Scenario: The refusal codes the contract marks raised are exactly those an operation raises
    The contract's machine-readable x-refusal map agrees with the behavior of this suite: each
    refusal a scenario observes has the status and the raised flag the contract declares for its
    code, and these are all the codes raised. The generic item read adds the not-found code of each
    served kind (tactic_not_found, directive_not_found, styleguide_not_found, paradigm_not_found and
    toolguide_not_found) and kind_not_found; the registration writes add validation_failed,
    registration_conflict and governance_policy_violation, and the whole-pack registration service adds
    pack_version_conflict and artifact_referenced, and its impact check adds override_not_permitted, enhances_cycle,
    conflicting_relations, self_override and override_cycle (ReservedRefusalCodesTest holds the flag to the main code that
    names the code), and the registration job and its admission add registration_not_found, pack_source_unavailable,
    registration_timed_out and pack_storage_full, the https_archive, filesystem and git source fetchers add
    pack_source_refused, and the two DELETEs add relationship_not_found and relationship_not_registered (and
    artifact_referenced, the code of the removal of an artifact another pack refers to). action_not_found is raised by
    the effective action context (ADR-0020 D7, PR C), which answers it with 404 when the merged closure holds no such
    action of the mission type. unresolved_edge_endpoint and pack_level_mismatch are raised by no
    operation. drg_schema_error, prompt_fragment_invalid, prompt_slot_malformed and prompt_slot_not_empty are
    violation codes of a refused registration (422 validation_failed, each at the pack-relative file of the fault),
    which the amended definition of raised counts: the whole-pack load that the registration runs refuses a graph that
    breaks its schema as drg_schema_error, a step enhancement or fragment that breaks a rule as
    prompt_fragment_invalid, and a step prompt whose slot declaration is malformed, or whose slot holds text under
    schema_version 1.0, as prompt_slot_malformed and prompt_slot_not_empty. The activation
    register adds activation_not_found, activation_invalid, artifact_required and artifact_locked, its
    per-artifact writes add artifact_not_found and mission_type_not_found, and the preset application adds
    preset_not_found.
    Then the contract says exactly these refusal codes are raised by an operation:
      | pack_unreadable             |
      | internal_error              |
      | stale_input                 |
      | pack_not_found              |
      | tactic_not_found            |
      | directive_not_found         |
      | styleguide_not_found        |
      | paradigm_not_found          |
      | toolguide_not_found         |
      | kind_not_found              |
      | validation_failed           |
      | registration_conflict       |
      | governance_policy_violation |
      | pack_version_conflict       |
      | artifact_referenced         |
      | relationship_not_found      |
      | relationship_not_registered |
      | override_not_permitted      |
      | enhances_cycle              |
      | conflicting_relations       |
      | self_override               |
      | override_cycle              |
      | registration_not_found      |
      | pack_source_refused         |
      | pack_source_unavailable     |
      | registration_timed_out      |
      | pack_storage_full           |
      | pack_dependency_cycle       |
      | pack_dependency_unknown     |
      | pack_dependency_out_of_range |
      | pack_parent_unresolved      |
      | pack_level_violation        |
      | pack_name_conflict          |
      | pack_duplicate_id           |
      | pack_sibling_conflict |
      | pack_reference_unqualified  |
      | pack_reference_pack_missing |
      | pack_reference_artifact_missing |
      | activation_not_found        |
      | activation_invalid          |
      | artifact_required           |
      | artifact_locked             |
      | artifact_not_found          |
      | mission_type_not_found      |
      | action_not_found            |
      | preset_not_found            |
      | drg_schema_error            |
      | prompt_fragment_invalid     |
      | prompt_slot_malformed       |
      | prompt_slot_not_empty       |
      | mission_step_contract_not_found |
      | skeleton_not_found |
