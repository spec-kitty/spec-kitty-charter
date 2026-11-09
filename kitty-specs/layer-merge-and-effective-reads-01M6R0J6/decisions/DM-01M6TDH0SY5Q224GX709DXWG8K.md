# Decision Moment `01M6TDH0SY5Q224GX709DXWG8K`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruling-408.depth`
- **Input key:** `cascade_depth`
- **Status:** `resolved`
- **Created:** `2026-11-07T07:10:50.686572+00:00`
- **Resolved:** `2026-11-07T07:11:12.718711+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

Issue #408 ruling 1: what does the cascade depth parameter count, and where does it ship?

## Options

_(none)_

## Final answer

depth counts suggests hops only, as ADR-0018 D4's depth does for the walk: requires and the other hard relations, enhances and lineage pass-through are walked in full; depth 0 follows no suggests edge; an absent depth is unlimited (core parity, ADR-0059 D2). It applies to a cascading select (SelectionRequest.depth) and a cascading deactivate (its body). It ships in PR A0 as its own WP (WP30), after WP08.

## Rationale

Truncating hard relations would write a selection whose requirements are not selected; the breadth #408 reports comes from suggests, whose when a write cannot evaluate. One meaning of depth across the contract (ADR-0018 D4). The change is to #399's built cascade request, which is A0's scope (C-005), and is small and contract-local.

## Change log

- `2026-11-07T07:10:50.686572+00:00` - opened
- `2026-11-07T07:11:12.718711+00:00` - resolved (final_answer="depth counts suggests hops only, as ADR-0018 D4's depth does for the walk: requires and the other hard relations, enhances and lineage pass-through are walked in full; depth 0 follows no suggests edge; an absent depth is unlimited (core parity, ADR-0059 D2). It applies to a cascading select (SelectionRequest.depth) and a cascading deactivate (its body). It ships in PR A0 as its own WP (WP30), after WP08.")
