# Decision Moment `01M7ZPVTTGHYCSC2Y39BZV0FXT`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `wp03-anti-pattern-must-have`
- **Input key:** `anti_pattern_must_have`
- **Status:** `resolved`
- **Created:** `2026-11-21T18:45:56.176527+00:00`
- **Resolved:** `2026-11-21T18:45:59.229209+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

R-l counts a preset must_have only when held as an artifact file; anti_pattern has no file form, and built-in/presets/default.yaml must_haves anti_pattern:indirection-for-repetition. How does WP03 proceed?

## Options

- Node counts for graph kinds
- Drop must_have in fixture
- Build the file kind first

## Final answer

Build the file kind first

## Rationale

Operator ruling R-p (2026-11-21): the anti_pattern file kind (ADR-0052 d8 shape) is built first as its own slice or mission; WP03 (#629, #632.1) is held until it lands; #629 does not close in this PR.

## Change log

- `2026-11-21T18:45:56.176527+00:00` - opened
- `2026-11-21T18:45:59.229209+00:00` - resolved (final_answer="Build the file kind first")
