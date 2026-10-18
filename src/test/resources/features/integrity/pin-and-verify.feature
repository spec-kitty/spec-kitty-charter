# Maintainer notes (not part of the behavior description):
# - Consumer-outcome scenarios for the integrity promise "a resolved pack is bit-for-bit what the
#   lockfile pinned, or it does not resolve at all". Promise-first.
# - Public surface this shapes (OpenAPI/HATEOAS, v1 — DM-13):
#     POST /api/v1/packs/{packId}/resolutions  (resolve = fetch -> materialize -> verify -> hand on)
#       201 -> ResolvedPack { packId, rev, snapshotDigest, verified: true, _links: distribute|source }
#       4xx -> Problem { refusalCode: "INTEGRITY_FAILURE", detail naming the failing tier }
#   Verification is fail-closed: a resolution is never returned partially verified.
# - Two-tier integrity (DM-11): the config-level lockfile entry carries {source, rev,
#   snapshot_sha256}; each constituent carries a `content_hash` in the pack's pack-manifest.yaml.
#   Both tiers must verify. The snapshot recipe is `spec-kitty-snapshot-sha256` (reused, not
#   reinvented — research Q4 / DM-11).
# - Decisions / spec exercised: DM-11 (two-tier lockfile), DM-10 floor (content-hash verify),
#   DIRECTIVE_043 fail-closed (research §Cross-cutting).
# - Fixtures: fixtures/lockfile/charter.lock.yaml (good), fixtures/packs/acme-review-pack-tampered/
#   (a constituent whose bytes no longer match its content_hash), fixtures/lockfile/
#   charter.lock.stale.yaml (a recorded snapshot digest that no longer matches the tree).

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
    When I resolve the pack "acme-review-pack"
    Then the pack resolves successfully
    And the resolution reports both the tree digest and every constituent as verified

  @promise:PR-PACK-INTEGRITY-002
  Scenario: A constituent altered after pinning fails closed
    Given one constituent's bytes no longer match its recorded content hash
    When I resolve the pack "acme-review-pack"
    Then resolution fails closed with "INTEGRITY_FAILURE"
    And the failure names the constituent whose content hash did not match
    And no part of the pack is handed on for distribution

  @promise:PR-PACK-INTEGRITY-003
  Scenario: A materialized tree that does not match the pinned snapshot fails closed
    Given the materialized tree's snapshot digest does not match the lockfile entry
    When I resolve the pack "acme-review-pack"
    Then resolution fails closed with "INTEGRITY_FAILURE"
    And the failure names the recorded and the computed snapshot digests
    And no part of the pack is handed on for distribution
