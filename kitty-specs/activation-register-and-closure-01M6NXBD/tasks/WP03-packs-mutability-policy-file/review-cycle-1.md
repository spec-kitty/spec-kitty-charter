---
affected_files: []
cycle_number: 1
mission_slug: activation-register-and-closure-01M6NXBD
reproduction_command:
reviewed_at: '2026-11-05T20:09:45Z'
reviewer_agent: claude:claude-opus-5-5:reviewer-renata
wp_id: WP03
---

# WP03 review: changes requested (reviewer-renata)

## Blocking

**Issue 1: a whole-pack write silently drops a declared policy file. The stored pack it leaves is unreadable (FR-022, ADR-0012 "tighten, never loosen").**

Reproduced with a scratch test, since deleted:
- Pack `qa` has `pack.yaml` with `mutability_policy: policy.yaml` and `policy.yaml` with `artifacts: {directive:X: locked}`.
- `WholePackStore.load(src)` returns `unmodelled=[policy.yaml]`.
- `WholePackStore.write(loaded, target)` succeeds without a refusal.
- The written `pack.yaml` still names `mutability_policy: policy.yaml`, because this WP's `YamlSerializationStrategy` edit writes the key.
- `target/policy.yaml` does not exist.
- `DomainRepositoryService.policyOf(qa)` and `compositionFacts(qa)` on the target both refuse `PACK_UNREADABLE: qa names mutability_policy policy.yaml, which is missing`.

The write accepts a pack with a lock and stores it without the lock, which leaves an unreadable pack. It fails closed, but the write still reports success while producing a broken pack. ADR-0063 Consequences/Negative lists this as a follow-up. It is a defect in this WP's scope, because this WP made the descriptor write carry the key without the file.

Fix (pick one, then add a test):
- (a, preferred) Model `policy.yaml` in `WholePackStore`. In `load`, when `descriptor.paths().mutabilityPolicy()` is present, read the file, validate it through `PolicyYaml`, and add it to `modelled`, so it is no longer listed as unmodelled. Carry it on `LoadedPack`, either as the parsed `MutabilityPolicySource` plus a writer, or as the verbatim bytes after validation. In `write`, put it right after `pack.yaml`. A named file that is missing or invalid at load should be a violation on the aggregate, so `write` refuses `VALIDATION_FAILED` and drops nothing.
- (b, minimum) In `write`, refuse with `VALIDATION_FAILED` when the descriptor names a policy file the loaded pack does not carry. The message should name the file. Do not store a pack whose own descriptor makes it unreadable.

Test: add a case to `WholePackStoreTest`, or a `PolicyRoundTripTest` beside `PresetRoundTripTest` and `PackDocumentRoundTripTest`. Load a pack whose descriptor names a `policy.yaml` that locks `directive:X`, then write it. With (a), assert that the target's `policyOf` equals the source's and that `unmodelled` no longer lists `policy.yaml`. With (b), assert the write is refused and no target exists. Add the paired control: a pack without the key round-trips unchanged. Update the ADR-0063 Negative bullet to match.

## Verified, no change needed

- DoD 1: the schema is closed. Reserved keys `roles` and `pins` are rejected by name, along with unknown keys, bad levels, non-URN keys, pack-qualified keys, non-artifact kinds, `schema_version` 2 or '1', and a missing version. `mutability_policy` is `const: policy.yaml`. `file-kinds.yaml` has the `*/policy.yaml` rule. `PackSchemasTest` (26) and `VocabularyEqualityTest` pass.
- DoD 2: `PolicyYaml` works. `compositionFacts` fails closed on a missing file and on a schema-invalid file (`pins`). The offering refuses `pack_unreadable` naming `q` (real-stack test).
- DoD 3: `PerPackPolicyTest` covers both orders, tie order, kind default against a later open entry, and an empty closure.
- DoD 4: the gate runs over the real compose. The refusal names the locking pack Q by pack_id, whether the pack is addressed by name or by pack_id. With no policy anywhere, every operation is permitted. The registration scenarios are untouched, and `CharterContractSuite` is green.
- DoD 7: `PackFileSetTest` shows the policy is digested after the manifest, a pack without it keeps an exact list, and a level edit moves the fingerprint.
- Out-of-map edits are justified: `YamlSerializationStrategy` (descriptor round trip), `PackRoundTripTest` (DSO arity), and `PackSchemasTest`, `PackFileKindTest` and `SchemaKeywordCoverageTest` (counts and `patternProperties`).
- The contract fragment is present, with one `### Added`. ADR numbered 0062 is acceptable.
- Complexity by hand: `compositionFacts` is 26 lines, `authorize` about 15, and `policyOf` about 17. No new suppressions and no disabled tests.
- Gates: `:kernel:check` 2655/0 failures, `:packs:check -x openPacksRoundTrip` 2813/0 failures/4 skipped (pre-existing), deployable contract+hex 300/0, `:contracts:check -PcontractBaseline=none` green, `:packs:openPacksRoundTrip` green, `check_docs all` ok.
