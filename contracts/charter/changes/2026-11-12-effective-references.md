### Added

- The references twin (#532; FR-012, research R1 and R10), tag `EffectiveReferences`:
  `listEffectiveArtifactReferences` (`GET /activations/{activationId}/artifacts/{kind}/{artifactId}/references`)
  answers `ArtifactReferenceList`, the references the version in force writes. It is the pack read of the references
  (`listArtifactReferences`) of the pack that holds the layer-merge winner, for the same kind and id, item for item and
  in its order, read against the source fingerprint of the read that named the winner (`500 stale_input` when the
  packs changed between the two); an overridden artifact answers the references its winning copy writes. Each item whose
  target the activation's closure holds, of a listed kind, also links `effective-artifact`, its read under the
  activation; the answer links `self` and `up` (the activation). The artifact is answered whatever its state. An
  unknown activation is `404 activation_not_found`, then a kind the service does not list `404 kind_not_found`, then an
  artifact no pack of the closure holds `404 artifact_not_found`. As the pack read, it has no `ETag`. No literal path
  ends in `/references`, so the template answers every listed kind, an asset and a skill included. The operation is
  `ready` in the switch-gate register.
