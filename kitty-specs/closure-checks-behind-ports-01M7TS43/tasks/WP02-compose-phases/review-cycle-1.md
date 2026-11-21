---
affected_files: []
cycle_number: 1
mission_slug: closure-checks-behind-ports-01M7TS43
reproduction_command:
reviewed_at: '2026-11-20T06:38:05Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review — claude::reviewer-renata — REJECT (changes requested)

The production change is right. Every phase now collects its faults. The phases stay sequential. With one fault,
`PackRefusals.of` returns `CharterRefusal.of(code, detail, packs, [], [])`, and that is the same object
`Refusal.toCharterRefusal` returned before, so single-fault answers are unchanged. The delegation fault keeps its own
`UNRESOLVED` violation. Read failures still propagate. `CompositeComponentResolver` still throws a `CharterRefusal`.

The disjoint-ranges scenario change is correct and honestly described. `CompositionRulesTest.rangesNoVersionSatisfiesTogether`
already shows that `faults()` returns `OUT_OF_RANGE, OUT_OF_RANGE` for this fixture on the #554 write path. The read
path now matches it, and the scenario text says so.

Targeted run on the lane: 191 tests, 0 failures.

The reason for rejecting is the T008 test deliverable. It is incomplete, so half of the collecting behaviour is not
pinned by any test.

## Findings

1. [MEDIUM] service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java (`unresolvedDelegations`)
   and SubstepDelegationRuleTest.java:135-185 — no test has two or more delegation faults, at any level. If someone
   reverted the method to "return after the first candidate", every test would still pass. T008 asks for
   "one case per phase with two or more faults".
   Recommendation: add a domain case with two unresolved candidates (from one pack, or from two packs) that asserts
   the count, the order and each fault's own `UNRESOLVED` violation. Also add an offering-level case
   (OfferingReadServiceTest) that asserts one `pack_unreadable` refusal with violations laid out as header, own,
   header, own (4 items) and the `(2 fault(s); ...)` detail.

2. [MEDIUM] service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java:366-392 —
   only two of the six phases have an offering-level multi-fault test: references (OfferingReadServiceTest) and the
   resolver (the composition.feature disjoint-ranges scenario). The step enhancement targets, presets, case-only and
   delegation phases have none. Each of them could keep only its first fault (for example `.limit(1)`, or
   `findFirst` before `refusePhase`) and every test would still pass. T008 also asks for one single-fault case per
   phase that equals today's answer. Only the reference phase has that (`alone`), and it checks only "no
   violations, no `fault(s)`".
   Recommendation: in OfferingReadServiceTest, add one two-fault case per remaining phase (step enhancement targets,
   presets, case-only, delegations). Each should assert the code, the violation count, the codes in order, and that
   the detail ends with `(2 fault(s); every one is in violations)`. For the single-fault side, assert full equality
   of code, detail and packs against the fault's own `toCharterRefusal()` / `ClosureFault` for at least one phase.
   The `@Nested StepEnhancementTarget` class already exists and is the natural place for the step enhancement case.

3. [LOW] service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java:614-635 — `checkQualified`
   now refuses every qualified fault of a pack, but EdgeIndexTest has no case with two or more. Add one alongside
   finding 2 (owned file, R8).

4. [LOW] PackCompositionResolver.java:320-327 — the javadoc of `refuseFirst` still says "the offering answers the
   first fault of each of its phases in turn". That is no longer true. Only `resolve`/`resolveRooted` and the tests use
   it now. Update the sentence.

5. [LOW] PackCompositionResolver.java:410 — `resolveRooted` now has no production caller, only
   PackCompositionResolverTest and CompositionGoldenTest. It is the throwing twin of `composeRooted`. This follows
   the `resolve()` precedent that T005 allows, so it is acceptable. Prefer the tests to call `composeRooted` +
   `refuseFirst` and delete `resolveRooted` (charter: no legacy reader beside the new one). At the least, its javadoc
   should say it exists for the first-fault readers.

6. [LOW] OfferingReadService.java:362-364 — the comment wrap left "only" alone on its own line. Reflow it.

7. [LOW] CompositionGoldenTest.java:38-46 — the class javadoc still calls the sequence "the resolver's part of
   OfferingReadService#composed, in its order", and says the "first refusal" is golden. The offering no longer
   answers the first fault of a phase with several faults. State that the golden pins the first fault of each phase,
   which is the first fault of the offering's one refusal.

8. [LOW] CompositeComponentResolver.java:112-118 — it uses `Refusal.toCharterRefusal()`, not `PackRefusals.of` as
   T007/R8 say. With one reference this is byte-identical, so it is acceptable. Mention it in the activity log, or
   route it through `PackRefusals.of(new ClosureVerdict(...))` for one mapping.

## To re-submit

Fix 1 and 2 (and 3 while you are in the tests). 4–8 are optional cleanups. Re-run `./gradlew :packs:test` and the
composition/effective-* contract scenarios.
