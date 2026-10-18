# BDD scenarios — pack registration & distribution

**Mission**: `pack-registration-distribution-01M56M49`
**Author**: analyst-annie
**Date**: 2026-10-18
**Status**: Draft — public-surface design input. Layer A (contract) only; no Java, no step
definitions, no harness (Layer B is a later mission).

These promise-first scenarios are the **public-surface design input**: the v1 REST (OpenAPI-HATEOAS)
contract is authored *from* them (DM-13). They describe observable behavior at the public surface /
domain boundary in stakeholder-readable Given/When/Then; where a scenario implies a concrete REST
resource/operation, the maintainer-note block at the head of each feature states it explicitly so it
shapes the OpenAPI surface. GraphQL is planned-later (DM-13) and is deliberately not scenarioed.

## Feature index

| Feature file (`src/test/resources/features/`) | Behavior cluster | Implied REST surface |
|---|---|---|
| `registration/register-pack.feature` | Pin & record a pack from each source kind; fail closed on conflict. | `POST /api/v1/packs` |
| `trust/source-trust.feature` | Allowlist trust decisions + pin-to-rev floor. | `POST /api/v1/packs` (pre-fetch check) |
| `integrity/pin-and-verify.feature` | Two-tier integrity verification at resolve; fail closed. | `POST /api/v1/packs/{packId}/resolutions` |
| `distribution/resolve-and-distribute.feature` | One canonical path across tiers; engine gate; offline cache; catfooding. | `GET /api/v1/packs/{packId}/distribution` |

## Behavior → scenario map (task behaviors covered)

| Required behavior (mission brief) | Scenario(s) |
|---|---|
| Register from git URL (pinned rev) | `PR-PACK-REGISTER-001` (git row), `-002` (branch→commit pin) |
| Register from filesystem path | `PR-PACK-REGISTER-001` (filesystem row) |
| Register from service-stored (uploaded) | `PR-PACK-REGISTER-001` (service-stored row) |
| Register from published-artifact | `PR-PACK-REGISTER-001` (published-artifact row) |
| Pin + integrity: resolve succeeds when content matches the two-tier lockfile | `PR-PACK-INTEGRITY-001` |
| Fail-closed when a constituent hash mismatches | `PR-PACK-INTEGRITY-002` |
| Fail-closed when the snapshot digest mismatches | `PR-PACK-INTEGRITY-003` |
| Trust: git source not on allowlist is refused | `PR-PACK-TRUST-001` |
| Trust: allowlisted source proceeds (pin-to-rev floor) | `PR-PACK-TRUST-002`, `-004` (floor) |
| Trust: built-in/open high-trust bypass | `PR-PACK-TRUST-003` |
| Resolve + distribute across tiers (built-in, org, consumer, private) | `PR-PACK-DISTRIBUTE-001` (outline, four tiers) |
| Built-in as first catfooding consumer (same path) | `PR-PACK-DISTRIBUTE-002` |
| Engine/compat: `requires_core` excludes engine → refused at resolve | `PR-PACK-DISTRIBUTE-003` |
| Conflict: two sources same pack id / colliding slug → fail closed | `PR-PACK-REGISTER-003` (id), `-004` (slug) |
| Offline/cache: previously-resolved pack resolves with no network | `PR-PACK-DISTRIBUTE-004` |
| Unreachable + uncached → fail closed, legible | `PR-PACK-DISTRIBUTE-005` |
| Private-pack tenancy/retention (DEFERRED — ≤1 placeholder) | `PR-PACK-DISTRIBUTE-006` `@deferred` |

## Promise → authority (decision / spec) trace

| Promise | Consumer guarantee | Authority (DM / spec / research) | Refusal code (contract) |
|---|---|---|---|
| `PR-PACK-REGISTER-001` | Every one of the four source kinds is pinned and recorded. | DM-7 (four source kinds); DM-11 (lockfile entry `{source,rev,snapshot_sha256}`). | — |
| `PR-PACK-REGISTER-002` | A moving branch is pinned to the commit it points at, never stored floating. | DM-10 floor (pin-to-rev); research Q3. | — |
| `PR-PACK-REGISTER-003` | Two sources for one pack id fail closed, not last-write-wins. | Research §Cross-cutting (DIRECTIVE_043); architecture §3.1 `PackRegistrationService` (`SOURCE_CONFLICT`). | `SOURCE_CONFLICT` |
| `PR-PACK-REGISTER-004` | A colliding constituent slug fails closed. | Research §Cross-cutting (colliding constituent slug → refuse); architecture `RefusalCode`. | `SOURCE_CONFLICT` |
| `PR-PACK-TRUST-001` | An unlisted git source is refused, no fetch. | DM-10 (explicit allowlist, fail-closed). | `SOURCE_NOT_ALLOWLISTED` |
| `PR-PACK-TRUST-002` | An allowlisted source proceeds and is pinned before materialization. | DM-10; architecture §3.1 `PackResolver` (fetch→materialize→verify). | — |
| `PR-PACK-TRUST-003` | Built-in/open high-trust tier bypasses the allowlist. | DM-10 (built-in/open = high-trust tier). | — |
| `PR-PACK-TRUST-004` | A source that cannot be pinned is refused on the floor. | DM-10 floor (pin-to-rev, never a floating ref); research Q3. | (floor refusal) |
| `PR-PACK-INTEGRITY-001` | Content matching the two-tier lockfile resolves, both tiers verified. | DM-11 (two-tier: snapshot + per-constituent content_hash). | — |
| `PR-PACK-INTEGRITY-002` | A constituent altered after pinning fails closed, named. | DM-10 floor (content-hash verify); DM-11; DIRECTIVE_043. | `INTEGRITY_FAILURE` |
| `PR-PACK-INTEGRITY-003` | A tree not matching the pinned snapshot fails closed. | DM-11 (snapshot_sha256); DIRECTIVE_043. | `INTEGRITY_FAILURE` |
| `PR-PACK-DISTRIBUTE-001` | All four tiers pull through one canonical path. | DM-8 (one canonical path; tier is a parameter, not a branch). | — |
| `PR-PACK-DISTRIBUTE-002` | Built-in resolves through that same path (first catfooding consumer). | DM-8, DM-9 (staged migration; built-in catfoods the path). | — |
| `PR-PACK-DISTRIBUTE-003` | A pack whose `requires_core` excludes the engine is refused at resolve. | Recorded default (engine gating at resolve; sibling to `PACK_LEVEL_MISMATCH`); research Q6. | `PACK_ENGINE_INCOMPATIBLE` |
| `PR-PACK-DISTRIBUTE-004` | A previously-resolved pack resolves from cache, no network. | DM-10 floor (offline cache); DM-9 (cold/offline bundle). | — |
| `PR-PACK-DISTRIBUTE-005` | Uncached + unreachable fails closed, legibly. | DM-10 floor (fail-closed); research §Cross-cutting. | `SOURCE_UNREACHABLE` |
| `PR-PACK-DISTRIBUTE-006` `@deferred` | Private pack visible only within its tenant. | DM `01M5781ER9V60RN0KVBJH0EQRM` (DEFERRED — storage-phase policy). | (deferred) |

> Refusal-code strings reuse the architecture's `RefusalCode` vocabulary (architecture §3.0) and the
> recorded default's `PACK_ENGINE_INCOMPATIBLE`. Exact contract strings are a contract-naming detail
> to ratify alongside the OpenAPI document.

## Surface gaps / ambiguities flagged for operator + architecture reconciliation

See the hand-back report; recorded here for traceability:

1. **Resolve is a modeled public resource.** The scenarios treat *resolution* as a first-class
   operation that can succeed/fail independently of distribution (integrity, engine gate, cache,
   reachability all surface at resolve). architecture.md §3.1 names `PackResolver` as a **domain
   service**, not a public REST resource. The suite implies `POST /api/v1/packs/{packId}/resolutions`
   returning a `ResolvedPack` with a `verified` state and refusal `Problem`s. If resolution is to
   stay purely internal (surfaced only via distribution), scenarios `INTEGRITY-00x` and
   `DISTRIBUTE-003/004/005` need their observable surface re-pointed. **Needs an OpenAPI surface
   decision (relates to DP-6 `_links`).**
2. **Requester tier is derived, not client-asserted.** `DISTRIBUTE-001` assumes the service derives
   the caller's tier (from auth) rather than trusting a request parameter. architecture.md does not
   specify the authn/authz model. Flagged so the contract does not accidentally expose tier as a
   client-set field (which would break the fail-closed trust posture).
3. **Conflict scope for constituent slugs.** `REGISTER-004` assumes slug uniqueness is enforced
   *across* registered packs (a global constituent namespace), consistent with research's "colliding
   constituent slug → refuse". If collision is only refused *within* a pack, the cross-pack guarantee
   needs its own decision. **Needs confirmation of the constituent-namespace scope.**
4. **Allowlist granularity (host vs org vs repo).** `TRUST-001/002` use host-level allowlisting
   (`github.com/acme`). DM-10 says "allowlist of source URLs / orgs" without fixing granularity. The
   contract/config schema must settle host vs org vs exact-repo matching.
5. **Published-artifact locator form.** `REGISTER-001` uses a `pkg:`-style coordinate
   (`pkg:charter/community-linters@2.3.1`) for the fourth source kind; no prior art exists for its
   exact shape (research Q1 treated published-artifact as reserved; DM-7 promoted it to v1). The
   coordinate grammar is unspecified and needs a contract decision.
