# An agent profile's references are judged at registration (#673; operator rulings of 2026-11-26). A profile names the
# doctrine it is handed in its selection data (ADR-0064 decision 11): its directive-references codes, its
# tactic-references, toolguide-references and styleguide-references ids, and its collaboration.operating-procedures.
# Each names an artifact of its list's kind by the artifact's id: bare inside the profile's own pack, <pack>::<id>
# across packs, or with the kind written out, <pack>::<kind>:<id>. A directive code is the directive's id, never a part
# of it. These references are judged as a graph edge's endpoints are (ADR-0053 decision 7):
# pack_reference_unqualified, pack_reference_pack_missing and pack_reference_artifact_missing. They mint no edge.
# pack-doctor validate --full answers the same codes for the same packs (PackDoctorMainTest).
#
# Steps: the existing whole-pack registration steps only (scenarios/README.md).

@contract @registration
Feature: An agent profile's references are judged at registration
  A pack whose agent profile names another pack's artifact bare, names a pack outside its depends_on closure, or names
  an artifact the named pack does not hold, is refused and stores nothing. The same profile registers once each
  reference names the pack that holds the artifact.

  Background:
    Given registration is switched on
    And the archive "base" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: base
      pack_level: org
      """
    And the archive "base" holds the file "directives/B_001.directive.yaml":
      """
      schema_version: '1.0'
      id: B_001
      title: Base one
      intent: The directive base holds
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
    When I PUT "/packs/base" with the archive "base"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario Outline: A profile reference that does not hold is refused with its code
    Given the archive "faulty" is a copy of the archive "derived"
    And the archive "faulty" holds the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: P
      role: implementer
      purpose: Cites doctrine
      specialization:
        primary-focus: Citing
      <field>:
        - {<entry>}
      """
    When I PUT "/packs/derived" with the archive "faulty"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "<code>"
    And the field at "/problem/detail" contains "<written>"
    When I GET "/packs/derived"
    Then the response status is 404

    Examples:
      | field                 | entry                                                  | written                   | code                            |
      | directive-references  | code: 'B_001', name: n, rationale: why                 | directive:B_001           | pack_reference_unqualified      |
      | directive-references  | code: 'other::O_001', name: n, rationale: why          | other::directive:O_001    | pack_reference_pack_missing     |
      | directive-references  | code: 'base::B_404', name: n, rationale: why           | base::directive:B_404     | pack_reference_artifact_missing |
      | directive-references  | code: 'base::directive:B_404', name: n, rationale: why | base::directive:B_404     | pack_reference_artifact_missing |
      | tactic-references     | id: 'other::some-tactic', rationale: why               | other::tactic:some-tactic | pack_reference_pack_missing     |
      | toolguide-references  | id: 'base::gradle', rationale: why                     | base::toolguide:gradle    | pack_reference_artifact_missing |
      | styleguide-references | id: 'other::styleguide:boring', rationale: why         | other::styleguide:boring  | pack_reference_pack_missing     |

  Scenario: An operating procedure the named pack does not hold is refused
    Given the archive "faulty" is a copy of the archive "derived"
    And the archive "faulty" holds the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: P
      role: implementer
      purpose: Follows a procedure
      specialization:
        primary-focus: Following
      collaboration:
        operating-procedures:
          - base::review
      """
    When I PUT "/packs/derived" with the archive "faulty"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_reference_artifact_missing"
    And the field at "/problem/detail" contains "base::procedure:review"

  Scenario: A profile whose references hold registers, and its dependency cannot then drop what it names
    Given the archive "sound" is a copy of the archive "derived"
    And the archive "sound" holds the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: P
      role: implementer
      purpose: Cites doctrine
      specialization:
        primary-focus: Citing
      directive-references:
        - code: base::B_001
          name: Base one
          rationale: qualified by the id
        - code: base::directive:B_001
          name: Base one
          rationale: qualified with the kind written out
        - code: HELD_BY_NONE
          name: Nowhere
          rationale: a bare reference no pack holds is no fault
      """
    And the archive "base-without" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: base
      pack_level: org
      """
    When I PUT "/packs/derived" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/base" with the archive "base-without"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_reference_artifact_missing"
    And the field at "/problem/detail" contains "base::directive:B_001"

  Scenario: A directive code that is no directive id is refused by the schema
    Given the archive "short" is a copy of the archive "derived"
    And the archive "short" holds the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: P
      role: implementer
      purpose: Cites doctrine by a short code
      specialization:
        primary-focus: Citing
      directive-references:
        - code: '001'
          name: Short
          rationale: a part of the id, not the id
      """
    When I PUT "/packs/derived" with the archive "short"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/violations/0/code" is "artifact_schema_error"
    And the field at "/problem/violations/0/pointer" is "agent_profiles/p.agent.yaml#/directive-references/0/code"

  Scenario: An artifact DELETE that drops what another pack's profile names is refused, and stores nothing
    Given the archive "sound" is a copy of the archive "derived"
    And the archive "sound" holds the file "agent_profiles/p.agent.yaml":
      """
      profile-id: p
      name: P
      role: implementer
      purpose: Cites doctrine
      specialization:
        primary-focus: Citing
      directive-references:
        - code: base::B_001
          name: Base one
          rationale: qualified by the id
      """
    When I PUT "/packs/derived" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I DELETE "/packs/base/artifacts/directive/B_001" with the header "If-Match": "*"
    Then the response status is 422
    And the problem code is "pack_reference_artifact_missing"
    When I GET "/packs/base/artifacts/directive/B_001"
    Then the response status is 200
