@contract @artifacts
Feature: Procedures are read by pack and under an activation (#484)
  A procedure is a listed kind: the generic list names it, the generic item read answers it in member procedure, with
  its steps and their roles, its anti-patterns and its conditions, and its references are read from the pack's graph
  documents (ADR-0064). The item carries the source fingerprint it verified and the pack revision as its ETag. A
  procedure the pack does not hold is artifact_not_found. Under an activation a procedure is read and listed like the
  other served kinds, with its own ETag and the fingerprint.

  Background:
    Given the built-in pack "core" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: core
      pack_level: built-in
      """
    And the built-in pack "core" with the file "procedures/refactoring.procedure.yaml":
      """
      schema_version: '1.0'
      id: refactoring
      title: Refactoring
      description: Change the structure, not the behavior.
      entry_condition: The tests pass.
      exit_condition: The tests still pass and the structure is simpler.
      steps:
        - title: Take one small step
      """
    And the org pack "qa" with the file "pack.yaml":
      """
      pack_id: 01ARWG13C000000000000000QA
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      """
    And the org pack "qa" with the file "directives/guard.directive.yaml":
      """
      schema_version: '1.0'
      id: GUARD
      title: Guard the gate
      intent: Guard it.
      enforcement: required
      """
    And the org pack "qa" with the file "procedures/test-first-bug-fixing.procedure.yaml":
      """
      schema_version: '1.0'
      id: test-first-bug-fixing
      title: Test-first bug fixing
      description: Fix a defect by first writing a test that fails on it.
      entry_condition: A defect is reported.
      exit_condition: The failing test passes and the defect is fixed.
      steps:
        - title: Write a test that fails on the defect
          description: Reproduce the defect in a test before touching the code.
          role: implementer
          on_success: Fix the code.
          on_failure: Ask the reporter for a reproduction.
        - title: Fix the code until the test passes
          role: implementer
      anti_patterns:
        - title: Fixing before reproducing
          content: A fix without a failing test proves nothing.
      notes: Keep the test.
      applies_to_languages:
        - java
      """
    And the org pack "qa" with the file "procedures.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - source: procedure:test-first-bug-fixing
        target: directive:GUARD
        relation: requires
      """

  Scenario: An org procedure is read in the envelope with its steps, an ETag and the fingerprint
    When I GET "/packs/qa/artifacts/procedure/test-first-bug-fixing"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the response body is:
      """
      {
        "kind": "procedure",
        "id": "test-first-bug-fixing",
        "packId": "qa",
        "procedure": {
          "id": "test-first-bug-fixing",
          "schemaVersion": "1.0",
          "title": "Test-first bug fixing",
          "description": "Fix a defect by first writing a test that fails on it.",
          "entryCondition": "A defect is reported.",
          "exitCondition": "The failing test passes and the defect is fixed.",
          "steps": [
            {"title": "Write a test that fails on the defect", "description": "Reproduce the defect in a test before touching the code.", "role": "implementer", "onSuccess": "Fix the code.", "onFailure": "Ask the reporter for a reproduction."},
            {"title": "Fix the code until the test passes", "description": null, "role": "implementer", "onSuccess": null, "onFailure": null}
          ],
          "antiPatterns": [{"title": "Fixing before reproducing", "content": "A fix without a failing test proves nothing.", "badExample": null, "goodExample": null}],
          "notes": "Keep the test.",
          "appliesToLanguages": ["java"]
        },
        "_links": {
          "self": {"href": "/api/v1/packs/qa/artifacts/procedure/test-first-bug-fixing"},
          "collection": {"href": "/api/v1/packs/qa/artifacts?kind=procedure"}
        }
      }
      """

  Scenario: A built-in procedure is read with an ETag and the fingerprint, and listed under its kind
    When I GET "/packs/core/artifacts/procedure/refactoring"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the response header "ETag" appears once
    And the field at "/procedure/steps/0/role" is null
    When I GET "/packs/core/artifacts?kind=procedure"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items" holds exactly the ids:
      """
      procedure:refactoring
      """

  Scenario: The references of a procedure are the edges its pack's graph documents write
    When I GET "/packs/qa/artifacts/procedure/test-first-bug-fixing/references"
    Then the response status is 200
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items/0/reference" is "directive:GUARD"
    And the field at "/items/0/relation" is "requires"
    And following the link "artifact" of every element of "items" answers 200

  Scenario: A procedure the pack does not hold is artifact_not_found, with no fingerprint
    When I GET "/packs/qa/artifacts/procedure/nope"
    Then the response status is 404
    And the response body is:
      """
      {"title": "Artifact not found", "status": 404, "code": "artifact_not_found", "detail": "Pack qa has no procedure nope."}
      """
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: Under an activation a built-in and an org procedure are read and listed, with an ETag and the fingerprint
    The activation selects both procedures; refactoring is held by the built-in pack, test-first-bug-fixing by the org
    pack. Each answers 200 with its own ETag, and its artifact link reads the version in force.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "packs": [{"packId": "core"}, {"packId": "qa"}], "selected": {"procedure": ["refactoring", "test-first-bug-fixing"]}}
      """
    When I GET "/activations/a-1/artifacts/procedure/refactoring"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/packId" is "core"
    And the field at "/state" is "active"
    And the field at "/_links/artifact/href" is "/api/v1/packs/core/artifacts/procedure/refactoring"
    When I GET "/activations/a-1/artifacts/procedure/test-first-bug-fixing"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/packId" is "qa"
    And the field at "/state" is "active"
    And the field at "/_links/artifact/href" is "/api/v1/packs/qa/artifacts/procedure/test-first-bug-fixing"
    When I follow the link "artifact"
    Then the response status is 200
    And the field at "/procedure/steps/0/role" is "implementer"
    When I GET "/activations/a-1/artifacts?kind=procedure"
    Then the response status is 200
    And the response header "ETag" appears once
    And the response header "Charter-Source-Fingerprint" appears once
    And the field at "/items" holds exactly the ids:
      """
      procedure:refactoring
      procedure:test-first-bug-fixing
      """
