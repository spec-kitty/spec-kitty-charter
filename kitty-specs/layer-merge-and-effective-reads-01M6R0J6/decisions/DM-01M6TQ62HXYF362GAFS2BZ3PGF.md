# Decision Moment `01M6TQ62HXYF362GAFS2BZ3PGF`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.edge-delete`
- **Input key:** `relationship_delete_scope`
- **Status:** `resolved`
- **Created:** `2026-11-07T09:59:37.789237+00:00`
- **Resolved:** `2026-11-07T09:59:40.311676+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

What does DELETE of a relationship remove, now that one pair can sit in several graph documents (ADR-0064)?

## Options

_(none)_

## Final answer

Operator (2026-11-07): the registered shard only. DELETE /packs/{packId}/relationships removes the edge from registered.graph.yaml (what putPackRelationship writes). An edge the author wrote in a source shard or drg/fragment.yaml answers 409 (register the whole pack instead); a derived relation (a guard's implemented_by, ADR-0064 D7) answers 409 too. Author files are never rewritten by a DELETE.

## Rationale

Phase A plan delta squad finding 1 (2026-11-07); keeps authored files authoritative and the DELETE well defined.

## Change log

- `2026-11-07T09:59:37.789237+00:00` - opened
- `2026-11-07T09:59:40.311676+00:00` - resolved (final_answer="Operator (2026-11-07): the registered shard only. DELETE /packs/{packId}/relationships removes the edge from registered.graph.yaml (what putPackRelationship writes). An edge the author wrote in a source shard or drg/fragment.yaml answers 409 (register the whole pack instead); a derived relation (a guard's implemented_by, ADR-0064 D7) answers 409 too. Author files are never rewritten by a DELETE.")
