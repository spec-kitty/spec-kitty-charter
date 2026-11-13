### Changed

- The `packId` path parameter declares the grammar the service enforces, `^[a-z0-9][a-z0-9-]{0,62}$`, and its
  `maxLength` is 63, not 64; an id outside it was already a `400` without a code, and is now one by the contract itself.
  The asset and skill item and payload paths, in a pack and under an activation, take a new `artifactId` parameter
  (`parameters/AssetOrSkillId.yaml`) with the kebab grammar of `Asset.id` and `Skill.id`,
  `^[a-z][a-z0-9]*(-[a-z0-9]+)*$`, at most 64 characters: `…/asset/Logo` is a `400`, not a `404`.
