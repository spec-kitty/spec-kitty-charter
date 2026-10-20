@contract
Feature: Pack listing and load refusals
  The packs of the offering in layer order, and the loud refusals of a pack that cannot load.

  Scenario: Packs are listed built-in first, then org, with the size of their graph
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      nodes:
      - id: QA_001
        kind: directives
        title: QA Traceability Mandatory
      edges:
      - source: directive:QA_001
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
    And the org pack "internal" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
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

  Scenario: A pack descriptor whose pack_level is an explicit null is refused
    An absent pack_level loads at the configured level with a diagnostic; a pack_level that is present must be a
    string, and null is not one.
    Given the org pack "internal" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: spec-kitty-internal
      pack_level: null
      """
    And the org pack "internal" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response body is:
      """
      {"title": "Pack unreadable", "status": 500, "code": "pack_unreadable", "detail": "pack.yaml of internal: pack_level must be a string"}
      """

  Scenario: A pack configured at one level that declares another is refused
    Given the org pack "rogue" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      name: rogue
      pack_level: built-in
      """
    And the org pack "rogue" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "pack_level_mismatch"
    And the response body is:
      """
      {"title": "Pack level mismatch", "status": 500, "code": "pack_level_mismatch", "detail": "Pack 'rogue' is configured as org but declares pack_level built-in"}
      """

  Scenario: A bare edge endpoint is refused as not a URN
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges:
      - source: QA_404
        target: directive:DIRECTIVE_010
        relation: suggests
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "qa/drg/fragment.yaml: edge endpoint 'QA_404' is not a URN"}
      """

  Scenario: A fragment that carries a header field is refused
    A fragment has no header: the shard-only keys (pack_name, source_kind, layer_index and the like) are not
    declared by the fragment schema, so a fragment that carries one is refused, not read.
    Given the org pack "qa" with the file "drg/fragment.yaml":
      """
      pack_name: qa
      nodes: []
      edges: []
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error", "detail": "qa/drg/fragment.yaml: unknown key 'pack_name'"}
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

  Scenario: A graph shard that is not valid YAML is refused as a DRG document
    The detail is "<pack>/<file> is not valid YAML: " and the first line of the YAML parser's message, which
    is the parser's own words and is not pinned here.
    Given the built-in pack "built-in" with the file "directive.graph.yaml":
      """
      nodes: [
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "drg_schema_error"
    And the response body, apart from "detail", is:
      """
      {"title": "Invalid DRG document", "status": 500, "code": "drg_schema_error"}
      """

  Scenario: A pack descriptor with an undeclared key loads with a diagnostic
    Retired fields count as undeclared. The key is reported and ignored, never refused.
    Given the org pack "internal" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: spec-kitty-internal
      pack_level: org
      surprise: true
      """
    And the org pack "internal" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
      """
    When I GET "/packs"
    Then the response status is 200
    And the response body is:
      """
      {
        "packs": [
          {"name": "internal", "level": "org", "packId": "01M4P01D5TWB5FKZGNNQG3943R", "packVersion": "1.0.0", "parentPack": null, "nodeCount": 0, "edgeCount": 0, "diagnostics": [{"code": "pack_unknown_key", "detail": "pack.yaml of internal declares unknown key 'surprise'; it is ignored"}]}
        ]
      }
      """

  Scenario: A pack descriptor whose requires_core is not a version specifier is refused
    Given the org pack "internal" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: spec-kitty-internal
      pack_level: org
      requires_core: banana
      """
    And the org pack "internal" with the file "drg/fragment.yaml":
      """
      nodes: []
      edges: []
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response body is:
      """
      {"title": "Pack unreadable", "status": 500, "code": "pack_unreadable", "detail": "pack.yaml of internal: requires_core 'banana' is not a PEP 440 version specifier"}
      """

  Scenario: An org pack without a drg/fragment.yaml is refused
    An org pack is its fragment: without one it has no graph to contribute.
    Given the org pack "lonely" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: lonely
      pack_level: org
      """
    When I GET "/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    And the response body is:
      """
      {"title": "Pack unreadable", "status": 500, "code": "pack_unreadable", "detail": "org pack lonely has no drg/fragment.yaml"}
      """
