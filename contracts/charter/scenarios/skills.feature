# The skill reads of a pack (#107, ADR-0074 decisions 4, 9 and 10; FR-007, FR-008 and FR-010): GET
# /packs/{packId}/artifacts/skill lists a pack's skills in id order, and GET …/skill/{artifactId} reads one skill in the
# Artifact envelope, a prompt skill with its body verbatim. The 400, 401, 403 and 500 answers of the two operations are
# asserted in read-refusals.feature, every-operation.feature and unexpected-failure.feature.

@contract @artifacts
Feature: A pack's skills are listed and read, a prompt skill with its body exactly as held
  A client lists a pack's skills to learn which apply, and reads one to run it. A prompt skill is answered with its
  body, the text of the file its body_path names, byte for byte: its final newline, its spacing and every character
  as the pack holds them. A wrapper skill is answered with what it expands to and no body. The body path, overrides and
  enhances are not answered: the contract answers content, not paths, and edges are read through the references. Every
  read answers the pack revision as its ETag. The pack is judged first: an unknown pack is 404 pack_not_found, then a
  skill the pack does not hold is 404 artifact_not_found.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: qa-pack
      pack_level: org
      """
    And the org pack "qa" with the file "skills/land-pr.skill.yaml":
      """
      schema_version: "1.0"
      id: land-pr
      title: Land a PR
      description: Lands a pull request.
      triggers: ["land the pr", "merge it"]
      form: prompt
      body_path: land-pr.skill.md
      parameters:
        - name: pr
          required: true
          description: The pull request, by number.
      invocation:
        user_invocable: true
        model_invocable: false
        side_effects: [gh-write]
      tools: [Bash]
      version: 1.2.0
      maintainers: ["@spec-kitty/core"]
      """
    And the org pack "qa" with the file "skills/land-pr.skill.md" in base64:
      """
      TGFuZCB0aGUgcHVsbCByZXF1ZXN0IOKAlCDDvGJlciBhbGxlcy4KCiAgS2VlcCAgIHRoZSBzcGFjZXMuCg==
      """
    And the org pack "qa" with the file "skills/terse.skill.yaml":
      """
      schema_version: "1.0"
      id: terse
      title: Terse
      description: A prompt skill whose body has no final newline.
      form: prompt
      body_path: terse.skill.md
      """
    And the org pack "qa" with the file "skills/terse.skill.md" in base64:
      """
      Tm8gZmluYWwgbmV3bGluZS4=
      """
    And the org pack "qa" with the file "skills/pr-status.skill.yaml":
      """
      schema_version: "1.0"
      id: pr-status
      title: Show the PR status
      description: Lists the open pull requests before landing one.
      form: wrapper
      expands_to:
        target: cli:spec-kitty agent tasks status
        args: "--mission {mission}"
      parameters:
        - name: mission
          default: current
      enhances: land-pr
      """

  Scenario: The list answers each skill's id, title, description and form, in id order
    When I GET "/packs/qa/artifacts/skill"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {
            "id": "land-pr",
            "title": "Land a PR",
            "description": "Lands a pull request.",
            "form": "prompt",
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill/land-pr"}}
          },
          {
            "id": "pr-status",
            "title": "Show the PR status",
            "description": "Lists the open pull requests before landing one.",
            "form": "wrapper",
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill/pr-status"}}
          },
          {
            "id": "terse",
            "title": "Terse",
            "description": "A prompt skill whose body has no final newline.",
            "form": "prompt",
            "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill/terse"}}
          }
        ],
        "_links": {"self": {"href": "/api/v1/packs/qa/artifacts/skill"}}
      }
      """
    And following the link "self" of every element of "items" answers 200

  Scenario: A prompt skill is read in the Artifact envelope with every field and its body verbatim
    When I GET "/packs/qa/artifacts/skill/land-pr"
    Then the response status is 200
    And the response body is:
      """
      {
        "kind": "skill",
        "id": "land-pr",
        "packId": "qa",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/skill/land-pr"},
          "collection": {"href": "/api/v1/packs/qa/artifacts/skill"}
        },
        "skill": {
          "schemaVersion": "1.0",
          "id": "land-pr",
          "title": "Land a PR",
          "description": "Lands a pull request.",
          "form": "prompt",
          "triggers": ["land the pr", "merge it"],
          "parameters": [{"name": "pr", "required": true, "description": "The pull request, by number."}],
          "invocation": {"userInvocable": true, "modelInvocable": false, "sideEffects": ["gh-write"]},
          "tools": ["Bash"],
          "version": "1.2.0",
          "maintainers": ["@spec-kitty/core"],
          "body": "Land the pull request — über alles.\n\n  Keep   the spaces.\n"
        }
      }
      """
    And the field at "/skill/bodyPath" is absent

  Scenario: A prompt skill whose body has no final newline is answered without one
    When I GET "/packs/qa/artifacts/skill/terse"
    Then the response status is 200
    And the field at "/skill/body" is "No final newline."
    And the field at "/skill/version" is absent
    And the field at "/skill/maintainers" is absent
    And the field at "/skill/invocation" is absent

  Scenario: A wrapper skill answers what it expands to and no body, and not its enhances edge
    When I GET "/packs/qa/artifacts/skill/pr-status"
    Then the response status is 200
    And the response body is:
      """
      {
        "kind": "skill",
        "id": "pr-status",
        "packId": "qa",
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/skill/pr-status"},
          "collection": {"href": "/api/v1/packs/qa/artifacts/skill"}
        },
        "skill": {
          "schemaVersion": "1.0",
          "id": "pr-status",
          "title": "Show the PR status",
          "description": "Lists the open pull requests before landing one.",
          "form": "wrapper",
          "triggers": [],
          "parameters": [{"name": "mission", "default": "current"}],
          "expandsTo": {"target": "cli:spec-kitty agent tasks status", "args": "--mission {mission}"},
          "tools": []
        }
      }
      """

  Scenario: The list and the item answer the pack revision as their ETag
    When I GET "/packs/qa"
    And I remember the header "ETag"
    And I GET "/packs/qa/artifacts/skill"
    Then the response header "ETag" is the remembered header
    When I GET "/packs/qa/artifacts/skill/land-pr"
    Then the response header "ETag" is the remembered header

  Scenario Outline: <operation> of a pack no configured pack answers to is 404 pack_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "pack_not_found"

    Examples:
      | operation      | path                                |
      | listPackSkills | /packs/nope/artifacts/skill         |
      | getPackSkill   | /packs/nope/artifacts/skill/land-pr |

  Scenario Outline: getPackSkill of a skill the pack does not hold is 404 artifact_not_found
    When I GET "<path>"
    Then the response status is 404
    And the problem code is "artifact_not_found"

    Examples:
      | path                                     |
      | /packs/qa/artifacts/skill/nothing        |
      | /packs/qa/artifacts/skill/land-pr.skill  |
