# Decision Moment `01M8A86PT7KJSXW37HJEB4RSZJ`

- **Mission:** `adr-0082-graph-walk-slices-01M8A867`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.edge-index-refusals`
- **Input key:** `edge_index_refusals`
- **Status:** `resolved`
- **Created:** `2026-11-25T21:01:22.632027+00:00`
- **Resolved:** `2026-11-25T21:19:12.911167+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

D2: Edge-index refusals in #639 --- move only the registration-side relation and edge-index checks into relations() and keep the read-time merge refusals (500 pack_unreadable) as they are, or unify read and registration codes (a wire change)?

## Options

- Registration-side only, reads unchanged (recommended)
- Unify read and registration codes
- Other

## Final answer

Unify read and registration codes

## Rationale

_(none)_

## Change log

- `2026-11-25T21:01:22.632027+00:00` - opened
- `2026-11-25T21:19:12.911167+00:00` - resolved (final_answer="Unify read and registration codes")
