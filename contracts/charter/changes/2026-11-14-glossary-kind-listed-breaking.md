### Breaking

- `glossary` becomes a served kind (#106; ADR-0022). `ArtifactKindToken`, the `?kind=` filter, lists `glossary` after
  `skill` and before `agent_profile`, and the `400` detail of an unknown `?kind=` names it in that order. `Artifact`
  gains the member `glossary` (`GlossaryContext`, with `GlossaryContextTerm` and `GlossaryAppliesTo`) in its `oneOf`,
  which the literal context read answers. Core must regenerate its DTOs (`ArtifactKindToken`, `Artifact` and the new
  glossary schemas) and handle the kind token and the member before it relies on an exhaustive switch over either.
