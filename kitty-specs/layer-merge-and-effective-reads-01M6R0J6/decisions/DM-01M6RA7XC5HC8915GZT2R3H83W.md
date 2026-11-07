# Decision Moment `01M6RA7XC5HC8915GZT2R3H83W`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruleset.a4-replace`
- **Input key:** `stale_on_put`
- **Status:** `resolved`
- **Created:** `2026-11-06T11:34:57.669164+00:00`
- **Resolved:** `2026-11-06T11:35:00.095902+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

A4 GET->PUT round trip

## Options

_(none)_

## Final answer

A full replace judges the floor only on deny entries it adds, and also drops ids the packs no longer hold, reporting them, instead of 422.

## Rationale

_(none)_

## Change log

- `2026-11-06T11:34:57.669164+00:00` - opened
- `2026-11-06T11:35:00.095902+00:00` - resolved (final_answer="A full replace judges the floor only on deny entries it adds, and also drops ids the packs no longer hold, reporting them, instead of 422.")
