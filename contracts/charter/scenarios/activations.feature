@contract @activations
Feature: The activation set is declared and reserved
  The activation operations (ADR-0020 D3, D6, D7) are declared in full in the contract before
  they are built, because core codes its deny-list against them (ADR-0022 D5). Until an
  operation is built it carries x-reserved and answers 501 Not Implemented with a Problem that
  has no code, before it reads its request (ADR-0020 D8). Each scenario below is a 501 pin: it
  sends a well-formed request to one operation and is removed when that operation is built.
  ReservedOperationCoverageTest keeps the pins and the reserved operations one to one. The
  register (create, register, read, remove) is built and pinned by activation-register.feature;
  the four per-artifact writes (select, unselect, deactivate, re-activate) are built and pinned by
  activation-selections.feature; the four effective reads are built with R9 (#103); the preset write, PR B.

  @reserved
  Scenario: Applying a preset is reserved
    When I PUT "/activations/a-1/presets/event-driven" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"force": false}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the effective artifacts of an activation is reserved
    When I GET "/activations/a-1/artifacts?kind=directive&state=active"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the skipped report of an activation is reserved
    When I GET "/activations/a-1/skipped"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the effective graph of an activation is reserved
    When I GET "/activations/a-1/graph"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading the effective context of an action is reserved
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context?depth=1"
    Then the operation is refused as not implemented

  @reserved
  Scenario: A reserved operation answers 501 before it reads its request
    The 501 comes before the body is bound or validated: this body cannot even be bound (a string
    where the schema has a boolean), which without the interceptor is a 400 before the operation
    runs. While the operation is reserved it is answered 501.
    When I PUT "/activations/a-1/presets/event-driven" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"force": "yes"}
      """
    Then the operation is refused as not implemented
