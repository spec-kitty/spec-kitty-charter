@contract @activations
Feature: The guards of the activation register
  The activation register answers the launcher's core process alone, as every operation does (ADR-0011): a
  request without the worktree token, or with another one, is a 401, and a request a browser makes is a 403.
  A write evaluates its preconditions before it judges its document (RFC 9110 section 13.2.2): a stale If-Match,
  or If-None-Match: * on a stored id, is a 412 even when the document names what its packs do not hold, and the
  stored document is kept. An If-Match on an id nothing is stored under is a 412 too, on PUT and on DELETE: no
  stored revision matches it (RFC 9110 section 13.1.1), so the precondition fails before the id is judged.

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

  Scenario: A write without the worktree token is a 401 that asks for a bearer token
    When I POST "/activations" without the worktree token
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"
    And the response has no "Charter-Source-Fingerprint" header

  Scenario: A read with another token is a 401
    When I GET "/activations/a-1" with the Authorization "Bearer not-the-worktree-token-not-the-worktree-token"
    Then the response status is 401
    And the response header "WWW-Authenticate" is "Bearer"

  Scenario: A write a browser makes is a 403 even with the right token
    When I PUT "/activations/a-1" with the header "Origin": "https://evil.example"
    Then the response status is 403
    And the response body is:
      """
      {"title": "Forbidden", "status": 403}
      """

  Scenario: A stale If-Match is refused before the body is judged
    The document names a directive no pack holds, which alone would be a 422; the stale validator is
    judged first, and the stored revision is kept.
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    And I remember the ETag
    When I PUT "/activations/a-1" with the body:
      """
      {"schemaVersion": 2, "selected": {"directive": []}}
      """
    Then the response status is 200
    When I remember the header "ETag"
    And I PUT "/activations/a-1" with the remembered ETag and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["NO_SUCH_DIRECTIVE"]}}
      """
    Then the response status is 412
    And the problem has no code
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/revision" is the remembered header "ETag" without its quotes

  Scenario: If-None-Match * on a stored id is refused before the body is judged
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I PUT "/activations/a-1" with the header "If-None-Match": "*" and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["NO_SUCH_DIRECTIVE"]}}
      """
    Then the response status is 412
    And the problem has no code
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"

  Scenario: A PUT with If-Match on an id nothing is stored under is a 412, not a 404
    When I PUT "/activations/a-9" with the header "If-Match": '"sha256:0000000000000000000000000000000000000000000000000000000000000000"' and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    Then the response status is 412
    And the problem has no code
    When I GET "/activations/a-9"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: A DELETE with If-Match on an id nothing is stored under is a 412, not a 404
    When I DELETE "/activations/a-9" with the header "If-Match": "*"
    Then the response status is 412
    And the problem has no code
    When I send DELETE "/activations/a-9"
    Then the response status is 404
    And the problem code is "activation_not_found"

  Scenario: An If-Match that is not a quoted revision or * is a 400 on a write, and nothing is stored
    The header breaks the pattern the contract declares for If-Match, so it is the request that is wrong (400),
    not a revision that is stale (412).
    Given the activation "a-1" holds:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_001"]}}
      """
    When I PUT "/activations/a-1" with the header "If-Match": "sha256:not-quoted" and the body:
      """
      {"schemaVersion": 2, "selected": {"directive": ["DIRECTIVE_002"]}}
      """
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The header If-Match is \"sha256:not-quoted\", which does not match ^(\*|\"sha256:[0-9a-f]{64}\")$."
    When I DELETE "/activations/a-1" with the header "If-Match": "sha256:not-quoted"
    Then the response status is 400
    And the problem has no code
    When I GET "/activations/a-1"
    Then the response status is 200
    And the field at "/config/selected/directive/0" is "DIRECTIVE_001"

  Scenario: An activation id outside the declared pattern is a 400 on the document and the closure read
    ADR-0020 D2: the id is opaque, but it is bounded to letters, digits and hyphens.
    When I GET "/activations/-leading-hyphen"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The path parameter activationId is \"-leading-hyphen\", which does not match ^[A-Za-z0-9][A-Za-z0-9-]{0,63}$."
    When I GET "/activations/-leading-hyphen/packs"
    Then the response status is 400
    And the problem has no code
