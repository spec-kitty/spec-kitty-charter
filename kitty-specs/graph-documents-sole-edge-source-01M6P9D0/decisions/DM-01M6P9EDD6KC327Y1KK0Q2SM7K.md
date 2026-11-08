# Decision Moment `01M6P9EDD6KC327Y1KK0Q2SM7K`

- **Mission:** `graph-documents-sole-edge-source-01M6P9D0`
- **Origin flow:** `specify`
- **Slot key:** `specify.scope.read-shapes`
- **Input key:** `read_shapes`
- **Status:** `resolved`
- **Created:** `2026-11-05T16:42:33.255000+00:00`
- **Resolved:** `2026-11-06T07:20:53.655162+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Do the per-kind read shapes lose their relationships arrays too, with listArtifactReferences.relation filled from the graph documents?

## Options

- Yes: remove from read shapes; relation from graph documents
- No: keep read-shape relationships as a derived view
- Other

## Final answer

Yes. Operator ruling 2026-11-05 (Q5 accepted): the read shapes lose their relationships arrays, and the references read takes its relation from the graph documents.

## Rationale

_(none)_

## Change log

- `2026-11-05T16:42:33.255000+00:00` — opened
- `2026-11-06T07:20:53.655162+00:00` — resolved (final_answer="Yes. Operator ruling 2026-11-05 (Q5 accepted): the read shapes lose their relationships arrays, and the references read takes its relation from the graph documents.")
