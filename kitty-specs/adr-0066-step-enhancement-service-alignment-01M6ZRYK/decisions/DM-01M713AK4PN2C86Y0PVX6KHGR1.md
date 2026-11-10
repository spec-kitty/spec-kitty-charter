# Decision Moment `01M713AK4PN2C86Y0PVX6KHGR1`

- **Mission:** `adr-0066-step-enhancement-service-alignment-01M6ZRYK`
- **Origin flow:** `plan`
- **Slot key:** `plan.closeout.d1_drg_schema_error_shape`
- **Input key:** `d1_drg_schema_error_shape`
- **Status:** `resolved`
- **Created:** `2026-11-09T21:27:15.350848+00:00`
- **Resolved:** `2026-11-09T21:27:18.026668+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

D-1: registration answers drg_schema_error as one {pointer: pack} violation on the first graph fault; describe it, or list every graph fault at its file?

## Options

- (a) describe truthfully, no behaviour change
- (b) collect every graph fault, one violation per fault at its file

## Final answer

(b): the registration load collects every graph fault and answers them all in one refusal report, one violation per fault at its file; red-first scenario + unit test; contract text and a ### fragment (Breaking if a documented response changes); pointer convention per D-5; escalate if it widens beyond the registration load or touches PR C/#438 seams.

## Rationale

Operator ruling 2026-11-09 on the pre-merge squad decisions, relayed by the orchestrator session: "follow recommendations, except for 1. I want all errors in a single report".

## Change log

- `2026-11-09T21:27:15.350848+00:00` - opened
- `2026-11-09T21:27:18.026668+00:00` - resolved (final_answer="(b): the registration load collects every graph fault and answers them all in one refusal report, one violation per fault at its file; red-first scenario + unit test; contract text and a ### fragment (Breaking if a documented response changes); pointer convention per D-5; escalate if it widens beyond the registration load or touches PR C/#438 seams.")
