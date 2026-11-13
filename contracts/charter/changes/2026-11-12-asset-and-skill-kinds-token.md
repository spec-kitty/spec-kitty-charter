### Changed

- `ArtifactKindToken` lists `asset` and `skill`, after `procedure` and before `agent_profile`, and the `400` detail of
  an unknown `?kind=` names them in that order (#105, #107). An asset id is now one lowercase kebab-case segment of at
  most 64 characters, the pattern a skill id has (`asset.schema.json`): a pack with any other asset id is refused as
  invalid content, `artifact_schema_error` in validate mode and `422 validation_failed` at registration. Every asset of
  the example packs already matches it.
