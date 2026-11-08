# Decision Moment `01M6R0TNEBR7KGJMW2SQXQGJ0A`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `specify`
- **Slot key:** `specify.reads.reporting`
- **Input key:** `reads_reporting`
- **Status:** `resolved`
- **Created:** `2026-11-06T08:50:26.379828+00:00`
- **Resolved:** `2026-11-06T08:59:28.731641+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How do the effective reads report overrides, stale ids and unapplied denies?

## Options

- Typed: skipped-report buckets, override list on the graph, packId/level provenance
- Shared diagnostics[] on every read
- Other

## Final answer

Typed: skipped-report buckets for stale ids and unapplied denies, layer-override list on the effective graph, packId/level provenance on effective artifacts; drop kind_default

## Rationale

_(none)_

## Change log

- `2026-11-06T08:50:26.379828+00:00` - opened
- `2026-11-06T08:59:28.731641+00:00` - resolved (final_answer="Typed: skipped-report buckets for stale ids and unapplied denies, layer-override list on the effective graph, packId/level provenance on effective artifacts; drop kind_default")
