@contract @journey-09
# Journey: docs/context/user-journeys/09-upgrade-packs.md
Feature: Journey 09 — a project upgrades its packs
  A new version of a pack is registered. The project opts in by updating its active charter; until it
  does, it is told it is outdated. The fingerprint that changes with the pack files works today;
  versions, opt-in and the outdated notice are not in the contract yet.

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

  Scenario: A new version of a pack file changes the source fingerprint
    Given the org pack "qa" with the file "rehearse.tactic.yaml":
      """
      id: rehearse
      schema_version: '1.0'
      name: Rehearse
      steps:
        - title: Only step
      """
    And I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And the org pack "qa" with the file "rehearse.tactic.yaml":
      """
      id: rehearse
      schema_version: '1.0'
      name: Rehearse again
      steps:
        - title: Only step
      """
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body differs from the remembered one

  @WIP
  Scenario: A new pack version does not change a project that has not opted in
    Given a project whose active charter pins the pack "qa" at version "1.0.0"
    When the pack "qa" at version "1.1.0" is registered
    Then the project's effective set is unchanged
    And the project is told the pack "qa" is outdated

  @WIP
  Scenario: Opting in updates the active charter to the new version
    Given a project whose active charter pins the pack "qa" at version "1.0.0"
    And the pack "qa" at version "1.1.0" is registered
    When the project updates its active charter to version "1.1.0"
    Then the effective set is the one of version "1.1.0"
    And the project is no longer told the pack "qa" is outdated
