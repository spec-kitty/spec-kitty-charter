# Decision Moment `01M7TS5S0KZYWF2ZGYG9WPXJYT`

- **Mission:** `closure-checks-behind-ports-01M7TS43`
- **Origin flow:** `specify`
- **Slot key:** `specify.wire.collected-refusal`
- **Input key:** `refusal_shape`
- **Status:** `resolved`
- **Created:** `2026-11-19T20:50:06.995705+00:00`
- **Resolved:** `2026-11-19T21:10:35.375089+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `claude::java-jenny`
- **Other answer:** `false`

## Question

D1: How does one HTTP refusal carry the collected faults?

## Options

- (a) problem code = first fault (fixed rule order), violations[] = every fault with its own code, packs = union (additive contract change)
- (b) new catch-all code closure_invalid with violations
- (c) keep one fault per refusal at the API; collect only internally

## Final answer

(a) Operator 2026-11-19: the problem code is the first fault's specific code; violations[] lists every fault; packs is the union. Recorded as an additive Changed entry.

## Rationale

_(none)_

## Change log

- `2026-11-19T20:50:06.995705+00:00` - opened
- `2026-11-19T21:10:35.375089+00:00` - resolved (final_answer="(a) Operator 2026-11-19: the problem code is the first fault's specific code; violations[] lists every fault; packs is the union. Recorded as an additive Changed entry.")
