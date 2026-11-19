@contract @activations
Feature: An activation is discoverable by links
  A client that knows only the root reaches an activation's reads and writes by links (#409, decision
  DM-01M78RKM6Q23PA9QAC80QJKQFF, FR-013 and FR-014). The root's activations link is followed with POST to create one;
  the activation answers self, artifacts, offering, packs, graph, skipped, tensions, the templated effective-context, its held
  assets, skills and glossary contexts (assets, the templated asset, skills, glossaries), its vocabulary
  (vocabulary-terms, vocabulary-contextive, vocabulary-taboos), and its writes with their methods: edit (PUT) and delete
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
      - source: mission_type:software-dev
        target: action:software-dev/implement
        relation: requires
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
      | relation              |
      | self                  |
      | offering              |
      | packs                 |
      | graph                 |
      | skipped               |
      | tensions              |
      | assets                |
      | skills                |
      | glossaries            |
      | vocabulary-terms      |
      | vocabulary-contextive |
      | vocabulary-taboos     |

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

  # ADR-0045 decision 11 (#602): the tensions read has a tag of its own, never the skipped report's, and an activation
  # nothing is stored under is 404 on it.
  Scenario: The tensions read of an activation answers under its own ETag, and an unknown activation is 404
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "main"}]}
      """
    When I GET "/activations/a-1/tensions"
    Then the response status is 200
    And the field at "/tensions" holds exactly 0 items
    And the field at "/findings" holds exactly 0 items
    And the field at "/_links/self/href" is "/api/v1/activations/a-1/tensions"
    When I remember the header "ETag"
    And I GET "/activations/a-1/skipped"
    Then the response status is 200
    And the response header "ETag" differs from the remembered header
    When I GET "/activations/01ARWG13C000000000000000ZZ/tensions"
    Then the response status is 404
    And the problem code is "activation_not_found"
