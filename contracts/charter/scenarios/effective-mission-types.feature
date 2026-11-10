# WP05 of mission-type-reads: the mission type, step and skeleton reads under an activation, getEffectiveMissionType,
# getEffectiveMissionStep and getEffectiveMissionSkeleton (FR-007, SC-001, US4). Written red first: the scenarios were
# committed with every step bound and run against the service as it stood, where
# /activations/a-1/artifacts/mission_type/software-dev was answered by the generic read of an artifact (no body) and the
# step and skeleton paths by the router's 404; the operations were then built in :missions-edge.
#
# The reads answer the winner of the layer merge across the levels of the activation's closure, as the generic read
# getEffectiveArtifact picks it: the mission type as the pack that holds the winner stores it, that pack's skeletons and
# step files, and the step contracts of the merged closure whose mission is the type. Every link stays under the
# activation, but the artifact link, which is the pack-scoped read of the winner, as on getEffectiveArtifact and
# getEffectiveAgentProfile. The vocabulary is the existing one (README.md); nothing is added.
#
# Fixture: main (org) holds software-dev, its step files, its skeletons spec-template.md and old-template.md, the step
# contract specify, and documentation. proj (project) holds its own software-dev, with its own step files, its own
# spec-template.md and the step contract implement. elsewhere (org) holds research and is not in the activation's
# closure. The activation selects software-dev and leaves documentation out.

@contract @activations @mission-types
Feature: A mission type, its steps and its skeletons are read under an activation, as the layer merge keeps them
  getEffectiveMissionType answers the winner of a mission type under an activation in the Artifact envelope, with the
  pack that holds it as packId and that pack's skeletons; getEffectiveMissionStep answers a step of the winner with the
  holding pack's prompt and guidelines and a link to the step contract the merged closure binds to it;
  getEffectiveMissionSkeleton answers a skeleton of the holding pack. Every link stays under the activation, and the
  artifact link is the pack-scoped read of the winner. Refusals come in order: the activation, then the mission type,
  then the step or the skeleton.

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
      title: Software Development at org
      description: The org's software development
      steps:
      - id: specify
        title: Specification
        prompt_path: steps/specify.prompt.md
        guidelines_path: steps/specify.guidelines.md
        expected_output_artifacts:
        - artifact_key: input.spec.main
          artifact_class: input
          path_pattern: spec.md
          blocking: true
          template_path: templates/spec-template.md
      """
    And the org pack "main" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at org
      """
    And the org pack "main" with the file "missions/software-dev/steps/specify.guidelines.md":
      """
      # Specify guidelines at org
      """
    And the org pack "main" with the file "missions/software-dev/templates/spec-template.md":
      """
      # Spec skeleton at org
      """
    And the org pack "main" with the file "missions/software-dev/templates/old-template.md":
      """
      # A skeleton only the org holds
      """
    And the org pack "main" with the file "missions/documentation/documentation.mission-type.yaml":
      """
      schema_version: '1.0'
      id: documentation
      title: Documentation
      description: Write documentation
      steps:
      - id: discover
        title: Discover
      """
    And the org pack "main" with the file "mission_step_contracts/specify.step-contract.yaml":
      """
      schema_version: '1.0'
      id: specify
      action: specify
      mission: software-dev
      steps:
        - id: bootstrap
          description: Load charter context for this action
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: proj-pack
      pack_level: project
      """
    And the project pack "proj" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: '1.0'
      id: software-dev
      title: Software Development at project
      description: The project's software development
      steps:
      - id: specify
        title: Specification at project
        prompt_path: steps/specify.prompt.md
        expected_output_artifacts:
        - artifact_key: input.spec.main
          artifact_class: input
          path_pattern: spec.md
          blocking: true
          template_path: templates/spec-template.md
      - id: implement
        title: Implementation
        role: implementer
      """
    And the project pack "proj" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at project
      """
    And the project pack "proj" with the file "missions/software-dev/templates/spec-template.md":
      """
      # Spec skeleton at project
      """
    And the project pack "proj" with the file "mission_step_contracts/implement.step-contract.yaml":
      """
      schema_version: '1.0'
      id: implement
      action: implement
      mission: software-dev
      steps:
        - id: build
          description: Build it
      """
    And the org pack "elsewhere" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: elsewhere-pack
      pack_level: org
      """
    And the org pack "elsewhere" with the file "missions/research/research.mission-type.yaml":
      """
      schema_version: '1.0'
      id: research
      title: Research
      description: Research a question
      steps:
      - id: gather
        title: Gather
      """
    And the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"]}}
      """

  Scenario: The mission type the project overrides is answered from the project pack, with its links under the activation
    FR-007, US4 AS1, SC-001. proj replaces main's software-dev at the project level, so the answer is proj's: its title,
    its steps and its one skeleton; main's old-template.md, a skeleton of the copy that lost, is not listed. Every step
    and skeleton link is under the activation and answers 200; the artifact link reads the winner in proj. The pack read
    of main's copy is the control that the losing copy is there and differs.
    When I GET "/packs/main/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the field at "/missionType/title" is "Software Development at org"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the response body is:
      """
      {
        "kind": "mission_type",
        "id": "software-dev",
        "packId": "proj",
        "missionType": {
          "id": "software-dev",
          "schemaVersion": "1.0",
          "title": "Software Development at project",
          "description": "The project's software development",
          "requiresSubstantiveSpec": false,
          "expectedOutputArtifacts": [],
          "steps": [
            {"id": "specify", "title": "Specification at project", "onDemand": false, "_links": {"self": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"}}},
            {"id": "implement", "title": "Implementation", "onDemand": false, "_links": {"self": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement"}}}
          ],
          "skeletons": [
            {"name": "spec-template.md", "path": "templates/spec-template.md", "_links": {"self": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/spec-template.md"}}}
          ]
        },
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev"},
          "collection": {"href": "/api/v1/activations/a-1/artifacts?kind=mission_type"},
          "artifact": {"href": "/api/v1/packs/proj/artifacts/mission_type/software-dev"}
        }
      }
      """
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/missionType/title" is "Software Development at project"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev"
    And I follow the link "collection"
    Then the response status is 200
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/implement"
    Then the response status is 200
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200

  Scenario: A step of the winner is served with the holding pack's files and the step contract the merged closure binds
    US4 AS2. The specify step is proj's: its title and its prompt; proj stores no guidelines for it, so main's guidelines,
    a file of the copy that lost, are not answered. The step contract bound to specify is main's: the merged closure
    holds it, although the winning type is proj's. The links stay under the activation, and the step contract link is
    the activation's read of the step contract, which answers 200.
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the field at "/id" is "specify"
    And the field at "/title" is "Specification at project"
    And the field at "/prompt" is the lines:
      """
      # Specify at project
      """
    And the field at "/guidelines" is absent
    And the field at "/expectedOutputArtifacts/0/_links/skeleton/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    And the field at "/_links/mission-type/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/_links/step-contract/href" is "/api/v1/activations/a-1/artifacts/mission_step_contract/specify"
    When I follow the link "step-contract"
    Then the response status is 200
    And the field at "/kind" is "mission_step_contract"
    And the field at "/artifactId" is "specify"
    And the field at "/packId" is "main"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/implement"
    Then the response status is 200
    And the field at "/role" is "implementer"
    And the field at "/_links/step-contract/href" is "/api/v1/activations/a-1/artifacts/mission_step_contract/implement"
    When I follow the link "step-contract"
    Then the response status is 200
    And the field at "/packId" is "proj"

  Scenario: A skeleton is the holding pack's, linked to the mission type under the activation
    US4 AS3. spec-template.md is answered from proj, which holds the winner; the same name read from main is main's.
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response has no "ETag" header
    And the field at "/name" is "spec-template.md"
    And the field at "/path" is "templates/spec-template.md"
    And the field at "/missionTypeId" is "software-dev"
    And the field at "/encoding" is "text"
    And the field at "/content" is the lines:
      """
      # Spec skeleton at project
      """
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    And the field at "/_links/mission-type/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    When I follow the link "mission-type"
    Then the response status is 200
    And the field at "/packId" is "proj"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 200
    And the field at "/content" is the lines:
      """
      # Spec skeleton at org
      """

  Scenario: A mission type the closure holds but the activation does not select is still answered
    As getEffectiveArtifact does, the read answers what the merged closure holds, whatever its state: documentation is
    held by main alone and left out of the selection. The effective list says it is inactive.
    When I GET "/activations/a-1/artifacts/mission_type/documentation"
    Then the response status is 200
    And the field at "/packId" is "main"
    And the field at "/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_type/documentation"
    When I GET "/activations/a-1/artifacts?kind=mission_type&state=inactive"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_type:documentation
      """

  Scenario: The step contracts are read under the activation by the generic effective read, and an unreached one is not found
    The effective-artifact link of an effective list item (and the step-contract link of a step) names
    /activations/{id}/artifacts/mission_step_contract/{id}, which the generic read answers with the winner and a link to
    the pack that holds it. A step contract no pack of the closure holds is a 404 artifact_not_found, as for any kind
    of the generic read; the pack read of the same id is the control that the name exists nowhere else.
    When I GET "/activations/a-1/artifacts/mission_step_contract/specify"
    Then the response status is 200
    And the field at "/kind" is "mission_step_contract"
    And the field at "/packId" is "main"
    And the field at "/level" is "org"
    And the field at "/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_step_contract/specify"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/missionStepContract/mission" is "software-dev"
    When I GET "/activations/a-1/artifacts/mission_step_contract/implement"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/level" is "project"
    When I GET "/activations/a-1/artifacts/mission_step_contract/gather"
    Then the response status is 404
    And the problem code is "artifact_not_found"

  Scenario: The refusals come in order: the activation, then the mission type, then the step or the skeleton
    FR-009 under an activation. An unknown activation is activation_not_found whatever the rest of the path names. A
    mission type that no pack of the activation's closure holds is mission_type_not_found, also under a step or a
    skeleton: research is held by elsewhere, which the activation does not build on, and the pack read of it is the
    control. An unknown step of the winner is action_not_found; a name that is no skeleton of the holding pack is
    skeleton_not_found, and so is old-template.md, a skeleton of the copy that lost.
    When I GET "/activations/nope/artifacts/mission_type/software-dev"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/nope/artifacts/mission_type/software-dev/steps/specify"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/activations/nope/artifacts/mission_type/software-dev/skeletons/spec-template.md"
    Then the response status is 404
    And the problem code is "activation_not_found"
    When I GET "/packs/elsewhere/artifacts/mission_type/research"
    Then the response status is 200
    When I GET "/activations/a-1/artifacts/mission_type/research"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1/artifacts/mission_type/research/steps/gather"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1/artifacts/mission_type/research/skeletons/spec-template.md"
    Then the response status is 404
    And the problem code is "mission_type_not_found"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/nope"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/skeletons/nope.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/skeletons/old-template.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
