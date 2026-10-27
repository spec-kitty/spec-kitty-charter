# Decision Moment `01M5YY9WFDRVZT6GZS8X59E2AZ`

- **Mission:** `asset-template-round-trip-01M5YY7G`
- **Origin flow:** `specify`
- **Slot key:** `specify.rules.content-form`
- **Input key:** `content_form_authority`
- **Status:** `resolved`
- **Created:** `2026-10-27T15:05:18.573205+00:00`
- **Resolved:** `2026-10-27T15:05:21.742082+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Can the storage format not depend on the file extension, but on the artefact file kind / metadata?

## Options

_(none)_

## Final answer

Yes: each file-kinds.yaml rule declares content_form (yaml|markdown|text|bytes, default yaml); never extension or mime; mime stays metadata.

## Rationale

_(none)_

## Change log

- `2026-10-27T15:05:18.573205+00:00` — opened
- `2026-10-27T15:05:21.742082+00:00` — resolved (final_answer="Yes: each file-kinds.yaml rule declares content_form (yaml|markdown|text|bytes, default yaml); never extension or mime; mime stays metadata.")
