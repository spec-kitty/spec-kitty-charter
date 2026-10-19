# Decision Moment `01M57QPDJYFCGD1SDR31C3X0R1`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `specify`
- **Slot key:** `specify.scenarios.bdd-layout`
- **Input key:** `bdd_layout`
- **Status:** `resolved`
- **Created:** `2026-10-18T14:47:17.342409+00:00`
- **Resolved:** `2026-10-18T14:49:01.350677+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Should #43's BDD layout (src/test/resources/features/, tests/scenarios/charter/) absorb contracts/charter/scenarios/?

## Options

- Keep contract scenarios beside the contract; #43 suites join them when bound
- Move contract scenarios into #43 layout
- Other

## Final answer

Keep contract scenarios in contracts/charter/scenarios/; #43's suites move to contracts/charter/scenarios/<cluster>/ when their harness is bound, renamed for the terms guard, keeping #43's house style and promise tags

## Rationale

_(none)_

## Change log

- `2026-10-18T14:47:17.342409+00:00` — opened
- `2026-10-18T14:49:01.350677+00:00` — resolved (final_answer="Keep contract scenarios in contracts/charter/scenarios/; #43's suites move to contracts/charter/scenarios/<cluster>/ when their harness is bound, renamed for the terms guard, keeping #43's house style and promise tags")
