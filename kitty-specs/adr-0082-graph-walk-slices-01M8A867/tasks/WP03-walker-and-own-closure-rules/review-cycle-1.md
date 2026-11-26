---
affected_files: []
cycle_number: 1
mission_slug: adr-0082-graph-walk-slices-01M8A867
reproduction_command:
reviewed_at: '2026-11-26T17:46:56Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review, cycle 1: changes requested (reviewer-renata)

Reviewed `c6b948e46..bee30da5b` on lane-c. The production design matches research R-3/R-4, data-model and operator
ruling D1, and I found no behaviour difference:

- The own-closure order is: fallback (only when named), own pins, conflicts (OnClose), enhancements (OnClose).
- The CharterRefusal catch in walkFaults is kept, and the ISE paths are unchanged.
- The census path is unchanged.
- `references()` visits the members in order, and its VIOLATION gate equals the old "any fault" stop, because every
  closure code is a violation by construction (`ClosureFault` ctor + `PackCodes.refusalOf`).

The tree equals `refactor/638-graph-walker` 3119fb476. check_docs all, markdownlint and cspell pass. The test results
on the lane are newer than bee30da5b and green for packs, pack-validation and pack-doctor.

The work is rejected for one MEDIUM: tests the WP requires are missing.

## MEDIUM

**Issue 1: T015's red-first ClosureImpactCheckTest cases are absent**
(`service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureImpactCheckTest.java`; the file is owned
and untouched). T015 requires:

- (a) two single-root closures with mixed own-closure breaches keep closure-major order;
- (b) a preset item beside a breach is reported;
- (c) the census keeps " (packs ...)".

No test in the module pins any of the three:

- `aReplacementThatBreaksTwoClosures...` covers compose faults only, with `anySatisfy`.
- `closuresWithSeveralComposeFaults...` covers compose faults, not `walkFaults`.
- No test asserts the census suffix.

The for_review note does not record the omission. `walkFaults` was rewired (a new graph per closure, plus a new
`requireNonNull` on `closures.composed().get(roots)` at ClosureImpactCheck.java:506). The WP's Risks section names
this write-path order as the order risk (squad S8).

Fix: add the three cases. Assert the full ordered list of violation codes and details for (a). For (b), assert the
breach and the preset item in one refusal, in that order.

## LOW (fold before merge)

**Issue 2: T014's "enhancements present and no merge throws" case is missing** from `OwnClosureRulesTest`. The
`IllegalStateException` at `ProfileEnhancementRule.java:39` has no test, and exceptions are part of the unchanged
behaviour. Fix: add the case, asserting `IllegalStateException` through `PackRulesets.ownClosure(..).run(..)`.

**Issue 3: T012's "a WARNING does not gate" case is missing.** `ValidationWalkerTest.everyClosureCodeIsAViolation`
(:120) replaces it. A mutant that gates on any fault instead of `violated()` (`ValidationWalker.java:193`) passes every
test. Behaviour is identical today, because a `ClosureFault` cannot carry a WARNING. Fix: either record that reason in
the WP history, or make the gate testable and add the case.

**Issue 4: the out-of-map list in the for_review note is incomplete.** `RolePinImpactTest.java` (new test
`aFallbackBreachComesBeforeThePinBreachesInOneRefusal`, :169) is owned by no WP and is not listed. Fix: list it in the
next for_review note.

**Issue 5: `Ledger.rationale()` (`Ledger.java:28`) has no production caller** (anti-pattern item 1). T012 mandates it as
the slice 7 projection. Fix: record that as the reason, or drop it until slice 7.

**Issue 6: unsigned commits after c6b948e46.** The three WP commits (c8f5b3034, 70fcac5f9, bee30da5b) are signed, and
no message names a model or carries a Co-Authored-By trailer. The spec-kitty CLI commits are unsigned: e8b9fa25c,
95639e728, 92e412ef2, 660942873, the merge 7e076b513, and this verdict's commits. The mission owner re-signs them
before landing, as on WP01.

## NIT (fold before merge)

**Issue 7: the `presetItemFaults` null branch is now dead.** `ClosureImpactCheck.java:536-537` keeps `@Nullable` and
`if (composed == null)`, but its one caller requires the closure to be non-null at :506 first. Fix: drop the
`@Nullable` and the branch, or pass in the non-null closure the caller already holds.

**Issue 8: the message at `ClosureImpactCheck.java:416`** ("the composed closure holds its declaring pack X") states
the invariant, but it is thrown when the invariant fails. Fix: reword it, for example "the composed closure does not
hold its declaring pack X".

**Issue 9: `data-model.md:18` still names the ledger `Findings`.** The code and docs call it `Ledger`. Fix: update the
data-model row.

## Not verifiable here

- **verifyAll:** not run (no Gradle while the timing run is in progress). The test-results XML on lane-c postdates
  bee30da5b and shows 0 failures.
- **"13 LOW/NIT folded":** there is no record of the 13 findings. 70fcac5f9 (and dc10633df) list six fold items.
