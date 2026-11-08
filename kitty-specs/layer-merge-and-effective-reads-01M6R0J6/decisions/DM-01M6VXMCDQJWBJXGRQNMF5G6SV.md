# Decision Moment `01M6VXMCDQJWBJXGRQNMF5G6SV`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp16-notice-kinds`
- **Input key:** `wp16-notice-kinds`
- **Status:** `resolved`
- **Created:** `2026-11-07T21:11:32.535351+00:00`
- **Resolved:** `2026-11-07T21:14:08.484976+00:00`
- **Resolved by:** `operator`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

PackRegistrationNotices items use ArtifactReference, whose kind is ActivatableKind; a lock or notice on a template, asset, guard or lifecycle artifact is dropped by the mapper. Widen the notices item kind to an open kind slug (contract change, fragment), declare those kinds never appear and refuse policy locks on them, or keep the drop and log it?

## Options

- widen notices kind to an open slug (### Changed/Breaking fragment)
- declare excluded; refuse policy locks on those kinds
- keep the drop and log it

## Final answer

keep the drop and log it

## Rationale

Operator, 2026-11-07: no contract change now. The contract says notices name activatable kinds only; the mapper logs a dropped entry; a follow-up issue tracks widening.

## Change log

- `2026-11-07T21:11:32.535351+00:00` - opened
- `2026-11-07T21:14:08.484976+00:00` - resolved (final_answer="keep the drop and log it")
