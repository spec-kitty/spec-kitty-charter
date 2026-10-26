@contract @journey-01
# Journey: docs/context/user-journeys/01-getting-started.md
Feature: Journey 01 — an individual developer gets started
  A developer installs Spec Kitty, starts from the minimal built-in pack with the sane-defaults preset
  and runs a first Mission. The charter service must answer for the worktree before core relies on it;
  activating a preset and starting a Mission are not in the contract yet.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      active_charter: org-charter.yaml
      pack_drg: drg/fragment.yaml
      """
    And the org pack "qa" with the file "guard.directive.yaml":
      """
      id: GUARD
      schema_version: '1.0'
      title: Guard the gate
      intent: Guard it
      enforcement: required
      """

  Scenario: The service answers for the developer's worktree before the first Mission
    When I GET "/info"
    Then the response status is 200
    And the field "contractVersion" of the response body is the contract version
    And the field "worktree" of the response body is "scenario-worktree"
    And the field "sourceFingerprint" of the response body is a sha256 digest

  Scenario: The starter pack's directives can be read
    When I GET "/packs/qa/directives"
    Then the response status is 200
    And the response body is a list with an element whose field "id" is "GUARD"

  @WIP
  Scenario: A new project activates the sane-defaults preset of the minimal built-in pack
    Given a project with no active charter
    When the developer activates the preset "sane-defaults" of the built-in pack
    Then the active charter names the preset "sane-defaults"
    And every artifact the preset selects is in the effective set

  @WIP
  Scenario: The first Mission starts against the active charter
    Given a project whose active charter holds the preset "sane-defaults"
    When the developer starts a Mission of the type "software-dev"
    Then the Mission's first step resolves to an action with its guidance
