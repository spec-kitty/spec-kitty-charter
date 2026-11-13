---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T23:05:48Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP09
---

# WP09 review feedback, cycle 1

**Verdict: changes requested (one HIGH, one MEDIUM; LOWs folded).**

## HIGH H1: the context's `artifact` link can name another version than `effective-artifact`, or 404

`EffectiveReadService.java:~420` names `session.stepPlace(...)` (the winner of `action:M/s`) for every listed step.
Action nodes come only from graph documents, so the winner of `action:M/s` is often not the winner of the mission type
even when the winning type's yaml declares the step.

- Probe 1: proj wins `software-dev` and declares `specify`; only main's graph declares `action:software-dev/specify`.
  Context `effective-artifact` → proj's step; `artifact` → main's step. Contradicts the new `EffectiveActionContext.yaml`
  sentence "Both answer the step the context starts from". Before WP09 both named proj.
- Probe 2: main's graph declares `action:software-dev/implement`, main's type lacks `implement`, proj's winning type
  declares it. `artifact` → `/packs/main/.../steps/implement` → **404 action_not_found**. A broken link, a regression.

Required fix (option a): one rule for the step's place, in the session: the winner of `mission_type:M` when that version
declares `s` (among its steps or on-demand steps), else the winner of `action:M/s`. `EffectiveReadService.stepPlace`,
the step read and the context's `artifact` link use it. Add probe 1 and probe 2 as scenarios in
`effective-mission-types.feature`: every link answers 200 and both context links answer the same step.

The prompt read (WP07, `ClosureView.effectivePrompt`) also places the step by the winner of `action:M/s`; whether it
moves to the same rule is escalated to the operator (see below). Do not change the prompt read in this cycle.

## MEDIUM M1: `contracts/charter/responses/ActivationMissionStepNotFound.yaml` is stale

- description → "then action_not_found for a step no version of the closure holds, judged as getEffectiveActionContext
  judges it";
- example summary → "No version of the mission type in the closure holds such a step.";
- example detail → the new message. Record it as out-of-map.

## LOW (fold in this cycle)

- L1: `getEffectiveMissionStepContract` offers its `step` link only when the winning type declares the step
  (`EffectiveMissionTypeReadService.java:~143`). Offer it whenever the step read answers 200 (the place rule). Update
  the D11 scenario wording accordingly.
- L3: the step path description (`activations_..._steps_stepId.yaml`): "here the step is the winning type's…" and
  "every link stays under the activation" now have the place exception; say so.
- L4: add a missions-edge unit test pinning a place step's skeleton links to the place pack's `getPackMissionSkeleton`
  (mutation M5 survived the unit suite).
- L2 (intermediate commit 72a7ce1a7 not compiling alone): accepted, recorded in ff4a8d556.

Docs files owned by WP08 (`hypermedia-hateoas.md:155`, `service-contract.md:72`) are handled in WP08.
