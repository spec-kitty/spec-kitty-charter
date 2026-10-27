# Decision Moment `01M5ZAMP0R5V5YF5KM09YXY0NS`

- **Mission:** `asset-template-round-trip-01M5YY7G`
- **Origin flow:** `plan`
- **Slot key:** `plan.reconcile.adr0039`
- **Input key:** `reconcile_with_adr0039`
- **Status:** `resolved`
- **Created:** `2026-10-27T18:40:55.320818+00:00`
- **Resolved:** `2026-10-27T18:40:58.210122+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

How do we reconcile with #192's ADR-0039 companion files?

## Options

_(none)_

## Final answer

Keep ours, unify seams: merge main; keep pack-level AssetFile + Template (companions cannot express sidecar-less payloads R1, templates-as-artifacts R2, or the assets/ base); share low-level pieces (one path/containment rule, one bytes read/write, fingerprint order); ADR-0040 records why assets are not companions and amends ADR-0039's Bytes-case note.

## Rationale

_(none)_

## Change log

- `2026-10-27T18:40:55.320818+00:00` — opened
- `2026-10-27T18:40:58.210122+00:00` — resolved (final_answer="Keep ours, unify seams: merge main; keep pack-level AssetFile + Template (companions cannot express sidecar-less payloads R1, templates-as-artifacts R2, or the assets/ base); share low-level pieces (one path/containment rule, one bytes read/write, fingerprint order); ADR-0040 records why assets are not companions and amends ADR-0039's Bytes-case note.")
