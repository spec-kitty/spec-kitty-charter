@contract @mission-types
Feature: A pack's mission types are read with their steps and skeletons
  A client reads a pack's mission type in the Artifact envelope (getPackMissionType), with its steps in stored order
  then its on-demand steps, each marked and linked, and its skeletons, each linked (FR-002). It follows a step link to
  the step (getPackMissionStep): its stored fields, its prompt and guidelines files byte for byte, its substeps, and
  links to its mission type and the skeleton of each expected output artifact that names one (FR-003). A skeleton is
  read by its one-segment name, the path under the type's templates/ escaped as in RFC 6901 (FR-005, D9). The mission
  type is listed, filtered and referenced like the other served kinds. Refusals come in order: the pack, then the mission type, then the step or
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
      - source: mission_type:documentation
        target: action:documentation/discover
        relation: requires
      - source: mission_type:software-dev
        target: action:software-dev/specify
        relation: requires
      - source: mission_type:software-dev
        target: action:software-dev/plan
        relation: requires
      - source: mission_type:software-dev
        target: action:software-dev/retrospect
        relation: requires
      - source: mission_type:software-dev
        target: directive:GUARD
        relation: scope
      """

  Scenario: A mission type is read in the envelope, its steps then its on-demand steps, then its skeletons, each linked
    FR-002, US1 AS1. The directive read is the control that the fixture pack loads. The missionType member holds the
    stored fields; a field the file does not declare (path conventions it does not name, a blocking it does not say) is
    absent or false, never invented. Each step and each skeleton links to its own read, and every link answers 200.
    Skeletons are ordered by name in code-point order. D9: the file templates/a~1b.md, whose name holds a literal
    "~1", is named a~01b.md ('~' escaped to '~0' first), which pins the order of the escaping and of the decoding.
    When I GET "/packs/main/artifacts/directive/GUARD"
    Then the response status is 200
    And I remember the header "ETag"
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
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
    guidelines likewise. The step links its mission type and, on its expected output artifact, the skeleton its
    templatePath names.
    When I GET "/packs/main/artifacts/directive/GUARD"
    Then the response status is 200
    And I remember the header "ETag"
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
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
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the field at "/name" is "spec-template.md"

  Scenario: A step without guidelines answers no guidelines member
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
    When I GET "/packs/main/artifacts/directive/GUARD"
    Then the response status is 200
    And I remember the header "ETag"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
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

  Scenario: The list filtered by mission_type names the pack's items, each linked to its read
    FR-001, US1 AS2, US2 AS3. Ids in code-point order; a mission type's title is its title.
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
  Scenario: The references of a mission type are read like any served kind's
    FR-001: /references answers for the kind; an unknown id is the not-found code of its kind. The mission type's
    references are its scope edge and the requires edge to each of its steps (operator ruling
    DM-01M7B1YQR5ZKQPDW6DX64KPNRN).
    When I GET "/packs/main/artifacts/mission_type/software-dev/references"
    Then the response status is 200
    And the field at "/items" holds exactly 4 items
    And the field at "/items/0/urn" is "action:software-dev/specify"
    And the field at "/items/0/relation" is "requires"
    And the field at "/items/1/urn" is "action:software-dev/plan"
    And the field at "/items/2/urn" is "action:software-dev/retrospect"
    And the field at "/items/3/urn" is "directive:GUARD"
    And the field at "/items/3/relation" is "scope"
    And the field at "/items/3/_links/artifact/href" is "/api/v1/packs/main/artifacts/directive/GUARD"
    When I GET "/packs/main/artifacts/mission_type/nope/references"
    Then the response status is 404
    And the problem code is "mission_type_not_found"

  Scenario: The refusals come in order: the pack, then the mission type, then the step or the skeleton
    FR-009, US1 AS4, US2 AS2, US3 AS3. An unknown pack is pack_not_found whatever the rest of the path names; an unknown
    mission type of a known pack is mission_type_not_found, also under a step or a skeleton; an unknown step is
    action_not_found (a step is the action action:<type>/<step>); a name that does not decode to a skeleton of the type
    is skeleton_not_found; a nested path spelled with a real slash after skeletons/ names no operation, so it is a 404
    without a code.
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
    And the org pack "bare" with the file "software-dev.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:software-dev
        target: action:software-dev/specify
        relation: requires
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

  Scenario: Every pack read of one thing carries the pack revision, which moves when the pack changes
    #487, FR-005, FR-006. The mission type, step, skeleton and tactic reads answer the pack revision as
    their ETag, equal to a directive's, and a different one once a DELETE has changed the
    pack. It is a concurrency token, not a cache validator: no read takes If-None-Match.
    Given registration is switched on
    And the org pack "main" with the file "tactics/rehearse.tactic.yaml":
      """
      id: rehearse
      schema_version: '1.0'
      name: Rehearse
      steps:
        - title: Only step
      """
    And the org pack "main" with the file "tactics/discard.tactic.yaml":
      """
      id: discard
      schema_version: '1.0'
      name: Discard
      steps:
        - title: Only step
      """
    When I GET "/packs/main/artifacts/directive/GUARD"
    Then the response status is 200
    And I remember the header "ETag"
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
    When I GET "/packs/main/artifacts/tactic/rehearse"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" is the remembered header
    When I DELETE "/packs/main/artifacts/tactic/discard" with the header "If-Match": "*"
    Then the response status is 204
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" differs from the remembered header
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/packs/main/artifacts/tactic/rehearse"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "ETag" differs from the remembered header

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

  Scenario: A mission type whose action node an author-written graph document names is not removed, and no file is rewritten
    FR-010, US6 AS1, D10: an edge that names one of the type's action nodes action:<type>/<step> refers to the type. The
    edge is in a document the author wrote and is no edge that goes with the type (an applies edge, not a scope edge),
    so the DELETE refuses and the pack is byte-identical; another kind's DELETE still goes.
    Given registration is switched on
    And the org pack "main" with the file "tactics/discard.tactic.yaml":
      """
      id: discard
      schema_version: '1.0'
      name: Discard
      steps:
        - title: Only step
      """
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
        relation: applies
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
    When I DELETE "/packs/main/artifacts/tactic/discard" with the header "If-Match": "*"
    Then the response status is 204

  Scenario Outline: A mission type id that is no slug is a 400 Problem without a code that says why (<case>)
    #411: the detail names the path parameter, its value (cut after 64 code points) and the rule it breaks.
    When I GET "/packs/main/artifacts/mission_type/<id>"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The path parameter missionTypeId is \"<shown>\", which <reason>."

    Examples:
      | case                 | id   | shown | reason                                    |
      | longer than 64       | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa… | is longer than 64 character(s)            |
      | an upper-case letter | Main | Main | is not in the form this parameter accepts |

  # FR-009 (#520): action_sequence and template_set are retired; the closed schema refuses either by its name, and the
  # same archive without the field registers.
  Scenario Outline: A whole-pack registration whose mission type authors <field> is refused, naming the field
    Given registration is switched on
    And the archive "rxp" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXP01
      pack_version: 1.0.0
      parent_pack: null
      name: rxp
      pack_level: org
      """
    And the archive "rxp" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      steps:
      - id: a
        title: A
      """
    And the archive "rxp" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    And the archive "retired" is a copy of the archive "rxp"
    And the archive "retired" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      <field>: <value>
      steps:
      - id: a
        title: A
      """
    When I PUT "/packs/rxp" with the archive "retired"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/pointer" is "missions/rx/rx.mission-type.yaml"
    And the field at "/problem/violations/0/detail" contains "<field>"
    When I PUT "/packs/rxp" with the archive "rxp"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples:
      | field           | value      |
      | action_sequence | [a]        |
      | template_set    | rx-default |

  # FR-011 (operator ruling DM-01M7B1YQR5ZKQPDW6DX64KPNRN): every step of a type is linked from it. An override that
  # drops a step and keeps the step's edge is refused where it enters, at its own registration, with the stray edge at
  # its graph document; the same override without the edge registers.
  Scenario: A mission type that drops a step and keeps its requires edge is refused at registration, beside the one that drops both
    Given registration is switched on
    And the archive "rxp" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXP01
      pack_version: 1.0.0
      parent_pack: null
      name: rxp
      pack_level: org
      """
    And the archive "rxp" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type that dropped its step b.
      steps:
      - id: a
        title: A
      """
    And the archive "rxp" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: mission_type:rx
        target: action:rx/b
        relation: requires
      """
    When I PUT "/packs/rxp" with the archive "rxp"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "mission_step_unlinked"
    And the field at "/problem/violations/0/pointer" is "mission_type.graph.yaml"
    And the field at "/problem/violations/0/detail" contains "action:rx/b names no step of the type"
    Given the archive "fixed" is a copy of the archive "rxp"
    And the archive "fixed" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    When I PUT "/packs/rxp" with the archive "fixed"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # FR-011: a step without its edge is refused at the mission type file.
  Scenario: A mission type with a step its type does not link is refused at registration, naming the step
    Given registration is switched on
    And the archive "rxp" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXP01
      pack_version: 1.0.0
      parent_pack: null
      name: rxp
      pack_level: org
      """
    And the archive "rxp" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      steps:
      - id: a
        title: A
      on_demand_steps:
      - id: help
        title: Help
      """
    And the archive "rxp" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    When I PUT "/packs/rxp" with the archive "rxp"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "mission_step_unlinked"
    And the field at "/problem/violations/0/pointer" is "missions/rx/rx.mission-type.yaml"
    And the field at "/problem/violations/0/detail" contains "the step help of mission_type:rx has no requires edge"

  # FR-011 at compose: each pack's own links are judged at its load; a requires edge another pack writes at the mission
  # type in force is judged once the merge has placed it. Naming an action that is no step of the type, it makes every
  # read of a closure that holds it refuse pack_reference_artifact_missing, with the violation mission_step_unlinked
  # (operator rulings D2 and Q-C), as a registration of the closure is refused; the twin, naming a step of the type,
  # is read.
  Scenario: Another pack's requires edge at a mission type to an action that is no step of it refuses the closure's reads, beside the edge to its step
    Given the org pack "rxbase" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXB01
      pack_version: 1.0.0
      parent_pack: null
      name: rxbase
      pack_level: org
      """
    And the org pack "rxbase" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      steps:
      - id: a
        title: A
      """
    And the org pack "rxbase" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:rx/a
        kind: action
        label: a
      - urn: action:rx/c
        kind: action
        label: c
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    And the org pack "rxext" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXE01
      pack_version: 1.0.0
      parent_pack: null
      name: rxext
      pack_level: org
      depends_on:
      - pack_id: 01M7B19K0000000000000RXB01
        range: '>=1.0'
      """
    And the org pack "rxext" with the file "extra.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: rxbase::mission_type:rx
        target: rxbase::action:rx/c
        relation: requires
      """
    And the activation "rx-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "rxext"}], "selected": {"mission_type": ["rx"]}}
      """
    When I GET "/activations/rx-1/graph"
    Then the response status is 500
    And the problem code is "pack_reference_artifact_missing"
    And the field at "/detail" contains "mission_type:rx"
    And the field at "/detail" contains "action:rx/c"
    And the field at "/violations/0/code" is "mission_step_unlinked"
    And the field at "/packs/0/packId" is "rxbase"
    Given the org pack "rxext" with the file "extra.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: rxbase::mission_type:rx
        target: rxbase::action:rx/a
        relation: requires
      """
    When I GET "/activations/rx-1/graph"
    Then the response status is 200

  # FR-011 on a composing read: a configured pack whose own mission type has a step without its requires edge makes
  # every read of a closure that holds it refuse, naming the mission type file; the twin with the edge is read.
  Scenario: A closure holding a pack whose mission type has an unlinked step is unreadable and names the type file
    Given the org pack "rxlone" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000RXN01
      pack_version: 1.0.0
      parent_pack: null
      name: rxlone
      pack_level: org
      """
    And the org pack "rxlone" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type.
      steps:
      - id: a
        title: A
      - id: b
        title: B
      """
    And the org pack "rxlone" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "missions/rx/rx.mission-type.yaml"
    And the field at "/detail" contains "the step b of mission_type:rx has no requires edge"
    Given the org pack "rxlone" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: mission_type:rx
        target: action:rx/b
        relation: requires
      """
    When I GET "/offering"
    Then the response status is 200

  # FR-004 (operator ruling DM-01M7B1YH1GF15RD0G4ZPSA8KW9): a step's substeps are served in authored order, every field
  # kept. A candidate the pack holds, written bare, links its pack-scoped read; one written with another pack's name is
  # resolved in a closure, which a pack read does not have, so it links nothing.
  Scenario: A step serves its substeps in authored order, each bare candidate linked to its read in the pack
    Given the org pack "sdbase" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDB01
      pack_version: 1.0.0
      parent_pack: null
      name: sdbase
      pack_level: org
      """
    And the org pack "sdbase" with the file "tactics/tdd.tactic.yaml":
      """
      schema_version: "1.0"
      id: tdd
      name: Test-Driven Development
      purpose: Write the failing test first.
      steps:
        - title: Write the test
          description: Write a test that fails.
      """
    And the org pack "sdext" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDE01
      pack_version: 1.0.0
      parent_pack: null
      name: sdext
      pack_level: org
      depends_on:
      - pack_id: 01M7B19K0000000000000SDB01
        range: '>=1.0'
      """
    And the org pack "sdext" with the file "tactics/own.tactic.yaml":
      """
      schema_version: "1.0"
      id: own
      name: Own
      purpose: The pack's own tactic.
      steps:
        - title: Do it
          description: Do it.
      """
    And the org pack "sdext" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates.
      steps:
      - id: a
        title: A
        substeps:
        - id: zeta
          description: Load the context
          command: spec-kitty charter context --action a --json
          inputs:
          - flag: --mission
            source: mission_slug
            optional: true
        - id: alpha
          description: Apply a tactic
          delegates_to:
            kind: tactic
            candidates:
            - own
            - sdbase::tdd
          guidance: Prefer the smallest change.
      """
    And the org pack "sdext" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: action:rx/a
        target: tactic:own
        relation: scope
      - source: action:rx/a
        target: sdbase::tactic:tdd
        relation: scope
      """
    When I GET "/packs/sdext/artifacts/mission_type/rx/steps/a"
    Then the response status is 200
    And the field at "/substeps" holds exactly 2 items
    And the field at "/substeps/0/id" is "zeta"
    And the field at "/substeps/0/description" is "Load the context"
    And the field at "/substeps/0/command" is "spec-kitty charter context --action a --json"
    And the field at "/substeps/0/inputs/0/flag" is "--mission"
    And the field at "/substeps/0/inputs/0/source" is "mission_slug"
    And the field at "/substeps/0/inputs/0/optional" is true
    And the field at "/substeps/0/delegatesTo" is absent
    And the field at "/substeps/1/id" is "alpha"
    And the field at "/substeps/1/description" is "Apply a tactic"
    And the field at "/substeps/1/inputs" holds exactly 0 items
    And the field at "/substeps/1/guidance" is "Prefer the smallest change."
    And the field at "/substeps/1/delegatesTo/kind" is "tactic"
    And the field at "/substeps/1/delegatesTo/candidates/0/id" is "own"
    And the field at "/substeps/1/delegatesTo/candidates/0/_links/artifact/href" is "/api/v1/packs/sdext/artifacts/tactic/own"
    And the field at "/substeps/1/delegatesTo/candidates/1/id" is "sdbase::tdd"
    And the field at "/substeps/1/delegatesTo/candidates/1/_links/artifact" is absent
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/plan"
    Then the response status is 200
    And the field at "/substeps" holds exactly 0 items

  # FR-005: every candidate of a substep resolves to an artifact of its kind in the pack, and the step's action has a
  # scope edge to it. Each is refused where it enters, at registration, at the mission type file; the twin with the
  # tactic and its edge registers.
  Scenario Outline: A whole-pack registration whose substep candidate is <fault> is refused with <code>
    Given registration is switched on
    And the archive "sdp" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDP01
      pack_version: 1.0.0
      parent_pack: null
      name: sdp
      pack_level: org
      """
    And the archive "sdp" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates.
      steps:
      - id: a
        title: A
        substeps:
        - id: run
          description: Run the step test first
          delegates_to:
            kind: tactic
            candidates:
            - tdd
      """
    And the archive "sdp" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      """
    And the archive "sdp" holds the file "<tactic>":
      """
      schema_version: "1.0"
      id: <tactic id>
      name: A tactic
      purpose: A purpose.
      steps:
        - title: Do it
          description: Do it.
      """
    When I PUT "/packs/sdp" with the archive "sdp"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "<code>"
    And the field at "/problem/violations/0/pointer" is "missions/rx/rx.mission-type.yaml#/steps/0/substeps/0/delegates_to/candidates/0"
    And the field at "/problem/violations/0/detail" contains "the substep run of the step a of mission_type:rx delegates to tactic tdd"
    Given the archive "fixed" is a copy of the archive "sdp"
    And the archive "fixed" holds the file "tactics/tdd.tactic.yaml":
      """
      schema_version: "1.0"
      id: tdd
      name: Test-Driven Development
      purpose: Write the failing test first.
      steps:
        - title: Write the test
          description: Write a test that fails.
      """
    And the archive "fixed" holds the file "action.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: action:rx/a
        target: tactic:tdd
        relation: scope
      """
    When I PUT "/packs/sdp" with the archive "fixed"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples:
      | fault                         | code                          | tactic                    | tactic id |
      | not held by the pack          | substep_delegation_unresolved | tactics/other.tactic.yaml | other     |
      | held but not scoped by action | substep_delegation_unscoped   | tactics/tdd.tactic.yaml   | tdd       |

  # FR-005 at compose: a candidate written with another pack's name is resolved in the writer's closure. The named pack
  # does not hold it, so every read of a closure that holds the writer refuses, by the candidate's own code and before
  # the reference of its scope edge is judged; the twin, the named pack holding it, is read.
  Scenario: A substep candidate another pack of the closure does not hold makes the closure unreadable, beside the one it holds
    Given the org pack "sdcbase" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDC01
      pack_version: 1.0.0
      parent_pack: null
      name: sdcbase
      pack_level: org
      """
    And the org pack "sdcbase" with the file "tactics/other.tactic.yaml":
      """
      schema_version: "1.0"
      id: other
      name: Other
      purpose: Another tactic.
      steps:
        - title: Do it
          description: Do it.
      """
    And the org pack "sdcext" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDC02
      pack_version: 1.0.0
      parent_pack: null
      name: sdcext
      pack_level: org
      depends_on:
      - pack_id: 01M7B19K0000000000000SDC01
        range: '>=1.0'
      """
    And the org pack "sdcext" with the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates to another pack.
      steps:
      - id: a
        title: A
        substeps:
        - id: run
          description: Run the step test first
          delegates_to:
            kind: tactic
            candidates:
            - sdcbase::tdd
      """
    And the org pack "sdcext" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: action:rx/a
        target: sdcbase::tactic:tdd
        relation: scope
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the field at "/detail" contains "sdcbase::tactic:tdd"
    And the field at "/detail" contains "substep_delegation_unresolved"
    And the field at "/violations/0/code" is "substep_delegation_unresolved"
    And the field at "/violations/0/pointer" is "missions/rx/rx.mission-type.yaml"
    Given the org pack "sdcbase" with the file "tactics/tdd.tactic.yaml":
      """
      schema_version: "1.0"
      id: tdd
      name: Test-Driven Development
      purpose: Write the failing test first.
      steps:
        - title: Write the test
          description: Write a test that fails.
      """
    When I GET "/offering"
    Then the response status is 200

  # FR-005 at registration: the registration composes the closure the pack joins as the reads do. A substep candidate
  # written with the name of a pack of its depends_on closure that does not hold it is the author's fault, refused as
  # an in-pack one is: 422 validation_failed with one substep_delegation_unresolved at the mission type file. The twin
  # archive differs only in the candidate and its scope edge, which name the tactic the base holds.
  Scenario: A registration whose substep candidate the named base pack does not hold is refused, and its twin is accepted
    Given registration is switched on
    And the org pack "sdqbase" with the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDQ01
      pack_version: 1.0.0
      parent_pack: null
      name: sdqbase
      pack_level: org
      """
    And the org pack "sdqbase" with the file "tactics/other.tactic.yaml":
      """
      schema_version: "1.0"
      id: other
      name: Other
      purpose: Another tactic.
      steps:
        - title: Do it
          description: Do it.
      """
    And the archive "sdq" holds the file "pack.yaml":
      """
      pack_id: 01M7B19K0000000000000SDQ02
      pack_version: 1.0.0
      parent_pack: null
      name: sdq
      pack_level: org
      depends_on:
      - pack_id: 01M7B19K0000000000000SDQ01
        range: '>=1.0'
      """
    And the archive "sdq" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates to its base.
      steps:
      - id: a
        title: A
        substeps:
        - id: run
          description: Run the step test first
          delegates_to:
            kind: tactic
            candidates:
            - sdqbase::tdd
      """
    And the archive "sdq" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: action:rx/a
        target: sdqbase::tactic:tdd
        relation: scope
      """
    And the archive "sdq-ok" is a copy of the archive "sdq"
    And the archive "sdq-ok" holds the file "missions/rx/rx.mission-type.yaml":
      """
      schema_version: '1.0'
      id: rx
      title: Rx
      description: A mission type whose step delegates to its base.
      steps:
      - id: a
        title: A
        substeps:
        - id: run
          description: Run the step test first
          delegates_to:
            kind: tactic
            candidates:
            - sdqbase::other
      """
    And the archive "sdq-ok" holds the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: mission_type:rx
        target: action:rx/a
        relation: requires
      - source: action:rx/a
        target: sdqbase::tactic:other
        relation: scope
      """
    When I PUT "/packs/sdq" with the archive "sdq"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/detail" contains "sdqbase::tactic:tdd"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "substep_delegation_unresolved"
    And the field at "/problem/violations/0/pointer" is "missions/rx/rx.mission-type.yaml"
    When I GET "/packs/sdq"
    Then the response status is 404
    When I PUT "/packs/sdq" with the archive "sdq-ok"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  Scenario: A step's model tier is served on the pack step read, and a step without one omits it (ADR-0080, #108)
    On charter's example packs: built-in's software-dev review step declares model_tier low, its on-demand charter
    step model_tier high, and its plan step none. The pack step read answers the tier as modelTier, and omits the key
    when the step declares none.
    Given the example pack "built-in" of the charter
    When I GET "/packs/built-in/artifacts/mission_type/software-dev/steps/review"
    Then the response status is 200
    And the field at "/id" is "review"
    And the field at "/modelTier" is "low"
    When I GET "/packs/built-in/artifacts/mission_type/software-dev/steps/charter"
    Then the response status is 200
    And the field at "/modelTier" is "high"
    When I GET "/packs/built-in/artifacts/mission_type/software-dev/steps/plan"
    Then the response status is 200
    And the field at "/modelTier" is absent
