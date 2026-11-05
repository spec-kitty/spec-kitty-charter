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
#
# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the integrity promise "a resolved pack is bit-for-bit what the
#   lockfile pinned, or it does not resolve at all". Promise-first.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 — DM-13, DM-16):
#   Resolution is a READ-ONLY PROJECTION, not a public action resource (DM-16). There is no
#   "POST a resolution" operation. Resolve+verify runs internally when the pack is DISTRIBUTED
#   (the write). Integrity outcomes therefore surface two ways:
#     POST /api/v1/packs/{packId}/distributions   (the distribute action; triggers resolve+verify)
#       4xx -> Problem { refusalCode: "INTEGRITY_FAILURE", detail naming the failing tier }
#     GET  /api/v1/packs/{packId}/resolution        (read-only resolution-state projection)
#       200 -> ResolutionState { packId, rev, snapshotDigest, verified, lastRefusal? }
#   Verification is fail-closed: distribution never hands on a partially verified pack.
# - Two-tier integrity (DM-11): the config-level lockfile entry carries {source, rev,
#   snapshot_sha256}; each constituent carries a `content_hash` in the pack's pack-manifest.yaml.
#   Both tiers must verify. The snapshot recipe is `spec-kitty-snapshot-sha256` (reused, not
#   reinvented — research Q4 / DM-11).
# - Decisions / spec exercised: DM-11 (two-tier lockfile), DM-10 floor (content-hash verify),
#   DIRECTIVE_043 fail-closed (research §Cross-cutting).
# - Fixtures: fixtures/lockfile/charter.lock.yaml (good), fixtures/packs/acme-review-pack-tampered/
#   (a constituent whose bytes no longer match its content_hash), fixtures/lockfile/
#   charter.lock.stale.yaml (a recorded snapshot digest that no longer matches the tree).

@contract @WIP @integrity
Feature: A resolved pack is exactly what the lockfile pinned, or it does not resolve
  As a consumer resolving a registered pack
  I want resolution to verify both the whole-tree digest and every constituent hash
  So that I never receive doctrine that was altered after it was pinned

  Background:
    Given the pack "acme-review-pack" is registered and recorded in the lockfile
    And its lockfile entry carries a source, a pinned revision, and a snapshot digest
    And each of its constituents carries a content hash in its pack manifest

  @promise:PR-PACK-INTEGRITY-001
  Scenario: A pack whose content matches the lockfile resolves, both tiers verified
    Given the materialized tree matches the recorded snapshot digest
    And every constituent's bytes match its recorded content hash
    When a consumer requests distribution of the pack "acme-review-pack"
    Then the pack is distributed
    And the read-only resolution-state projection reports both the tree digest and every constituent as verified

  @promise:PR-PACK-INTEGRITY-002
  Scenario: A constituent altered after pinning fails closed
    Given one constituent's bytes no longer match its recorded content hash
    When a consumer requests distribution of the pack "acme-review-pack"
    Then the distribute request fails closed with "INTEGRITY_FAILURE"
    And the failure names the constituent whose content hash did not match
    And no part of the pack is handed on to the consumer
    And the read-only resolution-state projection reports the pack as not verified

  @promise:PR-PACK-INTEGRITY-003
  Scenario: A materialized tree that does not match the pinned snapshot fails closed
    Given the materialized tree's snapshot digest does not match the lockfile entry
    When a consumer requests distribution of the pack "acme-review-pack"
    Then the distribute request fails closed with "INTEGRITY_FAILURE"
    And the failure names the recorded and the computed snapshot digests
    And no part of the pack is handed on to the consumer
    And the read-only resolution-state projection reports the pack as not verified
