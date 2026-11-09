# Decision Moment `01M6Y0X5E9ANR25MC8RPFXYM72`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `wp22-suggestion-carrier`
- **Input key:** `wp22-suggestion-carrier`
- **Status:** `resolved`
- **Created:** `2026-11-08T16:47:14.889167+00:00`
- **Resolved:** `2026-11-08T16:47:21.026175+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

#408 ruling 3: an artifact reached only through suggests is listed as a suggestion carrying its when and reason. Which contract member carries it?

## Options

- optional EffectiveArtifact.suggestion {when, reason}, on the list and the action context
- a separate typed list on the action context only (PR C)

## Final answer

optional EffectiveArtifact.suggestion {when, reason}, on the list and the action context

## Rationale

Runner, 2026-11-08: ruling 3 folds into FR-013 (the list, PR B) and FR-019 (the context, PR C), and says the artifact is listed as a suggestion, which is an entry, not a second list. A nested object keeps the edge's reason apart from the state reason. It is absent for an artifact reached through requires or another hard relation. WP22 declares it (### Added), WP24/WP25 serve it on the list, PR C on the context.

## Change log

- `2026-11-08T16:47:14.889167+00:00` - opened
- `2026-11-08T16:47:21.026175+00:00` - resolved (final_answer="optional EffectiveArtifact.suggestion {when, reason}, on the list and the action context")
