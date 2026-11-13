# Decision Moment `01M774HSKK0SSA4147QEG9ZCKR`

- **Mission:** `effective-step-and-action-context-01M774HB`
- **Origin flow:** `specify`
- **Slot key:** `specify.prompt-read.mission-type-not-in-force`
- **Input key:** `prompt_read_mission_type_not_in_force`
- **Status:** `resolved`
- **Created:** `2026-11-12T05:44:06.515272+00:00`
- **Resolved:** `2026-11-12T05:48:00.083765+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

OD-5 vs PR C: does the effective-prompt read answer 409 mission_type_not_in_force, or 200 with no fragments like the context read?

## Options

- 200 with no fragments, missionTypeInForce false (recommended)
- 409 mission_type_not_in_force (OD-5 as ruled)
- Other

## Final answer

409 mission_type_not_in_force: OD-5 stands (operator, 2026-11-12, relayed by the orchestrator). Record why the prompt read differs from the context read's 200-empty answer; add the refusal code and its change fragment.

## Rationale

_(none)_

## Change log

- `2026-11-12T05:44:06.515272+00:00` - opened
- `2026-11-12T05:48:00.083765+00:00` - resolved (final_answer="409 mission_type_not_in_force: OD-5 stands (operator, 2026-11-12, relayed by the orchestrator). Record why the prompt read differs from the context read's 200-empty answer; add the refusal code and its change fragment.")
