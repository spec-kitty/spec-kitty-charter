# Decision Moment `01M7ZQGHVNQXAQ0S3DMTK7EZCB`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `anti-pattern-file-kind-home`
- **Input key:** `anti_pattern_file_kind_home`
- **Status:** `resolved`
- **Created:** `2026-11-21T18:57:15.125909+00:00`
- **Resolved:** `2026-11-21T18:57:18.121979+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

With WP03 held until the anti_pattern file kind exists, where is the file kind (ADR-0052 d8) built?

## Options

- New WP in this mission
- Separate mission; PR ships without WP03
- Split WP03

## Final answer

New WP in this mission

## Rationale

Operator ruling R-q (2026-11-21): a new WP builds the anti_pattern file kind (schema, file-kinds rule, storage binding, kernel facts, contract, the built-in fixture file) before WP03; WP03 then runs as planned and #629/#632 still close in this PR.

## Change log

- `2026-11-21T18:57:15.125909+00:00` - opened
- `2026-11-21T18:57:18.121979+00:00` - resolved (final_answer="New WP in this mission")
