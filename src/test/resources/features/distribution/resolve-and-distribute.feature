# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the distribution promise "every tier pulls a registered pack
#   through one canonical path, offline-capable, and engine-gated". Promise-first.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 — DM-13):
#     GET /api/v1/packs/{packId}/distribution        -> DistributionResponse { packId, rev,
#                                                        tier, constituents[], _links: source|verify }
#       The requester tier (built_in|org|consumer|private) is DERIVED from the authenticated
#       caller, not a last-write client flag. One operation serves all tiers (DM-8 "one canonical
#       path"; tier is a parameter, not a branch).
#     Engine gate and reachability are enforced during the preceding resolution:
#       4xx -> Problem { refusalCode: "PACK_ENGINE_INCOMPATIBLE" | "SOURCE_UNREACHABLE" }.
# - Decisions / spec exercised: DM-8 (built-in = first catfooding consumer, one canonical path),
#   DM-9 (built-in bundles into the installed distribution for cold/offline), DM-10 floor (offline
#   cache), recorded default (engine gating at resolve, PACK_ENGINE_INCOMPATIBLE), DIRECTIVE_043
#   fail-closed (unreachable + uncached), DM "deferred" (private tenancy — @deferred placeholder).
# - requires_core example values track the engine at v4.0.0rc7 (CLAUDE.md): a pack requiring
#   ">=4.0.0rc7,<5" is compatible; one requiring ">=3.0.0,<4" (fixtures/packs/legacy-pack) is not.

Feature: Every tier pulls a registered pack through one canonical path
  As any consumer of the charter offering service
  I want built-in, org, consumer and private packs distributed by the same resolve-and-verify path
  So that no tier is a privileged special case and offline installs still work

  Background:
    Given the charter offering service is running with the operator configuration in force
    And the engine reports its core version as "4.0.0rc7"

  @promise:PR-PACK-DISTRIBUTE-001
  Scenario Outline: A registered pack is distributed to a consumer through the one canonical path
    Given the pack "<packId>" is registered and resolvable at the "<tier>" tier
    When a "<tier>"-tier consumer requests distribution of "<packId>"
    Then the pack is distributed through the same resolve-and-verify path used for every tier
    And the response reports the pinned revision it was verified against

    Examples: the four trust tiers (DM-8, one path; tier is a parameter)
      | tier     | packId                  |
      | built_in | built-in                |
      | org      | acme-review-pack        |
      | consumer | local-dev-pack          |
      | private  | acme-private-styleguide |

  @promise:PR-PACK-DISTRIBUTE-002
  Scenario: Built-in resolves through the same registration and distribution path as any other pack
    Given the built-in pack is registered from its pinned source like every other pack
    When the built-in pack is resolved and distributed
    Then it travels the same resolve-verify-distribute path, with no built-in-only shortcut
    And it is the first catfooding consumer of that path

  @promise:PR-PACK-DISTRIBUTE-003
  Scenario: A pack whose required core range excludes the engine is refused at resolve time
    Given the pack "legacy-pack" requires a core range of ">=3.0.0,<4"
    And the engine reports its core version as "4.0.0rc7"
    When I resolve the pack "legacy-pack"
    Then resolution is refused with "PACK_ENGINE_INCOMPATIBLE"
    And the refusal names the required range and the engine version
    And the refusal happens before any merge or activation work

  @promise:PR-PACK-DISTRIBUTE-004
  Scenario: A previously resolved pack resolves from cache with no network
    Given the pack "acme-review-pack" was resolved earlier and is in the offline cache
    And no network is available
    When I resolve the pack "acme-review-pack"
    Then the pack resolves from the cache without reaching the network
    And it is verified against the same pinned revision and digests

  @promise:PR-PACK-DISTRIBUTE-005
  Scenario: An uncached pack whose source is unreachable fails closed and legibly
    Given the pack "acme-review-pack" is not in the offline cache
    And its source cannot be reached
    When I resolve the pack "acme-review-pack"
    Then resolution fails closed with "SOURCE_UNREACHABLE"
    And the refusal names the source it could not reach

  @promise:PR-PACK-DISTRIBUTE-006 @deferred @deferred-reason:private-tenancy-is-storage-phase-policy
  Scenario: A private pack is visible only within its owning tenant boundary
    # DEFERRED (DM 01M5781ER9V60RN0KVBJH0EQRM): private-pack tenancy/visibility is storage-phase
    # policy, enforced at the storage port + API, not by directory presence. Captured here to mark
    # the boundary; it is NOT specified further in this mission and is expected to fail until the
    # storage-design decision lands.
    Given a private pack "acme-private-styleguide" owned by the tenant "acme"
    When a consumer outside the tenant "acme" requests distribution of "acme-private-styleguide"
    Then the request is refused and the private pack is not disclosed
