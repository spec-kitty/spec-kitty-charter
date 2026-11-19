---
affected_files: []
cycle_number: 1
mission_slug: pack-delete-and-update-guard-01M7SANQ
reproduction_command:
reviewed_at: '2026-11-19T10:13:56Z'
reviewer_agent: reviewer-claude
wp_id: WP04
---

# WP04 review (independent reviewer, cycle 1): REJECT

Target: feat/517-pack-delete-and-update-guard @ 85d47e3. `./gradlew verifyAll` green.

## R4-1 (HIGH, needs an operator ruling): I3's remedy cannot be done
A pack whose own `pack.yaml` does not parse is refused `500 pack_unreadable`, "replace it with a readable pack
first". A throwaway probe test (not committed) on PackRegistrationServiceTest's Rig showed that the replace is
refused too:
  delete: CharterRefusal: The pack qa has no readable pack.yaml ... replace it with a readable pack first.
  put:    CharterRefusal: pack.yaml of qa is not valid YAML ...
(`checkIdentity` calls `view.descriptors().get(...)` on the stored pack.) Such a pack can be neither removed
nor replaced through the API, only by hand on disk. The spec's original edge case ("can still be deleted") was
reworded without an operator ruling. The options go to the operator:
(a) delete it with no dependents judged by pack_id. Still check the configured-name dependents (activations)
and refuse when any other pack is unreadable.
(b) keep it fail-closed, but make the replace accept it, and add a test for that.
(c) keep it as is, and correct the contract text and ADR-0081 to say that only an operator can remove it.
The contract text in `paths/packs_packId.yaml` and the 500 description must follow whichever is chosen.

## R4-2 (MEDIUM): T017 is incomplete
T017 asks for `PackCommitStoreTest` crash/repair and swap-failure cases for `removeWhole`. No test calls
`PackCommitStore.removeWhole`. `aStaleAsideCopyIsNotRestoredOverARemovedPack` covers the stale-aside hide only.
Missing cases: a failed rename leaves the pack whole (the `pack_unreadable` branch); a crash after the rename
and before `deleteTree`, where a listing's repair must not restore `.pack-discard-*`; and the history healed
before the rename.

## R4-3 (MEDIUM): the audit behavior has no test
NFR-002 / P7: no test shows that `DELETE ...?dryRun=true` is not entered (the `RegistrationAuditFilter`
skip), or that a dry run refused 401/403 by `WorktreeGuard` *is* entered (I1). `RegistrationPathsTest` covers
only `classify` and `isDryRun`.

## R4-4 (LOW): the M5 scenario does not exercise M5
"An activation that lists no packs does not block a removal" stores `{"schemaVersion": 2}`, which holds no
selection of qa's artifacts. The plan says the scenarios "include the M5 stale-selection case". Add a selection
of `directive:D1` and assert that the removal succeeds and the selection later reads stale.

## Judged sound
- I1: auditing a dry run the guard refuses is correct. The guard runs before authentication is established, so
  a dry run must not hide a probe. (The entry does not mark it as a dry run. That is acceptable.)
- P6: only judgements (409 pack_has_dependents, 422) come back as a 200 body; 503/404/428/409/412/500 keep
  their status. Matches `isJudgement`.
- P9: a linked pack is refused 409 registration_conflict, and nothing outside the writable root is touched.
  Sound. Note that a PUT on a linked entry answers 422 validation_failed (`requireWritableLocation`): the two
  codes differ. LOW, no change required.
- D7/D8: synchronous, If-Match required (`*` accepted), order 503 → 404 → 428 → (locks) 409 → 412 →
  dependents 409 → 422. Matches the contract.
- D9: the history is healed and kept.
