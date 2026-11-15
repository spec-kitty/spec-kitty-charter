---
affected_files: []
cycle_number: 2
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-13T21:20:46Z'
reviewer_agent: claude-reviewer
wp_id: WP03
---

# WP03 review, cycle 2: changes requested

Reviewed bb60f7ddf (SSH-signed, no Co-Authored-By, no model name). The tip 0d5ca89 is the unsigned merge that the review claim tooling made, not implementer work.

Resolved: `leaves()` now expands `Kind`, reads `?`/`Object` as every kind and intersects all bounds. The odd-shape check fails any non-private static method that takes a KindRef and is not `(KindRef, KindRef) -> Relationship`, and it skips private helpers. `disagreements()` is one helper shared by the main test and the controls. The skewed-table control is sound: `containsExactly("rejects")` would fail if the scan were empty, because every live relation would then disagree. The article is fixed, the javadoc is reworded, `tensionKinds()` is removed and `KindTypes` is package-private. The production table is unchanged.

**Issue 1 (MAJOR): the factory-side control still cannot see a regression of Issue 1.** EndpointTableAgreementTest.java:56-60 and 201-216.

- `aTooWideFactoryIsCaught` asserts only that `requires` and `overrides` *disagree*. Each relation in `TooWide` has a single factory. If its expansion were empty, the empty set would still differ from the non-empty row.
- Revert `leaves()` to the cycle-1 code: `Object` gives an empty set, `Kind` is not expanded, and only `getBounds()[0]` is used. With that code, `requires(KindRef<?>, KindRef<? extends Kind>)` and `overrides(<K extends Kind & Overridable>)` both expand to nothing. The test still passes.
- Only `rejects` (Advisor x AntiPattern, a true superset of G x AntiPattern) shows real widening, and the old code already handled that case.
- Nothing therefore guards the fix for Issue 1. (Cycle 1's suggested wording, "assert that each disagrees", had the same hole.)
- Fix: assert that each TooWide relation accepts a pair outside its row, i.e. `accepted − row` is not empty. Better, pin the expanded sets: `requires` gives every NodeKind x every NodeKind, and `overrides` gives the diagonal of `Overridable`'s leaves only. Either form fails under the old `leaves()`.

**Issue 2 (MEDIUM): the neighbour refusals miss the boundaries that cycle 1 named.** EndpointRuleTest.java:44-79.

- `neighbourOf` keeps the first row's source and varies only the target. So no relation's source-side boundary is tested. For example, `skill → anti_pattern` for `rejects` is never bound.
- The diagonal exclusions are not tested: `mission_type → mission_type` for `enhances` and `step_enhancement → step_enhancement` for `overrides`. Nor is `action → styleguide` for `applies`.
- The code assertion is `isIn(ENDPOINT_NOT_ALLOWED, OVERRIDES_SOURCE_NOT_ACTIVATABLE)`, under an `if` that is always true for the generated neighbours. The neighbour's source is always a legal row source, so the code must be `relationship_endpoint_not_allowed`. Pin it, and drop the `if`.
- Fix: add the four named neighbours, pinned to `relationship_endpoint_not_allowed`. Also give each relation a source-side neighbour: a source kind outside the row with a target that is in it.

**Issue 3 (MINOR): wrap fallout.** EndpointTable.java:13-15 leaves "by" alone on a line. Join it with `{@code EndpointTableAgreementTest}.`

**Issue 4 (MINOR): commit message.** Cycle 1 asked that the next commit message name EndpointTable.java and KindTypes.java as out-of-map files. bb60f7d does not. Say so in the next commit.

**Low, record only.** The odd-shape scan matches only parameters whose raw type is `KindRef`. A varargs `KindRef<?>...` or a `List<KindRef<?>>` factory would escape it. Add `KindRef[]` to the check if it is cheap; otherwise leave it.
