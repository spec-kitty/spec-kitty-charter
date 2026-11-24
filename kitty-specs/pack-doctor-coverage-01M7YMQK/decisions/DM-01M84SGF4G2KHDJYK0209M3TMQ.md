# Decision Moment `01M84SGF4G2KHDJYK0209M3TMQ`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `full_references_gate`
- **Input key:** `full_references_gate`
- **Status:** `resolved`
- **Created:** `2026-11-23T18:08:21.648810+00:00`
- **Resolved:** `2026-11-23T18:08:24.581738+00:00`
- **Resolved by:** `claude`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

What gates REFERENCES under validate --full?

## Options

- Only composition faults (Recommended)
- Keep the broad gate

## Final answer

Only composition faults (Recommended)

## Rationale

Operator ruling R-aa (2026-11-23), source: operator (AskUserQuestion in this session), answer: "Only composition faults (Recommended)". REFERENCES runs whenever no own closure fails to compose (no CompositionRules fault); preset-item, contest and case-only faults no longer hide reference faults. ADR-0083 decision 8 and the guide are updated.

## Change log

- `2026-11-23T18:08:21.648810+00:00` - opened
- `2026-11-23T18:08:24.581738+00:00` - resolved (final_answer="Only composition faults (Recommended)")
