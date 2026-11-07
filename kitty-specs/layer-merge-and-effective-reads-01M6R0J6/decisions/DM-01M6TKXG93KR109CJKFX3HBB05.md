# Decision Moment `01M6TKXG93KR109CJKFX3HBB05`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.a0-squad.preset-unheld-member`
- **Input key:** `preset_unheld_member`
- **Status:** `deferred`
- **Created:** `2026-11-07T09:02:31.203530+00:00`
- **Resolved:** `2026-11-07T09:02:33.761645+00:00`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

A10 makes a governing preset with an unheld must-have item legal (the floor drops it), but applying that same preset answers 422 unknown_artifact (PresetRules.selected writes every member; ActivationPresetServiceTest.aPresetWhoseMemberTheClosureDoesNotHoldIs422 pins it). Should applying a preset drop members no pack holds and report them in dropped, as A4/A10 do elsewhere?

## Options

- Drop unheld members on apply and report them in dropped (consistent with A4/A10)
- Keep 422 unknown_artifact on apply (current)

## Final answer

_(none)_

## Rationale

Changes behaviour a test pins; operator ruling needed. Raised by the PR A0 pre-PR correctness squad (2026-11-07). Recommended: drop and report, for consistency with A4 and A10.

## Change log

- `2026-11-07T09:02:31.203530+00:00` - opened
- `2026-11-07T09:02:33.761645+00:00` - deferred
