# Decision Moment `01M6RA9BKJY7X9BFV93QW1KM1T`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruleset.b1-pins`
- **Input key:** `version_pins`
- **Status:** `resolved`
- **Created:** `2026-11-06T11:35:45.010332+00:00`
- **Resolved:** `2026-11-06T11:35:47.345925+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

B1 version pins

## Options

_(none)_

## Final answer

Writes drop unsatisfiable version pins and report them in dropped; keep a per-pack version->content-digest history so a version can never be reused with other content; reads report an unheld pin in the skipped report.

## Rationale

_(none)_

## Change log

- `2026-11-06T11:35:45.010332+00:00` - opened
- `2026-11-06T11:35:47.345925+00:00` - resolved (final_answer="Writes drop unsatisfiable version pins and report them in dropped; keep a per-pack version->content-digest history so a version can never be reused with other content; reads report an unheld pin in the skipped report.")
