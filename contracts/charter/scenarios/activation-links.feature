@contract @activations
Feature: An activation is discoverable by links
  A client that knows only the root reaches an activation's reads and writes by links (#409, decision
  DM-01M78RKM6Q23PA9QAC80QJKQFF, FR-013 and FR-014). The root's activations link is followed with POST to create one;
  the activation answers self, artifacts, offering, packs, graph, skipped, the templated effective-context, its held
  assets and skills (assets, the templated asset, skills), and its writes with their methods: edit (PUT) and delete
  (DELETE) on itself, and select, exclude and apply-preset (PUT), templated. unselect and include are not on the
  activation. Every href the scenarios use comes from a link: none is built by hand, not even by a guard runner that
  reads an asset (asset:<id>).

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
      intent: D
      enforcement: required
      """
    And the org pack "main" with the file "missions/software-dev/software-dev.mission-type.yaml":
      """
      schema_version: "1.0"
      id: software-dev
      title: software-dev
      description: software-dev
      steps:
        - id: implement
          title: implement
          role: implementer
      """
    And the org pack "main" with the file "assets/run.py.asset.yaml":
      """
      id: run
      mime: text/x-python
      path: run.py
      title: Run
      """
    And the org pack "main" with the file "assets/run.py":
      """
      print("run")
      """
    And the org pack "main" with the file "edges.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: directive:D
        relation: scope
      """

  Scenario: From the root, a client creates an activation and selects an artifact by links only
    When I GET "/"
    And I follow the link "activations" with its method and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    Then the response status is 201
    And the path of the Location header is the href of the link "self"
    And the field at "/_links/select/method" is "PUT"
    And the field at "/_links/select/templated" is true
    And the answer has no link "unselect"
    And the answer has no link "include"
    When I remember the header "ETag"
    And I expand the templated link "select" with:
      | kind       | directive |
      | artifactId | D         |
    And I follow the expanded link with If-Match the remembered header "ETag" and the body:
      """
      {}
      """
    Then the response status is 200
    And the field at "/applied" holds exactly the ids:
      """
      directive:D
      """
    When I follow the link "up"
    Then the response status is 200
    And the field at "/config/selected/directive" holds exactly the ids:
      """
      D
      """

  Scenario Outline: Every read an activation links answers it: <relation>
    When I GET "/"
    And I follow the link "activations" with its method and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    Then the response status is 201
    When I follow the link "<relation>"
    Then the response status is 200

    Examples:
      | relation |
      | self     |
      | offering |
      | packs    |
      | graph    |
      | skipped  |
      | assets   |
      | skills   |

  Scenario: The templated effective-context of an activation answers once expanded
    When I GET "/"
    And I follow the link "activations" with its method and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}], "selected": {"mission_type": ["software-dev"]}}
      """
    Then the response status is 201
    When I follow the templated link "effective-context" with:
      | missionType | software-dev |
      | action      | implement    |
      | depth       | 0            |
    Then the response status is 200
    And the field at "/missionType" is "software-dev"

  Scenario: A guard runner reads an asset under an activation by links only
    When I GET "/"
    And I follow the link "activations" with its method and the body:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    Then the response status is 201
    And the field at "/_links/asset/templated" is true
    When I follow the templated link "asset" with:
      | artifactId | run |
    Then the response status is 200
    And the field at "/kind" is "asset"
    And the field at "/artifactId" is "run"
    And the field at "/packId" is "main"
    When I follow the link "payload"
    Then the response status is 200
    And the response header "Content-Type" is "text/x-python"
    And the response body is the bytes of the file "assets/run.py" of the pack "main"
