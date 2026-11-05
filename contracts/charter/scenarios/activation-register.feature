@contract @activations
Feature: The activation register and the packs an activation builds on
  Core records one project's activation document on the service and reads it back (ADR-0020 D3-D6): POST
  mints an id, PUT stores under the client's id, both in canonical form under a revision, GET answers the
  stored document with the quoted revision as its ETag, and DELETE removes it. If-Match makes a write
  conditional and without it the last writer wins; If-None-Match: * creates only. The answer depends only on
  the stored document (ADR-0059 D5), so a document an offering change made stale is served as stored and
  refused on write (ADR-0059 D4). The closure read lists the packs the document builds on: the depends_on
  closure of its packs, in dependency order, in the offering's pack shape. Absent means none (ADR-0053 d10)
  for every selected kind; an absent packs means every configured pack.

  Background:
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "directives/DIRECTIVE_001.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_001
      title: First directive
      intent: Do the first thing
      enforcement: required
      """
    And the org pack "alpha" with the file "directives/DIRECTIVE_002.directive.yaml":
      """
      schema_version: '1.0'
      id: DIRECTIVE_002
      title: Second directive
      intent: Do the second thing
      enforcement: required
      """

  # User Story 1: record and read an activation document.

  Scenario: A created document is stored sorted and deduplicated, under its quoted revision
    US1 AS1: the stored document lists the selected ids once, in code-point order, and the ETag is the
    revision in double quotes.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_002", "DIRECTIVE_001", "DIRECTIVE_002"]}}
      """
    Then the response status is 201
    And the field at "/activationId" is "a-1"
    And the field at "/config/selected/directive" holds exactly 2 items
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"
    And the field at "/config/selected/directive/1" is "DIRECTIVE_002"
    And the response header "ETag" is the quoted field "/revision"
    And the path of the Location header is the href of the link "self"

  Scenario: POST stores the document under an id the service mints
    ADR-0020 D3: the service mints the id; Location names the new activation and the ETag is its revision.
    When I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 1}
      """
    Then the response status is 201
    And the field at "/config/schemaVersion" is 1
    And the response header "ETag" is the quoted field "/revision"
    And the path of the Location header is the href of the link "self"

  Scenario: A fetched config registered unchanged answers 200 with the same ETag
    US1 AS2, the round trip (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    When I remember the ETag
    And I remember the header "ETag"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 200
    And the response header "ETag" is the remembered header

  Scenario: A stale If-Match is refused and the current revision is kept
    US1 AS3: the If-Match names the first revision after another write replaced it.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    And I remember the ETag
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 200
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 1, "selected": {"directive": []}}
      """
    Then the response status is 412
    And the problem has no code
    When I GET "/activations/a-1"
    Then the response status is 200
    And the response header "ETag" is the remembered header
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"

  Scenario: Without If-Match the last writer wins
    US1 AS4 (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 200
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"
    And the response header "ETag" is the quoted field "/revision"

  Scenario: An id no pack of the closure holds is refused, naming the id, and nothing is stored
    US1 AS5.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_001", "NO_SUCH_DIRECTIVE"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/selected/directive/1"
    And the field at "/violations/0/code" is "unknown_artifact"
    When I GET "/activations/a-1"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A deny entry on a must-have item of a governing preset is refused, naming the pack
    US1 AS6: alpha's governing preset requires DIRECTIVE_001, so the document cannot deactivate it.
    Given the org pack "alpha" with the file "presets/governing.yaml":
      """
      schema_version: '1.0'
      id: governing
      description: The first directive is required.
      activated_directives:
        - id: DIRECTIVE_001
          must_have: true
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_required"
    And the field at "/requiredBy/0/packId" is "alpha"
    When I GET "/activations/a-1"
    Then the response status is 404

  Scenario: An empty deny list is refused, never stored
    US1 AS7.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "deactivated": {"directive": []}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A second DELETE finds nothing
    US1 AS8 (ADR-0020 D6).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1}
      """
    When I send DELETE "/activations/a-1"
    Then the response status is 204
    When I send DELETE "/activations/a-1"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: If-None-Match * creates only
    US1 AS9 (ADR-0020 D3).
    When I PUT "/activations/a-1" with the header "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 1}
      """
    Then the response status is 201
    When I PUT "/activations/a-1" with the header "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 1}
      """
    Then the response status is 412

  Scenario: A read whose If-None-Match matches the ETag answers 304 without a body
    FR-002, ADR-0059 D5.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1}
      """
    When I remember the header "ETag"
    And I GET "/activations/a-1" with If-None-Match the stored ETag
    Then the response status is 304
    And the response header "ETag" is the remembered header

  Scenario: Reading an unknown activation answers 404
    FR-002 (R7).
    When I GET "/activations/no-such-activation"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: Both validators on one request answer 400
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1}
      """
    When I PUT "/activations/a-1" with the headers "If-Match": "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 1}
      """
    Then the response status is 400

  Scenario: An If-None-Match other than * on a write answers 400
    J10: a malformed precondition.
    When I PUT "/activations/a-1" with the header "If-None-Match": "\"x\"" and the body:
      """
      {"schemaVersion": 1}
      """
    Then the response status is 400

  Scenario: A body over 1 MiB is refused as too large
    #323: the cap is judged before the document.
    When I PUT "/activations/a-1" with the body, its "padding" padded to 1048576 bytes:
      """
      {"schemaVersion": 1, "padding": ""}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/code" is "body_too_large"

  Scenario: A member the document does not declare is refused
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "favourite": "DIRECTIVE_001"}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A null list is refused
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": null}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A document naming an unconfigured pack is refused at the pack's id
    R4.
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "packs": [{"packId": "no-such-pack"}]}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"
    And the field at "/violations/0/pointer" is "/packs/0/packId"

  # The stale documents of the Edge Cases (FR-017, ADR-0059 D4).

  Scenario: A document naming an id the pack no longer holds is served as stored and refused on write
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    And the file "directives/DIRECTIVE_002.directive.yaml" of the pack "alpha" is deleted
    When I GET "/activations/a-1" with If-None-Match the stored ETag
    Then the response status is 304
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive/0" is "DIRECTIVE_002"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 422
    And the problem code is "activation_invalid"

  Scenario: A deny entry on an id a pack now locks is kept on read, and denying it anew is refused
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 1, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:DIRECTIVE_001: locked
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/deactivated/directive/0" is "DIRECTIVE_001"
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "deactivated": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "alpha"

  # User Story 2: the packs an activation builds on.

  Scenario: The closure read lists the activated pack after its dependency, and not an unrelated pack
    US2 AS1: alpha depends on beta; gamma is available and unrelated.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 2 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/1/dependsOn/0/descriptorPackId" is "01M4P01D5TWB5FKZGNNQG3943S"
    And the field at "/packs/1/dependsOn/0/range" is ">=1.0"
    And the field at "/packs/1/dependsOn/0/version" is "1.0.0"

  Scenario: A broken pack outside the closure is never judged
    US2 AS2: gamma's pack.yaml is not YAML, and the alpha activation's closure does not hold gamma.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: [not yaml
      """
    And the project activates the pack "alpha"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 2 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"

  Scenario: A document without packs builds on every configured pack, in the offering's order
    US2 AS3.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "gamma" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: gamma-pack
      pack_level: org
      """
    And the activation "project" holds:
      """
      {"schemaVersion": 1}
      """
    When I GET "/offering"
    Then the response status is 200
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/2/packId" is "gamma"
    When I GET "/activations/project/packs"
    Then the response status is 200
    And the answer holds exactly 3 packs
    And the field at "/packs/0/packId" is "beta"
    And the field at "/packs/1/packId" is "alpha"
    And the field at "/packs/2/packId" is "gamma"

  Scenario: A closure that does not compose is refused as the offering refuses it
    US2 AS4: the activation is stored, then alpha's range stops admitting the version beta holds.
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.9.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the project activates the pack "alpha"
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '==2.0.0'
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_dependency_out_of_range"
    And the field at "/packs/0/packId" is "alpha"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A qualified reference to a pack outside the closure refuses the closure read
    R7: alpha writes beta-pack::directive:SHARE, but beta is not in alpha's depends_on closure.
    Given the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the org pack "beta" with the file "directives/SHARE.directive.yaml":
      """
      schema_version: '1.0'
      id: SHARE
      title: Share the load
      intent: Share it
      enforcement: required
      """
    And the project activates the pack "alpha"
    And the org pack "alpha" with the file "mission_type.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes:
      - urn: action:software-dev/implement
        kind: action
        label: implement
      edges:
      - source: action:software-dev/implement
        target: beta-pack::directive:SHARE
        relation: requires
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_reference_pack_missing"
    And the field at "/packs/0/packId" is "alpha"

  Scenario: A pack of the closure whose directory is gone refuses the closure read as unreadable
    R4: the dependency is configured, so it is unreadable, not unknown.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    And the directory of the pack "beta" is missing
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A pack of the closure whose pack.yaml is not YAML refuses the closure read as unreadable
    R4.
    Given the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943S
        range: '>=1.0'
      """
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: beta-pack
      pack_level: org
      """
    And the project activates the pack "alpha"
    And the org pack "beta" with the file "pack.yaml":
      """
      pack_id: [not yaml
      """
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: The closure read of an unknown activation answers 404
    When I GET "/activations/no-such-activation/packs"
    Then the response status is 404
    And the problem code is "activation_not_found"

  # User Story 6: a pack declares locks.

  Scenario: A directive an org pack's policy file locks cannot be deactivated
    US6 AS1 (FR-022, ADR-0062).
    Given the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "house" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: locked
      """
    And the org pack "house" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: House rule
      intent: Keep the house rule
      enforcement: required
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "packs": [{"packId": "house"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "house"

  Scenario: A lock along the closure wins over a pack nearer built-in that leaves the directive open
    US6 AS2: the built-in pack holds D1 open; the org pack depending on it locks D1.
    Given the built-in pack "base" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: base-pack
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "base" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: open
      """
    And the built-in pack "base" with the file "directives/D1.directive.yaml":
      """
      schema_version: '1.0'
      id: D1
      title: Base rule
      intent: Keep the base rule
      enforcement: required
      """
    And the org pack "house" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: house-pack
      pack_level: org
      mutability_policy: policy.yaml
      depends_on:
      - pack_id: 01M4P01D5TWB5FKZGNNQG3943W
        range: '>=1.0'
      """
    And the org pack "house" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:D1: locked
      """
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 1, "packs": [{"packId": "house"}], "deactivated": {"directive": ["D1"]}}
      """
    Then the response status is 409
    And the problem code is "artifact_locked"
    And the field at "/requiredBy/0/packId" is "house"

  Scenario: A pack naming a policy file that is missing is unreadable on the offering and on the closure read
    US6 AS3.
    Given the project activates the pack "alpha"
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A pack naming a policy file that fails its schema is unreadable on the offering and on the closure read
    US6 AS3 (R7): role locks are reserved, so a roles key makes the file invalid.
    Given the project activates the pack "alpha"
    And the org pack "alpha" with the file "policy.yaml":
      """
      schema_version: 1
      roles:
        reviewer: locked
      """
    And the org pack "alpha" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: alpha-pack
      pack_level: org
      mutability_policy: policy.yaml
      """
    When I GET "/offering"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/activations/project/packs"
    Then the response status is 500
    And the problem code is "pack_unreadable"
