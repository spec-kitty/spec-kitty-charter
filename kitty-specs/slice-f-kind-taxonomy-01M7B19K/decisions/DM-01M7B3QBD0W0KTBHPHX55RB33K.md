# Decision Moment `01M7B3QBD0W0KTBHPHX55RB33K`

- **Mission:** `slice-f-kind-taxonomy-01M7B19K`
- **Origin flow:** `plan`
- **Slot key:** `plan.domain.typed-constructors`
- **Input key:** `typed_constructors`
- **Status:** `resolved`
- **Created:** `2026-11-13T18:46:37.728733+00:00`
- **Resolved:** `2026-11-13T19:49:01.531676+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How are relationship constructors typed by endpoint kind in Java?

## Options

- Kind marker types plus per-relation factories
- Per-relation record types
- Other

## Final answer

Kind marker types (sealed kind groups), NodeRef<K>, per-relation static factories on Relationship; one checked bridge at the boundary applies RelationType's table.

## Rationale

_(none)_

## Change log

- `2026-11-13T18:46:37.728733+00:00` - opened
- `2026-11-13T19:49:01.531676+00:00` - resolved (final_answer="Kind marker types (sealed kind groups), NodeRef<K>, per-relation static factories on Relationship; one checked bridge at the boundary applies RelationType's table.")
