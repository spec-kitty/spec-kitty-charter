# Decision Moment `01M801H54DXR0447M3F2ZQWFPZ`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `issue-154-scope`
- **Input key:** `issue_154_scope`
- **Status:** `resolved`
- **Created:** `2026-11-21T21:52:20.621186+00:00`
- **Resolved:** `2026-11-21T21:52:23.007080+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

WP10 gives anti_pattern a file form, part of #154. How much of #154 does this PR deliver?

## Options

- File kind only
- Plus graph-only refusal
- All of #154 now

## Final answer

All of #154 now

## Rationale

Operator ruling R-r (2026-11-21): this PR delivers all of #154: the anti_pattern file kind, the graph-only anti_pattern node refusal, and dropping the inline anti_patterns entries from the procedure and styleguide schemas (Breaking packs-contract change). open-packs styleguides/procedures with inline entries are refused until curated (draft open-packs#TBD). The PR closes #154.

## Change log

- `2026-11-21T21:52:20.621186+00:00` - opened
- `2026-11-21T21:52:23.007080+00:00` - resolved (final_answer="All of #154 now")
