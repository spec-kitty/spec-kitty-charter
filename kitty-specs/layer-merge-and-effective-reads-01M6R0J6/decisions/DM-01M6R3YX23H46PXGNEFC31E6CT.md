# Decision Moment `01M6R3YX23H46PXGNEFC31E6CT`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.floor.write-rule`
- **Input key:** `required_write_rule`
- **Status:** `resolved`
- **Created:** `2026-11-06T09:45:10.979797+00:00`
- **Resolved:** `2026-11-06T09:51:44.161853+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

Should the write rule allow a deny on a required T when an active override/enhancement of it is in force?

## Options

- Leave writes unchanged here; follow-up issue
- Change the write rule in this mission
- Other

## Final answer

Relax it in this mission, override only: a deny on a required T is accepted when an active override of T is in force and T is not locked. Overrides are not permitted on locked artifacts.

## Rationale

_(none)_

## Change log

- `2026-11-06T09:45:10.979797+00:00` - opened
- `2026-11-06T09:51:44.161853+00:00` - resolved (final_answer="Relax it in this mission, override only: a deny on a required T is accepted when an active override of T is in force and T is not locked. Overrides are not permitted on locked artifacts.")
