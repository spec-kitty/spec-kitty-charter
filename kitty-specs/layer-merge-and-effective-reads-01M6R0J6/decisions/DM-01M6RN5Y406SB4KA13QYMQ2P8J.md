# Decision Moment `01M6RN5Y406SB4KA13QYMQ2P8J`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.posttasks.version-pin-create`
- **Input key:** `version_pin_create`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:46:07.232471+00:00`
- **Resolved:** `2026-11-06T14:46:09.558724+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

version_not_held on create vs the B1 write-time drop

## Options

_(none)_

## Final answer

createActivation keeps 422 version_not_held for a pin in its own body that no pack satisfies (input error, nothing stored yet); replace and the per-artifact and preset writes drop an unsatisfiable pin into ActivationChange.dropped (B1).

## Rationale

_(none)_

## Change log

- `2026-11-06T14:46:07.232471+00:00` - opened
- `2026-11-06T14:46:09.558724+00:00` - resolved (final_answer="createActivation keeps 422 version_not_held for a pin in its own body that no pack satisfies (input error, nothing stored yet); replace and the per-artifact and preset writes drop an unsatisfiable pin into ActivationChange.dropped (B1).")
