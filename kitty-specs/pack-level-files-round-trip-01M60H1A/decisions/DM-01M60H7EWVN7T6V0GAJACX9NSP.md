# Decision Moment `01M60H7EWVN7T6V0GAJACX9NSP`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.empty_activation_list`
- **Input key:** `empty_activation_list`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:16.507829+00:00`
- **Resolved:** `2026-10-28T05:55:19.447032+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How is an empty activation list in a preset treated?

## Options

_(none)_

## Final answer

Refused by the schema (minItems 1). A kind is switched off only by leaving it out of activated_kinds.

## Rationale

Core reads [] as 'activate none' and absent as 'unrestricted', while ADR-0036 E2 makes them equal; refusing one spelling keeps both true without a comparator exception (squad F1, rule S6).

## Change log

- `2026-10-28T05:55:16.507829+00:00` — opened
- `2026-10-28T05:55:19.447032+00:00` — resolved (final_answer="Refused by the schema (minItems 1). A kind is switched off only by leaving it out of activated_kinds.")
