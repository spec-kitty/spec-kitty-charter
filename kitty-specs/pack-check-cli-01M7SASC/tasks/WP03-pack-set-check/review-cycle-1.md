---
affected_files: []
cycle_number: 1
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T14:22:44Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review, cycle 1 (claude::reviewer-renata): changes requested

Reviewed `git diff 9ddf9de0d..25f4f4a95` in lane-c (later lane commits are spec-kitty's own status commits).

Gates I ran in lane-c, all green: `:pack-validation:test :packs:test --rerun -PrequireSymlinks=true` (pack-validation
435 tests, 0 failed, 1 skipped by assumption; packs 6824, 0 failed, 6 skipped), `:deployable:test --tests
'*HexagonalArchitectureTest*'` (95, 0 failed). In a scratch copy, `:pack-validation:spotlessCheck checkstyleMain javadoc`
also passed. The implementer's commits are signed and carry no trailers.

Three MEDIUM findings block approval.

## M1 (MEDIUM): `PackSetCheck` derives CLOSURE a second time, beside `PackRulesets.closure(presetIdsOf)`

- **Where:** `service/pack-validation/.../set/PackSetCheck.java:173-174`, calling `CompositionRules.faults(set)` and
  `CompositionRules.presetFaults(set, ...)` directly.
- **Why it matters:** the composition is defined in two places.
  - FR-003 and data-model "PackSetCheck" name `PackRulesets.closure(presetIdsOf)`.
  - Squad disposition S4 (research.md) was accepted as "`PackSetCheck` calls only that".
  - `PackRulesets.closure(presetIdsOf)`'s own javadoc says "The closure walk's checks (#556) extend it here."
  - The charter asks for one model.
- **How it fails:** when #556 adds `.must(walk)` to `closure(presetIdsOf)`, pack-check never runs the new checks and
  reports green where the ruleset reports red. The parity test (`PackSetCheckTest.java:208`) catches this only if its
  one fixture set happens to trigger the new rule.
- **Mutant M1 survived:** I added a rule to `PackRulesets.closure(presetIdsOf)` that fails whenever built-in is in
  the set. The whole PackSetCheckTest and PackCheckReadoutTest suite stayed green. The valid-set test holds built-in,
  but `PackSetCheck` never sees the rule; the parity set does not hold built-in.
- **Fix:** add one fault-returning form in `:packs`, for example
  `PackRulesets.closureFaults(List<PackUnderResolution>, Function<PackUnderResolution, Collection<String>>)`, which
  returns `List<CompositionFault>`.
  - Define `closure(presetIdsOf)` as the reasons of `closureFaults` (`CompositionRules.reasons` does the same), so
    there is one composition and #556 extends `closureFaults`.
  - `PackSetCheck` calls only `closureFaults`.
  - In `:packs`, add a test that `closure(presetIdsOf)`'s reasons equal `closureFaults`' reasons, code and detail, in
    order. Keep the pack-validation parity test, or drop it once the `:packs` test holds.
  - **Coordination:** `PackRulesets` is WP02's file (approved, merged into lane-c through `9ddf9de0d`). Say so in the
    move-task note. WP04 has not started, so nothing needs a rebase.

## M2 (MEDIUM): a pack refusal from the round trip fails the whole set as an exception (exit 2), not as that pack's violation

- **Where:** `PackSetCheck.java:129-130` (`roundTrip`). Whatever `RoundTripping.run` throws propagates out of
  `checkWithRoundTrip`.
- **What I found (probe, real flow, scratch copy):** I ran `checkWithRoundTrip` over a copy of built-in whose
  `pack.yaml` is `pack_id: [`. It throws `CharterRefusal: pack.yaml of built-in is not valid YAML`.
  - The other two probes did not throw: a broken graph gives `round_trip_red`, and a missing policy gives no reason.
  - So a pack author with one broken `pack.yaml` who passes `--round-trip` gets no report for any pack. WP04's main
    then answers exit 2.
- **Why it matters:** the spec's edge cases say "A pack that cannot be read is a `pack_unreadable` violation (exit
  `1`); exit `2` is for failures outside a pack read (writing the output, the round trip's temporary directory)".
- **Fix:**
  - In `roundTrip`, catch `CharterRefusal` (kernel domain, so ArchUnit allows it). Turn it into that pack's round-trip
    reason, a `VIOLATION` with the refusal's code or `round_trip_red` and the refusal's detail. Alternatively, skip the
    round trip for that pack and record why; the implementer picks.
  - Only I/O failures (`UncheckedIOException` from the composition root's temporary target) propagate.
  - Document the boundary on `RoundTripping`: a refusal of the pack is a result, an I/O failure is an exception.
  - Add a test over the real flow with a garbled `pack.yaml`. The set must still give a verdict, and that pack must
    be failing.

## M3 (MEDIUM): a set with a level-unknown pack passes (exit 0) although CLOSURE never ran

- **Where:** `PackSetVerdict.PackResult.failing()` and `PackSetVerdict.failing()`. `PackSetCheckTest.java:319-335`
  pins `failing()` as false ("is no fault of the pack").
- **How it fails:** take `--pack built-in --pack <a pack with no pack_level>` with no level given. Validate mode gives
  the second pack no finding, because `LevelRules` returns nothing when no level is configured.
  - `CLOSURE` is skipped, `verdict.failing()` is false, and nothing gives that pack a problem.
  - With data-model's invariant (exit 0 iff `problems` is empty), WP04 answers exit 0 for a set whose composition
    was never judged.
- **Why it matters:** the spec's edge case says "`--pack <dir>` with no level and no readable declared level: the
  pack's entry has no `level`, **and its fault is reported**". D3 has the same intent: a skipped `CLOSURE` is never a
  silent pass.
- **Fix (preferred):**
  - Make an unknown level fail the pack, the way `Unreadable` does. For example, `failing()` is also true when
    `level()` is empty.
  - Add an "unreported" accessor beside `unreportedRefusal()`, so WP04 emits one problem for the pack, for example
    with the `pack_level_missing` token as a violation of the set check. The pack still never gets two problems.
  - Update the test to assert `verdict.failing()`.
- **Fix (alternative):** if the planner rules that an unknown level is not a fault, amend spec.md's edge case and
  data-model to say exit 0 with `skipped`. Then assert `verdict.failing()` is false explicitly, so the decision is
  pinned.

## LOW (fold into this cycle)

- **L1 (the implementer's point 2):** `levelOf` (`PackSetCheck.java:134-140`) takes the declared level only from
  `Built.descriptor()`.
  - A pack with `Unreadable` facts and a readable `pack.yaml` gets no level, against data-model (`level` "absent when
    neither is known").
  - Probe: built-in with a broken graph and no configured level gives `level=Optional.empty`.
  - Read the declared level from `readout.contents().descriptor()` when it is `FileRead.Read`, the source
    `LevelRules` reads. Then the level has one source and `Built.descriptor()` is not needed for it. Add a test.
  - The verdict does not change (`CLOSURE` is skipped either way), only the report's `level`. Hence LOW.
- **L2:** the `PackUnderResolution` field mapping is written three times.
  - `PackSetCheck.java:164`, `CompositionFactsBuilder.underResolution`, and the parity test.
  - Give `PackReadout.Built` (packs domain) an `underResolution(ConfiguredPack)` and use it in all three, so a new
    field of `PackUnderResolution` cannot be mapped in one place and missed in another. Same one-model reason as M1.
    The fix is small if done with M1.
- **L3:** `PackSetCheck.java:161` keys `presetIds` by `PackUnderResolution` value equality, which hashes whole
  artifact lists. With M1's API, key by pack name, or use an `IdentityHashMap`.
- **L4:** the class javadoc of `PackSetCheck` has unwrapped lines (`:37` is 201 characters, `:45` is 173). Reflow to
  the 120 width the other files keep.

## Rulings on the implementer's four points

1. **Two derivations of CLOSURE:** required, MEDIUM (M1). S4 already ruled "calls only that", and the surviving
   mutant shows the parity test does not guard the #556 extension.
2. **No level for an unreadable pack:** yes, take it from the readout's descriptor when `pack.yaml` was read. LOW
   (L1).
3. **The stub reading:** acceptable.
   - Three real plants (a missing policy, a garbled policy, a broken graph) each give `Unreadable` together with a
     violation, so the state is unreachable through `FileSystemPackInspector` today.
   - But `PackReading` is a domain port, and S10 asks for the branch and its test. The stub wraps a real read and
     replaces only the facts, as an error-handling case, so the canonical-shape rule is kept.
   - Keep `unreportedRefusal()`: it is not dead code, and WP04's `Problems` is its consumer.
4. **Round-trip exceptions propagate:** only for I/O. A pack refusal must become that pack's result (M2).

## Checklist

- **Dead code:** PASS. `check`, `PackSetCheck`, `RoundTripping` and `unreportedRefusal` are consumed by WP04 as
  planned.
- **Synthetic fixture:** PASS. Real reads throughout, and the one stub is documented as error handling.
- **Silent empty return:** PASS.
- **FR coverage:** PASS for FR-002 and FR-007. FR-003 is PARTIAL (M1), and FR-004 is PARTIAL (M2).
- **Frozen surface:** PASS.
- **Locked decision:** FAIL (S4, M1).
- **Shared-file ownership:** N/A now. M1 touches WP02's `PackRulesets`, so note it.
- **Production fragility:** FAIL (M2, a pack refusal escapes as an exception).

Mutants I planted in a scratch copy:

- **M1 (an extra rule in `closure(presetIdsOf)`):** survived.
- **M2 (`unreportedRefusal` without its no-violation guard):** killed by `anUnreadableGraphSkipsClosure`.
- **M3 (round trip only for a pack with no violation):** killed by `aRedRunGivesItsReasons`.
