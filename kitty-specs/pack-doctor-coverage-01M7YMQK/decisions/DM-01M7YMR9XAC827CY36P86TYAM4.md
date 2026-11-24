# Decision Moment `01M7YMR9XAC827CY36P86TYAM4`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `specify`
- **Slot key:** `specify.presets.refusal`
- **Input key:** `preset_refusal`
- **Status:** `resolved`
- **Created:** `2026-11-21T08:49:48.970570+00:00`
- **Resolved:** `2026-11-21T08:49:51.134954+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

#629/#632.1: do registration and pack-check refuse an unheld must_have and an undeclared preset id?

## Options

_(none)_

## Final answer

R-a Both refuse: registration and pack-check refuse a must_have no pack of the declaring pack's depends_on closure holds, and an activated_* id no closure pack declares as that kind; supersedes A10/C31 and the 2026-11-06 ruling for writes; reads keep reporting requiredNotHeld

## Rationale

_(none)_

## Change log

- `2026-11-21T08:49:48.970570+00:00` - opened
- `2026-11-21T08:49:51.134954+00:00` - resolved (final_answer="R-a Both refuse: registration and pack-check refuse a must_have no pack of the declaring pack's depends_on closure holds, and an activated_* id no closure pack declares as that kind; supersedes A10/C31 and the 2026-11-06 ruling for writes; reads keep reporting requiredNotHeld")
