---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T11:01:47Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP12
---

# WP12 review, cycle 1 (reviewer-renata): changes requested

Gate re-run in lane-j (`--rerun-tasks`): kernel 2769, packs 2968 (4 skipped, pre-existing), activations 565, deployable 1017; 0 failures. Checkstyle/PMD/SpotBugs/Spotless/HexagonalArchitectureTest green; no suppressions or @Disabled added. MixedReadConcurrencyTest: 9.0 s / 7.0 s / 2.1 s per case against @Timeout(30).

The production code (RegistryWriteLock, PackWriteGeneration, ConsistentRead, ClosureQueries retry, lock in CommitStorePackWriteStore) is sound; see "verified" below. One blocking finding is in the T057 stress test.

## Blocking

**Issue 1: MixedReadConcurrencyTest cannot fail on a mixed read (T057, research R-lock "with marker files", NFR-006 "no read answers a mix").**
- The only "mixed" oracle is `complete.contains(view.fingerprint())`. `view.fingerprint()` is the first digest that `ConsistentRead` itself verified equal to the second digest, so the check is circular: it can only fail if ConsistentRead's own digest comparison is broken.
- Versions A/B/C differ only in directive D's `title`/`intent`. `ClosureView` carries no artifact content (held URNs, presets, locks, reach), so a view loaded half from A and half from B is indistinguishable from either.
- No marker files are used, although T057, plan.md (concurrency tests) and research R-lock all require them.
- Consequence: removing the post-load `moved(written)` checks from `ConsistentRead` (the A-B-A protection, the reason the seqlock exists) would leave this test green. Its red run only failed on `PACK_UNREADABLE` (the missing-directory instant), which matches that reading. The A-B-A logic is pinned only by the ConsistentReadTest unit tests.
- Fix: make each version distinguishable in what the reader answers, and assert the answer's content matches the version its fingerprint belongs to. Options:
  - (a) Read through a content-bearing verified read (e.g. the directive read service via the same `ConsistentRead`), and assert that the title's version equals the version mapped from the verified fingerprint (`Map<fingerprint, version>` from `prime()`).
  - (b) Use real marker files: each version writes a marker that both the digest and the read can see (e.g. through the SlowSwap hook staging `marker-<V>`, or a second artifact per version), and assert that every marker/content element of a view belongs to one version.
- Then show red-first: with the post-load generation check removed locally, the new oracle must report a mixed read (or at least the A-B-A case `aSnapshot...` variant must), and say so in the commit body.
- Keep the existing overlap > 0, stale ≤ 10% and retries-end-in-a-version assertions.

## Verified (no change needed)
- **Lock order:** registry lock, then resolve, then the pack lock. The resolve listing runs under the registry lock, so no swap runs during it (RegistryLockOrderTest). The listing repair (StorageRootLocations.underLock) takes only pack locks, timed or tryLock, and never the registry lock. Readers and ConsistentRead take no lock. activations references none of PackWriteStore, RegistryWriteLock or PackWriteLocks. No cycle is possible.
- **Fairness:** `ReentrantLock(true)` with a timed `tryLock` honours fairness. The interrupt is mapped to busy.
- **Seqlock:** the Counter is `2*finished + (running>0)` under its monitor, so it is visible and monotonic. Overlapping swaps stay odd until the last one ends. A-B-A always moves the value. Overflow is not a concern. The post-check runs after the load. The first-digest failure while the generation moves maps to stale.
- **Retry:** 3 retries, each after an `awaitIdle(250 ms)`. The worst case is bounded (about 4 reads plus 750 ms), with no starvation beyond the specified `stale_input`.
- **T056 deviation (lock in the adapter):** acceptable, and stronger than per-service locking. Every PackWriteStore write goes through `exclusively`/`commit`, so all four per-kind services, both Toolguide lock scopes and `putPackRelationship` are covered. WholePackStore.write has no production caller today.
- **InterruptedCommitRepairTest edit:** legitimate. With one registry lock the two writers can no longer overlap in production. Separate locks keep pinning the StorageRootLocations "holding a pack lock ⇒ only tryLock" property, which the shared lock would otherwise turn into a hang.

## Non-blocking follow-ups (record for the named WPs; no change required in this cycle)
1. **Busy → 503:** `RegistryBusyException` is not mapped anywhere, so a busy per-artifact PUT answers `500 internal_error` today, while ADR-0065 D12 says "else 503". Map it to refused 503 with Retry-After when WP14/WP16 build the job and web mapping, covering the synchronous per-artifact writes too.
2. **Bracket width:** the generation brackets the whole `commitStore.commit` (copyTree, write, revision, swap), while the plan and D12 say "odd for the duration of the swap". It is correct, but the non-retrying per-kind reads (Tactic/Styleguide/Paradigm/Toolguide/ArtifactReadService) now answer stale_input (HTTP 500) during any pack's whole commit. Narrow it to `rename.swap` when WP14 touches PackCommitStore (replaceWhole/create), or give those reads the retry.
3. **Per-pack generation key:** it uses `pack.value()` as addressed, so a write addressed by configured name and one addressed by declared `pack_id` bump different counters. WP21's facts cache must key on the resolved entry or the canonical id (the registry counter is unaffected).
4. **Repair renames are not bracketed:** low risk. They only repair crash leftovers, the move is missing→present (no A-B-A), and the worst case is a transient `pack_unreadable` when two readers race one repair. Optionally bracket them later.
5. **Stress-test CI margin and PIT:** 9 s of 30 s locally, and the `stress` tag is not excluded anywhere yet (WP13 owns the PIT exclusion). Consider a longer margin or a separate stress task if CI is slow.
