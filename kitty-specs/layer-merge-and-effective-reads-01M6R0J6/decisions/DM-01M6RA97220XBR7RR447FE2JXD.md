# Decision Moment `01M6RA97220XBR7RR447FE2JXD`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.ruleset.a15-lock-scope`
- **Input key:** `lock_scope`
- **Status:** `resolved`
- **Created:** `2026-11-06T11:35:40.354190+00:00`
- **Resolved:** `2026-11-06T11:35:42.676282+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

A15 policy lock scope

## Options

_(none)_

## Final answer

A pack may lock and enforce artifacts of the packs it builds on (enterprise enforcement); scope is the pack and its depends_on/lineage ancestors, not unrelated packs that share the closure.

## Rationale

_(none)_

## Change log

- `2026-11-06T11:35:40.354190+00:00` - opened
- `2026-11-06T11:35:42.676282+00:00` - resolved (final_answer="A pack may lock and enforce artifacts of the packs it builds on (enterprise enforcement); scope is the pack and its depends_on/lineage ancestors, not unrelated packs that share the closure.")
