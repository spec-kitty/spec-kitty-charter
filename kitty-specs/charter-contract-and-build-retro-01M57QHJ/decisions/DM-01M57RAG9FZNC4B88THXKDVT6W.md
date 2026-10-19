# Decision Moment `01M57RAG9FZNC4B88THXKDVT6W`

- **Mission:** `charter-contract-and-build-retro-01M57QHJ`
- **Origin flow:** `specify`
- **Slot key:** `specify.build.javadoc-site`
- **Input key:** `javadoc_site`
- **Status:** `resolved`
- **Created:** `2026-10-18T14:58:15.471302+00:00`
- **Resolved:** `2026-10-18T14:58:18.866787+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Should the build produce Javadoc and publish it with the docsite?

## Options

_(none)_

## Final answer

Yes: Gradle javadoc for hand-written modules with doclint on and warnings failing; the Javadoc site is packaged by the main build workflow and published inside the docsite as a dedicated set of pages reached from the sidebar, styled to match the docsite

## Rationale

_(none)_

## Change log

- `2026-10-18T14:58:15.471302+00:00` — opened
- `2026-10-18T14:58:18.866787+00:00` — resolved (final_answer="Yes: Gradle javadoc for hand-written modules with doclint on and warnings failing; the Javadoc site is packaged by the main build workflow and published inside the docsite as a dedicated set of pages reached from the sidebar, styled to match the docsite")
