# Decision Moment `01M8A9W7G09TCRETWG48M9VRCG`

- **Mission:** `adr-0082-graph-walk-slices-01M8A867`
- **Origin flow:** `plan`
- **Slot key:** `plan.wire.stray-step-code`
- **Input key:** `stray_step_code`
- **Status:** `deferred`
- **Created:** `2026-11-25T21:30:36.416912+00:00`
- **Resolved:** `2026-11-25T21:30:47.481480+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Q-C: The stray step link (another pack's requires edge to an action that is no step of the mission type in force) has no registration twin and no refusal code. Answer it with pack_reference_artifact_missing plus a mission_step_unlinked violation (C37 precedent, no new code), or add a new refusal code (a closed-enum addition)?

## Options

- pack_reference_artifact_missing + violation mission_step_unlinked (recommended)
- New refusal code
- Other

## Final answer

_(none)_

## Rationale

Sent to the operator through the dispatcher on 2026-11-25; only WP04 depends on it, so WP01 to WP03 proceed.

## Change log

- `2026-11-25T21:30:36.416912+00:00` - opened
- `2026-11-25T21:30:47.481480+00:00` - deferred
