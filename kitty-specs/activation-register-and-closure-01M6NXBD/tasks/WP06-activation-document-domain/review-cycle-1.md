---
affected_files: []
cycle_number: 1
mission_slug: activation-register-and-closure-01M6NXBD
reproduction_command:
reviewed_at: '2026-11-05T20:28:42Z'
reviewer_agent: claude:claude-opus-5-5:reviewer-renata
wp_id: WP06
---

# WP06 review: changes requested (reviewer-renata)

## Blocking

**Issue 1: violation pointers do not point into the request body, so a 422 can name the wrong part, or two violations can share one pointer (FR-006, contract `RefusalViolation.pointer`).**

`contracts/_shared/schemas/RefusalViolation.yaml` and the kernel `RefusalViolation` Javadoc both say `pointer` is "an RFC 6901 JSON Pointer into the request body". `DocumentRules` instead indexes the canonical list (sorted and deduplicated) for `selected`, `deactivated` and `missionTypes`. For `packs` it indexes the list after the builder has dropped duplicates. Builder violations, by contrast, index the list as written. One response can mix the two schemes.

I reproduced this with a scratch test, since deleted. The snapshot holds pack `p` and `directive:D1`. The body sends `selected.directive: ["Z","D1"]` and `packs: [p, p, zeta]`. The violations returned:
- `/packs/1/packId duplicate_pack`, which is correct;
- `/packs/1/packId unknown_pack 'zeta'`, which is wrong: `zeta` is at `/packs/2`, and this pointer collides with the duplicate one;
- `/selected/directive/1 unknown_artifact 'Z'`, which is wrong: `Z` is at `/selected/directive/0`, and body `/1` is the valid `D1`.

The same shift happens to `version_not_held` after a duplicate pack, to every id that sorts or dedups before an unknown one, and to `/deactivated/<kind>/i`. `DocumentRulesTest.everyViolationIsCollectedInJsonPointerOrder` pins the wrong behaviour (`/selected/directive/0` and `/2` for `["Z","A","D1"]`, where the body has `Z` at 0 and `A` at 1).

Fix: make every pointer index the body as written. One way: have `ActivationDocument.Builder` keep the written lists (or a written-index map per member and kind) and carry them on `Built`. `DocumentRules.check(Built, …)` would then compute the unknown-id, unknown-pack and pin violations over the written lists. A repeated unknown id gets one violation per written position. For `check(ActivationDocument, …)` on a document with no written form (per-artifact writes, WP13), document which pointer that uses. Fix the Javadoc in `DocumentRules` and `ActivationDocument.Builder`, and fix the test. Add regression tests: an unsorted list with the unknown id written first; a duplicate pack followed by an unknown pack, with distinct pointers `/packs/1` and `/packs/2`; and a duplicate pack followed by a mismatched pin.

## Non-blocking (note for WP09/WP13)

- `RequiringPack.via` is always `[]`, because `ClosureSnapshot.requiredBy` carries no extends chain. The contract says `via` is the chain. That is acceptable for the snapshot shape this WP prompt defines, but record it as a known gap, or add the chain when WP09 builds the snapshot.

## Verified, no change needed

- JCS: I checked it independently against a Python UTF-16-sorted JCS on three vectors. They cover BMP vs supplementary keys (U+FF61 vs U+1F600, U+E000 vs U+1D11E, where code-point sort differs), escapes (`\t " \ /`, U+0001, U+001F, U+007F, U+2028), nesting, empty containers and ±2^53−1. The bytes are identical. The §3.2.2.2 vector, the lone-surrogate refusals and the 2^53 bound are pinned in `JcsTest`.
- The `sha256` of `{"schemaVersion":1}` was recomputed with `sha256sum` and matches `RevisionTest`.
- Kernel: `InForceWhenAbsent` = {NONE}. `git grep` shows only the enum and its test. Docs still saying "absent means all" belong to WP10.
- Absent vs `[]` is distinct in the canonical tree and both give `inForce` false. `packs` keeps written order. Locked wins over required. 409 is raised only with no 422.
- The domain is pure (grep clean). No new suppressions or disabled tests. Methods are under the limits by hand.
- Gates: `:kernel:check` 2675/0. `:activations:check` 81/0. `:packs:check -x openPacksRoundTrip` 2742/0/4 skipped (pre-existing). Deployable Hexagonal+CharterContractSuite 309/0.
