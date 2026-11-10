# Decision Moment `01M713B6WEH5WGNSF3CCY2PV86`

- **Mission:** `adr-0066-step-enhancement-service-alignment-01M6ZRYK`
- **Origin flow:** `plan`
- **Slot key:** `plan.closeout.d5_violation_pointer`
- **Input key:** `d5_violation_pointer`
- **Status:** `resolved`
- **Created:** `2026-11-09T21:27:35.566229+00:00`
- **Resolved:** `2026-11-09T21:27:37.939225+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

D-5: RefusalViolation.pointer carries a pack-relative file path (not RFC 6901) on registration violations; accept or add a file member?

## Options

- accept for 0.x and document
- dedicated file member

## Final answer

Accept the pointer overload for 0.x and document it ("…or pack when the fault is the pack's as a whole").

## Rationale

Operator ruling 2026-11-09 on the pre-merge squad decisions, relayed by the orchestrator session: "follow recommendations, except for 1. I want all errors in a single report".

## Change log

- `2026-11-09T21:27:35.566229+00:00` - opened
- `2026-11-09T21:27:37.939225+00:00` - resolved (final_answer="Accept the pointer overload for 0.x and document it ("…or pack when the fault is the pack's as a whole").")
