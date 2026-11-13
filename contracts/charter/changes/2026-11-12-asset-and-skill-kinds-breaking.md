### Breaking

- `asset` and `skill` become served kinds (#105, #107; ADR-0022). `ArtifactKindToken`, the `?kind=` filter, lists
  `asset` and `skill` after `procedure` and before `agent_profile`, and the `400` detail of an unknown `?kind=` names
  them in that order. `Artifact` gains the members `asset` (`Asset`, with `AssetExecutable`) and `skill` (`Skill`,
  with `SkillParameter`, `SkillInvocation` and `SkillExpansion`) in its `oneOf`, which the literal asset and skill
  reads answer. Core must regenerate its DTOs (`ArtifactKindToken`, `Artifact` and the new asset and skill schemas)
  and handle the two kind tokens and the two members before it relies on an exhaustive switch over either.
- An asset id is now one lowercase kebab-case segment of at most 64 characters, the pattern a skill id has
  (`asset.schema.json`). A pack with any other asset id that registered before is now refused as invalid content:
  `artifact_schema_error` in validate mode and `422 validation_failed` at registration. Core's seam must write asset
  ids as kebab slugs, and a pack it holds with another asset id must be renamed before it registers. Every asset of
  the example packs already matches it.
