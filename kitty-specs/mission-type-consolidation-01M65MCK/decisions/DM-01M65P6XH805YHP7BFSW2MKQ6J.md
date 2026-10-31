# Decision Moment `01M65P6XH805YHP7BFSW2MKQ6J`

- **Mission:** `mission-type-consolidation-01M65MCK`
- **Origin flow:** `specify`
- **Slot key:** `specify.d1a.store-shape`
- **Input key:** `specify.d1a.store-shape`
- **Status:** `resolved`
- **Created:** `2026-10-30T05:58:33.768635+00:00`
- **Resolved:** `2026-10-30T05:58:35.747333+00:00`
- **Resolved by:** `Stijn Dejongh (operator), relayed by the orchestrator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

D1a: With steps as sub-items, how are steps outside the sequence held?

## Options

- on_demand_steps: a second plural field
- Drop the on-demand steps
- in_sequence flag inside steps

## Final answer

on_demand_steps: a second plural field of the same sub-item shape; one mission-type file per type; per-step Markdown companions beside it.

## Rationale

Operator ruling 2026-10-30: option (a), as proposed after D1 clashed with one-file-per-step; D4 amended to match (no per-step YAML files, only per-step Markdown companions).

## Change log

- `2026-10-30T05:58:33.768635+00:00` — opened
- `2026-10-30T05:58:35.747333+00:00` — resolved (final_answer="on_demand_steps: a second plural field of the same sub-item shape; one mission-type file per type; per-step Markdown companions beside it.")
