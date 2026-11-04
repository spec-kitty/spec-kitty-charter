# Decision Moment `01M6JKVNX57FSK0RHCKXX2KKJJ`

- **Mission:** `ci-sonar-nightly-checkstyle-complexity-01M6HQ46`
- **Origin flow:** `plan`
- **Slot key:** `plan.register.cap`
- **Input key:** `register_cap`
- **Status:** `resolved`
- **Created:** `2026-11-04T06:27:35.973276+00:00`
- **Resolved:** `2026-11-04T06:27:38.592594+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Measured 18 over-limit methods vs SC-002 cap of 15: raise the cap, refactor 3 methods, or refactor all main-code methods?

## Options

- Raise cap to 18
- Refactor 3 methods to reach 15
- Refactor all 10 main-code methods
- Other

## Final answer

Refactor 3 methods to reach 15: flatten ComposedBundle.checkReferences, ComposedBundle.reaches and ActivationConfigSchemaParityTest.inline (behavior-preserving), folded into WP01 as T017

## Rationale

_(none)_

## Change log

- `2026-11-04T06:27:35.973276+00:00` - opened
- `2026-11-04T06:27:38.592594+00:00` - resolved (final_answer="Refactor 3 methods to reach 15: flatten ComposedBundle.checkReferences, ComposedBundle.reaches and ActivationConfigSchemaParityTest.inline (behavior-preserving), folded into WP01 as T017")
