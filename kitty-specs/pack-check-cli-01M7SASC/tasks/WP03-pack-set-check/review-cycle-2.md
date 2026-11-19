---
affected_files: []
cycle_number: 2
mission_slug: pack-check-cli-01M7SASC
reproduction_command:
reviewed_at: '2026-11-19T16:03:00Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review, cycle 2 (claude::reviewer-renata): changes requested

I reviewed `git diff 83917f0b4..4e10e03bb` in lane-c, on base 1b5ee448e.

## What I checked

- **The originals:** `git range-diff 9ddf9de0d..25f4f4a95 1b5ee448e..83917f0b4` shows all three commits as `=`. They
  are unchanged.
- **Commits:** every implementer commit from 1b5ee448e to 4e10e03bb is signed and has no trailer.
- **Tests in lane-c**, all green:
  - `:pack-validation:test :packs:test --rerun -PrequireSymlinks=true`: pack-validation 443 tests, 0 failed (1
    skipped by its assumption); packs 6826 tests, 0 failed.
  - `:deployable:test --tests '*HexagonalArchitectureTest*' --rerun`: 95 tests, 0 failed.

## Mutants

I ran these in a scratch copy against PackSetCheckTest and PackCheckReadoutTest:

- **m1:** an extra `.must(...)` on `closure(presetIdsOf)`, the #556 drift from cycle 1. Killed by
  `theExamplePacksFaultsAreTheRulesetsReasons`. This mutant survived in cycle 1.
- **m2:** `unreportedRefusal` without its guard. Killed.
- **m3:** round-trip only the packs with no violation. Killed by two tests.
- **m4:** `levelMissing` without its `Built` guard. Killed by `anUnreadablePackWithNoLevelGetsNoLevelReason`. The
  first form of this mutant did not compile under `-Werror`, so I used a variant that does.
- **m5:** a refused pack with no violation gets no `round_trip_red`. Killed by `aRefusedPackWithNoViolationIsRed`.
  Again, the variant that compiles.
- **m6:** catch every `RuntimeException`, not just `CharterRefusal`. Killed by `anIoFailurePropagates`.

## What is fixed

- **M1:** fixed. `closureFaults` and `closure(presetIdsOf)` share the one private `phases()` list.
  - `PackSetCheck` calls only `closureFaults`.
  - In main code, nothing else derives CLOSURE with presets. `closure()` without presets is the service's, and it is
    unchanged.
  - Parity is pinned in `:packs` and in pack-validation, and the pack-validation parity test now covers the five
    example packs.
- **L2:** fixed by `PackReadout.Built.underResolution`, which the builder, the check and the tests all use.
- **M2:** fixed. Only `CharterRefusal` is caught. `UncheckedIOException` and every other exception propagate, and
  m6 proves it. A real-flow test covers the garbled `pack.yaml`.
- **L1, L3, L4:** fixed.
- **M3:** fixed as the coordinator ruled.
  - The twin keeps the `PROMPT_SLOT_NOT_EMPTY` precedent: the same token, a `VIOLATION`, named apart by the constant.
  - `setCheck` is stated only for `Built` facts with no level. `LevelRules` states the warning only when a level is
    configured, so the two never meet.
  - A pack whose facts cannot be read gets no second problem.
  - The coverage exemption names a real test method, and a test checks that the method exists.

Two MEDIUM findings remain, both from the fixes.

## C2-M1 (MEDIUM): ArchUnit rule C does not cover the new level code

- **Where:** `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`,
  `onlyTheLevelRulesNameThePackLevelFindingsRule()`, about lines 2063-2074.
- **What rule C is for:** its javadoc says "only the level rules (`placement.LevelRules`) and the placement codes name
  the pack-level codes, so no class outside the placement rules states a pack-level finding by hand". ADR-0075's
  2026-11-16 amendment points the single-owner rules A to C at `LevelRules` and `PlacementCodes`.
- **The gap:** the rule lists only `PACK_LEVEL_MISSING` and `PACK_LEVEL_MISMATCH`.
  - `PackSetCheck.java` (`levelMissing`) now states a `pack_level_missing` finding by hand with
    `PACK_LEVEL_MISSING_VIOLATION`.
  - Any other class could do the same, and the gate would not notice. That is the hole rule C exists to close.
- **Fix, either way:**
  - (a) Add `.orShould().accessField(PlacementCodes.class, "PACK_LEVEL_MISSING_VIOLATION")` to rule C, and allow
    `ROOT + ".packvalidation.set.PackSetCheck"` by name in its prefix list.
  - (b) Preferred, because it keeps level findings in one place: give the placement package a public factory for the
    reason, for example `LevelRules.unknownLevel(String name)` or a small public `placement.SetLevel`. Then
    `PackSetCheck` calls it, and rule C gains the twin with no new allowed class.
  - With either fix, keep the bite fixture firing: extend `packlevelfixture` or add a second `BuildsFindingDirectly`
    access to the twin.

## C2-M2 (MEDIUM): the verdict's new `ROUND_TRIP` skip does not fit the planned report contract

- **Where:**
  - `PackSetCheck.run`, which adds `skipped.add(new Skip(Check.ROUND_TRIP, ...))`;
  - `PackSetVerdict.Skip.reason()`;
  - the mission's draft `contracts/PackCheckReport.yaml`, `skipped.items.properties.check`, which is
    `enum: [CLOSURE]` and described as "today only CLOSURE, when a pack has no facts";
  - data-model.md, which says `skipped` is "Present when `CLOSURE` was skipped".
- **How it fails:** take `--round-trip` with one pack the flow refuses, for example a garbled `pack.yaml`. WP04 then
  has two choices. It renders a skip with `check: ROUND_TRIP`, and the report fails its own contract test. Or it
  quietly drops the skip, so the verdict and the report drift apart. The charter asks for one model, with the
  contract examples updated along with it.
- **Fix:** reconcile the two now, before WP04 starts.
  - Either amend data-model.md and the draft schema: `enum: [CLOSURE, ROUND_TRIP]`, with a description of the
    per-pack round-trip skip. Tell WP04 in the move-task note.
  - Or drop the `ROUND_TRIP` skip and let the pack's refusal speak only through its verdict, or through the
    `round_trip_red` reason.
  - Either is fine. The planning artifacts are on the coordination branch, so the coordinator can make the edit.

## LOW (fold into this cycle)

- **C2-L1:** `CompositionRules.presets(...)` (`CompositionRules.java:109`) no longer has a production caller. Only
  `CompositionRulesTest` uses it, which leaves a third way to state the preset reasons. Delete it and point those
  tests at `PackRulesets.closure(presetIdsOf)` or `closureFaults`. If it must stay, say in its javadoc that it is a
  test seam.
- **C2-L2:** a refused pack with no violation now gets both a `ROUND_TRIP` skip and a `round_trip_red` reason
  (`aRefusedPackWithNoViolationIsRed`). The report then says the round trip both "did not run" and "is red". Pick one
  form for that case; this folds into C2-M2's fix.
