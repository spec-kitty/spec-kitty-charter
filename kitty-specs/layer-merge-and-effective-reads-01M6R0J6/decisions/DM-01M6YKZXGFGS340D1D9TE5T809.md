# Decision Moment `01M6YKZXGFGS340D1D9TE5T809`

- **Mission:** `layer-merge-and-effective-reads-01M6R0J6`
- **Origin flow:** `plan`
- **Slot key:** `pr-b-warnings-additions-only`
- **Input key:** `pr-b-warnings-additions-only`
- **Status:** `resolved`
- **Created:** `2026-11-08T22:20:48.015971+00:00`
- **Resolved:** `2026-11-08T22:20:50.536909+00:00`
- **Resolved by:** `claude-runner`
- **Opened by:** `claude-runner`
- **Other answer:** `false`

## Question

ActivationChange.warnings names every applied artifact, removals included, so unselecting or denying an enhancer whose target is out of force warns enhanced_target_not_in_force (squad D2). Warn on additions only, or document removals?

## Options

- warn only for artifacts the write puts in (select, preset additions)
- document that removals warn too

## Final answer

warn only for artifacts the write puts in (select, preset additions)

## Rationale

Runner, 2026-11-08. FR-032: a write predicts the read for what it asks to be in force. A removal asks for nothing to be in force, so a warning on it says nothing the client can act on.

## Change log

- `2026-11-08T22:20:48.015971+00:00` - opened
- `2026-11-08T22:20:50.536909+00:00` - resolved (final_answer="warn only for artifacts the write puts in (select, preset additions)")
