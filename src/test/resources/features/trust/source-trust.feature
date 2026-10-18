# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the trust promise "only sources I have authorized can enter,
#   and high-trust built-in content is not gated behind my allowlist". Promise-first.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 — DM-13):
#     POST /api/v1/packs with a git `source.kind` is evaluated against the operator allowlist
#     before any fetch. Refusal -> Problem { refusalCode: "SOURCE_NOT_ALLOWLISTED", detail }.
#     The allowlist is operator configuration (not a request field). Each entry is host-, org-, or
#     exact-repo-scoped (mixed per-entry granularity, DM-18; exact-repo = least privilege). The pack's
#     trust tier (built_in | open | consumer | private) is DERIVED from the authenticated caller
#     (DM-14), never client-asserted.
# - Decisions / spec exercised: DM-10 (explicit allowlist in config, v1; fail-closed; built-in/open
#   high-trust; floor = pin-to-rev + content-hash + offline cache). Signing is a reserved higher
#   tier (DM-10), NOT specified here.
# - The allowlist governs WHO may be a source; it does not prove authorship (tamper-evidence, not
#   tamper-proofing — research Q3). Integrity is a separate promise (see pin-and-verify.feature).

Feature: Only sources I have authorized can enter
  As an operator responsible for what doctrine my service will fetch and distribute
  I want unlisted git sources refused and high-trust built-in content let through
  So that third-party doctrine cannot enter my supply chain unless I allowed its source

  Background:
    Given the charter offering service is running with the operator configuration in force

  @promise:PR-PACK-TRUST-001
  Scenario: A git source whose host is not on the allowlist is refused
    Given the configured git allowlist permits only the host "github.com/acme"
    When I register a pack from a git source hosted at "github.com/stranger/doctrine"
    Then the registration is refused with "SOURCE_NOT_ALLOWLISTED"
    And no content is fetched from the refused source

  @promise:PR-PACK-TRUST-002
  Scenario: An allowlisted git source proceeds to registration
    Given the configured git allowlist permits the host "github.com/acme"
    When I register a pack from a git source hosted at "github.com/acme/acme-charter-packs"
    Then the source is accepted for registration
    And it is pinned to an exact revision before any constituent is materialized

  @promise:PR-PACK-TRUST-003
  Scenario: Built-in, as a high-trust tier, is not gated behind the operator allowlist
    Given the git allowlist does not name the built-in pack's source host
    When the built-in pack is registered as the high-trust "built_in" tier
    Then its registration is not refused for being off the allowlist
    And it is still pinned to an exact revision and recorded in the lockfile

  @promise:PR-PACK-TRUST-004
  Scenario: A git source that cannot be pinned to a revision is refused on the trust floor
    Given the configured git allowlist permits the host "github.com/acme"
    When I register a git source that resolves to no fixed commit
    Then the registration is refused on the pin-to-rev floor
    And nothing is registered against a floating reference

  @promise:PR-PACK-TRUST-005
  Scenario: An exact-repo allowlist entry admits only that repository, not its whole host
    Given the allowlist names the exact repository "github.com/acme/acme-charter-packs"
    And it does not allow the host "github.com/acme" as a whole
    When I register a pack from a different repository "github.com/acme/experimental-packs" on that same host
    Then the registration is refused with "SOURCE_NOT_ALLOWLISTED"
    And a pack from the exact repository "github.com/acme/acme-charter-packs" is admitted
