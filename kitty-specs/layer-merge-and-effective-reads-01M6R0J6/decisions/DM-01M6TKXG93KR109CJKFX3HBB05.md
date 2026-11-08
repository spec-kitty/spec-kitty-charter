# Decision Moment `01M6TKXG93KR109CJKFX3HBB05`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.a0-squad.preset-unheld-member`
- **Input key:** `preset_unheld_member`
- **Status:** `resolved`
- **Created:** `2026-11-07T09:02:31.203530+00:00`
- **Resolved:** `2026-11-07T10:03:12.155197+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

A10 makes a governing preset with an unheld must-have item legal (the floor drops it), but applying that same preset answers 422 unknown_artifact (PresetRules.selected writes every member; ActivationPresetServiceTest.aPresetWhoseMemberTheClosureDoesNotHoldIs422 pins it). Should applying a preset drop members no pack holds and report them in dropped, as A4/A10 do elsewhere?

## Options

- Drop unheld members on apply and report them in dropped (consistent with A4/A10)
- Keep 422 unknown_artifact on apply (current)

## Final answer

Keep 422 unknown_artifact on apply (current)

## Rationale

Operator ruling on PR #414 (2026-11-07, comment 6705379031): applying a preset that lists a must-have item no pack holds stays refused, as the test pins today; no drop-and-report.

## Change log

- `2026-11-07T09:02:31.203530+00:00` - opened
- `2026-11-07T10:03:12.155197+00:00` - resolved (final_answer="Keep 422 unknown_artifact on apply (current)")
