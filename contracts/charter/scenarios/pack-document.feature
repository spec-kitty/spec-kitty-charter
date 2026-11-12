# The pack document read (#451, operator ruling; FR-007, FR-008, FR-018): GET /packs/{packId}/doc answers the pack's
# README.md as text/markdown (the held file) or application/json (title, body and links), under one ETag, the pack
# revision, with Vary: Accept.
#
# Steps this file adds to the vocabulary (listed in scenarios/README.md): Then the response body is the text: (a body
# that is text, not JSON), Then the response body is empty, and When I GET "<path>" accepting "<media type>" with
# If-None-Match the remembered header "<name>". Everything else is the existing vocabulary.
#
# Where the contract scenarios use the example packs of the charter (service/packs/src/testFixtures), they name a pack by
# its files as every scenario does; the `project` example pack has no README.md and the `org-base` one has, so the two
# packs below stand for them.

@contract @registration
Feature: A pack's document is read as Markdown or as JSON
  A client reads the README.md of a stored pack. Accept: text/markdown answers the held file, frontmatter included;
  Accept: application/json answers its title, its body below the H1 in canonical form (no frontmatter) and links. Both
  forms share one strong ETag, the pack revision, and every 200, 304 and 406 says Vary: Accept. A pack with no README.md
  is 404 pack_document_not_found, an unknown pack 404 pack_not_found, an Accept that names neither form 406.

  Background:
    Given the org pack "docs" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: docs-pack
      pack_level: org
      """
    And the org pack "docs" with the file "README.md":
      """
      ---
      audience: pack authors
      ---

      # Docs pack

      What the pack is for.

      ## Contents

      Directives and tactics.
      """
    And the org pack "plain" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943S
      pack_version: 1.0.0
      parent_pack: null
      name: plain-pack
      pack_level: org
      """

  Scenario: Accept text/markdown answers the held README.md, byte for byte
    When I GET "/packs/docs/doc" accepting "text/markdown"
    Then the response status is 200
    And the response header "Content-Type" is "text/markdown;charset=UTF-8"
    And the response body is the text:
      """
      ---
      audience: pack authors
      ---

      # Docs pack

      What the pack is for.

      ## Contents

      Directives and tactics.
      """
    And the response header "Vary" is "Accept"
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: Accept application/json answers the title, the body without the frontmatter, and the links
    When I GET "/packs/docs/doc" accepting "application/json"
    Then the response status is 200
    And the response body is:
      """
      {
        "title": "Docs pack",
        "body": "What the pack is for.\n\n## Contents\n\nDirectives and tactics.\n",
        "_links": {
          "self": {"href": "/api/v1/packs/docs/doc"},
          "pack": {"href": "/api/v1/packs/docs"}
        }
      }
      """
    And the response header "Vary" is "Accept"
    And the response header "Charter-Source-Fingerprint" is a sha256 digest

  Scenario: The ETag is the pack revision, and the same for both forms
    When I GET "/packs/docs"
    Then the response header "ETag" is the quoted field "/revision"
    When I remember the header "ETag"
    And I GET "/packs/docs/doc" accepting "text/markdown"
    Then the response header "ETag" is the remembered header
    When I GET "/packs/docs/doc" accepting "application/json"
    Then the response header "ETag" is the remembered header

  Scenario: A README with no H1 has no title in the JSON form
    Given the org pack "untitled" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943V
      pack_version: 1.0.0
      parent_pack: null
      name: untitled-pack
      pack_level: org
      """
    And the org pack "untitled" with the file "README.md":
      """
      Just words, and no title.
      """
    When I GET "/packs/untitled/doc" accepting "application/json"
    Then the response status is 200
    And the field at "/title" is absent
    And the field at "/body" starts with "Just words, and no title."

  Scenario: A README whose body opens with a thematic break is read in both forms and linked
    Given the org pack "breaks" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: breaks-pack
      pack_level: org
      """
    And the org pack "breaks" with the file "README.md":
      """
      # Breaks

      ---

      Below the break.
      """
    When I GET "/packs/breaks"
    Then the field at "/_links/doc/href" is "/api/v1/packs/breaks/doc"
    When I GET "/packs/breaks/doc" accepting "text/markdown"
    Then the response status is 200
    And the response body is the text:
      """
      # Breaks

      ---

      Below the break.
      """
    When I GET "/packs/breaks/doc" accepting "application/json"
    Then the response status is 200
    And the field at "/title" is "Breaks"
    And the field at "/body" starts with "---"

  Scenario: A registration result links the document, and a replace without a README drops the link
    Given registration is switched on
    And the archive "with" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943X
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "with" holds the file "README.md":
      """
      # Acme
      """
    And the archive "without" is a copy of the archive "with"
    And the archive "without" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943X
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "without" lacks the file "README.md"
    When I PUT "/packs/acme" with the archive "with"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/_links/doc/href" is "/api/v1/packs/acme/doc"
    When I PUT "/packs/acme" with the archive "without"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/_links/doc" is absent
    When I GET "/packs/acme"
    Then the answer has no link "doc"
    When I GET "/packs/acme/doc" accepting "text/markdown"
    Then the response status is 404
    And the problem code is "pack_document_not_found"

  Scenario: The pack resource links to the document of a pack that has one, and the link is followed
    When I GET "/packs/docs"
    Then the response status is 200
    And the field at "/_links/doc/href" is "/api/v1/packs/docs/doc"
    When I follow the link "doc"
    Then the response status is 200
    And the field "title" of the response body is "Docs pack"

  Scenario: The pack resource has no document link when the pack has no document
    When I GET "/packs/plain"
    Then the response status is 200
    And the answer has no link "doc"

  Scenario Outline: The Accept header chooses the form by quality, then by specificity
    When I GET "/packs/docs/doc" accepting "<accept>"
    Then the response status is 200
    And the response header "Content-Type" is "<content type>"

    Examples:
      | accept                                  | content type                |
      | application/json                        | application/json            |
      | */*                                     | application/json            |
      | application/*                           | application/json            |
      | text/markdown                           | text/markdown;charset=UTF-8 |
      | text/*                                  | text/markdown;charset=UTF-8 |
      | text/markdown;q=0.5, application/json   | application/json            |
      | text/markdown, application/json;q=0.5   | text/markdown;charset=UTF-8 |
      | text/markdown;q=0.1, */*                | application/json            |
      | application/json, text/markdown         | application/json            |

  Scenario: A request with no Accept answers JSON
    When I GET "/packs/docs/doc"
    Then the response status is 200
    And the response header "Content-Type" is "application/json"
    And the field "title" of the response body is "Docs pack"

  Scenario Outline: A matching If-None-Match is 304 in either form, with the tag and Vary and no body
    When I GET "/packs/docs/doc" accepting "<accept>"
    And I remember the header "ETag"
    And I GET "/packs/docs/doc" accepting "<accept>" with If-None-Match the remembered header "ETag"
    Then the response status is 304
    And the response header "ETag" is the remembered header
    And the response header "Vary" is "Accept"
    And the response has no "Charter-Source-Fingerprint" header
    And the response body is empty

    Examples:
      | accept            |
      | application/json  |
      | text/markdown     |

  Scenario: If-None-Match * is 304
    When I GET "/packs/docs/doc" with the headers "Accept": "text/markdown" and "If-None-Match": "*"
    Then the response status is 304
    And the response header "Vary" is "Accept"

  Scenario: A value that is not an entity-tag list matches nothing
    When I GET "/packs/docs/doc" with the headers "Accept": "text/markdown" and "If-None-Match": "garbage"
    Then the response status is 200
    And the response header "Vary" is "Accept"

  Scenario: A pack with no README.md is 404 pack_document_not_found, whatever the Accept
    When I GET "/packs/plain/doc" accepting "text/markdown"
    Then the response status is 404
    And the problem code is "pack_document_not_found"
    When I GET "/packs/plain/doc" accepting "application/json"
    Then the response status is 404
    And the problem code is "pack_document_not_found"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: An unknown pack is 404 pack_not_found
    When I GET "/packs/nobody/doc" accepting "text/markdown"
    Then the response status is 404
    And the problem code is "pack_not_found"

  Scenario: A pack whose pack.yaml cannot be read is 500 pack_unreadable, as getPack answers it
    The doc read judges the pack as getPack does: a stored pack.yaml that declares the retired active_charter key
    cannot be read, so the README beside it is not served.
    Given the org pack "stale" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943Y
      pack_version: 1.0.0
      parent_pack: null
      name: stale-pack
      pack_level: org
      active_charter: org-charter.yaml
      """
    And the org pack "stale" with the file "README.md":
      """
      # Stale pack
      """
    When I GET "/packs/stale"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/packs/stale/doc" accepting "text/markdown"
    Then the response status is 500
    And the problem code is "pack_unreadable"
    When I GET "/packs/stale/doc" accepting "application/json"
    Then the response status is 500
    And the problem code is "pack_unreadable"

  Scenario: A built-in pack has no document read: 404 pack_not_found
    Given the built-in pack "baseline" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943T
      pack_version: 1.0.0
      parent_pack: null
      name: baseline
      pack_level: built-in
      """
    And the built-in pack "baseline" with the file "README.md":
      """
      # Baseline
      """
    When I GET "/packs/baseline/doc" accepting "text/markdown"
    Then the response status is 404
    And the problem code is "pack_not_found"

  Scenario Outline: An Accept that names neither form is 406 with no code, the shared headers and Vary
    When I GET "/packs/docs/doc" accepting "<accept>"
    Then the response status is 406
    And the problem has no code
    And the response header "Vary" is "Accept"
    And the response header "Charter-Contract-Version" is the contract version
    And the response has no "Charter-Source-Fingerprint" header

    Examples:
      | accept           |
      | image/png        |
      | application/xml  |
      | text/html        |

  Scenario: The contract declares pack_document_not_found as a 404 that getPackDocument raises
    Then the contract declares the refusal code "pack_document_not_found"
    And the contract says "pack_document_not_found" is answered with status 404
    And the contract says "pack_document_not_found" may not be retried
    And the contract says "pack_document_not_found" is raised by an operation
