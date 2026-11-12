@contract @registration
Feature: The per-artifact pack writes answer each refusal they declare
  Each per-artifact pack write declares its refusals in the contract, and the switch gate (switch-gate.yaml, ADR-0059
  D1) holds an operation ready only when a scenario asserts each of them on that operation. The behavior is the one
  directives.feature, paradigms.feature, pack-removal.feature and whole-pack-registration.feature describe; this file
  states it for the writes those files leave out, one row per write, each refusal with the write that succeeds as its
  control:
  - a write to a pack no configured pack names: 404 pack_not_found;
  - a write the mutability policy of an ancestor pack does not permit: 409 governance_policy_violation (ADR-0012);
  - a list sent as null: 422 validation_failed;
  - a stale If-Match: 412, and an If-Match that breaks its pattern: 400;
  - a removal that leaves the closure of a stored activation unable to compose: 422 (pack-removal.feature);
  - a write that meets a busy registry: 503 with Retry-After.

  Background:
    Given registration is switched on
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the org pack "qa" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        paradigm:lk: locked
        styleguide:lk: locked
        toolguide:lk: locked
      """
    And the org pack "qa" with the file "paradigms/lk.paradigm.yaml":
      """
      schema_version: '1.0'
      id: lk
      name: The paradigm qa locks
      summary: Locked.
      """
    And the org pack "qa" with the file "styleguides/lk.styleguide.yaml":
      """
      schema_version: '1.0'
      id: lk
      title: The styleguide qa locks
      scope: code
      principles:
        - name: Locked
          description: Locked.
      """
    And the org pack "qa" with the file "toolguides/lk.toolguide.yaml":
      """
      schema_version: '1.0'
      id: lk
      tool: lk
      title: The toolguide qa locks
      guide_path: lk.guide.md
      summary: Locked.
      """
    And the org pack "qa" with the file "toolguides/lk.guide.md":
      """
      # The toolguide qa locks

      ## Overview

      Locked.
      """
    And the org pack "qa" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: The source of the relationship
      enforcement: required
      """
    And the org pack "qa" with the file "directives/E.directive.yaml":
      """
      schema_version: '1.0'
      id: E
      title: E
      intent: The target of the relationship
      enforcement: required
      """
    And the project pack "proj" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000PJ
      pack_version: 1.0.0
      parent_pack: 01ARWG13C000000000000000QA
      name: proj
      pack_level: project
      """

  Scenario Outline: <operation> to a pack no configured pack names is 404 pack_not_found, and to qa it is written
    When I send <method> "/packs/<pack>/artifacts/<path>" as "application/json" with If-Match "*" and the body:
      """
      <body>
      """
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I send <method> "/packs/qa/artifacts/<path>" as "application/json" with If-Match "*" and the body:
      """
      <body>
      """
    Then the response status is 201

    Examples:
      | operation              | method | pack    | path                   | body                                                                                                                                  |
      | registerPackDirective  | POST   | no-such | directive              | {"id": "N", "schemaVersion": "1.0", "title": "N", "intent": "New", "enforcement": "required"}                                       |
      | putPackDirective       | PUT    | no-such | directive/N            | {"schemaVersion": "1.0", "title": "N", "intent": "New", "enforcement": "required"}                                                  |
      | registerPackParadigm   | POST   | no-such | paradigm               | {"id": "n", "schemaVersion": "1.0", "name": "N", "summary": "New"}                                                                  |
      | putPackParadigm        | PUT    | no-such | paradigm/n             | {"schemaVersion": "1.0", "name": "N", "summary": "New"}                                                                             |
      | registerPackStyleguide | POST   | no-such | styleguide             | {"id": "n", "schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]}          |
      | putPackStyleguide      | PUT    | no-such | styleguide/n           | {"schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]}                     |
      | registerPackToolguide  | POST   | no-such | toolguide              | {"id": "n", "schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}                   |
      | putPackToolguide       | PUT    | no-such | toolguide/n            | {"schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}                              |

  Scenario: putPackRelationship to a pack no configured pack names is 404 pack_not_found, and to qa it is written
    When I PUT "/packs/no-such/relationships" with If-Match "*" and the body:
      """
      {"from": "directive:D", "to": "directive:E", "type": "requires"}
      """
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/qa/relationships" with If-Match "*" and the body:
      """
      {"from": "directive:D", "to": "directive:E", "type": "requires"}
      """
    Then the response status is 201

  Scenario: putPackRelationship with a stale If-Match is 412, and with the pack's ETag it is written
    When I PUT "/packs/qa/relationships" with If-Match "\"sha256:1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f1f\"" and the body:
      """
      {"from": "directive:D", "to": "directive:E", "type": "requires"}
      """
    Then the response status is 412
    When I GET "/packs/qa"
    And I remember the ETag
    And I PUT "/packs/qa/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:D", "to": "directive:E", "type": "requires"}
      """
    Then the response status is 201

  Scenario Outline: <operation> of an artifact an ancestor locks is 409 governance_policy_violation, and the ancestor's own write is not refused
    When I PUT "/packs/proj/artifacts/<path>" with the body:
      """
      <body>
      """
    Then the response status is 409
    And the problem code is "governance_policy_violation"
    When I PUT "/packs/qa/artifacts/<path>" with the body:
      """
      <body>
      """
    Then the response status is 200

    Examples:
      | operation         | path          | body                                                                                                       |
      | putPackParadigm   | paradigm/lk   | {"schemaVersion": "1.0", "name": "lk", "summary": "Reworded"}                                              |
      | putPackStyleguide | styleguide/lk | {"schemaVersion": "1.0", "title": "lk", "scope": "code", "principles": [{"name": "Reworded", "description": "Reworded."}]} |
      | putPackToolguide  | toolguide/lk  | {"schemaVersion": "1.0", "tool": "lk", "title": "lk", "guide": "## Overview\\n\\nReworded.", "summary": "Reworded."} |

  Scenario Outline: <operation> with a list sent as null is 422 validation_failed, and without it the artifact is registered
    When I POST "/packs/qa/artifacts/<kind>" as JSON with the body:
      """
      <refused>
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I GET "/packs/qa/artifacts/<kind>/n"
    Then the response status is 404
    When I POST "/packs/qa/artifacts/<kind>" as JSON with the body:
      """
      <accepted>
      """
    Then the response status is 201

    Examples:
      | operation              | kind       | refused                                                                                                          | accepted                                                                                                                     |
      | registerPackParadigm   | paradigm   | {"id": "n", "schemaVersion": "1.0", "name": "N", "summary": "New", "principles": null}                           | {"id": "n", "schemaVersion": "1.0", "name": "N", "summary": "New"}                                                           |
      | registerPackStyleguide | styleguide | {"id": "n", "schemaVersion": "1.0", "title": "N", "scope": "code", "principles": null}                           | {"id": "n", "schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]}   |
      | registerPackToolguide  | toolguide  | {"id": "n", "schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New.", "commands": null} | {"id": "n", "schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."} |

  Scenario: deletePackArtifact with an If-Match that breaks its pattern is 400, and with * it removes the artifact
    When I DELETE "/packs/qa/artifacts/directive/E" with the header "If-Match": "not-a-revision"
    Then the response status is 400
    And the problem has no code
    When I DELETE "/packs/qa/artifacts/directive/E" with the header "If-Match": "*"
    Then the response status is 204

  Scenario: deletePackArtifact that leaves the closure of a stored activation unable to compose is 422, and is accepted once the closure is whole
    The activation lists no pack, so it roots at every configured pack, and the org pack rival holds D as qa does: the
    closure does not compose (two packs of one level hold one id), and no removal from qa repairs it
    (pack-removal.feature, decision DM-01M6TQ67C9ZTNGZB9CY28JS54F). Once the activation is deleted the removal is
    accepted.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2}
      """
    And the org pack "rival" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: rival
      pack_level: org
      """
    And the org pack "rival" with the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: The rival D
      intent: A directive of a pack that does not build on qa
      enforcement: required
      """
    When I DELETE "/packs/qa/artifacts/directive/E" with the header "If-Match": "*"
    Then the response status is 422
    And the problem code is "pack_sibling_conflict"
    When I DELETE "/activations/a-1" with the header "If-Match": "*"
    Then the response status is 204
    When I DELETE "/packs/qa/artifacts/directive/E" with the header "If-Match": "*"
    Then the response status is 204

  Scenario Outline: <operation> that meets a busy registry is 503 with Retry-After, and succeeds once it is free
    Given the pack registry is held by another write
    When I send <method> "/packs/qa/<path>" as "application/json" with If-Match "*" and the body:
      """
      <body>
      """
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When the held pack registry is released
    And I send <method> "/packs/qa/<path>" as "application/json" with If-Match "*" and the body:
      """
      <body>
      """
    Then the response status is 201

    Examples:
      | operation              | method | path                  | body                                                                                                                         |
      | registerPackDirective  | POST   | artifacts/directive   | {"id": "N", "schemaVersion": "1.0", "title": "N", "intent": "New", "enforcement": "required"}                              |
      | registerPackParadigm   | POST   | artifacts/paradigm    | {"id": "n", "schemaVersion": "1.0", "name": "N", "summary": "New"}                                                         |
      | putPackParadigm        | PUT    | artifacts/paradigm/n  | {"schemaVersion": "1.0", "name": "N", "summary": "New"}                                                                    |
      | registerPackStyleguide | POST   | artifacts/styleguide  | {"id": "n", "schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]} |
      | putPackStyleguide      | PUT    | artifacts/styleguide/n | {"schemaVersion": "1.0", "title": "N", "scope": "code", "principles": [{"name": "New", "description": "New."}]}           |
      | registerPackToolguide  | POST   | artifacts/toolguide   | {"id": "n", "schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}          |
      | putPackToolguide       | PUT    | artifacts/toolguide/n | {"schemaVersion": "1.0", "tool": "n", "title": "N", "guide": "## Overview\\n\\nNew.", "summary": "New."}                     |
      | putPackRelationship    | PUT    | relationships         | {"from": "directive:D", "to": "directive:E", "type": "requires"}                                                           |
