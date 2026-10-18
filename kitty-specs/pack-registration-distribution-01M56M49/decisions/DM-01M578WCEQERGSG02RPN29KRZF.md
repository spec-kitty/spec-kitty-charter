# Decision Moment `01M578WCEQERGSG02RPN29KRZF`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.constituent-slug-scope`
- **Input key:** `constituent_slug_scope`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:28:24.151912+00:00`
- **Resolved:** `2026-10-18T10:28:25.754574+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Constituent-slug conflict scope: global or per-pack?

## Options

- Global constituent namespace: collisions refused across packs (per research); reversible if too strict

## Final answer

Default: global constituent namespace — a slug collision across packs is refused (per research). Reversible to per-pack if it proves too strict.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:28:24.151912+00:00` — opened
- `2026-10-18T10:28:25.754574+00:00` — resolved (final_answer="Default: global constituent namespace — a slug collision across packs is refused (per research). Reversible to per-pack if it proves too strict.")
