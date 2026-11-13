### Added

- An `Activation` links its held assets and skills (#409, #105, #107): `assets` (`listEffectiveAssets`), `asset`,
  templated `…/activations/{activationId}/artifacts/asset/{artifactId}` (`getEffectiveAsset`), and `skills`
  (`listEffectiveSkills`), after `effective-context`. A guard runner (`asset:<id>`) expands `asset` with the asset's id
  and reads it, and its payload, without building a path. The relation table gains the three relations, in the
  contract on an activation.
