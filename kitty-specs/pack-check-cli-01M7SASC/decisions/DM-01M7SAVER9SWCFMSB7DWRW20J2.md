# Decision Moment `01M7SAVER9SWCFMSB7DWRW20J2`

- **Mission:** `pack-check-cli-01M7SASC`
- **Origin flow:** `specify`
- **Slot key:** `specify.symlinks.rule`
- **Input key:** `linked_entries_rule`
- **Status:** `resolved`
- **Created:** `2026-11-19T07:20:34.313623+00:00`
- **Resolved:** `2026-11-19T07:48:20.964580+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How does the one reader judge a symbolic link inside a pack directory (pack-check over a local directory, and registration via #597)?

## Options

- Refuse every link inside the pack root (new violation code), fold #597
- Refuse only a link resolving outside the pack root
- Follow links, warn file_linked
- Other

## Final answer

A: refuse every link inside the pack root with a new violation code in the one reader; fold and close #597; the pack root may be a link; Breaking note (operator via orchestrator, 2026-11-19)

## Rationale

_(none)_

## Change log

- `2026-11-19T07:20:34.313623+00:00` - opened
- `2026-11-19T07:48:20.964580+00:00` - resolved (final_answer="A: refuse every link inside the pack root with a new violation code in the one reader; fold and close #597; the pack root may be a link; Breaking note (operator via orchestrator, 2026-11-19)")
