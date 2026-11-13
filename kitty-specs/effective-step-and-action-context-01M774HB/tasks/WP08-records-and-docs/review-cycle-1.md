---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T23:10:37Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP08
---

# WP08 review feedback, cycle 1

**Verdict: changes requested.**

## M1 (MEDIUM): the prompt read's refusal order says more than the code does

The code composes and merges the closure before it judges the names, so a closure that cannot compose or merge answers
its 500 before `mission_type_not_found`, `action_not_found` and the 409; only `prompt_slot_missing` comes after the 409.

- rules page E18 amendment (~l.1281-1285): "`404 activation_not_found`; then the closure's composition codes and the
  merge's `pack_unreadable` (the names are judged on the merged closure, so a closure that cannot compose or merge
  answers its `500` first); then `404 mission_type_not_found` and `404 action_not_found` as E16 judges them; then
  `409 mission_type_not_in_force`; then `500 prompt_slot_missing` (F6); every one before a `304`."
- ADR-0066 (~l.308): "It is judged after `activation_not_found`, `mission_type_not_found` and `action_not_found` (which
  need a closure that composes and merges), and before `prompt_slot_missing` and any `304`."
- service-contract.md (~l.329): "…then `409 mission_type_not_in_force`, then `500 prompt_slot_missing`; a closure that
  cannot compose or merge answers its `500` before the names are judged; all before a `304`."

## M2: two F8 pins named under the wrong class

`theEffectivePromptOfSoftwareDevPlanServesBothExampleFragmentsBuiltInFirst` and
`theTwinWithOnlyTheMissionTypeInForceTheSlotIsListedWithNoFragment` are in `ClosureViewTest`, not `EffectiveReadMapperTest`.

## M3 (conditional): A7 recorded as built before WP09 is approved

WP09 was rejected (cycle 1): the step's place becomes one rule (the winning mission type's pack when its version declares
the step, else the winner of `action:M/s`). Re-record A7, F8's `effective-artifact` wording, the changelog bullet and the
`getEffectiveMissionStep` amendment once WP09 is approved, with its final pins. Also update
`docs/development/hypermedia-hateoas.md:155` (context `artifact` = the step at its place) and
`docs/api/service-contract.md:72` ("One step of the winning mission type").

## Folded

#97 "closed as delivered" wording stands (closeout closes it); glossary stamp stale like prior main commits (no CI check);
blob/main changelog link (nightly report-only).
