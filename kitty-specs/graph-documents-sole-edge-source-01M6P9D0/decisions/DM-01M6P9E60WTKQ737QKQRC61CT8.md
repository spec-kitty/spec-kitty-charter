# Decision Moment `01M6P9E60WTKQ737QKQRC61CT8`

- **Mission:** `graph-documents-sole-edge-source-01M6P9D0`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.directive-procedures`
- **Input key:** `directive_procedures`
- **Status:** `resolved`
- **Created:** `2026-11-05T16:42:25.692137+00:00`
- **Resolved:** `2026-11-06T07:20:48.532411+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

A directive's procedures field holds prose steps (192 entries), not procedure ids: keep it as text that mints no edge, move the text into the body, or drop it?

## Options

- Keep as text that mints no edge
- Drop and move text into the body (open-packs)
- Drop and lose the text
- Other

## Final answer

Drop it. Operator ruling 2026-11-05 (#240 comment 6664407049): the prose steps become procedure artifacts in open-packs, shared where reusable, and each directive requires them through a graph edge.

## Rationale

_(none)_

## Change log

- `2026-11-05T16:42:25.692137+00:00` — opened
- `2026-11-06T07:20:48.532411+00:00` — resolved (final_answer="Drop it. Operator ruling 2026-11-05 (#240 comment 6664407049): the prose steps become procedure artifacts in open-packs, shared where reusable, and each directive requires them through a graph edge.")
