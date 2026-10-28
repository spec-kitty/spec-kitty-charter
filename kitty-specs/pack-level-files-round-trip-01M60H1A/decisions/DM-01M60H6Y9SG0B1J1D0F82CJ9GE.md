# Decision Moment `01M60H6Y9SG0B1J1D0F82CJ9GE`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.preset_shape`
- **Input key:** `preset_shape`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:54:59.513876+00:00`
- **Resolved:** `2026-10-28T05:55:02.649056+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How far does the mission go with presets: round-trip as written, or the sanitized shape with the per-item must-have mark?

## Options

_(none)_

## Final answer

Sanitized shape with the per-item mark: schema_version, id (= file stem), description, activated_kinds, activated_<plural> and activated_mission_types lists of {id, must_have?} items; closed schema preset.schema.json.

## Rationale

Operator requirement 2026-10-28 (sanitized DSOs) and the #195 ruling (a mark on each item). The as-written shape cannot carry the mark, so #206 would break the same files twice (squad F5). No contract surface carries presets (only @WIP journey prose).

## Change log

- `2026-10-28T05:54:59.513876+00:00` — opened
- `2026-10-28T05:55:02.649056+00:00` — resolved (final_answer="Sanitized shape with the per-item mark: schema_version, id (= file stem), description, activated_kinds, activated_<plural> and activated_mission_types lists of {id, must_have?} items; closed schema preset.schema.json.")
