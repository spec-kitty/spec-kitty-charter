# Decision Moment `01M6YM02EG3X5V8G33T1HT5N0K`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-b-skipped-once`
- **Input key:** `pr-b-skipped-once`
- **Status:** `resolved`
- **Created:** `2026-11-08T22:20:53.072316+00:00`
- **Resolved:** `2026-11-08T22:20:55.600015+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

A required or locked artifact pruned by the enhancer or binding rule lands in requiredNotInForce and also in enhancedTargetNotInForce or missionTypeNotInForce, so summary.skipped counts it twice (squad D6). List it once, or document both?

## Options

- list it once: requiredNotInForce only
- document that it appears in both

## Final answer

list it once: requiredNotInForce only

## Rationale

Runner, 2026-11-08. The skipped report's description says each skipped artifact is listed once, in one bucket, and summary.skipped counts entries; requiredNotInForce already carries the reason.

## Change log

- `2026-11-08T22:20:53.072316+00:00` - opened
- `2026-11-08T22:20:55.600015+00:00` - resolved (final_answer="list it once: requiredNotInForce only")
