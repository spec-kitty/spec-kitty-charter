# Whole-pack registration (ADR-0065, US1 AS1 to AS8 of layer-merge-and-effective-reads, FR-001 to FR-004, FR-024,
# FR-025, FR-027, FR-037, SC-001, SC-003, NFR-004). It replaces registration/register-pack.feature (#374), a @WIP file
# that promised registration of four source kinds with a lockfile. Its promises are rewritten here:
# - PR-PACK-REGISTER-001 (a source of each kind is registered and recorded): three kinds are built, https_archive, git
#   and filesystem, and the record is the answer's source member, never a lockfile;
# - PR-PACK-REGISTER-002 (a branch is pinned to the commit it points at): source.commit;
# - PR-PACK-REGISTER-003 (two sources claiming one pack id are refused) is reversed by ADR-0065 D1 and D2: a second
#   registration of a pack id replaces the stored pack under the identity checks, and the old refusal (SOURCE_CONFLICT)
#   is gone (changes/2026-11-07-a-pack-registration-breaking.md);
# - PR-PACK-REGISTER-004 (a colliding constituent slug is refused) was already superseded by ADR-0053 decision 6.
#
# State: bound, with no @WIP tag. WP16 bound the PUT path, the status resource, the impact check and the version history,
# WP17 the https_archive and filesystem sources, and WP18 the git source, each recording the red assertion failure and
# removing the tag from the scenarios it bound (decision DM-01M6RN6GC7PJS8BGHMAYM4JSSW). The operations are built: none is
# x-reserved or answers 501.
#
# Steps the serving WPs add (listed in scenarios/README.md, "Registration steps"): the archive steps (an archive is
# built from the files named, as tar.gz unless a step says zip), the registration steps (poll the Location until the job
# ends, hold and release the jobs at the RegistrationJobs seam, forget the ended jobs), and one source seam per kind (a
# stub https exchange, a local bare git repository, a configured filesystem root).

@contract @registration
Feature: Registering a whole pack replaces the pack the service holds
  A client registers a pack it authored as one unit, PUT /packs/{packId} with the archive as the body or POST /packs
  naming a source, and the service then holds exactly that pack: an artifact, an edge, a preset item or a file the
  new pack does not carry is gone (ADR-0065 D1, SC-003). The registration is asynchronous: the service answers 202 and
  a status URI after the synchronous checks, and the client polls the status until the registration is succeeded or
  refused (FR-024). The 409 and 422 answers of the flow, the impact check included, are the problem of a refused
  status. Each absence below is paired with a presence in the same pack, so a registration that stored nothing at all
  cannot satisfy it.

  Background:
    Given registration is switched on
    And the archive "v1" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v1" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 1
      intent: The directive that stays
      enforcement: required
      """
    And the archive "v1" holds the file "directives/B.directive.yaml":
      """
      schema_version: '1.0'
      id: B
      title: B at version 1
      intent: The directive version 2 drops
      enforcement: required
      """

  # bound by: WP16 (US1 AS1)
  Scenario: A pack that does not exist yet is created, and everything the archive carries is readable
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    And the field at "/state" is "pending"
    And the field at "/packId" is "acme"
    And the path of the Location header starts with "/api/v1/pack-registrations/"
    When I poll the registration until it ends
    Then the response status is 200
    And the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/version" is "1.0.0"
    And the field at "/result/level" is "org"
    And the field at "/problem" is absent
    When I GET "/packs/acme"
    Then the response status is 200
    And the response header "ETag" is the quoted field "/revision"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200
    And the field at "/directive/title" is "A at version 1"
    When I GET "/packs/acme/artifacts/directive/B"
    Then the response status is 200
    And the field at "/directive/title" is "B at version 1"

  # bound by: WP16 (US1 AS1, the zip format)
  Scenario: A zip archive registers the same pack as a gzip archive
    When I PUT "/packs/acme" with the archive "v1" as a zip archive
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    When I GET "/packs/acme/artifacts/directive/B"
    Then the response status is 200

  # bound by: WP16 (US1 AS2, SC-003)
  Scenario: A replace drops what the new archive leaves out and keeps what it carries
    Given the archive "v2" is a copy of the archive "v1"
    And the archive "v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v2" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A at version 2
      intent: The directive that stays
      enforcement: required
      """
    And the archive "v2" lacks the file "directives/B.directive.yaml"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "v2"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"
    And the field at "/result/version" is "1.1.0"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200
    And the field at "/directive/title" is "A at version 2"
    When I GET "/packs/acme/artifacts/directive/B"
    Then the response status is 404
    And the problem code is "directive_not_found"

  # bound by: WP16 (US1 AS2: no edge from a dropped artifact remains)
  Scenario: A replace drops the registered edges an artifact it removes held
    Given the archive "v2" is a copy of the archive "v1"
    And the archive "v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "v2" lacks the file "directives/B.directive.yaml"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the ETag
    And I PUT "/packs/acme/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 201
    When I GET "/packs/acme/artifacts/directive/B/references"
    Then the response status is 200
    And the field at "/items/0/reference" is "directive:A"
    When I PUT "/packs/acme" with the archive "v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/packs/acme/artifacts/directive/B/references"
    Then the response status is 404
    When I GET "/packs/acme/artifacts/directive/A/references"
    Then the response status is 200
    And the field at "/items/0" is absent

  # bound by: WP16 (US1 AS4)
  Scenario: Sending the identical archive again changes nothing
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the field "revision" of the response body
    And I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "unchanged"
    And the field at "/result/revision" is the remembered one
    When I GET "/packs/acme"
    Then the field "revision" of the response body is the remembered one

  # bound by: WP16 (US1 AS3, the version history of IC-16)
  Scenario: The same version with other content is refused, and a new version is accepted
    Given the archive "same-version" is a copy of the archive "v1"
    And the archive "same-version" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A changed without a new version
      intent: The directive that stays
      enforcement: required
      """
    And the archive "next-version" is a copy of the archive "same-version"
    And the archive "next-version" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.1
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "same-version"
    Then the response status is 202
    When I poll the registration until it ends
    Then the response status is 200
    And the field at "/state" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "pack_version_conflict"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the field at "/directive/title" is "A at version 1"
    When I PUT "/packs/acme" with the archive "next-version"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the field at "/directive/title" is "A changed without a new version"

  # bound by: WP16 (US1 AS6, first clause)
  Scenario: A stale If-Match is refused at once and no registration exists
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "v1" and the header "If-Match": "\"sha256:0000000000000000000000000000000000000000000000000000000000000000\""
    Then the response status is 412
    And the problem has no code
    And the response has no "Location" header
    And the field at "/state" is absent

  # bound by: WP16 (US1 AS6, the control of the stale If-Match)
  Scenario: The current revision as If-Match is accepted
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the ETag
    And I PUT "/packs/acme" with the archive "v1" and the remembered ETag
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "unchanged"

  # bound by: WP16 (US1 AS6, If-None-Match: * and both validators)
  Scenario: If-None-Match makes a registration create-only, and both validators together are refused
    When I PUT "/packs/acme" with the archive "v1" and the header "If-None-Match": "*"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/result/outcome" is "created"
    When I PUT "/packs/acme" with the archive "v1" and the header "If-None-Match": "*"
    Then the response status is 412
    And the problem has no code
    When I PUT "/packs/acme" with the archive "v1" and the headers "If-None-Match": "*" and "If-Match": "*"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The headers If-Match and If-None-Match exclude each other, and the request sends both."

  # bound by: WP16 (US1 AS6, second clause: the revision moves after the 202)
  Scenario: A revision that moves after the registration was accepted ends it refused with 412
    Given the archive "next-version" is a copy of the archive "v1"
    And the archive "next-version" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.1
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the ETag
    And the registration jobs are held before they run
    And I PUT "/packs/acme" with the archive "next-version" and the remembered ETag
    Then the response status is 202
    When I remember the Location header
    And I PUT "/packs/acme/artifacts/directive/C" with the body:
      """
      {"schemaVersion": "1.0", "title": "C written while the registration waits", "intent": "A competing write", "enforcement": "required"}
      """
    Then the response status is 201
    When I release the held registration jobs
    And I poll the remembered registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 412
    And the field at "/problem/code" is absent
    When I GET "/packs/acme/artifacts/directive/C"
    Then the response status is 200
    And the field at "/directive/title" is "C written while the registration waits"

  # bound by: WP16 (FR-024, the status lifecycle)
  Scenario: A registration is observed pending, running and succeeded, and its status is forgotten later
    Given the registration jobs are held before they run
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202
    When I remember the Location header
    And I GET the remembered registration
    Then the response status is 200
    And the field at "/state" is "pending"
    And the field at "/finishedAt" is absent
    When I release the held registration jobs and hold them while they run
    And I GET the remembered registration
    Then the field at "/state" is "running"
    When I release the held registration jobs
    And I poll the remembered registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/finishedAt" is present
    When the service forgets the ended registrations
    And I GET the remembered registration
    Then the response status is 404
    And the problem code is "registration_not_found"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200

  # bound by: WP16 (FR-024, an id nobody issued, and an id outside the grammar)
  Scenario: A registration id nobody issued is not found
    When I GET "/pack-registrations/01M6TQ62HXYF362GAFS2BZ3PGF"
    Then the response status is 404
    And the problem code is "registration_not_found"
    When I GET "/pack-registrations/not-an-id"
    Then the response status is 400
    And the problem has no code

  # bound by: WP16 (US1 AS5; one outline, a violation each, a control in the same pack)
  Scenario Outline: An archive that breaks a pack rule is refused with the violations and stores nothing
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "<path>":
      """
      <content>
      """
    When I PUT "/packs/acme" with the archive "bad"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/detail" contains "<path>"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: the archive carries something the pack model refuses
      | path                        | content                                      |
      | notes.txt                   | a file the pack model does not know          |
      | ../outside.yaml             | a path that leaves the pack                  |
      | directives/C.directive.yaml | id: C, a directive missing its required keys |

  # Operator ruling D-1 (ADR-0066 closeout): a registration answers every fault of the pack in one report, a graph
  # document that breaks its schema included, one violation per fault at the pack-relative file of the document. A
  # fault that is not a graph fault (a file the pack model does not know) is listed after the graph faults.
  Scenario: An archive with two graph documents that break their schema is refused listing both at their files
    Given the archive "bad" is a copy of the archive "v1"
    And the archive "bad" holds the file "notes.txt":
      """
      a file the pack model does not know
      """
    And the archive "bad" holds the file "zeta.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      surprise: 1
      """
    And the archive "bad" holds the file "alpha.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: []
      other: 2
      """
    When I PUT "/packs/acme" with the archive "bad"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 3 item(s)
    And the field at "/problem/violations/0/code" is "drg_schema_error"
    And the field at "/problem/violations/0/pointer" is "alpha.graph.yaml"
    And the field at "/problem/violations/0/detail" contains "alpha.graph.yaml"
    And the field at "/problem/violations/1/code" is "drg_schema_error"
    And the field at "/problem/violations/1/pointer" is "zeta.graph.yaml"
    And the field at "/problem/violations/1/detail" contains "zeta.graph.yaml"
    And the field at "/problem/violations/2/code" is "unmodelled"
    And the field at "/problem/violations/2/pointer" is "notes.txt"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # #382 (ADR-0040 decision 6, amended 2026-11-11): a sidecar whose path leaves assets/ is refused as an escape, even
  # though a file is at the target (the pack's own pack.yaml), and the pack is not stored.
  Scenario: An archive with an asset sidecar whose path leaves assets/ is refused naming the escape
    Given the archive "escaping" is a copy of the archive "v1"
    And the archive "escaping" holds the file "assets/notes.md.asset.yaml":
      """
      id: notes
      mime: text/markdown
      path: ../pack.yaml
      """
    When I PUT "/packs/acme" with the archive "escaping"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/detail" is "The pack cannot be registered: 1 fault(s), the first at assets/notes.md.asset.yaml."
    And the field at "/problem/violations/0/code" is "asset"
    And the field at "/problem/violations/0/pointer" is "assets/notes.md.asset.yaml"
    And the field at "/problem/violations/0/detail" is "assets/notes.md.asset.yaml: asset path '../pack.yaml' escapes the pack's assets/ root"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"

  # #382: a sidecar path inside assets/ that names no file still reads "does not exist", once.
  Scenario: An archive with an asset sidecar whose payload is missing is refused naming the payload
    Given the archive "missing" is a copy of the archive "v1"
    And the archive "missing" holds the file "assets/notes.md.asset.yaml":
      """
      id: notes
      mime: text/markdown
      path: notes.md
      """
    When I PUT "/packs/acme" with the archive "missing"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/pointer" is "assets/notes.md.asset.yaml"
    And the field at "/problem/violations/0/detail" is "assets/notes.md.asset.yaml: payload assets/notes.md does not exist"

  # #254: a fragment node's body_path is relative to the pack root; one that leaves it is a graph fault of the fragment,
  # listed as drg_schema_error at drg/fragment.yaml, and the pack is not stored.
  Scenario: An archive whose fragment names a body_path outside the pack is refused at the fragment
    Given the archive "escaping" is a copy of the archive "v1"
    And the archive "escaping" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      pack_drg: drg/fragment.yaml
      """
    And the archive "escaping" holds the file "drg/fragment.yaml":
      """
      nodes:
      - id: A
        kind: directives
        title: A at version 1
        body_path: ../outside.directive.yaml
      edges: []
      """
    When I PUT "/packs/acme" with the archive "escaping"
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations" holds exactly 1 item(s)
    And the field at "/problem/violations/0/code" is "drg_schema_error"
    And the field at "/problem/violations/0/pointer" is "drg/fragment.yaml"
    And the field at "/problem/violations/0/detail" is "drg/fragment.yaml: node A body_path '../outside.directive.yaml' escapes the pack root"
    When I GET "/packs/acme"
    Then the response status is 404
    And the problem code is "pack_not_found"

  # bound by: WP16 (US1 AS7: a changed level or pack_id is refused, then the same identity is accepted)
  Scenario Outline: A replace may not change the level or the pack_id of the stored pack
    Given the archive "changed" is a copy of the archive "v1"
    And the archive "changed" holds the file "pack.yaml":
      """
      pack_id: <pack_id>
      pack_version: 2.0.0
      parent_pack: null
      name: acme
      pack_level: <level>
      """
    And the archive "same-identity" is a copy of the archive "changed"
    And the archive "same-identity" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 2.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "changed"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    When I GET "/packs/acme"
    Then the field at "/version" is "1.0.0"
    When I PUT "/packs/acme" with the archive "same-identity"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"

    Examples: a change of the level, and of the pack_id
      | pack_id                    | level   |
      | 01M4P01D5TWB5FKZGNNQG3943R | project |
      | 01M4P01D5TWB5FKZGNNQG3943S | org     |

  # bound by: WP16 (ADR-0065 D3: the built-in level is read-only)
  Scenario: A pack declaring the built-in level is refused as a conflict
    Given the archive "built-in" is a copy of the archive "v1"
    And the archive "built-in" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: built-in
      """
    When I PUT "/packs/acme" with the archive "built-in"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "registration_conflict"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # bound by: WP16 (PR-PACK-REGISTER-003 reversed: a second registration of a pack id replaces, ADR-0065 D1)
   @promise:PR-PACK-REGISTER-003
  Scenario: A second registration of a pack id replaces the pack, and another pack may not take its pack_id
    Given the archive "v2" is a copy of the archive "v1"
    And the archive "v2" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 2.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "twin" is a copy of the archive "v1"
    And the archive "twin" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: twin
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/acme" with the archive "v2"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"
    When I PUT "/packs/twin" with the archive "twin"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 409
    And the field at "/problem/code" is "registration_conflict"
    When I GET "/packs/acme"
    Then the field at "/version" is "2.0.0"

  # bound by: WP16 (FR-037, IC-18: the guard answers 401 before routing, so no registration rule is shown)
  Scenario: A registration without the worktree token is refused with the guard's 401, and one with it is accepted
    When I PUT "/packs/acme" with the archive "v1" without the worktree token
    Then the response status is 401
    And the problem has no code
    And the response has no "Location" header
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202

  # bound by: WP16 (FR-037, IC-18: charter.registration.enabled defaults to false)
  Scenario: Registration switched off answers 503, and nothing is stored
    Given registration is switched off
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I GET "/packs/acme"
    Then the response status is 404
    Given registration is switched on
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202

  # bound by: WP16 (spec edge case, ADR-0065 D8: two registrations run and eight wait; the next is refused with Retry-After)
  Scenario: A full registration queue answers 503 with Retry-After, and nothing is stored
    Given the registration jobs are held before they run
    And the registration queue is full
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When I GET "/packs/acme"
    Then the response status is 404
    When I release the held registration jobs
    And I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202

  # bound by: WP16 (NFR-004: the body limit holds before the body is read; the media type must match the bytes)
  Scenario: A body over the limit, or in another media type, is refused before a registration exists
    When I PUT "/packs/acme" with an archive larger than the body limit
    Then the response status is 413
    And the problem has no code
    When I PUT "/packs/acme" with the archive "v1" as "text/plain"
    Then the response status is 415
    And the problem has no code
    When I PUT "/packs/acme" with the gzip archive "v1" as "application/zip"
    Then the response status is 415
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202

  # bound by: WP16 (T079, decision DM-01M6TV6VDNHNCVWRHHC74WATV0: the packId grammar is checked before the body is read)
  Scenario Outline: A packId outside the grammar is a 400 before the body is read, and one inside it is accepted
    When I PUT "/packs/<packId>" with the archive "v1"
    Then the response status is 400
    And the problem has no code
    And the field at "/detail" is "The path parameter packId is \"<shown>\", which does not match ^[a-z0-9][a-z0-9-]{0,62}$."
    And the response has no "Location" header
    When I PUT "/packs/acme" with the archive "v1"
    Then the response status is 202

    Examples: ids the grammar ^[a-z0-9][a-z0-9-]{0,62}$ refuses
      | packId | shown |
      | Acme | Acme |
      | -acme | -acme |
      | acme_x | acme_x |
      | acme.x | acme.x |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa… |

  # bound by: WP16 (ADR-0065 D12, WP12 and WP14 hand-offs: a write that meets a busy registry is told to retry, not answered 500)
  Scenario: A per-artifact write that meets a busy registry answers 503 with Retry-After, and succeeds once it is free
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the ETag
    And the pack registry is held by another write
    And I PUT "/packs/acme/artifacts/directive/C" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "title": "C written while the registry is busy", "intent": "A write that waits", "enforcement": "required"}
      """
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When I GET "/packs/acme/artifacts/directive/C"
    Then the response status is 404
    When the held pack registry is released
    And I PUT "/packs/acme/artifacts/directive/C" with the remembered ETag and the body:
      """
      {"schemaVersion": "1.0", "title": "C written once the registry is free", "intent": "A write that waits", "enforcement": "required"}
      """
    Then the response status is 201

  # bound by: WP16 (review cycle 1, issue 2: an activation write takes the registry lock, so it can answer 503 as a registration can)
  Scenario: An activation write that meets a busy registry answers 503 with Retry-After, and succeeds once it is free
    When the pack registry is held by another write
    And I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 503
    And the problem has no code
    And the response header "Retry-After" appears once
    When the held pack registry is released
    And I POST "/activations" as JSON with the body:
      """
      {"schemaVersion": 2}
      """
    Then the response status is 201

  # bound by: WP17 (US1 AS7, https_archive; PR-PACK-REGISTER-001)
  @promise:PR-PACK-REGISTER-001
  Scenario: A pack fetched from an allowlisted https archive is registered and its source recorded
    Given the https host "packs.example.org" is allowed
    And the https host "packs.example.org" serves "https://packs.example.org/acme.tar.gz" with the archive "v1"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "https_archive", "packId": "acme", "url": "https://packs.example.org/acme.tar.gz"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "created"
    And the field at "/result/source/kind" is "https_archive"
    And the field at "/result/source/url" is "https://packs.example.org/acme.tar.gz"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200

  # bound by: WP17 (https_archive refusals, each with the control of the same source)
  Scenario Outline: A source the service refuses ends the registration refused with pack_source_refused
    Given the https host "packs.example.org" is allowed
    And the https host "packs.example.org" serves "https://packs.example.org/acme.tar.gz" with the archive "v1"
    When I POST "/packs" as JSON with the body:
      """
      <body>
      """
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "pack_source_refused"
    When I GET "/packs/acme"
    Then the response status is 404
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "https_archive", "packId": "acme", "url": "https://packs.example.org/acme.tar.gz"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: the refusals of the source guard, a host off the allowlist, a digest mismatch, another target, a foreign member
      | body                                                                                                                                                                           |
      | {"kind": "https_archive", "packId": "acme", "url": "https://elsewhere.example.net/acme.tar.gz"}                                                                                |
      | {"kind": "https_archive", "packId": "acme", "url": "https://packs.example.org/acme.tar.gz", "sha256": "00000000000000000000000000000000000000000000000000000000000000ff"}   |
      | {"kind": "https_archive", "packId": "other", "url": "https://packs.example.org/acme.tar.gz"}                                                                                   |
      | {"kind": "https_archive", "packId": "acme", "url": "https://packs.example.org/acme.tar.gz", "path": "packs/acme"}                                                              |

  # bound by: WP17 (filesystem source; PR-PACK-REGISTER-001)
  @promise:PR-PACK-REGISTER-001
  Scenario: A pack read from a path under the configured root is registered, and a path that is not there is refused
    Given the filesystem source root holds the directory "packs/acme" with the archive "v1"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/source/kind" is "filesystem"
    And the field at "/result/source/path" is "packs/acme"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/missing"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_source_refused"

  # bound by: WP17 (a path that leaves the root breaks the PackSource schema)
  Scenario: A path with a parent segment is a malformed source, and a path under the root is not
    Given the filesystem source root holds the directory "packs/acme" with the archive "v1"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "../outside"}
      """
    Then the response status is 400
    And the problem has no code
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202

  # bound by: WP17 (a source kind that is switched off)
  Scenario: A source kind that is switched off answers 503 pack_source_unavailable
    Given the filesystem source root holds the directory "packs/acme" with the archive "v1"
    And the filesystem source is switched off
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 503
    And the problem code is "pack_source_unavailable"
    When I GET "/packs/acme"
    Then the response status is 404
    Given the filesystem source is switched on
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "filesystem", "packId": "acme", "path": "packs/acme"}
      """
    Then the response status is 202

  # bound by: WP18 (git source; PR-PACK-REGISTER-001 and 002)
  @promise:PR-PACK-REGISTER-002
  Scenario: A git ref is pinned to the commit it points at, and the commit is the source's record
    Given the git host "git.example.org" is allowed
    And the git repository "https://git.example.org/acme/packs.git" holds the archive "v1" at the ref "refs/tags/v1.0.0"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "git", "packId": "acme", "url": "https://git.example.org/acme/packs.git", "ref": "refs/tags/v1.0.0"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/source/kind" is "git"
    And the field at "/result/source/ref" is "refs/tags/v1.0.0"
    And the field at "/result/source/commit" is the commit of the ref "refs/tags/v1.0.0"
    When I GET "/packs/acme/artifacts/directive/A"
    Then the response status is 200

  # bound by: WP18 (the one 428 of a registration: a branch ref with no commit and no If-Match)
  Scenario: A branch ref without a commit needs If-Match, and a commit that does not match the ref is refused
    Given the git host "git.example.org" is allowed
    And the git repository "https://git.example.org/acme/packs.git" holds the archive "v1" at the ref "refs/heads/main"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "git", "packId": "acme", "url": "https://git.example.org/acme/packs.git", "ref": "refs/heads/main"}
      """
    Then the response status is 428
    And the problem has no code
    When I POST "/packs" as JSON with the header "If-None-Match": "*" and the body:
      """
      {"kind": "git", "packId": "acme", "url": "https://git.example.org/acme/packs.git", "ref": "refs/heads/main"}
      """
    Then the response status is 202
    When I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I POST "/packs" as JSON with the body:
      """
      {"kind": "git", "packId": "acme", "url": "https://git.example.org/acme/packs.git", "ref": "refs/heads/main", "commit": "0000000000000000000000000000000000000000"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_source_refused"

  # bound by: WP18 (hostile refs are refused before a process starts)
  Scenario Outline: A hostile git ref is refused
    Given the git host "git.example.org" is allowed
    And the git repository "https://git.example.org/acme/packs.git" holds the archive "v1" at the ref "refs/heads/main"
    When I POST "/packs" as JSON with the header "If-None-Match": "*" and the body:
      """
      {"kind": "git", "packId": "acme", "url": "https://git.example.org/acme/packs.git", "ref": "<ref>"}
      """
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/code" is "pack_source_refused"
    When I GET "/packs/acme"
    Then the response status is 404

    Examples: refs outside the grammar
      | ref                   |
      | --upload-pack=evil    |
      | refs/heads/../secret  |
      | refs/heads/a@{1}      |
      | refs/heads/topic.lock |

  # bound by: WP16 (IC-15, FR-025: a composition code, naming the packs)
  Scenario: A registration that leaves a closure unable to compose is refused naming the packs
    Given the archive "dependent" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: null
      name: dependent
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "acme-two" is a copy of the archive "v1"
    And the archive "acme-two" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 2.0.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "acme-minor" is a copy of the archive "v1"
    And the archive "acme-minor" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.5.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I PUT "/packs/dependent" with the archive "dependent"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/acme" with the archive "acme-two"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "pack_dependency_out_of_range"
    And the field at "/problem/packs/0/packId" is "dependent"
    When I GET "/packs/acme"
    Then the field at "/version" is "1.0.0"
    When I PUT "/packs/acme" with the archive "acme-minor"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/outcome" is "replaced"

  # bound by: WP16 (IC-15, A6: an overrides edge onto a target that is not open; the control overrides an open one)
  # The lock is a built-in one, as US1 AS8 says, and the refusal names both packs: the pack that breaks the rule
  # first, then the pack that holds the lock.
  Scenario: An overrides edge onto a locked artifact is refused, and onto an open one is accepted
    Given the built-in pack "main" with the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943W
      pack_version: 1.0.0
      parent_pack: null
      name: main-pack
      pack_level: built-in
      mutability_policy: policy.yaml
      """
    And the built-in pack "main" with the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:LK: locked
      """
    And the built-in pack "main" with the file "directives/LK.directive.yaml":
      """
      schema_version: '1.0'
      id: LK
      title: LK
      intent: The directive main locks
      enforcement: required
      """
    And the built-in pack "main" with the file "directives/OP.directive.yaml":
      """
      schema_version: '1.0'
      id: OP
      title: OP
      intent: The directive main leaves open
      enforcement: required
      """
    And the archive "over-locked" is a copy of the archive "v1"
    And the archive "over-locked" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: 01M4P01D5TWB5FKZGNNQG3943W
      name: acme
      pack_level: project
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943W
          range: '>=1.0,<2'
      """
    And the archive "over-locked" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:A', target: 'main-pack::directive:LK', relation: overrides}
      """
    And the archive "over-open" is a copy of the archive "over-locked"
    And the archive "over-open" holds the file "overrides.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:A', target: 'main-pack::directive:OP', relation: overrides}
      """
    When I PUT "/packs/acme" with the archive "over-locked"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "override_not_permitted"
    And the field at "/problem/packs/0/packId" is "acme"
    And the field at "/problem/packs/1/packId" is "main"
    And the field at "/problem/packs/1/name" is "main-pack"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "over-open"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

  # bound by: WP16 (IC-15, A8, A11, A12, C28: each relation rule, each with the control that composes)
  Scenario Outline: A registration whose relations break a rule is refused with that rule's code
    Given the archive "relations" is a copy of the archive "v1"
    And the archive "relations" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: [<edges>]
      """
    And the archive "sound" is a copy of the archive "v1"
    And the archive "sound" holds the file "relations.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges: [{source: 'directive:B', target: 'directive:A', relation: enhances}]
      """
    When I PUT "/packs/acme" with the archive "relations"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "<code>"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: one rule each, an enhances cycle, an enhance and an override of one target, an overrides cycle ; and a bare self-loop, which the load refuses first as invalid (the qualified self-loop is the next scenario)
      | code                  | edges                                                                                                                                       |
      | enhances_cycle        | {source: 'directive:A', target: 'directive:B', relation: enhances}, {source: 'directive:B', target: 'directive:A', relation: enhances}     |
      | conflicting_relations | {source: 'directive:B', target: 'directive:A', relation: enhances}, {source: 'directive:B', target: 'directive:A', relation: overrides}    |
      | validation_failed     | {source: 'directive:A', target: 'directive:A', relation: overrides}                                                                         |
      | validation_failed     | {source: 'directive:A', target: 'directive:A', relation: enhances}                                                                          |
      | override_cycle        | {source: 'directive:A', target: 'directive:B', relation: overrides}, {source: 'directive:B', target: 'directive:A', relation: overrides}   |

  # bound by: ADR-0068 (#457): the fallback profile a pack declares is held in its closure, locked and role-neutral
  Scenario Outline: A fallback profile that breaks ADR-0068 is refused with its code, and a locked generic one is accepted
    Given the archive "fallback" is a copy of the archive "v1"
    And the archive "fallback" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      mutability_policy: policy.yaml
      fallback_profile: <named>
      """
    And the archive "fallback" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        agent_profile:generic-agent: <level>
      """
    And the archive "fallback" holds the file "agent_profiles/generic-agent.agent.yaml":
      """
      profile-id: generic-agent
      name: Generic agent
      <roles>
      <extra>
      purpose: Serve when no profile fits the asked role.
      specialization:
        primary-focus: Any work
      """
    And the archive "sound" is a copy of the archive "fallback"
    And the archive "sound" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      mutability_policy: policy.yaml
      fallback_profile: generic-agent
      """
    And the archive "sound" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        agent_profile:generic-agent: locked
      """
    And the archive "sound" holds the file "agent_profiles/generic-agent.agent.yaml":
      """
      profile-id: generic-agent
      name: Generic agent
      role: generic
      purpose: Serve when no profile fits the asked role.
      specialization:
        primary-focus: Any work
      """
    When I PUT "/packs/acme" with the archive "fallback"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "<code>"
    When I GET "/packs/acme"
    Then the response status is 404
    When I PUT "/packs/acme" with the archive "sound"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples:
      | code                              | named         | level      | roles             | extra                   |
      | fallback_profile_unknown          | nobody        | locked     | role: generic     | description: Any work.  |
      | fallback_profile_not_locked       | generic-agent | extendable | role: generic     | description: Any work.  |
      | fallback_profile_not_role_neutral | generic-agent | locked     | role: implementer | description: Any work.  |
      | fallback_profile_not_role_neutral | generic-agent | locked     | role: generic     | alt-roles: [reviewer]   |

  # bound by: WP16 (IC-15, A8, A12 and the WP15 hand-off: an edge whose two ends name one artifact of another pack)
  # A self-loop the load can see in one pack is refused first as invalid (the outline above); an edge whose ends are qualified
  # to one artifact of a pack the writer builds on is the shadow the impact check refuses, as the rule of its relation.
  Scenario Outline: An edge whose two ends name one artifact of another pack is refused as that rule, and an edge to the writer's own artifact is accepted
    Given the archive "acme-open" is a copy of the archive "v1"
    And the archive "acme-open" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.0.0
      parent_pack: null
      name: acme
      pack_level: org
      mutability_policy: policy.yaml
      """
    And the archive "acme-open" holds the file "policy.yaml":
      """
      schema_version: 1
      artifacts:
        directive:A: open
      """
    And the archive "derived" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943P
      pack_version: 1.0.0
      parent_pack: null
      name: derived
      pack_level: org
      depends_on:
        - pack_id: 01M4P01D5TWB5FKZGNNQG3943R
          range: '>=1.0,<2'
      """
    And the archive "derived" holds the file "directives/D.directive.yaml":
      """
      schema_version: '1.0'
      id: D
      title: D
      intent: The directive derived holds
      enforcement: required
      """
    And the archive "derived-shadow" is a copy of the archive "derived"
    And the archive "derived-shadow" holds the file "shadow.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'acme::directive:A', target: 'acme::directive:A', relation: <relation>}
      """
    And the archive "derived-own" is a copy of the archive "derived"
    And the archive "derived-own" holds the file "shadow.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'acme::directive:A', target: 'directive:D', relation: <relation>}
      """
    When I PUT "/packs/acme" with the archive "acme-open"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I PUT "/packs/derived" with the archive "derived-shadow"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "<code>"
    When I GET "/packs/derived"
    Then the response status is 404
    When I PUT "/packs/derived" with the archive "derived-own"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: the shadow of each relation, and the control that points at the writer's own artifact
      | relation  | code           |
      | overrides | self_override  |
      | enhances  | enhances_cycle |

  # bound by: WP16 (plan delta squad finding 3: a reserved shard is refused, a stamped one is accepted and replaces)
  Scenario: A registered.graph.yaml without the registration stamp is refused, and a stamped one replaces the registered edges
    Given the archive "unstamped" is a copy of the archive "v1"
    And the archive "unstamped" holds the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      nodes: []
      edges:
      - {source: 'directive:B', target: 'directive:A', relation: suggests}
      """
    And the archive "stamped" is a copy of the archive "v1"
    And the archive "stamped" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "stamped" holds the file "registered.graph.yaml":
      """
      schema_version: '1.0'
      generated_at: STATIC
      generated_by: charter-registration
      nodes: []
      edges: []
      """
    When I PUT "/packs/acme" with the archive "unstamped"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/detail" contains "registered.graph.yaml"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    And I GET "/packs/acme"
    And I remember the ETag
    And I PUT "/packs/acme/relationships" with the remembered ETag and the body:
      """
      {"from": "directive:B", "to": "directive:A", "type": "suggests"}
      """
    Then the response status is 201
    When I PUT "/packs/acme" with the archive "stamped"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    When I GET "/packs/acme/artifacts/directive/B/references"
    Then the response status is 200
    And the field at "/items/0" is absent
    When I GET "/packs/acme/artifacts/directive/B"
    Then the response status is 200

  # bound by: WP16 (finding 3: a field ADR-0064 removed is an unknown field; the control is the directive without it)
  Scenario Outline: A directive with a field that was removed is refused as invalid, never a 500
    Given the archive "removed-field" is a copy of the archive "v1"
    And the archive "removed-field" holds the file "directives/A.directive.yaml":
      """
      schema_version: '1.0'
      id: A
      title: A with a removed field
      intent: The directive that stays
      enforcement: required
      <field>: ['directive:B']
      """
    When I PUT "/packs/acme" with the archive "removed-field"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/detail" contains "<field>"
    When I PUT "/packs/acme" with the archive "v1"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: the three removed members of a directive
      | field         |
      | references    |
      | procedures    |
      | relationships |

  # bound by: WP16 (finding 3: a preset with activated_kinds, and a preset item outside the id grammar)
  Scenario Outline: A preset the pack model no longer reads is refused as invalid, and a sound preset is accepted
    Given the archive "preset" is a copy of the archive "v1"
    And the archive "preset" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A preset of the team
      <key>: <value>
      """
    And the archive "sound-preset" is a copy of the archive "v1"
    And the archive "sound-preset" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A preset of the team
      activated_directives:
        - id: A
      """
    When I PUT "/packs/acme" with the archive "preset"
    And I poll the registration until it ends
    Then the field at "/state" is "refused"
    And the field at "/problem/status" is 422
    And the field at "/problem/code" is "validation_failed"
    And the field at "/problem/violations/0/detail" contains "presets/team.yaml"
    When I PUT "/packs/acme" with the archive "sound-preset"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"

    Examples: what the load refuses, a removed key, a URN spelling and a space in an item id
      | key                  | value                  |
      | activated_kinds      | [directive]            |
      | activated_directives | [{id: 'directive:A'}]  |
      | activated_directives | [{id: 'A B'}]          |

  # bound by: WP16 (FR-027: the notices of a registration that changes the floor)
  Scenario: A registration that removes a must-have item reports it in the notices
    Given the archive "floor" is a copy of the archive "v1"
    And the archive "floor" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A governing preset
      activated_directives:
        - id: A
          must_have: true
      """
    And the archive "no-floor" is a copy of the archive "floor"
    And the archive "no-floor" holds the file "pack.yaml":
      """
      pack_id: 01M4P01D5TWB5FKZGNNQG3943R
      pack_version: 1.1.0
      parent_pack: null
      name: acme
      pack_level: org
      """
    And the archive "no-floor" holds the file "presets/team.yaml":
      """
      schema_version: '1.0'
      id: team
      description: A preset that requires nothing
      activated_directives:
        - id: A
      """
    When I PUT "/packs/acme" with the archive "floor"
    And I poll the registration until it ends
    Then the field at "/result/notices/floorAdded/0/artifactId" is "A"
    And the field at "/result/notices/floorRemoved" holds exactly 0 item(s)
    When I PUT "/packs/acme" with the archive "no-floor"
    And I poll the registration until it ends
    Then the field at "/state" is "succeeded"
    And the field at "/result/notices/floorRemoved/0/artifactId" is "A"
    And the field at "/result/notices/floorAdded" holds exactly 0 item(s)
