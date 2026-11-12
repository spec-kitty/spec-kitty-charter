@contract
Feature: The worktree guard answers in front of every operation
  The worktree guard (security.feature) is one filter in front of every route, so it answers before
  any operation runs: a request without the worktree token is a 401, and a request a browser could make (here, one
  with an Origin header) is a 403 even with the right token. security.feature proves what the guard answers; this
  file proves it answers on every operation of the contract, one row per operation, so the switch gate
  (switch-gate.yaml, ADR-0059 D1) sees each operation's declared 401 and 403 asserted on that operation.
  The path values are any values the parameters accept: the guard answers before a pack or an activation is read.
  A new operation gets a row here.

  Scenario Outline: <operation> without the worktree token is a 401
    When I <method> "<path>" without the worktree token
    Then the response status is 401
    And the problem has no code
    And the response header "WWW-Authenticate" is "Bearer"

    Examples:
      | operation                       | method | path                                                                                          |
      | getApiRoot                      | GET    | /                                                                                             |
      | getServiceInfo                  | GET    | /info                                                                                         |
      | getOpenApiDocument              | GET    | /openapi.yaml                                                                                 |
      | getOffering                     | GET    | /offering                                                                                     |
      | registerPackFromSource          | POST   | /packs                                                                                        |
      | registerPackFromArchive         | PUT    | /packs/qa                                                                                     |
      | getPack                         | GET    | /packs/qa                                                                                     |
      | getPackRegistration             | GET    | /pack-registrations/01ARWG13C000000000000000RG                                                |
      | putPackRelationship             | PUT    | /packs/qa/relationships                                                                       |
      | deletePackRelationship          | DELETE | /packs/qa/relationships                                                                       |
      | listPackArtifacts               | GET    | /packs/qa/artifacts                                                                           |
      | getPackAgentProfile             | GET    | /packs/qa/artifacts/agent_profile/rehearse                                                    |
      | registerPackDirective           | POST   | /packs/qa/artifacts/directive                                                                 |
      | putPackDirective                | PUT    | /packs/qa/artifacts/directive/rehearse                                                        |
      | registerPackStyleguide          | POST   | /packs/qa/artifacts/styleguide                                                                |
      | putPackStyleguide               | PUT    | /packs/qa/artifacts/styleguide/rehearse                                                       |
      | registerPackParadigm            | POST   | /packs/qa/artifacts/paradigm                                                                  |
      | putPackParadigm                 | PUT    | /packs/qa/artifacts/paradigm/rehearse                                                         |
      | registerPackToolguide           | POST   | /packs/qa/artifacts/toolguide                                                                 |
      | putPackToolguide                | PUT    | /packs/qa/artifacts/toolguide/rehearse                                                        |
      | getPackMissionType              | GET    | /packs/qa/artifacts/mission_type/software-dev                                                 |
      | getPackMissionStep              | GET    | /packs/qa/artifacts/mission_type/software-dev/steps/implement                                 |
      | getPackMissionSkeleton          | GET    | /packs/qa/artifacts/mission_type/software-dev/skeletons/spec.md                               |
      | getPackArtifact                 | GET    | /packs/qa/artifacts/directive/rehearse                                                        |
      | deletePackArtifact              | DELETE | /packs/qa/artifacts/directive/rehearse                                                        |
      | listArtifactReferences          | GET    | /packs/qa/artifacts/directive/rehearse/references                                             |
      | createActivation                | POST   | /activations                                                                                  |
      | getActivation                   | GET    | /activations/01ARWG13C000000000000000AC                                                       |
      | replaceActivation               | PUT    | /activations/01ARWG13C000000000000000AC                                                       |
      | deleteActivation                | DELETE | /activations/01ARWG13C000000000000000AC                                                       |
      | listActivationPacks             | GET    | /activations/01ARWG13C000000000000000AC/packs                                                 |
      | selectActivationArtifact        | PUT    | /activations/01ARWG13C000000000000000AC/selections/directive/rehearse                         |
      | unselectActivationArtifact      | DELETE | /activations/01ARWG13C000000000000000AC/selections/directive/rehearse                         |
      | deactivateActivationArtifact    | PUT    | /activations/01ARWG13C000000000000000AC/exclusions/directive/rehearse                         |
      | reactivateActivationArtifact    | DELETE | /activations/01ARWG13C000000000000000AC/exclusions/directive/rehearse                         |
      | applyActivationPreset           | PUT    | /activations/01ARWG13C000000000000000AC/presets/strict                                        |
      | listEffectiveArtifacts          | GET    | /activations/01ARWG13C000000000000000AC/artifacts                                             |
      | getEffectiveAgentProfile        | GET    | /activations/01ARWG13C000000000000000AC/artifacts/agent_profile/rehearse                      |
      | getEffectiveMissionType         | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev                   |
      | getEffectiveMissionStep         | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev/steps/implement   |
      | getEffectiveMissionSkeleton     | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev/skeletons/spec.md |
      | getEffectiveMissionStepContract | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_step_contract/implement             |
      | getEffectiveArtifact            | GET    | /activations/01ARWG13C000000000000000AC/artifacts/directive/rehearse                          |
      | getActivationSkippedReport      | GET    | /activations/01ARWG13C000000000000000AC/skipped                                               |
      | getEffectiveGraph               | GET    | /activations/01ARWG13C000000000000000AC/graph                                                 |
      | getEffectiveActionContext       | GET    | /activations/01ARWG13C000000000000000AC/mission-types/software-dev/actions/implement/context  |

  Scenario Outline: <operation> with an Origin header is a 403 even with the right token
    When I <method> "<path>" with the header "Origin": "https://evil.example"
    Then the response status is 403
    And the problem has no code
    And the response has no "WWW-Authenticate" header

    Examples:
      | operation                       | method | path                                                                                          |
      | getApiRoot                      | GET    | /                                                                                             |
      | getServiceInfo                  | GET    | /info                                                                                         |
      | getOpenApiDocument              | GET    | /openapi.yaml                                                                                 |
      | getOffering                     | GET    | /offering                                                                                     |
      | registerPackFromSource          | POST   | /packs                                                                                        |
      | registerPackFromArchive         | PUT    | /packs/qa                                                                                     |
      | getPack                         | GET    | /packs/qa                                                                                     |
      | getPackRegistration             | GET    | /pack-registrations/01ARWG13C000000000000000RG                                                |
      | putPackRelationship             | PUT    | /packs/qa/relationships                                                                       |
      | deletePackRelationship          | DELETE | /packs/qa/relationships                                                                       |
      | listPackArtifacts               | GET    | /packs/qa/artifacts                                                                           |
      | getPackAgentProfile             | GET    | /packs/qa/artifacts/agent_profile/rehearse                                                    |
      | registerPackDirective           | POST   | /packs/qa/artifacts/directive                                                                 |
      | putPackDirective                | PUT    | /packs/qa/artifacts/directive/rehearse                                                        |
      | registerPackStyleguide          | POST   | /packs/qa/artifacts/styleguide                                                                |
      | putPackStyleguide               | PUT    | /packs/qa/artifacts/styleguide/rehearse                                                       |
      | registerPackParadigm            | POST   | /packs/qa/artifacts/paradigm                                                                  |
      | putPackParadigm                 | PUT    | /packs/qa/artifacts/paradigm/rehearse                                                         |
      | registerPackToolguide           | POST   | /packs/qa/artifacts/toolguide                                                                 |
      | putPackToolguide                | PUT    | /packs/qa/artifacts/toolguide/rehearse                                                        |
      | getPackMissionType              | GET    | /packs/qa/artifacts/mission_type/software-dev                                                 |
      | getPackMissionStep              | GET    | /packs/qa/artifacts/mission_type/software-dev/steps/implement                                 |
      | getPackMissionSkeleton          | GET    | /packs/qa/artifacts/mission_type/software-dev/skeletons/spec.md                               |
      | getPackArtifact                 | GET    | /packs/qa/artifacts/directive/rehearse                                                        |
      | deletePackArtifact              | DELETE | /packs/qa/artifacts/directive/rehearse                                                        |
      | listArtifactReferences          | GET    | /packs/qa/artifacts/directive/rehearse/references                                             |
      | createActivation                | POST   | /activations                                                                                  |
      | getActivation                   | GET    | /activations/01ARWG13C000000000000000AC                                                       |
      | replaceActivation               | PUT    | /activations/01ARWG13C000000000000000AC                                                       |
      | deleteActivation                | DELETE | /activations/01ARWG13C000000000000000AC                                                       |
      | listActivationPacks             | GET    | /activations/01ARWG13C000000000000000AC/packs                                                 |
      | selectActivationArtifact        | PUT    | /activations/01ARWG13C000000000000000AC/selections/directive/rehearse                         |
      | unselectActivationArtifact      | DELETE | /activations/01ARWG13C000000000000000AC/selections/directive/rehearse                         |
      | deactivateActivationArtifact    | PUT    | /activations/01ARWG13C000000000000000AC/exclusions/directive/rehearse                         |
      | reactivateActivationArtifact    | DELETE | /activations/01ARWG13C000000000000000AC/exclusions/directive/rehearse                         |
      | applyActivationPreset           | PUT    | /activations/01ARWG13C000000000000000AC/presets/strict                                        |
      | listEffectiveArtifacts          | GET    | /activations/01ARWG13C000000000000000AC/artifacts                                             |
      | getEffectiveAgentProfile        | GET    | /activations/01ARWG13C000000000000000AC/artifacts/agent_profile/rehearse                      |
      | getEffectiveMissionType         | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev                   |
      | getEffectiveMissionStep         | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev/steps/implement   |
      | getEffectiveMissionSkeleton     | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_type/software-dev/skeletons/spec.md |
      | getEffectiveMissionStepContract | GET    | /activations/01ARWG13C000000000000000AC/artifacts/mission_step_contract/implement             |
      | getEffectiveArtifact            | GET    | /activations/01ARWG13C000000000000000AC/artifacts/directive/rehearse                          |
      | getActivationSkippedReport      | GET    | /activations/01ARWG13C000000000000000AC/skipped                                               |
      | getEffectiveGraph               | GET    | /activations/01ARWG13C000000000000000AC/graph                                                 |
      | getEffectiveActionContext       | GET    | /activations/01ARWG13C000000000000000AC/mission-types/software-dev/actions/implement/context  |
