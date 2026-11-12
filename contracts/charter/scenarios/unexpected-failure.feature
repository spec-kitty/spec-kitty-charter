@contract
Feature: An unexpected failure is refused with internal_error on every operation that declares a 500
  An unexpected failure is one no refusal names: the service answers 500 internal_error, a Problem whose detail says the
  failure is logged and never shows it (errors.feature). Every operation that declares a 500 reads the configured packs
  on its way, so the failure is planted where they are read (the step of errors.feature), after the stored state the
  operation needs is in place, and the switch gate (switch-gate.yaml, ADR-0059 D1) sees each operation's declared 500
  asserted on that operation. Each write is sent with the precondition it needs, so the failure is the only reason it
  is refused. Two operations declare a 500 no pack read can raise: getPackRegistration reads the registration jobs and
  deleteActivation, sent a quoted revision or *, reads no packs, so their 500 is a failure of the job table or the
  activation storage, which the scenarios hold in memory and cannot fail; switch-gate.yaml holds them.

  Background:
    Given registration is switched on
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      """
    And the org pack "qa" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: The directive the operations name
      enforcement: required
      """
    And the org pack "qa" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: The directive D requires
      enforcement: required
      """
    And the org pack "qa" with the file "tactics/x.tactic.yaml":
      """
      schema_version: '1.0'
      id: x
      name: The tactic the activation denies
      steps:
        - title: Only step
      """
    And the org pack "qa" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The preset the write applies.
      activated_tactics:
        - id: x
      """

  Scenario Outline: <operation> that fails unexpectedly is 500 internal_error
    Given the service fails unexpectedly while it reads the packs
    When I GET "<path>"
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation              | path                                                 |
      | getServiceInfo         | /info                                                |
      | getOffering            | /offering                                            |
      | getPack                | /packs/qa                                            |
      | listPackArtifacts      | /packs/qa/artifacts                                  |
      | getPackArtifact        | /packs/qa/artifacts/directive/D                      |
      | listArtifactReferences | /packs/qa/artifacts/directive/D/references           |
      | getPackAgentProfile    | /packs/qa/artifacts/agent_profile/p                  |
      | getPackMissionType     | /packs/qa/artifacts/mission_type/m                   |
      | getPackMissionStep     | /packs/qa/artifacts/mission_type/m/steps/s           |
      | getPackMissionSkeleton | /packs/qa/artifacts/mission_type/m/skeletons/spec.md |

  Scenario Outline: <operation> of a stored activation that fails unexpectedly is 500 internal_error
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}], "selected": {"directive": ["D"]}, "deactivated": {"tactic": ["x"]}}
      """
    And the service fails unexpectedly while it reads the packs
    When I GET "<path>"
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation                       | path                                                        |
      | getActivation                   | /activations/a-1                                            |
      | listActivationPacks             | /activations/a-1/packs                                      |
      | listEffectiveArtifacts          | /activations/a-1/artifacts                                  |
      | getEffectiveArtifact            | /activations/a-1/artifacts/directive/D                      |
      | getEffectiveAgentProfile        | /activations/a-1/artifacts/agent_profile/p                  |
      | getEffectiveMissionType         | /activations/a-1/artifacts/mission_type/m                   |
      | getEffectiveMissionStep         | /activations/a-1/artifacts/mission_type/m/steps/s           |
      | getEffectiveMissionSkeleton     | /activations/a-1/artifacts/mission_type/m/skeletons/spec.md |
      | getEffectiveMissionStepContract | /activations/a-1/artifacts/mission_step_contract/c          |
      | getActivationSkippedReport      | /activations/a-1/skipped                                    |
      | getEffectiveGraph               | /activations/a-1/graph                                      |
      | getEffectiveActionContext       | /activations/a-1/mission-types/m/actions/s/context          |

  Scenario: createActivation that fails unexpectedly is 500 internal_error
    Given the service fails unexpectedly while it reads the packs
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}]}
      """
    Then the response status is 500
    And the problem code is "internal_error"

  Scenario Outline: <operation> of a stored activation that fails unexpectedly is 500 internal_error
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}], "selected": {"directive": ["D"]}, "deactivated": {"tactic": ["x"]}}
      """
    And the service fails unexpectedly while it reads the packs
    When I PUT "<path>" with the stored ETag and the body:
      """
      <body>
      """
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation                    | method | path                                    | body                                              |
      | replaceActivation            | PUT    | /activations/a-1                        | {"schemaVersion": 2, "packs": [{"packId": "qa"}]} |
      | selectActivationArtifact     | PUT    | /activations/a-1/selections/directive/D | {}                                                |
      | deactivateActivationArtifact | PUT    | /activations/a-1/exclusions/directive/D | {}                                                |
      | applyActivationPreset        | PUT    | /activations/a-1/presets/team           | {}                                                |

  Scenario Outline: <operation> of a stored activation that fails unexpectedly is 500 internal_error
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "qa"}], "selected": {"directive": ["D"]}, "deactivated": {"tactic": ["x"]}}
      """
    And the service fails unexpectedly while it reads the packs
    When I DELETE "<path>" with the stored ETag
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation                    | path                                    |
      | unselectActivationArtifact   | /activations/a-1/selections/directive/D |
      | reactivateActivationArtifact | /activations/a-1/exclusions/tactic/x    |

  Scenario Outline: <operation> that fails unexpectedly is 500 internal_error
    Given the service fails unexpectedly while it reads the packs
    When I send <method> "<path>" as "application/json" with If-Match "*" and the body:
      """
      <body>
      """
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation              | method | path                             | body                                                                                                                       |
      | registerPackDirective  | POST   | /packs/qa/artifacts/directive    | {"id": "N", "schemaVersion": "1.0", "title": "N", "intent": "New", "enforcement": "required"}                              |
      | putPackDirective       | PUT    | /packs/qa/artifacts/directive/N  | {"schemaVersion": "1.0", "title": "N", "intent": "New", "enforcement": "required"}                                         |
      | registerPackParadigm   | POST   | /packs/qa/artifacts/paradigm     | {"id": "n", "schemaVersion": "1.0", "name": "N", "summary": "New"}                                                         |
      | putPackParadigm        | PUT    | /packs/qa/artifacts/paradigm/n   | {"schemaVersion": "1.0", "name": "N", "summary": "New"}                                                                    |
      | registerPackStyleguide | POST   | /packs/qa/artifacts/styleguide   | {"id": "n", "schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]} |
      | putPackStyleguide      | PUT    | /packs/qa/artifacts/styleguide/n | {"schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]}            |
      | registerPackToolguide  | POST   | /packs/qa/artifacts/toolguide    | {"id": "n", "schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}          |
      | putPackToolguide       | PUT    | /packs/qa/artifacts/toolguide/n  | {"schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}                     |
      | putPackRelationship    | PUT    | /packs/qa/relationships          | {"from": "directive:D", "to": "directive:E", "type": "requires"}                                                           |

  Scenario Outline: <operation> that fails unexpectedly is 500 internal_error
    Given the service fails unexpectedly while it reads the packs
    When I DELETE "<path>" with the header "If-Match": "*"
    Then the response status is 500
    And the problem code is "internal_error"

    Examples:
      | operation              | path                                                    |
      | deletePackArtifact     | /packs/qa/artifacts/directive/E                         |
      | deletePackRelationship | /packs/qa/relationships?from=directive:D&to=directive:E |
