@contract @activations
Feature: The activation writes answer each refusal they declare
  Each activation write declares its refusals in the contract, and the switch gate (switch-gate.yaml, ADR-0059 D1)
  holds an operation ready only when a scenario asserts each of them on that operation. The behavior is the one
  activation-selections.feature, activation-presets.feature and activation-register-guards.feature describe; this file
  states it for the writes those files leave out, one row per write:
  - an If-Match that is neither * nor one quoted revision breaks its pattern: 400;
  - a stale If-Match: 412, and a sub-resource write without one: 428 (ADR-0020 D9);
  - a body that is not application/json: 415;
  - a path kind outside the selectable kinds: 422 activation_invalid;
  - a write that meets a busy registry: 503 with Retry-After, and the same write succeeds once the registry is free.

  Background:
    Given registration is switched on
    And the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: The directive the activation selects
      enforcement: required
      """
    And the org pack "main" with the file "tactics/x.tactic.yaml":
      """
      schema_version: '1.0'
      id: x
      name: The tactic the activation denies
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: The preset the writes apply.
      activated_tactics:
        - id: x
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"]}, "deactivated": {"tactic": ["x"]}}
      """

  Scenario Outline: <operation> with a malformed If-Match is 400, and with the stored ETag it is judged
    When I send <method> "<path>" as "application/json" with If-Match "not-a-revision" and the body:
      """
      {}
      """
    Then the response status is 400
    And the problem has no code
    When I <method> "<path>" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200

    Examples:
      | operation                    | method | path                                    |
      | applyActivationPreset        | PUT    | /activations/a-1/presets/team           |
      | deactivateActivationArtifact | PUT    | /activations/a-1/exclusions/directive/D |

  Scenario Outline: <operation> with a malformed If-Match is 400, and with the stored ETag it is judged
    When I DELETE "<path>" with the header "If-Match": "not-a-revision"
    Then the response status is 400
    And the problem has no code
    When I DELETE "<path>" with the stored ETag
    Then the response status is 200

    Examples:
      | operation                    | path                                    |
      | reactivateActivationArtifact | /activations/a-1/exclusions/tactic/x    |
      | unselectActivationArtifact   | /activations/a-1/selections/directive/D |

  Scenario: deactivateActivationArtifact with a stale If-Match is 412, and with the stored ETag it is judged
    When I PUT "/activations/a-1/exclusions/directive/D" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {}
      """
    Then the response status is 412
    And the problem has no code
    When I PUT "/activations/a-1/exclusions/directive/D" with the stored ETag and the body:
      """
      {}
      """
    Then the response status is 200

  Scenario: unselectActivationArtifact with a stale If-Match is 412, and with the stored ETag it is judged
    When I DELETE "/activations/a-1/selections/directive/D" with the header "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\""
    Then the response status is 412
    And the problem has no code
    When I DELETE "/activations/a-1/selections/directive/D" with the stored ETag
    Then the response status is 200

  Scenario: reactivateActivationArtifact without If-Match is 428, and with the stored ETag it is judged
    When I send DELETE "/activations/a-1/exclusions/tactic/x"
    Then the response status is 428
    And the problem has no code
    When I DELETE "/activations/a-1/exclusions/tactic/x" with the stored ETag
    Then the response status is 200

  Scenario Outline: <operation> on a kind outside the selectable kinds is 422 at the path, and on a selectable one it is judged
    When I DELETE "<refused>" with the stored ETag
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/kind"
    When I DELETE "<judged>" with the stored ETag
    Then the response status is 200

    Examples:
      | operation                    | refused                                  | judged                                  |
      | reactivateActivationArtifact | /activations/a-1/exclusions/template/x   | /activations/a-1/exclusions/tactic/x    |
      | unselectActivationArtifact   | /activations/a-1/selections/template/D   | /activations/a-1/selections/directive/D |

  Scenario Outline: <operation> with a body that is not JSON is 415, and as JSON it is judged
    When I send <method> "<path>" as "text/plain" with If-Match "<ifMatch>" and the body:
      """
      <body>
      """
    Then the response status is 415
    And the problem has no code
    When I send <method> "<path>" as "application/json" with If-Match "<ifMatch>" and the body:
      """
      <body>
      """
    Then the response status is <judged>

    Examples:
      | operation                    | method | path                                    | ifMatch | body              | judged |
      | selectActivationArtifact     | PUT    | /activations/a-1/selections/directive/D | *       | {}                | 200    |
      | deactivateActivationArtifact | PUT    | /activations/a-1/exclusions/directive/D | *       | {}                | 200    |
      | applyActivationPreset        | PUT    | /activations/a-1/presets/team           | *       | {}                | 200    |

  Scenario: replaceActivation with a body that is not JSON is 415, and as JSON it is registered
    replaceActivation declares no 428, so the request without If-Match registers a new id: the control shows the same
    body is accepted once it is sent as JSON.
    When I send PUT "/activations/a-2" as "text/plain" with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 415
    And the problem has no code
    When I send PUT "/activations/a-2" as "application/json" with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 201

  Scenario: createActivation with a body that is not JSON is 415, and as JSON it is created
    When I send POST "/activations" as "text/plain" with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 415
    And the problem has no code
    When I send POST "/activations" as "application/json" with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 201

  Scenario Outline: <operation> that meets a busy registry is 503 with Retry-After, and succeeds once it is free
    Given the pack registry is held by another write
    When I <method> "<path>" with the stored ETag and the body:
      """
      <body>
      """
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When the held pack registry is released
    And I <method> "<path>" with the stored ETag and the body:
      """
      <body>
      """
    Then the response status is 200

    Examples:
      | operation                    | method | path                                    | body                                                                     |
      | replaceActivation            | PUT    | /activations/a-1                        | {"schemaVersion": 2, "packs": [{"packId": "main"}]}                      |
      | selectActivationArtifact     | PUT    | /activations/a-1/selections/directive/D | {}                                                                       |
      | deactivateActivationArtifact | PUT    | /activations/a-1/exclusions/directive/D | {}                                                                       |
      | applyActivationPreset        | PUT    | /activations/a-1/presets/team           | {}                                                                       |

  Scenario Outline: <operation> that meets a busy registry is 503 with Retry-After, and succeeds once it is free
    Given the pack registry is held by another write
    When I DELETE "<path>" with the stored ETag
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When the held pack registry is released
    And I DELETE "<path>" with the stored ETag
    Then the response status is <done>

    Examples:
      | operation                    | path                                    | done |
      | deleteActivation             | /activations/a-1                        | 204  |
      | reactivateActivationArtifact | /activations/a-1/exclusions/tactic/x    | 200  |
      | unselectActivationArtifact   | /activations/a-1/selections/directive/D | 200  |
