# Decision Moment `01M6R2WSARGC382BHX89GZB64E`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.delete.same-pack`
- **Input key:** `delete_same_pack_refs`
- **Status:** `resolved`
- **Created:** `2026-11-06T09:26:33.048505+00:00`
- **Resolved:** `2026-11-06T09:39:52.031778+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

DELETE of an artifact another artifact of the same pack references?

## Options

- Refuse 409 artifact_referenced
- Drop the referring edges too
- Other

## Final answer

Refuse 409 artifact_referenced

## Rationale

_(none)_

## Change log

- `2026-11-06T09:26:33.048505+00:00` - opened
- `2026-11-06T09:39:52.031778+00:00` - resolved (final_answer="Refuse 409 artifact_referenced")
