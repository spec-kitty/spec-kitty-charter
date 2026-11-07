# Decision Moment `01M6TWVNN6EXVYWVVM9M2GZP3C`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `plan.phase-a.artifact-delete-404`
- **Input key:** `artifact_delete_not_found`
- **Status:** `resolved`
- **Created:** `2026-11-07T11:38:48.358850+00:00`
- **Resolved:** `2026-11-07T11:38:50.883465+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `true`

## Question

Which not-found code does DELETE /packs/{packId}/artifacts/{kind}/{artifactId} answer: artifact_not_found, or the per-kind code getPackArtifact answers on the same path?

## Options

_(none)_

## Final answer

The per-kind code (directive_not_found, tactic_not_found, ...), as getPackArtifact answers on the same path; one path, one not-found vocabulary. Contract A (WP11) already declares it; IC-06, contract-delta and WP19 T094 follow.

## Rationale

WP11 review cycle 2 follow-up 1.

## Change log

- `2026-11-07T11:38:48.358850+00:00` - opened
- `2026-11-07T11:38:50.883465+00:00` - resolved (final_answer="The per-kind code (directive_not_found, tactic_not_found, ...), as getPackArtifact answers on the same path; one path, one not-found vocabulary. Contract A (WP11) already declares it; IC-06, contract-delta and WP19 T094 follow.")
