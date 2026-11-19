# Decision Moment `01M7SBD3AH3M4XJM6NK2G5WK3Q`

- **Mission:** `pack-delete-and-update-guard-01M7SANQ`
- **Origin flow:** `specify`
- **Slot key:** `specify.domain.history-after-delete`
- **Input key:** `history_after_delete`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:30:12.433920+00:00`
- **Resolved:** `2026-11-19T07:30:14.731877+00:00`
- **Resolved by:** `operator (relayed by orchestrator session_019C2Tk6DpTSEBeTcn9F6smy)`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What does the version history (ADR-0065 D9) do after a pack DELETE?

## Options

- (a) keep; reinstall must be newer than every version
- (b) keep for label reuse only
- (c) drop

## Final answer

(b) The history is kept for the label-reuse check only: after a DELETE a re-install may take any pack_version the history does not hold with other bytes.

## Rationale

_(none)_

## Change log

- `2026-11-19T07:30:12.433920+00:00` - opened
- `2026-11-19T07:30:14.731877+00:00` - resolved (final_answer="(b) The history is kept for the label-reuse check only: after a DELETE a re-install may take any pack_version the history does not hold with other bytes.")
