### Added

- `asset` and `skill` are listed kinds of a pack (#105, #107): `listPackArtifacts` lists a pack's assets and skills
  after its procedures and before its agent profiles, and `?kind=asset` and `?kind=skill` filter on them. An asset's
  summary title is its title, or its id when it has none; a skill's is its title. The new `Artifact` members and kind
  tokens are under `### Breaking`. The templated item read
  (`getPackArtifact`) renders neither: it refuses `404 kind_not_found` and names the literal path, as for an
  `agent_profile`. `deletePackArtifact` does not remove an asset or a skill: it answers `404 kind_not_found`, as before.
  A reference whose target is an asset or a skill the compose holds now carries its `artifact` link.
