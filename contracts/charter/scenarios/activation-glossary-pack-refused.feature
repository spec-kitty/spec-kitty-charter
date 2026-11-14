# Mission glossary-contexts-and-terms-01M7B1PP (#106), WP03: the write half of research R2
# (DM-01M7B1Q1HW2KDEEW3MKCK2A2PT). The glossary kind's only token is glossary; glossary_pack is no member of the
# document, so the closed body refuses it as it refuses any member it does not declare (activation-register.feature, "A
# member the document does not declare is refused": 422 activation_invalid, unknown_member at its pointer). The stored
# half is ActivationGlossaryPackRefusalTest. The old member appears here only because this is a refusal scenario.
# - Steps from the vocabulary of README.md only.

@contract @activations
Feature: The replaced glossary_pack member is refused on an activation write
  The glossary kind replaced glossary_pack outright (ADR-0049, FR-005): there is no deprecation window and no reader of
  the old member. A write that sends selected.glossary_pack or deactivated.glossary_pack is refused as any member the
  document does not declare is: 422 activation_invalid with an unknown_member violation at its pointer. The same body
  under glossary is accepted (the positive control).

  The fixture is one org pack, main, holding the glossary context core.

  Background:
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "glossaries/core.glossary.yaml":
      """
      schema_version: '1.0'
      id: core
      name: Core
      description: The pack's glossary context.
      terms:
      - id: lane
        name: lane
        definition: A sequence of work packages.
        status: active
      """

  Scenario: selected.glossary_pack in a create body is 422 activation_invalid with unknown_member
    The same context under selected.glossary is created (the positive control).
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"glossary_pack": ["core"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations" holds exactly 1 item
    And the field at "/violations/0/pointer" is "/selected/glossary_pack"
    And the field at "/violations/0/code" is "unknown_member"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"glossary": ["core"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/glossary" holds exactly the ids:
      """
      core
      """

  Scenario: deactivated.glossary_pack in a create body is 422 activation_invalid with unknown_member
    The same deny entry under deactivated.glossary is created (the positive control).
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"glossary_pack": ["core"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations" holds exactly 1 item
    And the field at "/violations/0/pointer" is "/deactivated/glossary_pack"
    And the field at "/violations/0/code" is "unknown_member"
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "deactivated": {"glossary": ["core"]}}
      """
    Then the response status is 201
    And the field at "/config/deactivated/glossary" holds exactly the ids:
      """
      core
      """
