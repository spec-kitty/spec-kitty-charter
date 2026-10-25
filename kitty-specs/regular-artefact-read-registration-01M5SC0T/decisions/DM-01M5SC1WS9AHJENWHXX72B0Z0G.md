# Decision Moment `01M5SC1WS9AHJENWHXX72B0Z0G`

- **Mission:** `regular-artefact-read-registration-01M5SC0T`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.reference-slice`
- **Input key:** `reference_slice`
- **Status:** `resolved`
- **Created:** `2026-10-25T11:10:10.217040+00:00`
- **Resolved:** `2026-10-25T11:21:57.942407+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which artefact kind should be the step-2 reference slice taken end-to-end through read + registration first?

## Options

- Tactic (extend existing read slice with registration + relationships)
- Toolguide (new kind, simplest, full add-a-kind + read + registration)
- Directive (dangling existing kind, prove read wiring + registration)

## Final answer

Directive is the step-2 reference slice. Directives are top-level DRG entry points (referenced by paradigms); tactics seldom are, so Directive better exercises the graph walk and relationship registration end to end.

## Rationale

_(none)_

## Change log

- `2026-10-25T11:10:10.217040+00:00` — opened
- `2026-10-25T11:21:57.942407+00:00` — resolved (final_answer="Directive is the step-2 reference slice. Directives are top-level DRG entry points (referenced by paradigms); tactics seldom are, so Directive better exercises the graph walk and relationship registration end to end.")
