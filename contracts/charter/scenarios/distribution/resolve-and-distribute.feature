# Decided, not built (#374; operator ruling 2026-11-05, decision 01M6NE5YW0Y4G60B62YTX08WXT):
# - ADR-0005 and ADR-0019 decide that the service registers and distributes packs. The operations these
#   scenarios need (POST /packs, POST /packs/{packId}/distributions, GET /packs/{packId}/resolution) are
#   listed under "Decided, not built" in docs/api/service-contract.md, with no inventory issue, and are
#   outside the cutover scope of ADR-0055 D1. They are not in ../../openapi.yaml.
# - So every scenario here is tagged @WIP: the suite excludes it (CharterContractSuite, @ExcludeTags("WIP")),
#   and its steps are not bound. Moved here from src/test/resources/features/ so the scenarios keep a home
#   next to the contract without running; no runner executed them there either.
# - When the surface is built, the scenarios change before @WIP comes off:
#   - the refusal codes take the contract's snake_case names (../../schemas/CharterRefusalCode.yaml), not
#     SOURCE_CONFLICT, SOURCE_NOT_ALLOWLISTED, INTEGRITY_FAILURE, PACK_ENGINE_INCOMPATIBLE or SOURCE_UNREACHABLE;
#   - the requester tiers (built_in, org, consumer, private) become pack levels (built-in, org, project;
#     ADR-0053 decision 1), plus an identity model the service does not have yet;
#   - a constituent collision is refused only at one level: across levels the same id is a layer override
#     (ADR-0053 decision 6, as amended).
# - Fixtures: the files the steps name (operator configuration, lockfiles, packs) stay in
#   src/test/resources/fixtures/, outside the contract tree, because a YAML file here is read as part of the
#   contract (README "Rules"). Their hashes and digests are illustrative.
# - Noted, not ruled: ADR-0048 (consequences, lines 195-197) has core pin and fetch the built-in pack itself,
#   against ADR-0005 decisions 2 and 7 (built-in registered and distributed through the service like any
#   other pack). This affects the wording of PR-PACK-TRUST-003 and PR-PACK-DISTRIBUTE-002 only.
#
# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the distribution promise "every tier pulls a registered pack
#   through one canonical path, offline-capable, and engine-gated". Promise-first.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 — DM-13, DM-16):
#     POST /api/v1/packs/{packId}/distributions   -> DistributionResponse { packId, rev,
#                                                     tier, constituents[], _links: source|resolution }
#       The distribute action is the WRITE that triggers resolve+verify internally, so the engine
#       gate, cache hit and reachability all surface as THIS action's result. One operation serves
#       all tiers (DM-8 "one canonical path"; tier is a parameter, not a branch). The requester tier
#       (built_in|org|consumer|private) is DERIVED from the authenticated caller (DM-14), never a
#       client-set field.
#       4xx -> Problem { refusalCode: "PACK_ENGINE_INCOMPATIBLE" | "SOURCE_UNREACHABLE" }.
#     GET /api/v1/packs/{packId}/resolution        -> read-only resolution-state projection (DM-16),
#       reflecting verified/rev/digests and the last refusal — NOT a resolve action resource.
# - Decisions / spec exercised: DM-8 (built-in = first catfooding consumer, one canonical path),
#   DM-9 (built-in bundles into the installed distribution for cold/offline), DM-10 floor (offline
#   cache), recorded default (engine gating at resolve, PACK_ENGINE_INCOMPATIBLE), DIRECTIVE_043
#   fail-closed (unreachable + uncached), DM "deferred" (private tenancy — @deferred placeholder).
# - requires_core example values track the engine at v4.0.0rc7 (CLAUDE.md): a pack requiring
#   ">=4.0.0rc7,<5" is compatible; one requiring ">=3.0.0,<4" (fixtures/packs/legacy-pack) is not.

@contract @WIP @distribution
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

  # Covered today, read side: built-in packs load through the same configured-pack compose as org and project
  # packs (offering.feature ordering; composition.feature "The single built-in tree still loads"). Not covered:
  # registration from a pinned source and distribution, which wait on this surface.
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
    When a consumer requests distribution of the pack "legacy-pack"
    Then the distribute request is refused with "PACK_ENGINE_INCOMPATIBLE"
    And the refusal names the required range and the engine version
    And the refusal happens before any merge or activation work
    And the read-only resolution-state projection reports the pack as not resolvable on this engine

  @promise:PR-PACK-DISTRIBUTE-004
  Scenario: A previously resolved pack resolves from cache with no network
    Given the pack "acme-review-pack" was resolved earlier and is in the offline cache
    And no network is available
    When a consumer requests distribution of the pack "acme-review-pack"
    Then the pack is distributed from the offline cache without reaching the network
    And it is verified against the same pinned revision and digests

  @promise:PR-PACK-DISTRIBUTE-005
  Scenario: An uncached pack whose source is unreachable fails closed and legibly
    Given the pack "acme-review-pack" is not in the offline cache
    And its source cannot be reached
    When a consumer requests distribution of the pack "acme-review-pack"
    Then the distribute request fails closed with "SOURCE_UNREACHABLE"
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
