# Decision Moment `01M7232ZMR4EWNDA1P2VF455Y1`

- **Mission:** `agent-profile-reads-01M70ZDM`
- **Origin flow:** `plan`
- **Step id:** `wp07-review-2`
- **Input key:** `governance_entry_edge_words`
- **Status:** `resolved`
- **Created:** `2026-11-10T06:42:20.440664+00:00`
- **Resolved:** `2026-11-10T06:42:24.037379+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

FR-004: how do governance entries carry the when/reason of their edge, given requires edges also carry words?

## Options

- Generic edge words: one edge {when?, reason?} member on every entry of both reads, {} when none; replaces suggestion
- Add field, keep suggestion
- Narrow FR-004 to suggests entries

## Final answer

Generic edge words: one edge {when?, reason?} member on every entry of both reads, {} when none; replaces suggestion

## Rationale

Operator ruling 2026-11-10: meets FR-004 as written with one name for one fact; nothing merged yet, so WP04's suggestion member is replaced, not deprecated.

## Change log

- `2026-11-10T06:42:20.440664+00:00` - opened
- `2026-11-10T06:42:24.037379+00:00` - resolved (final_answer="Generic edge words: one edge {when?, reason?} member on every entry of both reads, {} when none; replaces suggestion")
