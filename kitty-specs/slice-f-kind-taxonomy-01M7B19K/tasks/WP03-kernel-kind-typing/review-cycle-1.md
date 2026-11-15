---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-13T21:10:26Z'
reviewer_agent: claude-reviewer
wp_id: WP03
---

# WP03 review, cycle 1: changes requested

The production side is right: EndpointTable equals data-model.md's table row by row. G is the six guidance kinds. Same-kind `enhances` is the 11 activatable kinds without mission_type and step_enhancement. `overrides` is the 12 activatable kinds without step_enhancement. `vocabulary` takes every kind but guard and lifecycle. The retired relations are empty.

The factories are exact rectangles. KindRef is invariant, its constructor is private and it has only leaf factories, so no group-typed ref exists. `-Xlint:all -Werror` turns raw types and unchecked casts into compile errors. The same-kind generics refuse two different leaves, and two wildcard captures as well.

The faults are in the guard. WP03 makes the reflection test the guard against loopholes, and the test has a blind spot and a vacuous control.

**Issue 1 (MAJOR): EndpointTableAgreementTest cannot see the widest factories.**

- `leaves()` (EndpointTableAgreementTest.java:125-143) does not expand `Kind` (`next != Kind.class`, line 138). It also returns an empty set for `Object`, which is the bound of `?`.
- So a factory `requiresAnything(KindRef<?>, KindRef<?>)` or `(KindRef<? extends Kind>, …)` adds no pairs, and the test still passes. That is the regression the test exists to catch (D-B1).
- Methods whose shape differs are skipped silently, without a failure (lines 66-72). An example is a 3-arg factory that takes a KindRef.
- A type variable uses only `getBounds()[0]`, so `<K extends Kind & Overridable>` would expand from `Kind` alone.

Fix:
- Expand `Kind` like any sealed type.
- Treat `Object` and an unbounded wildcard as every kind.
- Intersect the leaf sets of all bounds.
- Fail any public static method of Relationship that takes a `KindRef` parameter but is not the two-KindRef shape.

**Issue 2 (MAJOR): the positive control is vacuous** (lines 42-50).

- `assertThat(accepted.get(REJECTS)).isNotEqualTo(skewed)` holds by construction.
- It does not run the comparison the main test uses. It would also pass if `acceptedByFactories()` returned an empty map, because then `null` is not equal to `skewed`.

Fix:
- Factor the comparison into one helper, `assertAgrees(accepted, table)`.
- Assert `assertThatThrownBy(() -> assertAgrees(accepted, skewedTable)).isInstanceOf(AssertionError.class)`.
- Add a factory-side control: run the same expansion over a test-only fixture class whose factory is too wide. One example is `rejects(KindRef<? extends Advisor>, KindRef<AntiPattern>)`. Another is one bounded by `Kind` or `?`. Assert that each disagrees with the table. The second shows that Issue 1 is closed.

**Issue 3 (MEDIUM): EndpointRuleTest does not refuse a neighbour per row, as T015 requires.**

- Only four refusals are tested. Add a refusal for each relation: one pair adjacent to a row and not in it. For example, `skill → anti_pattern` for `rejects`, `action → styleguide` for `applies`, `mission_type → mission_type` for `enhances`, and `step_enhancement → step_enhancement` for `overrides`. Assert `relationship_endpoint_not_allowed`.
- Remove the empty `if` block and its comment in `everyRowBinds` (EndpointRuleTest.java:31-33). It does nothing.

**Issue 4 (MINOR): grammar and layout.**

- `Fault.message()` (EndpointRule.java:55) produces "a overrides edge", "a applies edge" and "a implemented_by edge". The test at EndpointRuleTest.java:61 pins the error. Reword the message, for example to "an edge of relation <r> may not connect <s> to <t>", or choose the article. The message will reach clients through WP05.
- In the Relationship javadoc, "Builds a {@code enhances|overrides|applies|instantiates|implemented_by}" needs "an".
- In KindRef, "A reference to a {@code agent_profile|anti_pattern|asset}" needs "an".
- Wrap the javadoc at 120 columns, as the rest of the file is wrapped: Relationship.java:131, 165, 187, 198, 253, 265, 277 and 299; EndpointTable.java:13; kinds/Advisor.java:4; kinds/MissionStepContract.java:3; kinds/StepEnhancement.java:3.

**Issue 5 (MINOR): dead surface.**

- `EndpointRule.tensionKinds()` (EndpointRule.java:107) has no caller, not even in a test. Remove it.
- `KindTypes` is used only inside `kernel.domain` and its tests, and `leafOf` only in a test. Make the class package-private, or say who outside needs it.

**No change needed (recorded):**

- EndpointTable.java and KindTypes.java are outside the owned_files list. They are new, in the same package and package-scoped, and no other WP touches them, so they are acceptable. Name them as out-of-map in the next commit's message.
- `KindRef.of(Urn)` is omitted. `EndpointRule.bind` is the bridge, so the omission is fine.
- The Activatable group is replaced by Overridable and Enhanceable, which are the exact rectangles.
- `Fault.relation` is a String so that it can carry `in_tension_with`.
- StepEnhancementRules comes first in the fault order. It lives in packs.domain, so the caller (WP05) orders it.
- The commit is SSH-signed, with no Co-Authored-By trailer and no model name.
