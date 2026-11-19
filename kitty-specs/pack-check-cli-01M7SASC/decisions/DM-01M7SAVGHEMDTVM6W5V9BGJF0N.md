# Decision Moment `01M7SAVGHEMDTVM6W5V9BGJF0N`

- **Mission:** `pack-check-cli-01M7SASC`
- **Origin flow:** `specify`
- **Slot key:** `specify.output.shape`
- **Input key:** `output_shape`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:20:36.142537+00:00`
- **Resolved:** `2026-11-19T07:48:22.835070+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does pack-check write on stdout?

## Options

- A PackCheckReport envelope (checks run, packs, problems[] each a contract Problem), schema in contracts
- A bare JSON array of Problems, checks-run on stderr
- Other

## Final answer

A: PackCheckReport envelope as an additive contract schema; warnings on stderr (operator via orchestrator, 2026-11-19)

## Rationale

_(none)_

## Change log

- `2026-11-19T07:20:36.142537+00:00` - opened
- `2026-11-19T07:48:22.835070+00:00` - resolved (final_answer="A: PackCheckReport envelope as an additive contract schema; warnings on stderr (operator via orchestrator, 2026-11-19)")
