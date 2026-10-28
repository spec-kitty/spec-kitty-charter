# Decision Moment `01M60H79MAHAVFSPCBHDXFXW3Y`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.required_lists`
- **Input key:** `required_lists`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:11.114242+00:00`
- **Resolved:** `2026-10-28T05:55:13.835744+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What happens to required_<plural> lists in this mission?

## Options

_(none)_

## Final answer

A preset never had them; the closed preset schema refuses any required_* key. Migrating org-charter.yaml's required_<plural> lists and refusing them there stays in #206.

## Rationale

Operator ruling on #195: migrate then refuse, planned as its own mission after the epic.

## Change log

- `2026-10-28T05:55:11.114242+00:00` — opened
- `2026-10-28T05:55:13.835744+00:00` — resolved (final_answer="A preset never had them; the closed preset schema refuses any required_* key. Migrating org-charter.yaml's required_<plural> lists and refusing them there stays in #206.")
