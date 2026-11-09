# Decision Moment `01M70N4GK3PGAEVFPYT3DN1Q9F`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-c-ic13-fragment-500`
- **Input key:** `pr-c-ic13-fragment-500`
- **Status:** `resolved`
- **Created:** `2026-11-09T17:19:16.067443+00:00`
- **Resolved:** `2026-11-09T17:19:18.184275+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

PR C squad (architect, MAJOR): besides 409 to 200/201, the IC-13 writes now answer 500 pack_unreadable where they answered 409 when the closure layer merge refuses (DIV-204). Does this move the IC-13 fragment to ### Breaking (follow-up to 01M70JFXZHY2NKA6Y6RQ66AYA9)?

## Options

- keep ### Changed, and state both changed answers in the fragment: 409 artifact_required becomes 200/201 when an active override satisfies it, and becomes 500 pack_unreadable when the closure layer merge refuses (a 500 of OfferingUnreadable the writes already declare), as the PR B fragment 2026-11-08-b-write-cascade-merged-edges.md did
- move IC-13 to ### Breaking and ask the operator for a re-ruling

## Final answer

keep ### Changed, and state both changed answers in the fragment: 409 artifact_required becomes 200/201 when an active override satisfies it, and becomes 500 pack_unreadable when the closure layer merge refuses (a 500 of OfferingUnreadable the writes already declare), as the PR B fragment 2026-11-08-b-write-cascade-merged-edges.md did

## Rationale

Runner: the mission's own precedent for this exact case is PR B's ### Changed fragment 2026-11-08-b-write-cascade-merged-edges.md: a write over a closure whose merge refuses answers the 500 the sub-resource writes already declare (OfferingUnreadable names pack_unreadable), with no operation, schema or response change. IC-13 adds no response to any operation either; the 500 and the 200/201 are both declared on main. The a0 Breaking precedent (422 to a silent drop) is different: stored client data disappears without a refusal. The architect's minimum fix is accepted: the fragment names both status changes and the declared 500 explicitly. Surfaced to the operator through the dispatcher; a ruling for ### Breaking would only move the block, no code changes.

## Change log

- `2026-11-09T17:19:16.067443+00:00` - opened
- `2026-11-09T17:19:18.184275+00:00` - resolved (final_answer="keep ### Changed, and state both changed answers in the fragment: 409 artifact_required becomes 200/201 when an active override satisfies it, and becomes 500 pack_unreadable when the closure layer merge refuses (a 500 of OfferingUnreadable the writes already declare), as the PR B fragment 2026-11-08-b-write-cascade-merged-edges.md did")
