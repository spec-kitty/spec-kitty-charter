# The retired charter.yaml keys at the activation seam (slice F, #520, #514; ADR-0077 decisions 3 and 7). A
# repository's charter.yaml is its activation set stored as a file; the service never reads that file, so its content
# is proven through the activation document it registers (operator ruling, 2026-11-15). The scenarios stand in their
# own file because they name the retired kind, and RetiredNamesScanTest admits a retired name in the contracts only
# where a file records or pins the retirement.

@contract @activations
Feature: An activation document carrying a retired charter key is refused at registration
  The activation document is closed (ActivationConfig) and has no catalog member: template_set, which a project
  charter carried in its catalog and which is dropped from it, is carried at the document's root, where no member
  admits it; the mission step contract, whose charter key was activated_mission_step_contracts, is no activatable
  kind, so selected names no such kind. A registration that carries either is refused 422 activation_invalid with one
  unknown_member violation at the key's pointer, and nothing is stored. The same document without the key registers
  (the positive control).

  Background:
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/DIRECTIVE_001.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_001
      title: First directive
      intent: Do the first thing
      enforcement: required
      """

  Scenario: A document carrying template_set is refused at /template_set, and without it the document registers
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}, "template_set": "software-dev-default"}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations" holds exactly 1 item(s)
    And the field at "/violations/0/pointer" is "/template_set"
    And the field at "/violations/0/code" is "unknown_member"
    When I GET "/activations/a-1"
    Then the response status is 404
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"

  Scenario: A document selecting mission step contracts is refused at their kind, and without it the document registers
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"], "mission_step_contract": ["specify"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations" holds exactly 1 item(s)
    And the field at "/violations/0/pointer" is "/selected/mission_step_contract"
    And the field at "/violations/0/code" is "unknown_member"
    When I GET "/activations/a-1"
    Then the response status is 404
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 201
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"
