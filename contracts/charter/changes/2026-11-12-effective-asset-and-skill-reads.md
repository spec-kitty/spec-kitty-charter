### Added

- The activation twins of the asset and skill reads (#105, #107; FR-009 to FR-011, research R1 to R3 and R15), tags
  `EffectiveAssets` and `EffectiveSkills`. `listEffectiveAssets` (`GET /activations/{activationId}/artifacts/asset`)
  answers `AssetList` of the assets the closure holds, each the copy the layer merge keeps (a project copy over a
  built-in one), in id order, unpaged; an asset is in force when a pack of the closure holds it.
  `getEffectiveAsset` (`GET …/asset/{artifactId}`) answers `EffectiveAssetRead {activationId, revision,
  governanceFingerprint, kind: asset, artifactId, packId, level, policy, asset, _links}`: no `state`, `reason` or
  `required`, which an asset never has, and links `self`, `artifact`, `payload`, `collection` and `up`.
  `getEffectiveAssetPayload` (`GET …/asset/{artifactId}/payload`) answers the winning copy's bytes as
  `getPackAssetPayload` answers a pack's, read against the source fingerprint of the read that named the winner
  (`500 stale_input` when the packs changed between the two). `listEffectiveSkills` (`GET …/artifacts/skill`) answers
  `SkillList` of the closure's skill winners, whatever their state, and `getEffectiveSkill` (`GET …/skill/{artifactId}`)
  answers `EffectiveSkillRead`, the members of `EffectiveArtifactRead` plus `skill`, whatever its state. A list item
  links `effective-artifact` and `artifact`; a list links `self` and `up`. Each of the five answers its own
  `EffectiveETag` and `304` to a matching `If-None-Match`; an unknown activation is `404 activation_not_found`, then an
  asset or skill no pack of the closure holds `404 artifact_not_found`, judged before `If-None-Match`. The effective
  list keeps answering `?kind=asset` with `400`, and the templated effective read (`getEffectiveArtifact`), which the
  literal routes win over, refuses `asset` with `kind_not_found` naming the literal path. The five operations are
  `ready` in the switch-gate register.
