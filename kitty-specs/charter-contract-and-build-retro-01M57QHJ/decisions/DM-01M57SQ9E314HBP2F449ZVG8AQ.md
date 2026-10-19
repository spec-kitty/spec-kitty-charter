# Decision Moment `01M57SQ9E314HBP2F449ZVG8AQ`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `plan`
- **Slot key:** `plan.rulings.scenarios-dir`
- **Input key:** `ruling_scenarios_dir`
- **Status:** `resolved`
- **Created:** `2026-10-18T15:22:43.011907+00:00`
- **Resolved:** `2026-10-18T15:33:02.646924+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Ruling on the contracts/charter/scenarios directory and suite classpath

## Options

_(none)_

## Final answer

Ratify the name and amend: only scenarios/** on the test classpath; contract scenarios selected by an @contract tag; fixtures stay inline as doc strings; FR-013 covers all three scenario homes (contract scenarios; #43 suites moved and bound atomically; tests/scenarios/charter acceptance scenarios with harness #31); every scenario change gets a contract CHANGELOG line; 'contract scenarios' is the canonical term.

## Rationale

_(none)_

## Change log

- `2026-10-18T15:22:43.011907+00:00` — opened
- `2026-10-18T15:33:02.646924+00:00` — resolved (final_answer="Ratify the name and amend: only scenarios/** on the test classpath; contract scenarios selected by an @contract tag; fixtures stay inline as doc strings; FR-013 covers all three scenario homes (contract scenarios; #43 suites moved and bound atomically; tests/scenarios/charter acceptance scenarios with harness #31); every scenario change gets a contract CHANGELOG line; 'contract scenarios' is the canonical term.")
