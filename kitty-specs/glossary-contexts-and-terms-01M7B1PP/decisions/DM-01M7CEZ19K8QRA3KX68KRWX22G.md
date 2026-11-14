# Decision Moment `01M7CEZ19K8QRA3KX68KRWX22G`

- **Mission:** `glossary-contexts-and-terms-01M7B1PP`
- **Origin flow:** `plan`
- **Slot key:** `plan.governance.own-charter-yaml`
- **Input key:** `own_charter_yaml_test`
- **Status:** `resolved`
- **Created:** `2026-11-14T07:22:18.292025+00:00`
- **Resolved:** `2026-11-14T07:22:21.116836+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

After the rename, how is this repo's core-written .kittify/charter/charter.yaml handled by ProjectCharterYamlSchemaTest?

## Options

_(none)_

## Final answer

Narrow the test: it stops validating core's file against charter's schema (core's format, no compatibility kept); the file is not edited and the CLI keeps working (operator, 2026-11-14)

## Rationale

_(none)_

## Change log

- `2026-11-14T07:22:18.292025+00:00` - opened
- `2026-11-14T07:22:21.116836+00:00` - resolved (final_answer="Narrow the test: it stops validating core's file against charter's schema (core's format, no compatibility kept); the file is not edited and the CLI keeps working (operator, 2026-11-14)")
