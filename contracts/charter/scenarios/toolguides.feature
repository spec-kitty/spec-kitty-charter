@contract @toolguides
Feature: Toolguides are read and registered functionally
  A client reads a pack's toolguides (FR-015, US3) and registers one through functional registration
  (ADR-0026): the whole pack aggregate is revalidated and committed atomically, a refusal leaves the pack
  unchanged (NFR-002), and every write answers an ETag revision for optimistic concurrency (FR-012). A
  toolguide mints no relationships. There is no DELETE or PATCH.

  Background:
    Given the org pack "qa" with the file "pack.yaml":
      """
      pack_id: qa
      pack_version: 1.0.0
      parent_pack: null
      name: qa
      pack_level: org
      requires_core: '>=1.0'
      active_charter: org-charter.yaml
      pack_drg: drg/fragment.yaml
      """

  Scenario: A registered toolguide is read back value-equal, last_updated present (FR-015, SC-001)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guidePath": "git.md", "summary": "Using git under governance.", "commands": ["git status"], "appliesToLanguages": ["java"], "lastUpdated": "2026-03-03", "references": ["docs/git.md"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 200
    And the field "id" of the response body is "git"
    And the field "tool" of the response body is "git"
    And the field "guidePath" of the response body is "git.md"
    And the field "lastUpdated" of the response body is "2026-03-03"

  Scenario: A toolguide with no last_updated still round-trips its other fields (SC-001 control)
    When I PUT "/packs/qa/toolguides/make" with the body:
      """
      {"schemaVersion": "1.0", "tool": "make", "title": "How to use make", "guidePath": "make.md", "summary": "Using make under governance."}
      """
    Then the response status is 201
    When I GET "/packs/qa/toolguides/make"
    Then the response status is 200
    And the field "tool" of the response body is "make"
    And the field "guidePath" of the response body is "make.md"

  Scenario: A duplicate id is a conflict on POST while a distinct id is created (FR-015, SC-003)
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 201
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "Using git again", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "make", "schemaVersion": "1.0", "tool": "make", "title": "How to use make", "guidePath": "make.md", "summary": "Using make."}
      """
    Then the response status is 201

  Scenario: A malformed body is refused and a well-formed registration succeeds (SC-003)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "9.9", "tool": "git", "title": "How to use git", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 201

  Scenario: A stale If-Match is refused with 412 and a fresh one succeeds (FR-012)
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 201
    When I PUT "/packs/qa/toolguides/git" with If-Match "sha256:0000000000000000000000000000000000000000000000000000000000000000" and the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git (v2)", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 412
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/toolguides/git" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git (v2)", "guidePath": "git.md", "summary": "Using git."}
      """
    Then the response status is 200
