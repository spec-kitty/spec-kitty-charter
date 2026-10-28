# Decision Moment `01M60H8A6NGJK8HW57CN8CTC8S`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.schemaless_rule`
- **Input key:** `schemaless_rule`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:44.469365+00:00`
- **Resolved:** `2026-10-28T05:55:47.238903+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What is the rule for kinds without a schema (ADR-0036 decision 6)?

## Options

_(none)_

## Final answer

Every file the round trip covers is a files rule with a closed schema (yaml), a payloads rule of a built non-yaml form, or an exclusion with a reason. A YAML file is never held without a schema. Derived fields are declared per kind in an ADR, never as a reader rule.

## Rationale

Closes ADR-0036 open question 3 together with the manifest rule.

## Change log

- `2026-10-28T05:55:44.469365+00:00` — opened
- `2026-10-28T05:55:47.238903+00:00` — resolved (final_answer="Every file the round trip covers is a files rule with a closed schema (yaml), a payloads rule of a built non-yaml form, or an exclusion with a reason. A YAML file is never held without a schema. Derived fields are declared per kind in an ADR, never as a reader rule.")
