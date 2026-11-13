# The activation twins of the asset and skill reads (#105, #107; FR-009 to FR-011, research R1, R2, R3 and R15):
# listEffectiveAssets, getEffectiveAsset and getEffectiveAssetPayload answer the copy of each asset the layer merge keeps
# across the levels of the activation's closure, with no state; listEffectiveSkills and getEffectiveSkill answer the
# skill winners, the item whatever its state. The 400, 401, 403 and 500 answers of the five operations are asserted in
# read-refusals.feature, every-operation.feature and unexpected-failure.feature.
#
# Fixture: core (built-in) holds the assets run and logo and the skills land-pr and terse; proj (project) holds its own
# copy of run, which wins. The activation selects land-pr and leaves terse out.

@contract @activations
Feature: The assets and skills of an activation are read as the copies the layer merge keeps
  Under an activation, an asset is in force when a pack of the closure holds it: the read answers the winning copy's
  pack, level, policy and descriptor, and its payload is that copy's bytes, with no state. A skill is answered with the
  state and reason the activation gives it, whatever they are, and the skill as its winning pack declares it. Each read
  answers its own ETag and a 304 to a matching If-None-Match. The activation is judged first, then whether a pack of the
  closure holds what the path names.

  Background:
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core-pack
      pack_level: built-in
      """
    And the built-in pack "core" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      title: Run, built-in copy
      """
    And the built-in pack "core" with the file "assets/run.py":
      """
      print("built-in")
      """
    And the built-in pack "core" with the file "assets/logo.png.asset.yaml":
      """
      id: logo
      mime: image/png
      path: logo.png
      """
    And the built-in pack "core" with the file "assets/logo.png" in base64:
      """
      iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==
      """
    And the built-in pack "core" with the file "skills/land-pr.skill.yaml":
      """
      schema_version: "1.0"
      id: land-pr
      title: Land a PR
      description: Lands a pull request.
      form: prompt
      body_path: land-pr.skill.md
      """
    And the built-in pack "core" with the file "skills/land-pr.skill.md":
      """
      Land the pull request.
      """
    And the built-in pack "core" with the file "skills/terse.skill.yaml":
      """
      schema_version: "1.0"
      id: terse
      title: Terse
      description: A skill the activation does not select.
      form: prompt
      body_path: terse.skill.md
      """
    And the built-in pack "core" with the file "skills/terse.skill.md":
      """
      Be terse.
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      title: Run, project copy
      executable:
        runtime: python
        entry: run.py
        args:
        - --strict
      """
    And the project pack "proj" with the file "assets/run.py":
      """
      print("project")
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "proj"}], "selected": {"skill": ["land-pr"]}}
      """

  Scenario: The assets of the activation are the winning copies, in id order, linked under the activation and in their pack
    When I GET "/activations/a-1/artifacts/asset"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [
          {
            "id": "logo",
            "mime": "image/png",
            "executable": false,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/asset/logo"},
              "artifact": {"href": "/api/v1/packs/core/artifacts/asset/logo"}
            }
          },
          {
            "id": "run",
            "mime": "text/x-python",
            "executable": true,
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/asset/run"},
              "artifact": {"href": "/api/v1/packs/proj/artifacts/asset/run"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/asset"},
          "up": {"href": "/api/v1/activations/a-1"}
        }
      }
      """
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts/asset"
    Then following the link "artifact" of every element of "items" answers 200

  Scenario: The project copy of an asset wins over the built-in copy, for its descriptor and for its payload's bytes
    run is held by core and by proj, which replaces it at the project level. The answer names proj and project, carries
    no state, and its payload is proj's bytes, not core's. logo is the control: core alone holds it.
    When I GET "/activations/a-1/artifacts/asset/run"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/activationId" is "a-1"
    And the field at "/kind" is "asset"
    And the field at "/artifactId" is "run"
    And the field at "/packId" is "proj"
    And the field at "/level" is "project"
    And the field at "/policy" is "open"
    And the field at "/asset/title" is "Run, project copy"
    And the field at "/asset/executable/runtime" is "python"
    And the field at "/state" is absent
    And the field at "/reason" is absent
    And the field at "/required" is absent
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/asset/run"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/asset/run"
    And the field at "/_links/payload/href" is "/api/v1/activations/a-1/artifacts/asset/run/payload"
    And the field at "/_links/collection/href" is "/api/v1/activations/a-1/artifacts/asset"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"
    When I follow the link "payload"
    Then the response status is 200
    And the response header "Content-Type" is "text/x-python"
    And the response header "X-Content-Type-Options" is "nosniff"
    And the response header "Content-Security-Policy" is "sandbox; default-src 'none'"
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is the bytes of the file "assets/run.py" of the pack "proj"
    When I GET "/activations/a-1/artifacts/asset/run"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/asset/title" is "Run, project copy"
    When I GET "/activations/a-1/artifacts/asset/logo"
    Then the response status is 200
    And the field at "/packId" is "core"
    And the field at "/level" is "built-in"
    When I GET "/activations/a-1/artifacts/asset/logo/payload"
    Then the response status is 200
    And the response header "Content-Type" is "image/png"
    And the response body is the bytes of the file "assets/logo.png" of the pack "core"

  Scenario: The skills of the activation are its skill winners, whatever their state, in id order
    When I GET "/activations/a-1/artifacts/skill"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response body is:
      """
      {
        "items": [
          {
            "id": "land-pr",
            "title": "Land a PR",
            "description": "Lands a pull request.",
            "form": "prompt",
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/skill/land-pr"},
              "artifact": {"href": "/api/v1/packs/core/artifacts/skill/land-pr"}
            }
          },
          {
            "id": "terse",
            "title": "Terse",
            "description": "A skill the activation does not select.",
            "form": "prompt",
            "_links": {
              "effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/skill/terse"},
              "artifact": {"href": "/api/v1/packs/core/artifacts/skill/terse"}
            }
          }
        ],
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/skill"},
          "up": {"href": "/api/v1/activations/a-1"}
        }
      }
      """
    And following the link "effective-artifact" of every element of "items" answers 200

  Scenario: A selected skill is answered in force with the skill its pack declares and its links
    When I GET "/activations/a-1/artifacts/skill/land-pr"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/kind" is "skill"
    And the field at "/artifactId" is "land-pr"
    And the field at "/packId" is "core"
    And the field at "/level" is "built-in"
    And the field at "/state" is "active"
    And the field at "/reason" is "selected"
    And the field at "/required" is false
    And the field at "/policy" is "open"
    And the field at "/skill/title" is "Land a PR"
    And the field at "/skill/body" is present
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/skill/land-pr"
    And the field at "/_links/artifact/href" is "/api/v1/packs/core/artifacts/skill/land-pr"
    And the field at "/_links/collection/href" is "/api/v1/activations/a-1/artifacts/skill"
    And the field at "/_links/up/href" is "/api/v1/activations/a-1"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/skill/title" is "Land a PR"

  Scenario: A skill the activation does not select is answered with its state and reason, not refused
    When I GET "/activations/a-1/artifacts/skill/terse"
    Then the response status is 200
    And the field at "/state" is "inactive"
    And the field at "/reason" is "not_selected"
    And the field at "/skill/title" is "Terse"

  Scenario Outline: <operation> answers 304 to a matching If-None-Match, and its tag is not the tag of <against>
    When I GET "<path>"
    Then the response status is 200
    When I remember the header "ETag"
    And I GET "<path>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response body is empty
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 304
    When I GET "<other>" with If-None-Match the remembered header "ETag"
    Then the response status is 200

    Examples:
      | operation                | against                              | path                                         | other                                        |
      | listEffectiveAssets      | listEffectiveSkills                  | /activations/a-1/artifacts/asset             | /activations/a-1/artifacts/skill             |
      | getEffectiveAsset        | its payload                          | /activations/a-1/artifacts/asset/run         | /activations/a-1/artifacts/asset/run/payload |
      | getEffectiveAssetPayload | its descriptor                       | /activations/a-1/artifacts/asset/run/payload | /activations/a-1/artifacts/asset/run         |
      | listEffectiveSkills      | listEffectiveAssets                  | /activations/a-1/artifacts/skill             | /activations/a-1/artifacts/asset             |
      | getEffectiveSkill        | another skill                        | /activations/a-1/artifacts/skill/land-pr     | /activations/a-1/artifacts/skill/terse       |

  Scenario Outline: <operation> under an activation that is not stored is 404 activation_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "activation_not_found"

    Examples:
      | operation                | path                                          |
      | listEffectiveAssets      | /activations/nope/artifacts/asset             |
      | getEffectiveAsset        | /activations/nope/artifacts/asset/run         |
      | getEffectiveAssetPayload | /activations/nope/artifacts/asset/run/payload |
      | listEffectiveSkills      | /activations/nope/artifacts/skill             |
      | getEffectiveSkill        | /activations/nope/artifacts/skill/land-pr     |

  Scenario Outline: <operation> of an id no pack of the closure holds is 404 artifact_not_found, also against If-None-Match
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "artifact_not_found"
    When I GET "<path>" with the header "If-None-Match": "*"
    Then the response status is 404
    And the problem code is "artifact_not_found"

    Examples:
      | operation                | path                                          |
      | getEffectiveAsset        | /activations/a-1/artifacts/asset/ghost         |
      | getEffectiveAssetPayload | /activations/a-1/artifacts/asset/ghost/payload |
      | getEffectiveSkill        | /activations/a-1/artifacts/skill/ghost         |
      | getEffectiveAsset        | /activations/a-1/artifacts/asset/land-pr       |
      | getEffectiveSkill        | /activations/a-1/artifacts/skill/run           |

  Scenario: The effective list does not take kind asset, as before; the activation's assets are listed at their own path
    When I GET "/activations/a-1/artifacts?kind=asset"
    Then the response status is 400
    When I GET "/activations/a-1/artifacts?kind=skill"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      skill:land-pr
      skill:terse
      """
