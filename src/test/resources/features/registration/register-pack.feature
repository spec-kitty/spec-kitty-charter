# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the pack-registration promise "a pack I register is pinned and
#   recorded, whatever kind of source it came from". Promise-first, authored to the
#   feature-and-scenario-authoring styleguide: outcomes not mechanism; one promise per scenario;
#   the consumer's language only.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 source of record — DM-13):
#     POST /api/v1/packs            body: a PackSource descriptor (discriminated on `source.kind`)
#                                   201 -> RegisteredPack { packId, source(with rev), lockfileEntry,
#                                                           _links: self|resolve|verify|distribute|source }
#                                   4xx -> Problem { refusalCode, detail }
#   The four `source.kind` discriminator values are the four v1 source kinds (DM-7). The
#   published-artifact locator is purl-style `pkg:charter/<id>@<version>` (DM-17).
# - Decisions / spec exercised: DM-7 (four source kinds incl. published-artifact),
#   DM-17 (purl-style published-artifact locator), DM-11 (two-tier lockfile: config entry
#   {source,rev,snapshot_sha256}), DM-10 floor (pin-to-rev), DM-15 (constituent-slug collisions are
#   GLOBAL — refused across packs), DM "fail-closed" cross-cut (SOURCE_CONFLICT, research §Cross-cutting).
# - Promises mapped to decision/spec in kitty-specs/.../bdd-scenarios.md.

Feature: A pack I register is pinned and recorded, whatever source it came from
  As an operator registering a pack with the charter offering service
  I want every source kind pinned to an exact revision and recorded in the lockfile
  So that what the service distributes later is exactly what I registered, reproducibly

  Background:
    Given the charter offering service is running with the operator configuration in force
    And the configured git allowlist permits the host "github.com/acme"

  @promise:PR-PACK-REGISTER-001
  Scenario Outline: Registering a pack from each supported source kind pins and records it
    When I register a pack from a <kind> source at "<locator>"
    Then the pack "<packId>" is registered
    And it is pinned to the exact revision "<pin>"
    And the lockfile records one entry for "<packId>" carrying its source, that revision, and a snapshot digest

    Examples: the four v1 source kinds (DM-7)
      | kind               | locator                                                    | packId                  | pin                                      |
      | git                | https://github.com/acme/acme-charter-packs#review-pack     | acme-review-pack        | a1b2c3d4e5f60718293a4b5c6d7e8f9012345678 |
      | filesystem         | file:fixtures/packs/local-dev-pack                         | local-dev-pack          | sha256:4f1d... (snapshot of the materialized tree) |
      | service-stored     | stored://acme-private-styleguide@1.0.0                     | acme-private-styleguide | stored-blob@1.0.0                        |
      | published-artifact | pkg:charter/community-linters@2.3.1                        | community-linters       | 2.3.1                                    |

  @promise:PR-PACK-REGISTER-002
  Scenario: A git source given as a moving branch is pinned to the commit it points at
    When I register a pack from a git source tracking the branch "main"
    Then the registration resolves the branch to the commit it currently points at
    And the lockfile records that commit revision, never the branch name

  @promise:PR-PACK-REGISTER-003
  Scenario: Two sources claiming the same pack id are refused, not last-write-wins
    Given a pack "acme-review-pack" is already registered from its git source
    When I register a different source that also claims the pack id "acme-review-pack"
    Then the registration is refused with "SOURCE_CONFLICT"
    And the pack id "acme-review-pack" still resolves to the originally registered source

  @promise:PR-PACK-REGISTER-004
  Scenario: A pack whose constituent slug collides with one already registered is refused
    Given a pack "acme-review-pack" is already registered and contributes the skill "spk-review-gate"
    When I register a pack "acme-audit-pack" that also contributes a skill named "spk-review-gate"
    Then the registration is refused with "SOURCE_CONFLICT"
    And neither pack's "spk-review-gate" is silently overwritten
