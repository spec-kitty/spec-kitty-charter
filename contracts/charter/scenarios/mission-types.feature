@contract @mission-types
Feature: A pack's mission types are read with their steps, step contracts and skeletons
  A client reads a pack's mission type in the Artifact envelope (getPackMissionType), with its steps in stored order
  then its on-demand steps, each marked and linked, and its skeletons, each linked (FR-002). It follows a step link to
  the step (getPackMissionStep): its stored fields, its prompt and guidelines files byte for byte, and links to its
  mission type, its step contract and the skeleton of each expected output artifact that names one (FR-003). The step
  contract is a served kind of the generic item read (FR-001, FR-004), and a skeleton is read by its one-segment name,
  the path under the type's templates/ escaped as in RFC 6901 (FR-005, D9). Both kinds are listed, filtered and
  referenced like the other served kinds. Refusals come in order: the pack, then the mission type, then the step or
  the skeleton (FR-009).

  Background:
    Given the org pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: org
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: '1.0'
      id: software-dev
      title: Software Development
      description: Build high-quality software with structured workflows and test-driven development
      content: You are a software development agent following TDD practices.
      requires_substantive_spec: true
      path_conventions:
        workspace: src/
        tests: tests/
      expected_output_artifacts:
      - artifact_key: evidence.research
        artifact_class: evidence
        path_pattern: research.md
      steps:
      - id: specify
        title: Specification
        description: Define user scenarios and acceptance criteria
        prompt_path: steps/specify.prompt.md
        guidelines_path: steps/specify.guidelines.md
        expected_output_artifacts:
        - artifact_key: input.spec.main
          artifact_class: input
          path_pattern: spec.md
          blocking: true
          template_path: templates/spec-template.md
      - id: plan
        title: Implementation Planning
        description: Design technical architecture
        role: architect
        prompt_path: steps/plan.prompt.md
        expected_input_artifacts:
        - artifact_key: input.spec.main
          path_pattern: spec.md
          blocking: true
        expected_output_artifacts:
        - artifact_key: output.plan.main
          artifact_class: output
          path_pattern: plan.md
          blocking: true
      on_demand_steps:
      - id: retrospect
        title: Retrospective
        description: Capture what helped, what did not and the governance gaps of a finished mission.
        role: facilitator
        prompt_path: steps/retrospect.prompt.md
      """
    And the org pack "main" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      ---
      description: Create a mission specification
      ---
      # /spec-kitty.specify - Create Mission Specification

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->

      ## Discovery

      Ask the operator what the mission is for.
      """
    And the org pack "main" with the file "missions/software-dev/steps/specify.guidelines.md":
      """
      # Specify Action - Governance Guidelines

      ## Core Authorship Focus

      - Focus on **WHAT** users need and **WHY**.
      """
    And the org pack "main" with the file "missions/software-dev/steps/plan.prompt.md":
      """
      # /spec-kitty.plan - Create Implementation Plan

      Design the architecture before any code is written.
      """
    And the org pack "main" with the file "missions/software-dev/steps/retrospect.prompt.md":
      """
      # /spec-kitty.retrospect - Mission Retrospective

      Record what helped and what did not.
      """
    And the org pack "main" with the file "missions/software-dev/templates/spec-template.md":
      """
      # Mission Specification: [MISSION NAME]
      <!-- Replace [MISSION NAME] with the confirmed friendly title. -->

      ## User Scenarios & Testing
      """
    And the org pack "main" with the file "missions/software-dev/templates/a~1b.md":
      """
      # A skeleton whose file name holds a tilde and a one
      """
    And the org pack "main" with the file "missions/documentation/documentation.mission-type.yaml":
      """
      schema_version: '1.0'
      id: documentation
      title: Documentation
      description: Write documentation that follows the Divio system
      steps:
      - id: discover
        title: Discover
      """
    And the org pack "main" with the file "missions/documentation/templates/divio/howto-template.md":
      """
      # How to [do the thing]

      ## Steps

      1. State the goal.
      """
    And the org pack "main" with the file "mission_step_contracts/specify.step-contract.yaml":
      """
      schema_version: '1.0'
      id: specify
      action: specify
      mission: software-dev
      gates:
        - on_transition: in_progress->for_review
          handler: spec-kitty-pre-review
          handler_kind: mission_step_contract
          schema_version: '1.0'
          fail_open: true
          provenance: built-in
      steps:
        - id: bootstrap
          description: Load charter context for this action
          command: spec-kitty charter context --action specify --json
          inputs:
            - flag: --mission
              source: mission_slug
              optional: true
        - id: capture_intent
          description: Capture confirmed user intent
          delegates_to:
            kind: directive
            candidates:
              - GUARD
              - NOPE
          guidance: Ask before you write.
        - id: map_examples
          description: Capture concrete rules and examples
          delegates_to:
            kind: procedure
            candidates:
              - example-mapping-workshop
      """
    And the org pack "main" with the file "mission_step_contracts/orphan.step-contract.yaml":
      """
      schema_version: '1.0'
      id: orphan
      action: gather
      mission: research
      steps:
        - id: first
          description: The first step
      """
    And the org pack "main" with the file "directives/guard.directive.yaml":
      """
      schema_version: '1.0'
      id: GUARD
      title: Guard the gate
      intent: Guard it.
      enforcement: required
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:software-dev
        target: directive:GUARD
        relation: requires
      - source: mission_step_contract:specify
        target: directive:GUARD
        relation: requires
      """

  Scenario: A mission type is read in the envelope, its steps then its on-demand steps, then its skeletons, each linked
    FR-002, US1 AS1. The directive read is the control that the fixture pack loads. The missionType member holds the
    stored fields; a field the file does not declare (path conventions it does not name, a blocking it does not say) is
    absent or false, never invented. Each step and each skeleton links to its own read, and every link answers 200.
    Skeletons are ordered by name in code-point order. D9: the file templates/a~1b.md, whose name holds a literal
    "~1", is named a~01b.md ('~' escaped to '~0' first), which pins the order of the escaping and of the decoding.
    When I GET "/packs/main/artifacts/directive/GUARD"
    Then the response status is 200
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "kind": "mission_type",
        "id": "software-dev",
        "packId": "main",
        "missionType": {
          "id": "software-dev",
          "schemaVersion": "1.0",
          "title": "Software Development",
          "description": "Build high-quality software with structured workflows and test-driven development",
          "content": "You are a software development agent following TDD practices.",
          "requiresSubstantiveSpec": true,
          "pathConventions": {"workspace": "src/", "tests": "tests/"},
          "expectedOutputArtifacts": [
            {"artifactKey": "evidence.research", "artifactClass": "evidence", "pathPattern": "research.md", "blocking": false}
          ],
          "steps": [
            {"id": "specify", "title": "Specification", "onDemand": false, "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"}}},
            {"id": "plan", "title": "Implementation Planning", "onDemand": false, "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/plan"}}},
            {"id": "retrospect", "title": "Retrospective", "onDemand": true, "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/retrospect"}}}
          ],
          "skeletons": [
            {"name": "a~01b.md", "path": "templates/a~1b.md", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/a~01b.md"}}},
            {"name": "spec-template.md", "path": "templates/spec-template.md", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"}}}
          ]
        },
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev"},
          "collection": {"href": "/api/v1/packs/main/artifacts?kind=mission_type"}
        }
      }
      """
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/plan"
    Then the response status is 200
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/retrospect"
    Then the response status is 200
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/a~01b.md"
    Then the response status is 200
    And the field at "/name" is "a~01b.md"
    And the field at "/path" is "templates/a~1b.md"
    And the field at "/content" is the lines:
      """
      # A skeleton whose file name holds a tilde and a one
      """
    And the field at "/sha256" is "sha256:779294cb464502d4d2cb24424ea41761f998cad15ca7c1e447b753f9f1326683"
    And the field at "/size" is 53
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    When I follow the link "collection"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_type:documentation
      mission_type:software-dev
      """

  Scenario: A step in sequence is served with its prompt and guidelines byte for byte and its links
    FR-003, US1 AS3. The prompt is the stored file, its frontmatter and the bare spdd slot markers included; the
    guidelines likewise. The step links its mission type, the step contract whose mission and action are the type and
    the step, and, on its expected output artifact, the skeleton its templatePath names.
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the field at "/id" is "specify"
    And the field at "/title" is "Specification"
    And the field at "/description" is "Define user scenarios and acceptance criteria"
    And the field at "/role" is absent
    And the field at "/onDemand" is false
    And the field at "/prompt" is the lines:
      """
      ---
      description: Create a mission specification
      ---
      # /spec-kitty.specify - Create Mission Specification

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->

      ## Discovery

      Ask the operator what the mission is for.
      """
    And the field at "/guidelines" is the lines:
      """
      # Specify Action - Governance Guidelines

      ## Core Authorship Focus

      - Focus on **WHAT** users need and **WHY**.
      """
    And the field at "/expectedInputArtifacts" holds exactly 0 items
    And the field at "/expectedOutputArtifacts" holds exactly 1 item
    And the field at "/expectedOutputArtifacts/0/artifactKey" is "input.spec.main"
    And the field at "/expectedOutputArtifacts/0/templatePath" is "templates/spec-template.md"
    And the field at "/expectedOutputArtifacts/0/_links/skeleton/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    And the field at "/_links/self/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"
    And the field at "/_links/mission-type/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev"
    And the field at "/_links/step-contract/href" is "/api/v1/packs/main/artifacts/mission_step_contract/specify"
    When I follow the link "step-contract"
    Then the response status is 200
    And the field at "/kind" is "mission_step_contract"
    And the field at "/missionStepContract/action" is "specify"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the field at "/name" is "spec-template.md"

  Scenario: A step without guidelines answers no guidelines member, and a step no contract binds has no step-contract link
    Edge case of the spec: a step without a guidelines.md answers no guidelines member, never an empty string. The plan
    step has expected inputs and an expected output without a templatePath, so that output carries no skeleton link.
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/plan"
    Then the response status is 200
    And the field at "/role" is "architect"
    And the field at "/prompt" is the lines:
      """
      # /spec-kitty.plan - Create Implementation Plan

      Design the architecture before any code is written.
      """
    And the field at "/guidelines" is absent
    And the field at "/expectedInputArtifacts/0/artifactKey" is "input.spec.main"
    And the field at "/expectedInputArtifacts/0/pathPattern" is "spec.md"
    And the field at "/expectedInputArtifacts/0/blocking" is true
    And the field at "/expectedOutputArtifacts/0/artifactKey" is "output.plan.main"
    And the field at "/expectedOutputArtifacts/0/templatePath" is absent
    And the field at "/expectedOutputArtifacts/0/_links/skeleton" is absent
    And the answer has no link "step-contract"
    When I follow the link "mission-type"
    Then the response status is 200
    And the field at "/id" is "software-dev"

  Scenario: An on-demand step is served like any step, marked on-demand
    US1 AS5.
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/retrospect"
    Then the response status is 200
    And the field at "/id" is "retrospect"
    And the field at "/onDemand" is true
    And the field at "/role" is "facilitator"
    And the field at "/prompt" is the lines:
      """
      # /spec-kitty.retrospect - Mission Retrospective

      Record what helped and what did not.
      """
    And the field at "/guidelines" is absent

  Scenario: A flat skeleton is served as text with its digest and size, linked to its mission type
    FR-005, US3 AS1. The content is the stored bytes as text, since they are valid UTF-8; sha256 and size are those
    of the stored bytes.
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "name": "spec-template.md",
        "path": "templates/spec-template.md",
        "missionTypeId": "software-dev",
        "encoding": "text",
        "content": "# Mission Specification: [MISSION NAME]\n<!-- Replace [MISSION NAME] with the confirmed friendly title. -->\n\n## User Scenarios & Testing\n",
        "sha256": "sha256:4f892e0384d5503103f359755d5061b1449fd38b85bc090bdd44a2538334a01d",
        "size": 136,
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"},
          "mission-type": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev"}
        }
      }
      """

  Scenario: A nested skeleton is read by its one-segment name, the path escaped as in RFC 6901
    US3 AS2, D9: templates/divio/howto-template.md of documentation is named divio~1howto-template.md, which the
    mission type lists and links.
    When I GET "/packs/main/artifacts/mission_type/documentation"
    Then the response status is 200
    And the field at "/missionType/skeletons/0/name" is "divio~1howto-template.md"
    And the field at "/missionType/skeletons/0/path" is "templates/divio/howto-template.md"
    And the field at "/missionType/skeletons/0/_links/self/href" is "/api/v1/packs/main/artifacts/mission_type/documentation/skeletons/divio~1howto-template.md"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/divio~1howto-template.md"
    Then the response status is 200
    And the response body is:
      """
      {
        "name": "divio~1howto-template.md",
        "path": "templates/divio/howto-template.md",
        "missionTypeId": "documentation",
        "encoding": "text",
        "content": "# How to [do the thing]\n\n## Steps\n\n1. State the goal.\n",
        "sha256": "sha256:e0e93ebc3d54f0553ffe12a6a7d321b7964dc80e05167c3fa113712046772918",
        "size": 54,
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts/mission_type/documentation/skeletons/divio~1howto-template.md"},
          "mission-type": {"href": "/api/v1/packs/main/artifacts/mission_type/documentation"}
        }
      }
      """

  Scenario: A step contract is read through the generic item read, linked to its mission type, its step and its delegates
    FR-001, FR-004, US2 AS1. A delegation candidate of a served kind that the pack holds links to its pack-scoped read;
    one the pack does not hold (NOPE), or one of a kind the service does not serve (a procedure), has no link. A step
    without inputs answers an empty list. No ETag: the kind has no write. A step contract whose mission names a mission
    type the pack does not hold still reads, without mission-type and step links.
    When I GET "/packs/main/artifacts/mission_step_contract/specify"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "kind": "mission_step_contract",
        "id": "specify",
        "packId": "main",
        "missionStepContract": {
          "id": "specify",
          "schemaVersion": "1.0",
          "action": "specify",
          "mission": "software-dev",
          "steps": [
            {
              "id": "bootstrap",
              "description": "Load charter context for this action",
              "command": "spec-kitty charter context --action specify --json",
              "inputs": [{"flag": "--mission", "source": "mission_slug", "optional": true}]
            },
            {
              "id": "capture_intent",
              "description": "Capture confirmed user intent",
              "inputs": [],
              "delegatesTo": {
                "kind": "directive",
                "candidates": [
                  {"id": "GUARD", "_links": {"artifact": {"href": "/api/v1/packs/main/artifacts/directive/GUARD"}}},
                  {"id": "NOPE", "_links": {}}
                ]
              },
              "guidance": "Ask before you write."
            },
            {
              "id": "map_examples",
              "description": "Capture concrete rules and examples",
              "inputs": [],
              "delegatesTo": {
                "kind": "procedure",
                "candidates": [
                  {"id": "example-mapping-workshop", "_links": {}}
                ]
              }
            }
          ],
          "gates": [
            {
              "onTransition": "in_progress->for_review",
              "handler": "spec-kitty-pre-review",
              "handlerKind": "mission_step_contract",
              "schemaVersion": "1.0",
              "failOpen": true,
              "provenance": "built-in"
            }
          ]
        },
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts/mission_step_contract/specify"},
          "collection": {"href": "/api/v1/packs/main/artifacts?kind=mission_step_contract"},
          "mission-type": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev"},
          "step": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/specify"}
        }
      }
      """
    When I follow the link "step"
    Then the response status is 200
    And the field at "/id" is "specify"
    When I GET "/packs/main/artifacts/mission_step_contract/orphan"
    Then the response status is 200
    And the field at "/missionStepContract/mission" is "research"
    And the field at "/missionStepContract/steps/0/inputs" holds exactly 0 items
    And the field at "/_links/self/href" is "/api/v1/packs/main/artifacts/mission_step_contract/orphan"
    And the answer has no link "mission-type"
    And the answer has no link "step"

  Scenario: The list filtered by mission_type and by mission_step_contract names the pack's items, each linked to its read
    FR-001, US1 AS2, US2 AS3. Ids in code-point order; a mission type's title is its title, a step contract's its action.
    When I GET "/packs/main/artifacts?kind=mission_type"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {"kind": "mission_type", "id": "documentation", "title": "Documentation", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/documentation"}}},
          {"kind": "mission_type", "id": "software-dev", "title": "Software Development", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_type/software-dev"}}}
        ],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts?kind=mission_type"},
          "first": {"href": "/api/v1/packs/main/artifacts?kind=mission_type"}
        }
      }
      """
    And following the link "self" of every element of "items" answers 200
    When I GET "/packs/main/artifacts?kind=mission_step_contract"
    Then the response status is 200
    And the response body is:
      """
      {
        "items": [
          {"kind": "mission_step_contract", "id": "orphan", "title": "gather", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_step_contract/orphan"}}},
          {"kind": "mission_step_contract", "id": "specify", "title": "specify", "_links": {"self": {"href": "/api/v1/packs/main/artifacts/mission_step_contract/specify"}}}
        ],
        "page": {"hasNextPage": false, "nextPageCursor": null, "pageSize": 50},
        "_links": {
          "self": {"href": "/api/v1/packs/main/artifacts?kind=mission_step_contract"},
          "first": {"href": "/api/v1/packs/main/artifacts?kind=mission_step_contract"}
        }
      }
      """
    And following the link "self" of every element of "items" answers 200

  Scenario: The references of a mission type and of a step contract are read like any served kind's
    FR-001: /references answers for both kinds; an unknown id is the not-found code of its kind.
    When I GET "/packs/main/artifacts/mission_type/software-dev/references"
    Then the response status is 200
    And the field at "/items" holds exactly 1 item
    And the field at "/items/0/urn" is "directive:GUARD"
    And the field at "/items/0/relation" is "requires"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/main/artifacts/directive/GUARD"
    When I GET "/packs/main/artifacts/mission_step_contract/specify/references"
    Then the response status is 200
    And the field at "/items" holds exactly 1 item
    And the field at "/items/0/urn" is "directive:GUARD"
    And the field at "/items/0/relation" is "requires"
    When I GET "/packs/main/artifacts/mission_type/nope/references"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts/mission_step_contract/nope/references"
    Then the response status is 404
    And the problem code is "mission_step_contract_not_found"

  Scenario: The refusals come in order: the pack, then the mission type, then the step, the skeleton or the step contract
    FR-009, US1 AS4, US2 AS2, US3 AS3. An unknown pack is pack_not_found whatever the rest of the path names; an unknown
    mission type of a known pack is mission_type_not_found, also under a step or a skeleton; an unknown step is
    action_not_found (a step is the action action:<type>/<step>); a name that does not decode to a skeleton of the type
    is skeleton_not_found; a nested path spelled with a real slash after skeletons/ names no operation, so it is a 404
    without a code; an unknown step contract is mission_step_contract_not_found.
    When I GET "/packs/nope/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I GET "/packs/nope/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I GET "/packs/nope/artifacts/mission_type/software-dev"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I GET "/packs/main/artifacts/mission_type/research-x"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/main/artifacts/mission_type/research-x/steps/specify"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts/mission_type/research-x/skeletons/spec-template.md"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/deploy"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/steps/specify"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/nope.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/howto-template.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/divio~2howto-template.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/divio~1howto-template.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/divio/howto-template.md"
    Then the response status is 404
    And the problem has no code
    When I GET "/packs/main/artifacts/mission_step_contract/nope"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Mission step contract not found", "status": 404, "code": "mission_step_contract_not_found", "detail": "Pack main has no mission_step_contract nope."}
      """

  Scenario: A configured pack without pack.yaml is pack_unreadable on every mission type read
    Operator ruling of 2026-10-31, FR-009: a configured pack without a readable pack.yaml cannot be read, whatever its
    other files hold, so every mission type read of it is a 500 pack_unreadable, after the pack is found and before the
    mission type is judged.
    Given the org pack "bare" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: '1.0'
      id: software-dev
      title: Software Development
      description: Build software
      steps:
      - id: specify
        title: Specification
      """
    When I GET "/packs/bare/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/packs/bare/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/packs/bare/artifacts/mission_type/software-dev"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response has no "Charter-Source-Fingerprint" header
    When I GET "/packs/bare/artifacts/mission_step_contract/nope"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A step contract is removed as the five kinds are, and its mission type stays
    FR-010, US6 AS1, D10. Neither mission kind answers an ETag, so a DELETE sends If-Match: * (or the pack revision) as
    for any kind. The removal answers 204 with the pack's new revision; the next DELETE of the id is its kind's
    not-found code.
    Given registration is switched on
    And the org pack "main" with the file "mission_step_contracts/discover.step-contract.yaml":
      """
      schema_version: '1.0'
      id: discover
      action: discover
      mission: documentation
      steps:
        - id: first
          description: The first step
      """
    When I DELETE "/packs/main/artifacts/mission_step_contract/discover" with the header "If-Match": "*"
    Then the response status is 204
    And the response header "ETag" appears once
    And the pack "main" holds no file "mission_step_contracts/discover.step-contract.yaml"
    When I GET "/packs/main/artifacts/mission_step_contract/discover"
    Then the response status is 404
    And the problem code is "mission_step_contract_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation"
    Then the response status is 200
    And the field at "/missionType/id" is "documentation"
    When I DELETE "/packs/main/artifacts/mission_step_contract/discover" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "mission_step_contract_not_found"

  Scenario: A mission type is removed with its step files and its skeletons, and every read of it is then not found
    FR-010, US6 AS1, D10: the type, its step prompt and guidelines files and its skeletons go as one removal under one
    impact check. A DELETE needs the pack revision: none is 428, a stale one is 412, and neither changes anything. The
    other mission type keeps its skeletons.
    Given registration is switched on
    And the org pack "main" with the file "missions/documentation/documentation.mission-type.yaml":
      """
      schema_version: '1.0'
      id: documentation
      title: Documentation
      description: Write documentation that follows the Divio system
      steps:
      - id: discover
        title: Discover
        prompt_path: steps/discover.prompt.md
        guidelines_path: steps/discover.guidelines.md
      """
    And the org pack "main" with the file "missions/documentation/steps/discover.prompt.md":
      """
      # /spec-kitty.discover - Discover the audience

      Ask who reads the documentation.
      """
    And the org pack "main" with the file "missions/documentation/steps/discover.guidelines.md":
      """
      # Discover Action - Governance Guidelines

      - Name the reader first.
      """
    When I send DELETE "/packs/main/artifacts/mission_type/documentation"
    Then the response status is 428
    When I DELETE "/packs/main/artifacts/mission_type/documentation" with the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    When I GET "/packs/main/artifacts/mission_type/documentation/steps/discover"
    Then the response status is 200
    And the field at "/prompt" contains "Ask who reads the documentation."
    When I GET "/packs/main"
    And I remember the ETag
    And I DELETE "/packs/main/artifacts/mission_type/documentation" with the remembered ETag
    Then the response status is 204
    And the response header "ETag" appears once
    And the pack "main" holds no file "missions/documentation/documentation.mission-type.yaml"
    And the pack "main" holds no file "missions/documentation/steps/discover.prompt.md"
    And the pack "main" holds no file "missions/documentation/steps/discover.guidelines.md"
    And the pack "main" holds no file "missions/documentation/templates/divio/howto-template.md"
    And the pack "main" holds no file "missions/documentation"
    When I GET "/packs/main/artifacts/mission_type/documentation"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/steps/discover"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/divio~1howto-template.md"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/packs/main/artifacts?kind=mission_type"
    Then the response status is 200
    And the field at "/items" holds exactly 1 item
    And the field at "/items/0/id" is "software-dev"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    When I DELETE "/packs/main/artifacts/mission_type/documentation" with the header "If-Match": "*"
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: A mission type a step contract binds is not removed until the contract is gone
    FR-010, US6 AS1, D10: a step contract whose mission names the type refers to it, as any referrer of the five kinds
    does, and the refusal names it and changes nothing.
    Given registration is switched on
    And the org pack "main" with the file "mission_step_contracts/discover.step-contract.yaml":
      """
      schema_version: '1.0'
      id: discover
      action: discover
      mission: documentation
      steps:
        - id: first
          description: The first step
      """
    When I DELETE "/packs/main/artifacts/mission_type/documentation" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/packs/0/packId" is "main"
    And the field at "/violations/0/detail" contains "mission_step_contract:discover"
    When I GET "/packs/main/artifacts/mission_type/documentation/skeletons/divio~1howto-template.md"
    Then the response status is 200
    When I DELETE "/packs/main/artifacts/mission_step_contract/discover" with the header "If-Match": "*"
    Then the response status is 204
    When I DELETE "/packs/main/artifacts/mission_type/documentation" with the header "If-Match": "*"
    Then the response status is 204

  Scenario: A mission type whose action node an author-written graph document names is not removed, and no file is rewritten
    FR-010, US6 AS1, D10: an edge that names one of the type's action nodes action:<type>/<step> refers to the type. The
    edge is in a document the author wrote, so the DELETE refuses and the pack is byte-identical; another kind's
    DELETE still goes.
    Given registration is switched on
    And the org pack "main" with the file "actions.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:documentation/discover
        kind: action
        label: discover
      edges:
      - source: action:documentation/discover
        target: directive:GUARD
        relation: requires
      """
    When I GET "/info"
    And I remember the field "sourceFingerprint" of the response body
    And I DELETE "/packs/main/artifacts/mission_type/documentation" with the header "If-Match": "*"
    Then the response status is 409
    And the problem code is "artifact_referenced"
    And the field at "/violations/0/detail" contains "action:documentation/discover"
    When I GET "/info"
    Then the field "sourceFingerprint" of the response body is the remembered one
    When I GET "/packs/main/artifacts/mission_type/documentation"
    Then the response status is 200
    When I DELETE "/packs/main/artifacts/mission_step_contract/orphan" with the header "If-Match": "*"
    Then the response status is 204
