# Decision Moment `01M6RN5SG19KXAHH4VGT9QBYA1`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.posttasks.timing-gates`
- **Input key:** `timing_gates`
- **Status:** `resolved`
- **Created:** `2026-11-06T14:46:02.497586+00:00`
- **Resolved:** `2026-11-06T14:46:04.792598+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

NFR-003/NFR-008 failing gate

## Options

_(none)_

## Final answer

The build-failing gate is a deterministic operation-count budget in check (one compose per read, walk node visits bounded by the closure size, one in-force resolution per write); wall time is measured in timingBudgets and reported at 1x and 3x, report-only per ADR-0057 (runner variance ~2x). The NFR-008 end state (registration_timed_out at 60 s + 30 s) is a deterministic fake-clock test.

## Rationale

_(none)_

## Change log

- `2026-11-06T14:46:02.497586+00:00` - opened
- `2026-11-06T14:46:04.792598+00:00` - resolved (final_answer="The build-failing gate is a deterministic operation-count budget in check (one compose per read, walk node visits bounded by the closure size, one in-force resolution per write); wall time is measured in timingBudgets and reported at 1x and 3x, report-only per ADR-0057 (runner variance ~2x). The NFR-008 end state (registration_timed_out at 60 s + 30 s) is a deterministic fake-clock test.")
