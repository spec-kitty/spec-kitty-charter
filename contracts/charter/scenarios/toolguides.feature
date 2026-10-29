@contract @toolguides
Feature: Toolguides are read and registered functionally
  A client reads a pack's toolguides (FR-015, US3) and registers one through functional registration
  (ADR-0026): the whole pack aggregate is revalidated and committed atomically, a refusal leaves the pack
  unchanged (NFR-002), and every write answers an ETag revision for optimistic concurrency (FR-012). A
  toolguide mints no relationships. There is no DELETE or PATCH. The guide travels as content, never as a
  path (FR-010, US5): a registration carries exactly one of guide (its Markdown) or guideUri (fetched by the
  service), and a read answers the stored guide with its H1 written from the title (FR-011).

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

  Scenario: A registered toolguide is read back value-equal, its guide with the H1 from the title (FR-015, US5 AS1)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\nUse git under governance.\n\n## Usage\n\nRun `git status` first.", "summary": "Using git under governance.", "commands": ["git status"], "appliesToLanguages": ["java"], "lastUpdated": "2026-03-03", "references": ["docs/git.md"]}
      """
    Then the response status is 201
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 200
    And the field "id" of the response body is "git"
    And the field "tool" of the response body is "git"
    And the field "lastUpdated" of the response body is "2026-03-03"
    And the field "guide" of the response body is the lines:
      """
      # How to use git

      ## Overview

      Use git under governance.

      ## Usage

      Run `git status` first.
      """

  Scenario: A toolguide with no last_updated and a guide whose H1 is the title still round-trips (SC-001 control)
    When I PUT "/packs/qa/toolguides/make" with the body:
      """
      {"schemaVersion": "1.0", "tool": "make", "title": "How to use make", "guide": "# How to use make\n\n## Overview\n\nUse make under governance.", "summary": "Using make under governance."}
      """
    Then the response status is 201
    When I GET "/packs/qa/toolguides/make"
    Then the response status is 200
    And the field "tool" of the response body is "make"
    And the field "guide" of the response body is the lines:
      """
      # How to use make

      ## Overview

      Use make under governance.
      """

  Scenario: A guide whose H1 differs from the title is refused, and the same guide under its own title is accepted (FR-011)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "# Using git\n\n## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The guide has the H1 'Using git', which differs from the toolguide title 'How to use git'."
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 404
    And the problem code is "toolguide_not_found"
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "Using git", "guide": "# Using git\n\n## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 201

  Scenario: A guide that is not a valid guide is refused naming the rule, and nothing is stored (US5 AS4)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Usage\n\nRun git.", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The guide has no ## Overview."
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 404
    And the problem code is "toolguide_not_found"

  Scenario Outline: A registration without exactly one non-empty guide or guideUri is refused (US5 AS3: <case>)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "summary": "Using git."<fields>}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 404

    Examples:
      | case              | fields                                                                          |
      | neither           |                                                                                 |
      | a null guide      | , "guide": null                                                                 |
      | an empty guide    | , "guide": ""                                                                   |
      | an empty guideUri | , "guideUri": ""                                                                |
      | both              | , "guide": "## Overview", "guideUri": "https://guides.example.org/git.guide.md" |
      | a null beside URI | , "guide": null, "guideUri": "https://guides.example.org/git.guide.md"          |
      | a null beside it  | , "guide": "## Overview", "guideUri": null                                      |

  Scenario: An inline guide over 1 MiB is refused while one of exactly 1 MiB is accepted (NFR-005, US5 AS4)
    When I PUT "/packs/qa/toolguides/git" with the body, its "guide" padded to 1048577 bytes:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\n", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body is "The guide is larger than 1048576 bytes of UTF-8 (1 MiB)."
    When I PUT "/packs/qa/toolguides/git" with the body, its "guide" padded to 1048576 bytes:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\n", "summary": "Using git."}
      """
    Then the response status is 201

  Scenario: A guideUri is fetched, stored and read back as content, and a later PUT fetches it again (US5 AS2)
    Given the guide host serves "https://guides.example.org/git.guide.md" with:
      """
      ## Overview

      Use git under governance.
      """
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guideUri": "https://guides.example.org/git.guide.md", "summary": "Using git."}
      """
    Then the response status is 201
    And the field "guide" of the response body is the lines:
      """
      # How to use git

      ## Overview

      Use git under governance.
      """
    Given the guide host serves "https://guides.example.org/git.guide.md" with:
      """
      ## Overview

      Use git, signed.
      """
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guideUri": "https://guides.example.org/git.guide.md", "summary": "Using git."}
      """
    Then the response status is 200
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 200
    And the field "guide" of the response body is the lines:
      """
      # How to use git

      ## Overview

      Use git, signed.
      """

  Scenario: A guideUri whose host is not allowlisted is refused with the typed reason, and nothing is stored (US5 AS5)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guideUri": "https://elsewhere.example.org/private/git.guide.md?token=secret", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    And the field "detail" of the response body starts with "The guide could not be fetched: guide fetch refused (HOST_NOT_ALLOWED) for host elsewhere.example.org"
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 404
    And the problem code is "toolguide_not_found"

  Scenario: A duplicate id is a conflict on POST while a distinct id is created (FR-015, SC-003)
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 201
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "Using git again", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 409
    And the problem code is "registration_conflict"
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "make", "schemaVersion": "1.0", "tool": "make", "title": "How to use make", "guide": "## Overview\n\nUse make.", "summary": "Using make."}
      """
    Then the response status is 201

  Scenario: A malformed body is refused and a well-formed registration succeeds (SC-003)
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "9.9", "tool": "git", "title": "How to use git", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 422
    And the problem code is "validation_failed"
    When I PUT "/packs/qa/toolguides/git" with the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 201

  Scenario: A stale If-Match is refused with 412 and a fresh one succeeds (FR-012)
    When I POST "/packs/qa/toolguides" as JSON with the body:
      """
      {"id": "git", "schemaVersion": "1.0", "tool": "git", "title": "How to use git", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 201
    When I PUT "/packs/qa/toolguides/git" with If-Match "sha256:0000000000000000000000000000000000000000000000000000000000000000" and the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git (v2)", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 412
    When I GET "/packs/qa/toolguides/git"
    Then the response status is 200
    When I remember the ETag
    When I PUT "/packs/qa/toolguides/git" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "tool": "git", "title": "How to use git (v2)", "guide": "## Overview\n\nUse git.", "summary": "Using git."}
      """
    Then the response status is 200
    And the field "guide" of the response body starts with "# How to use git (v2)"
