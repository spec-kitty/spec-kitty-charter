@contract @activations
Feature: The activation set is declared and reserved
  The activation operations (ADR-0020 D3, D6, D7) are declared in full in the contract before
  they are built, because core codes its deny-list against them (ADR-0022 D5). Until an
  operation is built it carries x-reserved and answers 501 Not Implemented with a Problem that
  has no code, before it reads its request (ADR-0020 D8). Each scenario below is a 501 pin: it
  sends a well-formed request to one operation and is removed when that operation is built.
  ReservedOperationCoverageTest keeps the pins and the reserved operations of the activation set one to one, and
  fails if a reserved operation takes a request body without a pin before its body is bound (none does).
  The register (create, register, read, remove) is built and pinned by activation-register.feature;
  the four per-artifact writes (select, unselect, deactivate, re-activate) by
  activation-selections.feature; the preset write by activation-presets.feature. The effective artifact list and
  the skipped report are declared as built operations (PR B, WP22) and their two 501 pins are gone: their scenarios
  are effective-reads.feature and layer-merge.feature, which WP25 binds. Only the effective graph and the effective
  action context remain reserved, until PR C builds them (#103).

  @reserved
  Scenario: Reading the effective graph of an activation is reserved
    When I GET "/activations/a-1/graph"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the effective context of an action is reserved
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the operation is refused as not implemented
