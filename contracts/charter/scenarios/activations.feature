@contract @activations
Feature: The activation set is declared and reserved
  The activation operations (ADR-0020 D3, D6, D7) are declared in full in the contract before
  they are built, because core codes its deny-list against them (ADR-0022 D5). Until an
  operation is built it carries x-reserved and answers 501 Not Implemented with a Problem that
  has no code, before it reads its request (ADR-0020 D8). Each scenario below is a 501 pin: it
  sends a well-formed request to one operation and is removed when that operation is built.
  ReservedOperationCoverageTest keeps the pins and the reserved operations one to one. The four
  effective reads are built with R9 (#103); the nine writes with the activation build that
  follows #125.

  @reserved
  Scenario: Creating an activation with a service-minted id is reserved
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 1}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Registering an activation document under a client-chosen id is reserved
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "missionTypes": ["software-dev"], "selected": {"directive": ["DIRECTIVE_001"]}, "deactivated": {"tactic": ["domain-event-capture"]}, "languages": ["java"]}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Reading an activation is reserved
    When I GET "/activations/a-1"
    Then the operation is refused as not implemented

  @reserved
  Scenario: Removing an activation is reserved
    When I DELETE "/activations/a-1" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

  @reserved
  Scenario: Selecting an artifact is reserved
    When I PUT "/activations/a-1/selections/directive/DIRECTIVE_001" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"cascade": ["tactic"], "includeDeactivated": false}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Selecting a mission type is reserved
    When I PUT "/activations/a-1/selections/mission_type/software-dev" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Unselecting an artifact is reserved
    When I DELETE "/activations/a-1/selections/directive/DIRECTIVE_001" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

  @reserved
  Scenario: Deactivating an artifact is reserved
    When I PUT "/activations/a-1/exclusions/directive/DIRECTIVE_001" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"cascade": ["all"]}
      """
    Then the operation is refused as not implemented

  @reserved
  Scenario: Re-activating an artifact is reserved
    When I DELETE "/activations/a-1/exclusions/directive/DIRECTIVE_001" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the operation is refused as not implemented

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
    where the schema has an integer, a number where it has a list), which without the interceptor
    is a 400 before the operation runs. While the operation is reserved it is answered 501.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": "two", "packs": 5}
      """
    Then the operation is refused as not implemented
