---
affected_files: []
cycle_number: 3
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-13T06:08:40Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP09
---

# WP09 reopened: operator ruling Q3 (2026-11-13)

**Ruling:** the prompt read follows the one place rule (`ClosureView.stepPlace`), so the step read, the action
context and the prompt read agree on which pack serves a step. This amends research R8.

## Required

1. `ClosureView.effectivePrompt(missionType, action, states)`: the prompt in force is the stored prompt companion at
   the step's place by `stepPlace` (the winner of `mission_type:M` when its version declares the step, else the winner
   of `action:M/s` when its version declares it), not at the winner of `action:M/s`. A step with no place keeps today's
   refusal (`action_not_found`, judged before as the context judges it). Keep the closure-fingerprint check.
2. Red first: a scenario in `effective-step-prompt.feature` (or the H1 scenarios in `effective-mission-types.feature`)
   with probe 1's shape: proj wins `software-dev`, declares `specify` with its own prompt; only main's graph declares
   `action:software-dev/specify`. The prompt read answers proj's prompt, `promptPackId: proj`, and its
   `effective-artifact` link answers the same step. Retitle the "WP09, A7 …" scenario if its wording no longer fits.
   A `ClosureViewTest` pin for the same case, plus its twin.
3. Contract wording: the prompt path description, `EffectiveStepPrompt.promptPackId`, and the WP09 change fragment
   say the prompt is at the step's place. SC-001 on the example packs must still pass unchanged.

## Folds

- L-a: an `EffectiveMissionTypeReadServiceTest` pinning that `stepContract` passes on a refusal other than
  `action_not_found` from the place read (fake `stepPlace` throws `STALE_INPUT` → `stepContract` refuses `STALE_INPUT`).
- WP07 contract refusal-order wording (out-of-map, record it): in
  `contracts/charter/paths/activations_activationId_missionTypes_missionType_actions_action_prompt.yaml` and
  `contracts/charter/changes/2026-11-12-effective-step-prompt.md`, the order is: `404 activation_not_found`; a closure
  that cannot compose or merge answers its `500` (composition codes, `pack_unreadable`) before the names are judged;
  then `404 mission_type_not_found`, `404 action_not_found`; then `409 mission_type_not_in_force`; then
  `500 prompt_slot_missing`; all before a `304`.
