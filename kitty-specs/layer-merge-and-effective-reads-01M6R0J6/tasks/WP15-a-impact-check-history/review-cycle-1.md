---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T16:44:47Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP15
---

# WP15 review, cycle 1: changes requested (reviewer-renata)

Reviewed lane-m at d4fa3daf, against 73f618e2 (lane-l with WP14 approved).

The gate is green. `verifyAll --continue` passed, and `:packs:test --rerun` ran 3251 tests with 0 failures and 4
skipped.

The red/green order holds for each area. The relation codes each have a test and a near-miss. The q4b follow-up is
fixed. The hand-off cases are pinned: probe p5b, a `depends_on` naming a pack that is not stored, is now refused
`PACK_DEPENDENCY_UNKNOWN`.

Three things block approval.

The probes are in
`/tmp/claude-0/-home-user/72e3e65b-b349-5267-927a-2423aaa3f1ac/scratchpad/renata-wp15/service/packs/src/test/java/ai/speckitty/charter/packs/application/RenataWp15ProbeTest.java`
(run in a scratch clone at d4fa3daf, not in the lane).

## Blocking

**Issue 1: C21. The staged gate classifies a layer override of an *extendable* ancestor id as REGISTER, so it is
allowed. Ruling 01M6VD94VEVPSC1JXQ1RA7YYHQ requires that it be refused.** The ruling: "holding an id an ancestor holds
is a REPLACE of that ancestor's artifact, permitted only where the ancestor's policy opens it". Probe `c21LayerOverride`
uses `base` with `LOCKED_ONE` locked, `EXT_ONE` extendable and `OPEN_ONE` open:
- Create derived holding `EXT_ONE` with other content: **CREATED**. The ruling expects 409 governance_policy_violation.
- Replace a stored derived with an upload that adds `EXT_ONE`: **REPLACED**. The ruling expects 409.
- Create derived holding `OPEN_ONE`: CREATED (correct).
- Create derived holding `LOCKED_ONE`: 409, as `Operation REGISTER` (refused, but only because locked also refuses
  REGISTER).

Cause: `PackRegistrationService.writesOf` sends an id that is new to the written pack as `REGISTER`. `MutabilityLevel`
lets `extendable` permit `REGISTER`. The gate never asks whether a pack of the staged closure already holds the id.

Fix, in the gate (red first), as the ruling says. In `PackMutabilityPolicy.authorizeStaged`, judge a `REGISTER` whose
target an ancestor of the staged closure holds as a `REPLACE` against that ancestor's scoped policy. Do the same in
`authorize`, which the per-artifact PUT path uses with the same `exists ? REPLACE : REGISTER` choice. WP14's
`anExtendableArtifactOfTheAncestorMayBeRegisteredButNotReplaced` currently pins the opposite (CREATED). Under the ruling
its first step must expect 409; record that oracle change in the commit, citing the ruling.

**Issue 2: a crash between the swap and the history publish leaves the history without the stored pair for good, and
a version label can then be reused (IC-16).** `replaceWhole` and `create` stage the history, swap, then
`history.publish()`. If the process dies between the swap and the publish, the pack holds `(v2, r2)` but the history
file does not. Nothing ever adds it back. After the next registration (v3) moves on, v2 can carry other content.

Probe `historyCrash`: register 1.0.0, then 2.0.0. Put back the history file as it was after 1.0.0 (the state the crash
leaves). Register 3.0.0, then 2.0.0 with other content: **REPLACED**. IC-16 expects `409 pack_version_conflict`. The
history now reads `[1.0.0, 3.0.0, 2.0.0']`.

A related fault: if `publish` throws after the swap succeeded, `replaceWhole` answers `PACK_UNREADABLE`, a 500, for a
registration that was committed. The client is told it failed when the pack changed.

Fix: make the history heal itself.
- When staging the next history, append the stored pack's `(pack_version, revision)` first if the history lacks it.
  The commit store has the live directory and can compute it.
- Treat a publish failure after the swap as a logged warning, not a refusal; the next write heals it.

Add a test that plants the lost entry and expects the 409.

**Issue 3: the impact check costs more than linear in the fan-out and exceeds NFR-009's 5 s budget under the registry
lock.** Probe `cost` registers a new version of a base pack, with the stored packs installed directly:

| Shape | Time for the registration |
|---|---|
| 50 packs depending on base | 0.83 s |
| 200 packs depending on base | 7.2 s |
| 200 dependents plus an activation that lists no packs | 7.5 s |
| A chain of 50 | 0.50 s |
| A chain of 100 | 2.1 s |

Four times the fan-out costs nine times the time. All of it runs in `decide`, holding the registry lock, so every
other pack write is refused 503 after its 5 s `tryLock`. Two hundred project packs on one org base is a realistic
shape.

One visible cause: every closure goes through `OfferingReadService.composed(catalog.configured(), roots)`, which reads
every configured descriptor (`readDescriptors(configured)`) and builds the closure from that. Each closure also takes
an after-snapshot and a before-snapshot. The facts are memoized (the existing test counts them), but the per-closure
work over the whole catalog is not.

Fix: make the work per closure proportional to the closure, not to the catalog. For example, read and resolve the
descriptors once per check and hand the composer the pre-read set. Or compose the dependents' closures in one pass
where their union composes. Add a bound test that counts reads or resolves, not one that times them, and note the
10-pack fixture time as the prompt's Risks section asks.

## Non-blocking

1. **Strictness makes a broken dependent freeze its ancestor.** One dependent with a legacy file makes every
   registration of the base refused `PACK_UNREADABLE` (probe `brokenDependentBlocksAncestor`). That is what
   DM-01M6TQ67C9ZTNGZB9CY28JS54F rules, so I don't block on it. The message names the dependent, and the census
   warns. The runner may want the docs WP to state it.
2. **Activations are read under the packs' locks, but activation writes do not take the registry lock.** An
   activation stored while a registration runs is not among the root sets judged. This is fine if the activation
   write judges its own closure against the then-current packs; please confirm.
3. **Item 4 (p4):** the changed expectation does not weaken p4. Dropping `A_ONE` while renaming is still `409
   artifact_referenced`. A rename that keeps `A_ONE` is now refused by the impact check
   (`PACK_REFERENCE_PACK_MISSING`), which is stricter. The fixtures moved to `base::` because the impact check refuses
   bare cross-pack references. InboundReferences' bare-reference attribution now matters only for stored legacy
   packs.
4. **Crash leftovers.** `.history/*.tmp-*` files are never swept after a crash, and the directory is not fsynced
   after the publish rename.
5. **Out-of-map edits.** `ActivationRepository.list()` changed the port, both stores and the test doubles, beyond the
   one adapter the prompt names. That is needed, but name each file in an `out-of-map` trailer if one is missing.
6. **Item 5 (the RegistrationConcurrencyTest flake):** it did not reproduce. Six `--rerun`s passed on lane-l
   (362bf3fc) and six on lane-m. The test's unlocked `rig.writeStore.currentRevision` is the same code on lane-l, so
   the race is a property of the test, not new in WP15. Make the test retry a `PACK_NOT_FOUND`
   from that read, or take the read under the lock.

## Anti-pattern checklist

1. Dead code: PASS. The impact check, the history and the census are wired, and `:deployable:test` is green.
2. Synthetic fixture: PASS. Each refusal goes through the Rig over real files.
3. Silent empty return: PASS. The skipped activations and unreadable descriptors are documented.
4. FR coverage: FR-025, FR-026 and FR-027 are covered. FR-026 is partial (Issue 2).
5. Frozen surface: PASS.
6. Locked decision: FAIL. Ruling 01M6VD94VEVPSC1JXQ1RA7YYHQ is not met (Issue 1).
7. Shared-file ownership: noted. `PackRegistrationService` and `PackCommitStore` (WP14), `ActivationImpactAdapter`
   (WP25, created here), and the contract flags.
8. Production fragility: Issue 2's 500 after a committed swap.
