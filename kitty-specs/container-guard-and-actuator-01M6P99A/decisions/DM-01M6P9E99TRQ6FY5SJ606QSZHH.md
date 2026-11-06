# Decision Moment `01M6P9E99TRQ6FY5SJ606QSZHH`

- **Mission:** `container-guard-and-actuator-01M6P99A`
- **Origin flow:** `specify`
- **Slot key:** `specify.guard.health-exemption`
- **Input key:** `health_exemption`
- **Status:** `resolved`
- **Created:** `2026-11-05T16:42:29.050790+00:00`
- **Resolved:** `2026-11-05T16:42:31.882207+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Which requests may reach health without the token?

## Options

_(none)_

## Final answer

Exactly GET /actuator/health (raw URI, no context path), container profile only; HEAD and near misses need the token (ADR-0041 D7, squad R3).

## Rationale

_(none)_

## Change log

- `2026-11-05T16:42:29.050790+00:00` — opened
- `2026-11-05T16:42:31.882207+00:00` — resolved (final_answer="Exactly GET /actuator/health (raw URI, no context path), container profile only; HEAD and near misses need the token (ADR-0041 D7, squad R3).")
