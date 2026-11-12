---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T18:50:53Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP05
---

# WP05 review, cycle 1 (reviewer-renata): changes requested

The work is close. The pure rule, `requireSlots`, the read session, the operations map, `RefusalHandler`, the
fragments and the twin matrix all hold up. Every mutation I planted was caught (details at the end). One blocking
issue remains: the registration gate's skip of an activation with no before view goes against rule G21.

## Issue 1 (MEDIUM, blocking): the gate skips an activation whose closure did not compose before the write, which G21 does not allow

- `service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapter.java:96`
  `if (change.before().isEmpty()) { return; }`, with its Javadoc at l.78-79.
- `service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapterTest.java:307`
  `anActivationThatCouldNotBeReadBeforeTheChangeIsNotBlamedOnIt` pins this behaviour.
- `contracts/charter/changes/2026-11-12-prompt-slot-missing-raised.md:16` publishes it: "An activation whose closure
  could not be composed before the write is not judged (G21)."

G21 says: a registration into a closure that does not compose today "is let through when the closure composes after
it (G8 holds for the new state); one that leaves it broken is refused as before. A write that fixes a broken compose is
not blocked by the breakage it fixes." The rule exempts a write only from the breakage it fixes. It never exempts a
write whose new state fails a compose check. The gate makes C38 part of what must hold for the new state. A repair
registration, where the before closure did not compose and the after closure composes but leaves a stored activation
with an in-force enhancement whose slot is missing, therefore leaves the closure broken and must be refused. Today it is
let through, and every composing read of that activation then answers `500 prompt_slot_missing`. Refusing that at
write time is what FR-001 and OD-4 (i) ask for. The case where the activation "held none of its packs" (its first pack
is registered) is the same: G8 judges every closure in `Aff(z)`, whatever its before state.

The phrase "already failed before the change is not blamed" (research R7, squad A2) is a paraphrase. The orchestrator
has confirmed that the docs WP will correct it. G21 is the model.

The other half of the decision is right and should stay as it is. An activation that composed before but already
lacked a slot is refused by any write that leaves it broken, and a write that fixes it is not blocked. The
`putPackRelationship` scenario (unrelated `suggests` edge, l.595) shows the "leaves it broken" branch correctly.

**Fix:**
1. Remove the `change.before().isEmpty()` early return. The gate then judges the after view of every activation whose
   closure the write joins. The merge-refusal skip (`after.inForce` throwing) stays (decision 2, see below).
2. Turn `anActivationThatCouldNotBeReadBeforeTheChangeIsNotBlamedOnIt` into a failing fixture: a change with
   `Optional.empty()` before and an after closure without the slot is refused `PROMPT_SLOT_MISSING`, naming `a-1`.
   Add its twin: the same change where the after closure declares the slot passes (G21: a repair write is let through).
3. Correct the Javadoc of `ActivationImpactAdapter.requireComposable` (l.74-81) and the sentence in the Breaking
   fragment (l.16). If any other Javadoc repeats "not blamed", correct it too.

I probed the fix. With the early return removed, `:activations:test` fails only the pinned test above, and the whole
`CharterContractSuite` stays green. No other behaviour depends on the skip.

## Judgements on the decisions you were asked about (no change needed)

- **Decision 2 (merge refusals skipped by both gates): sound.** G8 records that C21 is judged at read, not at
  registration. A closure whose merge refuses cannot be judged for slots. Its reads answer the merge's code first (F6),
  and a write keeps the answer its own rules gave before. Both catches are documented and limited to the resolution.
  The slot refusal itself is never caught.
- **Decision 3 (overlap through `DocumentRules.unsatisfied` → `statesOf`): harmless.** It is the same code and gets the
  same per-operation 422. The precedence is deterministic. Only a document that both denies an unsatisfied required
  artifact and breaks a slot answers `422 prompt_slot_missing` instead of `409 artifact_required`. No rule orders those
  two codes on writes. Note that `warned()` also reaches `statesOf(after)`. When I removed the explicit pre-store check,
  the scenario's select still answered 422, and only the `PUT /activations/a-2` step failed. The unit tests in
  `ActivationServiceTest` do tell the paths apart, so this is not a gap. It is recorded only so that nobody reads the
  scenario as the proof for the select path.
- **`ActivationImpact.requireComposable(ClosureChanges)` rather than `(ClosureChange)`: agreed.** One `ClosureChange`
  cannot tell the adapter which stored activation it belongs to. `ClosureChanges` is the same root-set lookup the
  notices already take. It is built once per check, and the adapter, which owns the stored activations, keeps the
  iteration.

## Non-blocking notes (LOW, fold in if convenient)

- `contracts/charter/scenarios/step-enhancement-slots.feature:841`: the red step "ETag is the stored ETag" was wrong.
  `getActivation`'s ETag is the closure tag, not the revision, so I confirmed that replacing it is not a weakening.
  "Nothing stored" now rests on the absent `step_enhancement` field alone. A stronger form is
  `When I remember the stored file of the activation "a-1"` before the write, then
  `the stored file of the activation "a-1" is the remembered one`.
- `contracts/charter/changes/2026-11-12-prompt-slot-missing-wording.md` is not in `owned_files`. The split is correct,
  since the fragments README requires exactly one `###` block per file. Mention it in the next commit message, in the
  same way as the out-of-map files.
- `ActivationImpactAdapter.java:164`: write `java.util.Comparator` as an import, not a qualified name inline.

## Checklist

- (a) Failing fixture and twin per surface: PASS (8 scenarios, all green). Mutations: (1) `SlotCompose.missing`
  always empty → 6 unit tests and all 4 failing scenarios fail. (2) `activations.requireComposable(joined)` removed →
  3 `RegistrationImpactTest` cases and the registration and relationship scenarios fail. (3) pre-store
  `session.requireComposable` removed from `ActivationWrites` and `ActivationService` → 5 `ActivationServiceTest`
  cases and the activation-write scenario fail. All reverted.
- (b) Only in-force enhancements refuse, and only the winning copy: PASS (read twin with the enhancement held but
  unselected → 200; `SlotComposeTest` covers not in force and the winning copy).
- (c) No refusal swallowed: PASS. Only merge refusals are skipped, by design.
- (d) Contract `operations` map (19) = `IMPACT_CHECKED_WRITES` + `getPackRegistration` + `ACTIVATION_WRITES` in
  `RefusalHandler`: PASS (19 `errors.feature` pins green).
- (e) No weakened assertion: PASS (see the ETag note).
- (f) `445b2489d` and `4de89d301` are signed (gpgsig present), with no trailer and no model name. `ClosureViewSession`,
  `PacksSession` and `FakeAvailablePacks` are recorded as out-of-map. The lane-sync merge `2f959fabb` and the
  status commits are tool-made and unsigned: noted, not held against the WP.
- (g) Javadoc and style: PASS (spotless, checkstyle and javadoc green for packs and activations).
- Anti-pattern checklist: dead code PASS; synthetic fixture PASS; silent empty return PASS (every early return is
  documented; Issue 1 is about one of them); FR coverage PASS; frozen surface PASS (no slice-B file touched); locked
  decision PASS except Issue 1 against G21; shared-file ownership PASS (out-of-map recorded); production fragility PASS.
- Gradle: `:packs:test`, `:activations:test`, `:web-transport:test` and `:deployable:test --tests '*CharterContractSuite*'`
  are all green on `2f959fabb`.
