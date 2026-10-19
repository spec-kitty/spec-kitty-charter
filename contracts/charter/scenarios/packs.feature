@contract
Feature: Pack listing and load refusals
  The packs of the offering in layer order, and the loud refusals of a pack that cannot load.

  Scenario: Packs are listed built-in first, then org, with the size of their graph
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      pack_name: qa
      nodes:
      - id: QA_001
        kind: directives
        title: QA Traceability Mandatory
      edges:
      - source: QA_001
        target: directive:DIRECTIVE_010
        relation: suggests
      """
    And the built-in pack "built-in" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000FG
      pack_version: 1.0.0
      parent_pack: null
      name: built-in
      pack_level: built-in
      """
    And the built-in pack "built-in" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: directive:DIRECTIVE_010
        kind: directive
        label: Specification Fidelity Requirement
      edges: []
      """
    When I GET "/packs"
    Then the response status is 200
    And the response body is:
      """
      {
        "packs": [
          {"name": "built-in", "level": "built-in", "packId": "01ARWG13C000000000000000FG", "packVersion": "1.0.0", "parentPack": null, "nodeCount": 1, "edgeCount": 0, "diagnostics": []},
          {"name": "qa", "level": "org", "packId": null, "packVersion": null, "parentPack": null, "nodeCount": 1, "edgeCount": 1, "diagnostics": [{"code": "pack_level_missing", "detail": "org pack qa has no pack.yaml; the configured level org is used"}]}
        ]
      }
      """

  Scenario: A pack descriptor without pack_level loads at its configured level, with a diagnostic
    Given the org pack "internal" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: spec-kitty-internal
      """
    When I GET "/packs"
    Then the response status is 200
    And the response body is:
      """
      {
        "packs": [
          {"name": "internal", "level": "org", "packId": "01M4P01D5TWB5FKZGNNQG3943R", "packVersion": "1.0.0", "parentPack": null, "nodeCount": 0, "edgeCount": 0, "diagnostics": [{"code": "pack_level_missing", "detail": "pack.yaml of internal has no pack_level; the configured level org is used"}]}
        ]
      }
      """

  Scenario: A pack configured at one level that declares another is refused
    Given the org pack "rogue" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      name: rogue
      pack_level: built-in
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "pack_level_mismatch"
    And the response body is:
      """
      {"title": "Pack level mismatch", "status": 500, "code": "pack_level_mismatch", "detail": "Pack 'rogue' is configured as org but declares pack_level built-in"}
      """

  Scenario: A bare edge endpoint the fragment does not declare is refused
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      pack_name: qa
      nodes: []
      edges:
      - source: QA_404
        target: directive:DIRECTIVE_010
        relation: suggests
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "unresolved_edge_endpoint"
    And the response body is:
      """
      {"title": "Unresolved edge endpoint", "status": 500, "code": "unresolved_edge_endpoint", "detail": "qa/drg/fragment.yaml: edge endpoint 'QA_404' is neither a URN nor an id this document declares"}
      """

  Scenario: A graph shard with an unknown key is refused
    Given the built-in pack "built-in" with the file "directive.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      surprise: true
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "built-in/directive.graph.yaml: unknown key 'surprise'"}
      """
