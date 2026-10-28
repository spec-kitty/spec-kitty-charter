# Decision Moment `01M60H8FP4AJ8TJWH670JT4WAV`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.fingerprint_coverage`
- **Input key:** `fingerprint_coverage`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:50.085006+00:00`
- **Resolved:** `2026-10-28T05:55:52.923639+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Does the verified-source fingerprint cover the pack-level files?

## Options

_(none)_

## Final answer

Yes: presets, pack.md and pack-manifest.yaml are appended to PackFileSet.digested after the templates.

## Rationale

Every file the store reads is in the fingerprint (ADR-0040 decision 9 pattern); #110 stays the contract-scope decision about core's pin.

## Change log

- `2026-10-28T05:55:50.085006+00:00` — opened
- `2026-10-28T05:55:52.923639+00:00` — resolved (final_answer="Yes: presets, pack.md and pack-manifest.yaml are appended to PackFileSet.digested after the templates.")
