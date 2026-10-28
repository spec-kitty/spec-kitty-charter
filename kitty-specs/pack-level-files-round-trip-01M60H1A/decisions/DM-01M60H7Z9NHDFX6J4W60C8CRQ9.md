# Decision Moment `01M60H7Z9NHDFX6J4W60C8CRQ9`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.manifest_shape`
- **Input key:** `manifest_shape`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:33.301550+00:00`
- **Resolved:** `2026-10-28T05:55:36.213423+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

What shape does pack-manifest.yaml have?

## Options

_(none)_

## Final answer

The authored-pack variant only: schema_version "1.0", manifest_hash, constituents [{kind, id, path, content_hash}], presets [{id, path, content_hash}]; closed. charter, fetched_at, source_url, source_type, generated_at, generated_by and provenance_path are not in it.

## Rationale

Sanitized-shape rules S1-S5; the fetched and charter-bundle variants are not pack content (#110).

## Change log

- `2026-10-28T05:55:33.301550+00:00` — opened
- `2026-10-28T05:55:36.213423+00:00` — resolved (final_answer="The authored-pack variant only: schema_version "1.0", manifest_hash, constituents [{kind, id, path, content_hash}], presets [{id, path, content_hash}]; closed. charter, fetched_at, source_url, source_type, generated_at, generated_by and provenance_path are not in it.")
