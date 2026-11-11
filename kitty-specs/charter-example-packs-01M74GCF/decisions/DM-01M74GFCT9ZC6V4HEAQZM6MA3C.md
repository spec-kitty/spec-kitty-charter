# Decision Moment `01M74GFCT9ZC6V4HEAQZM6MA3C`

- **Mission:** `charter-example-packs-01M74GCF`
- **Origin flow:** `plan`
- **Slot key:** `plan.architecture.fixture-home`
- **Input key:** `example_packs_home`
- **Status:** `resolved`
- **Created:** `2026-11-11T05:14:47.497971+00:00`
- **Resolved:** `2026-11-11T05:16:05.755501+00:00`
- **Opened by:** `cli`
- **Other answer:** `false`

## Question

Where do the example packs live so both :packs and :deployable tests read the same files? (A) a java-test-fixtures source set in :packs (Gradle core plugin, no new dependency) exposing the packs and an ExamplePacks locator; (B) a plain directory under the repository (e.g. examples/packs/) passed to both test tasks as a system property, like openPacksDir today.

## Options

- A: test-fixtures source set in :packs
- B: repository directory passed as a system property
- Other

## Final answer

A: test-fixtures source set in :packs

## Rationale

_(none)_

## Change log

- `2026-11-11T05:14:47.497971+00:00` - opened
- `2026-11-11T05:16:05.755501+00:00` - resolved (final_answer="A: test-fixtures source set in :packs")
