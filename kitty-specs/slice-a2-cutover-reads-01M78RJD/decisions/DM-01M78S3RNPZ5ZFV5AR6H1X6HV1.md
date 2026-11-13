# Decision Moment `01M78S3RNPZ5ZFV5AR6H1X6HV1`

- **Mission:** `slice-a2-cutover-reads-01M78RJD`
- **Origin flow:** `specify`
- **Slot key:** `specify.assets.in-force`
- **Input key:** `asset_in_force_rule`
- **Status:** `resolved`
- **Created:** `2026-11-12T21:02:41.334667+00:00`
- **Resolved:** `2026-11-12T21:14:35.711178+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Asset is not an activatable kind. When is an asset in force under an activation, for the asset twins?

## Options

- Held by a pack of the closure, layer override picks the winner
- Only when a guard in force names it
- Other

## Final answer

Held by the closure: every asset a pack of the activation's closure holds is in force; across levels the same id is a layer override (project over org over built-in, ADR-0053 d6); no selection, deny or cascade applies.

## Rationale

_(none)_

## Change log

- `2026-11-12T21:02:41.334667+00:00` - opened
- `2026-11-12T21:14:35.711178+00:00` - resolved (final_answer="Held by the closure: every asset a pack of the activation's closure holds is in force; across levels the same id is a layer override (project over org over built-in, ADR-0053 d6); no selection, deny or cascade applies.")
