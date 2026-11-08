---
affected_files: []
cycle_number: 2
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command: spec-kitty agent tasks move-task WP24 --to approved --mission layer-merge-and-effective-reads-01M6R0J6 --agent claude:opus:reviewer-renata
reviewed_at: '2026-11-08T19:01:23Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP24
---

Approved by claude:opus:reviewer-renata: approval:WP24

# WP24 review, cycle 2 (claude:opus:reviewer-renata, 2026-11-08)

**Verdict: approved.** F1, F2 and F3 from cycle 1 are fixed, each red before green. F4 stays a hand-off and F5 is
now listed in the move-task note. Nothing blocks. Two low notes go to follow-ups (N1, N2).

Cycle 2 is the six commits after `7db39991`: `02ea4fc1` → `2f4d0b86`, `b93b69c3` → `6c121ea9`, `6a7a49a3` → `eff8cb5f`.

## Commands run

- `./gradlew --max-workers=1 -Dorg.gradle.internal.repository.max.retries=10 -PcontractBaseline=none -PopenPacksDir=/home/user/open-packs :packs:check :activations:check`: **BUILD SUCCESSFUL**.
  - EffectiveSetTest 26/26, EffectiveTagTest 7/7, PacksAvailabilityTest 20/20.
  - ClosureQueriesTest 26/26, ConsistentReadTest 20/20, InForceResolutionTest 53/53.
  - FileSystemPackSourcesTest 16 with 3 skipped. The skips are older than this WP: permission assumptions from `08cade56` that do not hold when the tests run as root.
- The same flags with `:deployable:test`: 1132 tests, 1 failure, the accepted OperationCoverage R-20 (`GET /activations/{activationId}/artifacts 200`, `.../skipped 200`). 509/509 scenarios pass and HexagonalArchitectureTest passes 61/61.
- Red sampling, in a throwaway worktree that was removed afterwards:
  - `02ea4fc1`: `ClosureQueriesTest.theClosureFingerprintCoversTheClosurePacksAndNoOtherConfiguredPack` and `FileSystemPackSourcesTest.theFingerprintOfNamedPacksFollowsTheirFilesAloneOnRealDirectories` fail on their assertions ("a pack outside the closure", "a pack that is not named").
  - `b93b69c3`: the 2 EffectiveTagTest and 2 PacksAvailabilityTest tests fail on their assertions.
- The diff adds no suppression, `@Disabled` or assumption, and removes no assertion.

## F1: the closure digest

- **Scope.** `ClosureQueries.closureFingerprint` is SHA-256 over `charter-closure/v1`, then each closure pack's configured id and level in closure order, then `read.fingerprintOf(closure pack names)`.
  - `FileSystemPackSources.fingerprintOf` filters the configured locations by name and reuses `PackFileSet::digested` through the same `digestNamed`. It therefore digests the same files, under the same `name/relative` labels, as the whole digest.
- **Configured id.** It is digested explicitly. It is also the location name: `DomainRepositoryService.java:307` builds `ConfiguredPack(location.name(), location.level())`, so the name filter matches the closure's packs in production.
- **Level.** It is digested explicitly. It is also read from `pack.yaml` (`StorageRootLocations.declaredLevel`), whose bytes are in the file digest. A level change therefore changes the digest twice over.
- **A descriptor that changes while its files don't.** This cannot happen: the descriptor is `pack.yaml`, a digested file. A dependency added to a closure pack changes that pack's file and the closure's membership, and both reach the digest.
- **An unreadable pack.**
  - A closure pack: `digestNamed` refuses with `pack_unreadable` inside the load, and `ConsistentRead.loaded` then answers `stale_input` or the refusal, as before.
  - A pack outside the closure: it still fails the read through the whole-source digest. That behaviour is older than this WP and was not loosened.
- **Project configuration files.** `ProjectConfigSources.fingerprintOf` leaves them out. That is sound, because the service digests `config.yaml` and `charter.yaml` but never parses them (`ProjectConfigSources` javadoc), so they cannot change a closure's answer.
- **Defaults are safe.** These defaults are wider, never narrower:
  - `PackSources.fingerprintOf` falls back to the whole digest;
  - the old `ClosureView` and `ClosureSnapshot` constructors default `closureFingerprint` to the whole digest.
- **Freshness is not weakened.** `ConsistentRead.read` is unchanged: whole digest, load, whole digest. `ClosureView.fingerprint()` and `Charter-Source-Fingerprint` keep the whole digest. The scoped digest is taken inside the load, between the two whole digests, so a closure file that changes during the read makes the read stale.
- **The `ClosureQueriesTest` fake.** The implementer's claim checks out. `digests` is incremented only in `fingerprint()`, `fingerprintOf` answers `"scoped:" + names` without touching the counter, and no assertion in the file changed.
- **The tests use the real digest.**
  - `FileSystemPackSourcesTest` runs over real directories.
  - `ClosureQueriesTest` covers alpha → beta with gamma outside the closure, and another closure.
  - `PacksAvailabilityTest` runs the real `ClosureQueries`, `ConsistentRead` and `FileSystemPackSources` over three `@TempDir` packs. A gamma change leaves the list and getActivation tags unchanged; a beta change gives new tags.
  - The case-3 unit test now pins only that the tag reads `closureFingerprint`, not `fingerprint`, which is honest. The real proof sits in the adapter test.

## F2 and F3

- `EffectiveSet.notSelected` limits suggestions to reason `NOT_SELECTED`, per runner decision `01M6Y6VE6VZQ4MXD98N93JB3GJ`. One test covers all five other out-of-force reasons.
- `ENHANCES` is removed from `HARD_RELATIONS`, and a test shows that an artifact an active artifact enhances keeps its suggestion.
- The two changes leave the rest of the rule unchanged: still one hop, still the smallest source's edge.

## Regressions from cycle 1

None. Every cycle-1 test still passes unchanged, apart from the case-3 rewrite above. The cycle-1 rulings on items 1–8 stand.

## Low notes, non-blocking

- **N1:** `FileSystemPackSources.java:39-46`: when a name matches no location, `fingerprintOf` skips it silently. Production cannot hit this, because configured ids are the location names. A defensive refusal (`IllegalStateException` for a requested name with no location) would turn a future mismatch into a loud failure, where today it would freeze the tag against file changes. It can be folded at closeout.
- **N2:** each closure read now reads the closure packs' files once more, for the scoped digest. WP25's NFR-003 timing case should include it, and the PR B description must not claim a read-cost win (see the WP21 N2 note).
- **N3:** no unit test pins that a level change alone, with identical files, changes `closureFingerprint`. The level is digested explicitly and also lives in `pack.yaml`, so this is belt and braces. Optional.

## Hand-offs

- **WP25:**
  - Use `ClosureSnapshot.closureFingerprint()` for every effective tag and for getActivation's tag, and serve the either-tag If-Match (`revision.token()` or the current `EffectiveTag.activation`).
  - `governanceFingerprint` in the bodies should carry the same closure digest, so the body and the tag agree. The contract says "of the packs of the activation's closure".
  - Keep `Charter-Source-Fingerprint` on the whole digest.
  - Add the extra digest to the timing budget (N2).
  - Align the `ActivationSummary.required` and `locked` wording (cycle-1 F4).
- **WP26:**
  - Divergence and ADR entries: the closure-scoped digest (`charter-closure/v1`), which differs from `ServiceInfo.sourceFingerprint`, so amend the "as ServiceInfo.sourceFingerprint" wording in `EffectiveArtifactList.yaml` and the related schemas; per-read and per-query tags; suggestions only for `not_selected` (decision `01M6Y6VE6V`); `enhances` is not a hard relation.
  - Update R5 and the data model's EffectiveTag text.
  - Record the shared edits in packs: `PackSources.fingerprintOf`, `ConsistentRead.fingerprintOf`, `ClosureView` and `ClosureQueries`.
- **Closeout and PR C:**
  - The action context uses the same suggestion and hard-relation definition (decision `01M6Y6VE6V`).
  - Fold N1 and N3 if convenient.
