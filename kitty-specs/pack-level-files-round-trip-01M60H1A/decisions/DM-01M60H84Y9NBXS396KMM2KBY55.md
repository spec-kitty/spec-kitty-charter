# Decision Moment `01M60H84Y9NBXS396KMM2KBY55`

- **Mission:** `pack-level-files-round-trip-01M60H1A`
- **Origin flow:** `specify`
- **Slot key:** `specify.manifest_hash_rule`
- **Input key:** `manifest_hash_rule`
- **Status:** `resolved`
- **Created:** `2026-10-28T05:55:39.081174+00:00`
- **Resolved:** `2026-10-28T05:55:41.852819+00:00`
- **Resolved by:** `mission author (grounding squad); operator questions on #195`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Is content_hash recomputed on write, or are constituents compared by bytes (ADR-0036 open question 3)?

## Options

_(none)_

## Final answer

Recomputed. content_hash and manifest_hash are derived: the writer recomputes them from the bytes it writes (content_hash = SHA-256 of LF-normalised bytes; manifest_hash = SHA-256 of RFC 8785 canonical JSON without manifest_hash). Membership is carried as read; a listed path missing from the written pack refuses the write. The reader does not check freshness; the gate verifies derived fields on both sides.

## Rationale

Carrying hashes would describe bytes that no longer exist; comparing constituents by bytes contradicts ADR-0036 ruling 2 for YAML (squad F3, F9). JCS is emitter-independent.

## Change log

- `2026-10-28T05:55:39.081174+00:00` — opened
- `2026-10-28T05:55:41.852819+00:00` — resolved (final_answer="Recomputed. content_hash and manifest_hash are derived: the writer recomputes them from the bytes it writes (content_hash = SHA-256 of LF-normalised bytes; manifest_hash = SHA-256 of RFC 8785 canonical JSON without manifest_hash). Membership is carried as read; a listed path missing from the written pack refuses the write. The reader does not check freshness; the gate verifies derived fields on both sides.")
