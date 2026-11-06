# Decision Moment `01M6RA86QPBM1VE5QZTB9AZ195`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruleset.a6-override-gate`
- **Input key:** `override_gate`
- **Status:** `resolved`
- **Created:** `2026-11-06T11:35:07.254757+00:00`
- **Resolved:** `2026-11-06T11:35:09.756626+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

A6 extendable vs overrides edge

## Options

_(none)_

## Final answer

Same gate for both override paths: only open artifacts can be overridden; an overrides edge onto an extendable or locked target is refused at registration and not applied at read.

## Rationale

_(none)_

## Change log

- `2026-11-06T11:35:07.254757+00:00` - opened
- `2026-11-06T11:35:09.756626+00:00` - resolved (final_answer="Same gate for both override paths: only open artifacts can be overridden; an overrides edge onto an extendable or locked target is refused at registration and not applied at read.")
