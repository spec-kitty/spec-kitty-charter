# Decision Moment `01M6R2WVRKB4JGZM40D6QF60BQ`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.registration.replace-refs`
- **Input key:** `replace_removes_referenced`
- **Status:** `resolved`
- **Created:** `2026-11-06T09:26:35.539449+00:00`
- **Resolved:** `2026-11-06T09:39:54.332364+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Whole-pack replace removing an artifact another pack references?

## Options

- Refuse 409 (same scan as DELETE)
- Allow; the reference goes stale
- Other

## Final answer

Refuse 409 (same scan as DELETE)

## Rationale

_(none)_

## Change log

- `2026-11-06T09:26:35.539449+00:00` - opened
- `2026-11-06T09:39:54.332364+00:00` - resolved (final_answer="Refuse 409 (same scan as DELETE)")
