---
affected_files: []
cycle_number: 1
mission_slug: layer-merge-and-effective-reads-01M6R0J6
reproduction_command:
reviewed_at: '2026-11-07T13:58:06Z'
reviewer_agent: claude:opus:reviewer-renata
wp_id: WP14
---

# WP14 review, cycle 1: changes requested (reviewer-renata)

Reviewed lane-l at 3a1c1655 (the WP's commits 9877e3a7, 69908cf8, 37a8ea2c and e0ad0fa1, identical by range-diff to
aac8d188, 65f38c59, d7a62287 and 71b478d9) against db796216.

The gate is green: `verifyAll --continue` passed, and `:packs:test --rerun` ran 3188 tests with 0 failures and 4
skipped. The flow follows IC-04's order, and the version rule, the preconditions, the pack-id grammar, the link and
case-twin checks and the swap bracket are right. Three things block approval.

All reproductions are JUnit probes in the `PackRegistrationServiceTest.Rig` style. They were run in a scratch clone,
not in the lane: `/tmp/claude-0/-home-user/72e3e65b-b349-5267-927a-2423aaa3f1ac/scratchpad/renata-wp14/service/packs/src/test/java/ai/speckitty/charter/packs/application/RenataProbeTest.java`.

## Blocking

**Issue 1: an unreadable stored pack turns off the REMOVE gate, the removed-edge gate and the inbound scan (fail-open).**
`beforeOf` catches any `CharterRefusal` from `loadStored` and uses `Before.unreadable(upload)`. That treats the stored
pack as holding exactly what the upload holds, with no edges. As a result:
- nothing counts as removed, so no `REMOVE` write is gated;
- no stored edge counts as dropped, so a dropped edge gets no `RELATIONSHIP` check;
- `removed` is empty, so `InboundReferences.scan` returns at once.

The trigger is ordinary. One legacy directive that still carries `references:` (a field ADR-0064 D5 removed) is
enough, which is exactly the fixture of `aStoredPackThatCannotBeReadIsReplacedByAFixedUpload`. Every pack written
before D5 is in this state.

Reproduction. `base` locks `LOCKED_ONE` as in `rigWithBase`. `derived` builds on `base`. In each case the second
variant also gives one of derived's or base's directives `references:\n  - directive:X\n`.
- p1a: derived is installed holding `LOCKED_ONE`, and the upload drops it. Readable stored pack: `409
  governance_policy_violation (REMOVE)`. With the legacy file: **REPLACED**.
- p1b: derived is installed with `edges.graph.yaml` holding `LOCKED_ONE -> DERIVED_ONE`, and the upload drops the
  edge. Readable: `409 (RELATIONSHIP)`. With the legacy file: **REPLACED**.
- p1c: derived holds an edge onto base's `A_ONE`, and base's upload drops `A_ONE`. Readable: `409
  artifact_referenced`. With the legacy file in base: **REPLACED**, and `A_ONE` is gone while derived still refers
  to it.

The green commit also rewrote a red oracle in this direction. `WholePackStoreTest` expected "loading a stored pack
stays partial-tolerant" and now expects `loadStored` to throw `PACK_UNREADABLE`. That is the change that left the
diff with nothing to work from.

Fix: fail closed. Diff against what can be read of the stored tree:
- the artifact ids of the tolerant `load()` aggregate, plus the files present per kind, so that every id the stored
  pack held and the upload lacks is gated `REMOVE` and scanned;
- the edges of every graph document that reads.

If part of the stored tree cannot be diffed (for example a graph that does not parse), refuse rather than treat it
as empty, or at least gate and scan every id that can be listed. Add red tests for p1a, p1b and p1c. Keep the repair
test green, but make it repair a stored pack whose readable part does not trip the gate.

**Issue 2: a qualified referrer is not found when the replace renames the pack (FR-036).** `removedTarget` compares a
qualified reference's pack name only with `staged.descriptor().name()`. `requireSameIdentity` lets a replace change
`name`. Referrers wrote their references against the *stored* name.

Reproduction (p4). derived holds an edge `DERIVED_ONE -> base::directive:A_ONE`.
- Upload base 2.0.0 without `A_ONE`, keeping the name: `409 artifact_referenced` (correct).
- The same upload with the name `base2`: **REPLACED**, and derived's reference dangles.

Fix: match a qualified reference against the stored pack's name as well as the staged one. You could also refuse a
rename while qualified references to the old name exist. Add a test.

**Issue 3: the near-misses for the ADR-0064 D11 data-only fields are synthetic (anti-pattern 2).**
`aDataOnlyFieldTheFactsKeepAsAReferenceIsNotInbound` takes four labels: agent-profile directive references,
agent-profile tactic/toolguide/styleguide references, glossary `see_also` and step-contract delegations. The label is
used only in `as(...)`. All four cases build the same hand-made `PackCompositionFacts`, so no agent profile, glossary
or step contract is ever loaded. If the loader started turning one of those fields into an edge, the test would still
pass. The prompt asks for "a near-miss each", and the skill cases (`aSkillsDataOnlyFieldIsNotInbound`) show how:
real pack files through the `Rig`. Do the same for each of the four fields, each with its control.

## Non-blocking (fix if cheap; otherwise hand on)

1. **Crash during `create` wedges the pack id.** A crash between `reserve` (`createDirectory`) and `moveOver` leaves
   an empty `<root>/<packId>`. The listing serves it as a pack. Every later registration of that id answers `500
   pack_unreadable "pack newpack has no pack.yaml"`, with or without `If-None-Match: *` (probe p2). Only an operator
   can clear it, and the Javadoc's "nothing is left behind" is not true for a crash. Suggested fix: under the pack
   lock, treat an empty directory at the entry as an interrupted create and remove it, or rename the staging over it
   (on Linux, rename(2) replaces an empty directory). Add a test.
2. **A replace may take another pack's `name`** (probe p3: pack-x renamed `other` while `other` is stored gives
   REPLACED). IC-04 step 3 only requires name uniqueness for a new pack. WP15's impact check refuses a duplicate
   inside a closure, but not outside one. This needs a planner ruling, or the same 409 as on create.
3. **The create race does not test create exclusivity.** `ofEightCreatesOfOneName...` gives all eight uploads one
   `pack_version`, so the seven losers are `pack_version_conflict` replaces, not refused creates. Add the
   `If-None-Match: *` variant with distinct versions: one CREATED and seven 412.
4. **The reader race can pass vacuously.** `everyReadSeesOneCompleteVersion...` never asserts that a read overlapped
   a swap. `MixedReadConcurrencyTest` asserts `overlapped > 0`; do the same.
5. **`copyTree` runs under the registry lock** (`replaceWhole`, `create`). The plan keeps the slow work outside every
   lock (NFR-009). For a large pack, the copy holds the lock for other writers' 5 s `tryLock` and they get 503s.
   Copy into the pack filesystem before taking the lock, and keep only the swap inside it.
6. **Crash leftovers are never swept.** `.pack-commit-*` and `.pack-write-*` directories left by a crash before the
   swap stay forever (the same pattern as the existing `commit`).
7. **The staged gate can skip ancestors without refusing.** `ancestorsJudging` skips an ancestor that is not stored
   or whose descriptor cannot be read (`declares` answers false), so its locks are not applied (probe p5b: an
   unknown `depends_on` plus a `LOCKED_ONE` gives REPLACED). This relies on WP15's impact check refusing the
   closure. Fine within PR A, but WP15 must cover it.
8. **A stored pack whose `pack.yaml` cannot be read cannot be repaired by registration.** `checkIdentity` calls
   `descriptors.get` without a catch, which answers 500.
9. **Dropping the ancestor frees the pack from its lock (informational).** A replace that drops its `depends_on`
   on the locking pack may then hold `LOCKED_ONE` (probe p5: REPLACED). That follows "judge along the new closure"
   (ADR-0063 D3, A15 scope), but it is worth confirming with the planner that this is intended.

## Anti-pattern checklist

1. Dead code: PASS (with a caveat). `PackRegistrationService` has no production caller yet; the WP16 job is that
   caller. The beans wire, since `:deployable:test` is green.
2. Synthetic fixture: FAIL (Issue 3).
3. Silent empty return: PASS. Every `Optional.empty()` and `false` on an unreadable input is documented. Issue 1 is
   the consequence of one of them.
4. FR coverage: PASS for FR-001, FR-002, FR-003, FR-007 and NFR-006. FR-036 is partial (Issues 1 and 2).
5. Frozen surface: PASS.
6. Locked decision: FAIL in effect. The gate's own Javadoc says "a lock that cannot be read is never taken as open",
   and Issue 1 does exactly that for removals and dropped edges.
7. Shared-file ownership: noted. These files are shared:
   - `PackMutabilityPolicy` (WP07);
   - `CharterRefusalCode.yaml` and `errors.feature` (the contract; the raised-flag reading is WP16's);
   - `WritePolicyGate`, `MutabilityLevel` and `PermissiveWritePolicyGate`, which no WP owns.

   All are named in the commit trailers.
8. Production fragility: PASS. The new raises are refusals with codes. The 400 for both preconditions is WP16's.
