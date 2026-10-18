# Decision Moment `01M577WZG8726DCYH7ECDQWWZ1`

- **Mission:** `pack-registration-distribution-01M56M49`
- **Origin flow:** `plan`
- **Slot key:** `plan.sourcing.lockfile-granularity`
- **Input key:** `lockfile_granularity`
- **Status:** `resolved`
- **Created:** `2026-10-18T10:11:15.080831+00:00`
- **Resolved:** `2026-10-18T10:13:34.235556+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Pin/integrity lockfile granularity (reuse the snapshot_sha256 recipe in all cases)?

## Options

- Two-tier: config-level lockfile of per-pack {source, rev, snapshot_sha256} + per-constituent content_hash in each pack-manifest.yaml
- Per-pack lock files (mirrors built-in.source.yaml)
- Single repo/config-level lockfile

## Final answer

Two-tier: a config-level lockfile of per-pack {source, rev, snapshot_sha256} + per-constituent content_hash in each pack-manifest.yaml; reuse the snapshot_sha256 recipe verbatim.

## Rationale

_(none)_

## Change log

- `2026-10-18T10:11:15.080831+00:00` — opened
- `2026-10-18T10:13:34.235556+00:00` — resolved (final_answer="Two-tier: a config-level lockfile of per-pack {source, rev, snapshot_sha256} + per-constituent content_hash in each pack-manifest.yaml; reuse the snapshot_sha256 recipe verbatim.")
