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
#
# The pre-PR fold (D11) adds getEffectiveMissionStepContract, the literal read of a step contract under an activation:
# the scenario that pinned the generic getEffectiveArtifact's body-less answer at that URL (WP05) now pins the literal
# answer, and a step's step-contract link followed answers the envelope (its /id, where the generic read had
# /artifactId). Written red first: run against the service before the operation was built, the two D11 scenarios and
# the step scenario failed.
#
# WP06 (FR-008, US5) adds the links of the effective reads to these reads: an effective list item of kind mission_type
# or mission_step_contract links effective-artifact and artifact, an action node of the graph links effective-artifact
# to its step's read, and the action context links its step's read under the activation and in the pack that holds the
# winning mission type. Written red first: run before the mapper changed, the five scenarios that pin these links (the
# three below, and one each in effective-reads.feature and effective-graph-and-context.feature) failed on their first
# link assertion.
#
# WP09 of effective-step-and-action-context (A7, DIV-243, research R8; operator ruling of 2026-11-12) makes the step read
# agree with the action context and the effective prompt for a step that only a version the winner replaced keeps: it is
# read from the step's place, the merged winner of action:<type>/<step>, and its skeleton links name that pack's reads,
# which serve its version's skeletons. Written red first: run before the read changed, the scenario failed on the step
# read's 404 action_not_found.

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
    And the field at "/id" is "specify"
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

  Scenario: D11 - a step contract is read under the activation as the layer merge keeps it, every link under the activation
    D11, US4 AS2. getEffectiveMissionStepContract answers the winner of the step contract in the Artifact envelope, as
    the pack read does, with the pack that holds it as packId. The literal path wins over the generic getEffectiveArtifact,
    which answered this URL before D11 with the winner and no body (this scenario pinned that answer in WP05). specify is
    main's, while the winning software-dev is proj's: its mission-type and step links stay under the activation and
    answer proj's type and step, never the copy that lost. A delegation candidate of a listed kind the closure holds links
    effective-artifact, its read under the activation: GUARD is proj's, so main's own pack read links nothing for it; NOPE
    is held nowhere, and neither is the procedure. artifact is the pack read of the winner.
    Given the project pack "proj" with the file "directives/guard.directive.yaml":
      """
      schema_version: '1.0'
      id: GUARD
      title: Guard the gate
      intent: Guard it.
      enforcement: required
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
        - id: capture_intent
          description: Capture the intent
          delegates_to:
            kind: directive
            candidates:
              - GUARD
              - NOPE
        - id: map_examples
          description: Capture concrete rules and examples
          delegates_to:
            kind: procedure
            candidates:
              - example-mapping-workshop
      """
    When I GET "/activations/a-1/artifacts/mission_step_contract/specify"
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
            {"id": "bootstrap", "description": "Load charter context for this action", "inputs": []},
            {
              "id": "capture_intent",
              "description": "Capture the intent",
              "inputs": [],
              "delegatesTo": {
                "kind": "directive",
                "candidates": [
                  {"id": "GUARD", "_links": {"effective-artifact": {"href": "/api/v1/activations/a-1/artifacts/directive/GUARD"}}},
                  {"id": "NOPE", "_links": {}}
                ]
              }
            },
            {
              "id": "map_examples",
              "description": "Capture concrete rules and examples",
              "inputs": [],
              "delegatesTo": {"kind": "procedure", "candidates": [{"id": "example-mapping-workshop", "_links": {}}]}
            }
          ],
          "gates": []
        },
        "_links": {
          "self": {"href": "/api/v1/activations/a-1/artifacts/mission_step_contract/specify"},
          "collection": {"href": "/api/v1/activations/a-1/artifacts?kind=mission_step_contract"},
          "mission-type": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev"},
          "step": {"href": "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"},
          "artifact": {"href": "/api/v1/packs/main/artifacts/mission_step_contract/specify"}
        }
      }
      """
    When I follow the link "mission-type"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/missionType/title" is "Software Development at project"
    When I GET "/activations/a-1/artifacts/mission_step_contract/specify"
    And I follow the link "step"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    And the field at "/_links/step-contract/href" is "/api/v1/activations/a-1/artifacts/mission_step_contract/specify"
    When I GET "/activations/a-1/artifacts/mission_step_contract/specify"
    And I follow the link "collection"
    Then the response status is 200
    When I GET "/activations/a-1/artifacts/directive/GUARD"
    Then the response status is 200
    And the field at "/packId" is "proj"
    When I GET "/activations/a-1/artifacts/mission_step_contract/specify"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/packId" is "main"
    And the field at "/missionStepContract/steps/1/delegatesTo/candidates/0/_links/artifact" is absent
    When I GET "/activations/a-1/artifacts/mission_step_contract/implement"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/_links/step/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_step_contract/implement"

  Scenario: D11 - a step contract links its mission type when the closure holds it, and its step when the step read answers it
    review is bound to software-dev, which the closure holds, but neither copy of software-dev declares review and no
    version of the closure holds the step, so the step read answers 404 for it: it links mission-type and no step (WP09:
    the step link is offered whenever the step read answers the step, from the winner or from the step's place). stray is bound to research, which no pack of the closure holds: it links neither. A step
    contract no pack of the closure holds is mission_step_contract_not_found, after an unknown activation, which is
    activation_not_found whatever the rest of the path names.
    Given the org pack "main" with the file "mission_step_contracts/review.step-contract.yaml":
      """
      schema_version: '1.0'
      id: review
      action: review
      mission: software-dev
      steps:
        - id: read
          description: Read the change
      """
    And the org pack "main" with the file "mission_step_contracts/stray.step-contract.yaml":
      """
      schema_version: '1.0'
      id: stray
      action: gather
      mission: research
      steps:
        - id: look
          description: Look around
      """
    When I GET "/activations/a-1/artifacts/mission_step_contract/review"
    Then the response status is 200
    And the field at "/_links/mission-type/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/_links/step" is absent
    When I GET "/activations/a-1/artifacts/mission_step_contract/stray"
    Then the response status is 200
    And the field at "/_links/mission-type" is absent
    And the field at "/_links/step" is absent
    When I GET "/activations/a-1/artifacts/mission_step_contract/gather"
    Then the response status is 404
    And the problem code is "mission_step_contract_not_found"
    When I GET "/activations/nope/artifacts/mission_step_contract/specify"
    Then the response status is 404
    And the problem code is "activation_not_found"

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

  Scenario: WP06 - an effective list item of a mission kind links its read under the activation and the winner's pack read
    FR-008, US5 AS1. The effective list's items of kind mission_type and mission_step_contract carry effective-artifact,
    the read under the activation (getEffectiveMissionType for a mission type, getEffectiveArtifact for a step
    contract), and artifact, the pack-scoped read of the pack that holds the version in force: proj for software-dev
    and implement, main for documentation and specify. Every link followed answers 200.
    When I GET "/activations/a-1/artifacts?kind=mission_type"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_type:documentation
      mission_type:software-dev
      """
    And the field at "/items/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/documentation"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_type/documentation"
    And the field at "/items/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/items/1/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_type/software-dev"
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts?kind=mission_type"
    Then following the link "artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract"
    Then the response status is 200
    And the field at "/items" holds exactly the ids:
      """
      mission_step_contract:implement
      mission_step_contract:specify
      """
    And the field at "/items/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_step_contract/implement"
    And the field at "/items/0/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_step_contract/implement"
    And the field at "/items/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_step_contract/specify"
    And the field at "/items/1/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_step_contract/specify"
    And following the link "effective-artifact" of every element of "items" answers 200
    When I GET "/activations/a-1/artifacts?kind=mission_step_contract"
    Then following the link "artifact" of every element of "items" answers 200

  Scenario: WP06 - an action node of the graph links the step's read under the activation
    FR-008, US5 AS1. proj's graph declares software-dev's two steps, so the closure holds them, and the graph lists the
    mission type in force and its steps: each action node action:<type>/<step>
    links effective-artifact, the step's read under the activation (getEffectiveMissionStep), and the mission type node
    its own read. A node names no pack, so neither has an artifact link. Every node's link followed answers 200, and the
    specify step's is the winner's, proj's.
    Given the project pack "proj" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges: []
      """
    When I GET "/activations/a-1/graph"
    Then the response status is 200
    And the graph holds exactly the nodes:
      """
      action:software-dev/implement
      action:software-dev/specify
      mission_type:software-dev
      """
    And the field at "/nodes/0/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/implement"
    And the field at "/nodes/1/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    And the field at "/nodes/1/_links/artifact" is absent
    And the field at "/nodes/2/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/nodes/2/_links/artifact" is absent
    And following the link "effective-artifact" of every element of "nodes" answers 200
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    Then the field at "/title" is "Specification at project"

  Scenario: WP06 - the action context links its step's read under the activation and in the pack that holds the winner
    FR-008, US5 AS1. proj's graph declares the step specify. The context of software-dev's specify links
    effective-artifact, the step's read under the activation, and artifact, the step's read in proj, the pack that holds
    the winning mission type. Both answer proj's step.
    Given the project pack "proj" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges: []
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/specify/context"
    Then the response status is 200
    And the field at "/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_type/software-dev/steps/specify"
    When I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    When I GET "/activations/a-1/mission-types/software-dev/actions/specify/context"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    And the field at "/_links/self/href" is "/api/v1/packs/proj/artifacts/mission_type/software-dev/steps/specify"

  Scenario: WP09, A7 - a step only the replaced version keeps is read from its place, as the context and the prompt read it
    FR-003, research R8, DIV-243. main's software-dev declares review, and its graph declares the step, so main holds
    action:software-dev/review; proj's software-dev, which wins the layer merge, drops it. A winning mission type keeps
    the unique steps of the versions it replaced (A7): the step's place is the merged winner of the action, main. The
    step read answers main's step with main's prompt, as the prompt read does, and its skeleton link names main, whose
    version stores the skeleton (the activation's skeleton read serves proj's and answers skeleton_not_found for it).
    The context's artifact link is the step's read in main, and main's step contract review links the step, as the step
    read answers it. Every link followed answers 200. The twin, deploy, differs only in the step id: no version declares
    it, and the three reads answer 404 action_not_found.
    Given the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
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
      - id: review
        title: Review at org
        role: reviewer
        prompt_path: steps/review.prompt.md
        expected_output_artifacts:
        - artifact_key: input.review.notes
          artifact_class: input
          path_pattern: review.md
          blocking: true
          template_path: templates/old-template.md
      """
    And the org pack "main" with the file "missions/software-dev/steps/review.prompt.md":
      """
      # Review at org
      """
    And the org pack "main" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/review
        kind: action
        label: review
      edges: []
      """
    And the org pack "main" with the file "mission_step_contracts/review.step-contract.yaml":
      """
      schema_version: '1.0'
      id: review
      action: review
      mission: software-dev
      steps:
        - id: read
          description: Read the change
      """
    When I GET "/activations/a-1/artifacts/mission_type/software-dev"
    Then the response status is 200
    And the field at "/packId" is "proj"
    And the field at "/missionType/steps/1/id" is "implement"
    And the field at "/missionType/steps/2" is absent
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/review"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/id" is "review"
    And the field at "/title" is "Review at org"
    And the field at "/role" is "reviewer"
    And the field at "/prompt" is the lines:
      """
      # Review at org
      """
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"
    And the field at "/_links/mission-type/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev"
    And the field at "/expectedOutputArtifacts/0/_links/skeleton/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev/skeletons/old-template.md"
    When I follow the link "self"
    Then the response status is 200
    And the field at "/title" is "Review at org"
    When I GET "/packs/main/artifacts/mission_type/software-dev/skeletons/old-template.md"
    Then the response status is 200
    And the field at "/content" is the lines:
      """
      # A skeleton only the org holds
      """
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/skeletons/old-template.md"
    Then the response status is 404
    And the problem code is "skeleton_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/review/prompt"
    Then the response status is 200
    And the field at "/promptPackId" is "main"
    And the field at "/prompt" is the lines:
      """
      # Review at org
      """
    And the field at "/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"
    When I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Review at org"
    When I GET "/activations/a-1/mission-types/software-dev/actions/review/context"
    Then the response status is 200
    And the field at "/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"
    And the field at "/_links/artifact/href" is "/api/v1/packs/main/artifacts/mission_type/software-dev/steps/review"
    When I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Review at org"
    When I GET "/activations/a-1/mission-types/software-dev/actions/review/context"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/title" is "Review at org"
    When I GET "/activations/a-1/artifacts/mission_step_contract/review"
    Then the response status is 200
    And the field at "/_links/step/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/review"
    When I follow the link "step"
    Then the response status is 200
    And the field at "/title" is "Review at org"
    When I GET "/activations/a-1/artifacts/mission_type/software-dev/steps/deploy"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/deploy/context"
    Then the response status is 404
    And the problem code is "action_not_found"
    When I GET "/activations/a-1/mission-types/software-dev/actions/deploy/prompt"
    Then the response status is 404
    And the problem code is "action_not_found"

  Scenario: WP09 review H1, Q3 - a step the winning type declares is placed at the winner, whichever pack's graph declares it
    The step's place is the winner of the mission type when its version declares the step, else the merged winner of the
    action (A7). proj's winning software-dev declares specify; only main's graph declares action:software-dev/specify.
    The context's effective-artifact and artifact links answer the same step, proj's, and the step read answers it. The
    prompt read follows the same place (operator ruling Q3, 2026-11-13): it answers proj's prompt, and its
    effective-artifact link answers the same step.
    Given the org pack "main" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges: []
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/specify/context"
    Then the response status is 200
    And the field at "/_links/effective-artifact/href" is "/api/v1/activations/a-1/artifacts/mission_type/software-dev/steps/specify"
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_type/software-dev/steps/specify"
    When I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    When I GET "/activations/a-1/mission-types/software-dev/actions/specify/context"
    And I follow the link "artifact"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    When I GET "/activations/a-1/mission-types/software-dev/actions/specify/prompt"
    Then the response status is 200
    And the field at "/promptPackId" is "proj"
    And the field at "/prompt" is the lines:
      """
      # Specify at project
      """
    When I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Specification at project"
    And the field at "/prompt" is the lines:
      """
      # Specify at project
      """

  Scenario: WP09 review H1 - the context's artifact link answers a step only the winning type declares, whose action another pack's graph declares
    main's graph declares action:software-dev/implement and main's software-dev does not declare implement; proj's
    winning software-dev does. The step's place is proj, the winner of the mission type, so both context links answer
    proj's step, never a 404 from main's copy.
    Given the org pack "main" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges: []
      """
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    Then the response status is 200
    And the field at "/_links/artifact/href" is "/api/v1/packs/proj/artifacts/mission_type/software-dev/steps/implement"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/title" is "Implementation"
    When I GET "/activations/a-1/mission-types/software-dev/actions/implement/context"
    And I follow the link "effective-artifact"
    Then the response status is 200
    And the field at "/title" is "Implementation"
    When I GET "/packs/main/artifacts/mission_type/software-dev/steps/implement"
    Then the response status is 404
    And the problem code is "action_not_found"

  Scenario: WP09 Q3 - the slot check judges the prompt at the step's place, the one the prompt read serves
    Operator ruling Q3: every judgement of the prompt in force places it by the one rule, the slot check (C38) too.
    proj's winning software-dev declares specify, and only main's graph declares action:software-dev/specify, so the
    prompt in force is proj's, which declares no spdd:reasons-block; main's, which declares it, is not in force. The
    enhancement reasons in force fills that slot: the prompt read of a stored activation that puts it in force answers
    500 prompt_slot_missing, and an activation write that puts it in force 422, naming proj, never judging main's
    prompt. The twin differs only in proj's prompt (and stores a-3 by its write, which this one refuses).
    Given the org pack "main" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at org

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->
      """
    And the org pack "main" with the file "missions/software-dev/enhancements/reasons.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: reasons
      prompt_fragments:
        - slot: spdd:reasons-block
          path: reasons.prompt-fragment.md
      """
    And the org pack "main" with the file "missions/software-dev/enhancements/reasons.prompt-fragment.md":
      """
      Fill the REASONS canvas.
      """
    And the org pack "main" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges:
      - source: step_enhancement:reasons
        target: action:software-dev/specify
        relation: enhances
      """
    And the project pack "proj" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at project
      """
    And the stored file of the activation "a-3" holds:
      """
      {"packs":[{"packId":"main"},{"packId":"proj"}],"schemaVersion":2,"selected":{"mission_type":["software-dev"],"step_enhancement":["reasons"]}}
      """
    When I GET "/activations/a-3/mission-types/software-dev/actions/specify/prompt"
    Then the response status is 500
    And the problem code is "prompt_slot_missing"
    And the field at "/detail" contains "step_enhancement:reasons"
    And the field at "/detail" contains "`proj-pack`"
    When I PUT "/activations/a-4" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["reasons"]}}
      """
    Then the response status is 422
    And the problem code is "prompt_slot_missing"
    When I GET "/activations/a-4"
    Then the response status is 404

  Scenario: WP09 Q3 - the twin: proj's prompt at the step's place declares the slot, so the read answers and the write is stored
    The twin of the scenario above, differing only in proj's prompt, which declares spdd:reasons-block: the prompt read
    answers proj's prompt with the fragment of reasons in the slot, and the activation write is stored.
    Given the org pack "main" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at org

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->
      """
    And the org pack "main" with the file "missions/software-dev/enhancements/reasons.step-enhancement.yaml":
      """
      schema_version: '1.0'
      id: reasons
      prompt_fragments:
        - slot: spdd:reasons-block
          path: reasons.prompt-fragment.md
      """
    And the org pack "main" with the file "missions/software-dev/enhancements/reasons.prompt-fragment.md":
      """
      Fill the REASONS canvas.
      """
    And the org pack "main" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/specify
        kind: action
        label: specify
      edges:
      - source: step_enhancement:reasons
        target: action:software-dev/specify
        relation: enhances
      """
    And the project pack "proj" with the file "missions/software-dev/steps/specify.prompt.md":
      """
      # Specify at project

      <!-- spdd:reasons-block:start -->
      <!-- spdd:reasons-block:end -->
      """
    And the activation "a-3" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["reasons"]}}
      """
    When I GET "/activations/a-3/mission-types/software-dev/actions/specify/prompt"
    Then the response status is 200
    And the field at "/promptPackId" is "proj"
    And the field at "/slots/0/slot" is "spdd:reasons-block"
    And the field at "/slots/0/fragments/0/enhancement" is "reasons"
    When I PUT "/activations/a-4" with the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}, {"packId": "proj"}], "selected": {"mission_type": ["software-dev"], "step_enhancement": ["reasons"]}}
      """
    Then the response status is 201
