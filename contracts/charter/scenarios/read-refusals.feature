@contract @offering
Feature: The reads answer each refusal and each conditional answer they declare
  Each read declares its refusals and, where it has an ETag, its 304 in the contract, and the switch gate
  (switch-gate.yaml, ADR-0059 D1) holds an operation ready only when a scenario asserts each of them on that operation.
  The behavior is the one request-id-length.feature, mission-types.feature and activation-register.feature describe;
  this file states it for the reads those files leave out, one row per read:
  - a path parameter outside its grammar (an activation id over 64 characters, a pack id over 63 characters or with an
    upper-case letter, an asset or skill id with an upper-case letter, a mission type id with an upper-case letter) is
    a 400 without a code, and the same read with a valid id is judged by the domain;
  - a read whose If-None-Match matches the ETag it answered is a 304, and the ETag is sent again.

  Background:
    Given the org pack "main" with the file "pack.yaml":
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
      intent: The directive the reads name
      enforcement: required
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"directive": ["D"]}}
      """

  Scenario Outline: <operation> with a path parameter outside its grammar is a 400 without a code, and with a valid one it is judged
    When I GET "<refused>"
    Then the response status is 400
    And the problem has no code
    When I GET "<judged>"
    Then the response status is <status>

    Examples:
      | operation                       | refused                                                                                                         | judged                                                         | status |
      | getPack                         | /packs/Main                                                                                                     | /packs/main                                                    | 200    |
      | getPackDocument                 | /packs/Main/doc                                                                                                 | /packs/main/doc                                                | 404    |
      | listPackPresets                 | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/presets                                | /packs/main/presets                                            | 200    |
      | getPackPreset                   | /packs/main/presets/Team                                                                                        | /packs/main/presets/team                                       | 404    |
      | listArtifactReferences          | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/directive/D/references       | /packs/main/artifacts/directive/D/references                   | 200    |
      | listPackAssets                  | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset                        | /packs/main/artifacts/asset                                    | 200    |
      | listPackAssets (pack id)        | /packs/Main/artifacts/asset | /packs/main/artifacts/asset | 200    |
      | getPackAsset                    | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset/logo                   | /packs/main/artifacts/asset/logo                               | 404    |
      | getPackAsset (asset id)         | /packs/main/artifacts/asset/Logo | /packs/main/artifacts/asset/logo | 404    |
      | getPackAssetPayload (asset id)  | /packs/main/artifacts/asset/Logo/payload | /packs/main/artifacts/asset/logo/payload | 404    |
      | getPackAssetPayload             | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset/logo/payload           | /packs/main/artifacts/asset/logo/payload                       | 404    |
      | listPackSkills                  | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/skill                        | /packs/main/artifacts/skill                                    | 200    |
      | getPackSkill                    | /packs/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/skill/land-pr                | /packs/main/artifacts/skill/land-pr                            | 404    |
      | getPackSkill (skill id)         | /packs/main/artifacts/skill/land-pr.skill | /packs/main/artifacts/skill/land-pr | 404    |
      | getPackMissionStep              | /packs/main/artifacts/mission_type/Main/steps/specify                                                           | /packs/main/artifacts/mission_type/main/steps/specify          | 404    |
      | getPackMissionSkeleton          | /packs/main/artifacts/mission_type/Main/skeletons/spec.md                                                       | /packs/main/artifacts/mission_type/main/skeletons/spec.md      | 404    |
      | getEffectiveMissionType         | /activations/a-1/artifacts/mission_type/Main                                                                    | /activations/a-1/artifacts/mission_type/main                   | 404    |
      | getEffectiveMissionStep         | /activations/a-1/artifacts/mission_type/Main/steps/specify                                                      | /activations/a-1/artifacts/mission_type/main/steps/specify     | 404    |
      | getEffectiveMissionSkeleton     | /activations/a-1/artifacts/mission_type/Main/skeletons/spec.md                                                  | /activations/a-1/artifacts/mission_type/main/skeletons/spec.md | 404    |
      | listEffectiveAssets             | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset                  | /activations/a-1/artifacts/asset                               | 200    |
      | getEffectiveAsset               | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset/logo             | /activations/a-1/artifacts/asset/logo                          | 404    |
      | getEffectiveAsset (asset id)    | /activations/a-1/artifacts/asset/Logo | /activations/a-1/artifacts/asset/logo | 404    |
      | getEffectiveAssetPayload (asset id) | /activations/a-1/artifacts/asset/Logo/payload | /activations/a-1/artifacts/asset/logo/payload | 404    |
      | getEffectiveAssetPayload        | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/asset/logo/payload     | /activations/a-1/artifacts/asset/logo/payload                  | 404    |
      | listEffectiveSkills             | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/skill                  | /activations/a-1/artifacts/skill                               | 200    |
      | getEffectiveSkill               | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/skill/land-pr          | /activations/a-1/artifacts/skill/land-pr                       | 404    |
      | getEffectiveSkill (skill id)    | /activations/a-1/artifacts/skill/Land-PR | /activations/a-1/artifacts/skill/land-pr | 404    |
      | listEffectiveArtifactReferences | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/artifacts/directive/D/references | /activations/a-1/artifacts/directive/D/references              | 200    |
      | getActivationSkippedReport      | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/skipped                          | /activations/a-1/skipped                                       | 200    |
      | getEffectiveGraph               | /activations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/graph                            | /activations/a-1/graph                                         | 200    |

  Scenario Outline: <operation> whose If-None-Match matches its ETag answers 304, and sends the ETag again
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header

    Examples:
      | operation                  | path                     |
      | getActivationSkippedReport | /activations/a-1/skipped |
      | listActivationPacks        | /activations/a-1/packs   |
      | getOpenApiDocument         | /openapi.yaml            |
