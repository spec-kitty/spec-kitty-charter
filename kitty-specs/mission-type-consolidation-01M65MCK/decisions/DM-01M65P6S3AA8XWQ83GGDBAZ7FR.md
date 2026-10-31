# Decision Moment `01M65P6S3AA8XWQ83GGDBAZ7FR`

- **Mission:** `mission-type-consolidation-01M65MCK`
- **Origin flow:** `specify`
- **Slot key:** `specify.d1.kinds`
- **Input key:** `specify.d1.kinds`
- **Status:** `resolved`
- **Created:** `2026-10-30T05:58:29.226087+00:00`
- **Resolved:** `2026-10-30T05:58:31.548109+00:00`
- **Resolved by:** `Stijn Dejongh (operator), relayed by the orchestrator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

D1: How do mission steps enter the model, and does mission_step get a fragment plural?

## Options

- Two artifact kinds with a mission_steps plural (amend R14)
- Two kinds, no plural

## Final answer

Two kinds, no plural: the multiplicity is defined by the type (Set<MissionStep>); the DSO carries a plural field with sub-items. R14 / DIV-011 unchanged; mission_step_contract stays a separate kind.

## Rationale

Operator ruling 2026-10-30.

## Change log

- `2026-10-30T05:58:29.226087+00:00` — opened
- `2026-10-30T05:58:31.548109+00:00` — resolved (final_answer="Two kinds, no plural: the multiplicity is defined by the type (Set<MissionStep>); the DSO carries a plural field with sub-items. R14 / DIV-011 unchanged; mission_step_contract stays a separate kind.")
