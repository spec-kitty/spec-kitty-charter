### Added

- `getEffectiveArtifact`, `GET /activations/{activationId}/artifacts/{kind}/{artifactId}` (tag `Activation`), the generic
  activation-scoped read of one artifact (#407, #96): the version the layer merge keeps across the levels of the
  activation's closure, as `EffectiveArtifactRead` (closed): `activationId`, `revision`, `governanceFingerprint`, `kind`,
  `artifactId`, the `packId` and `level` that hold the version in force, and its `state`, `required`, `policy` and `reason`
  as the effective list gives them, with `satisfiedBy` and `suggestion` where the list has them. It embeds no body:
  `_links` holds `self` (with `depth` when the request sent it), `artifact` (the pack-scoped read of the winner's pack) and
  `up` (the activation). `kind` is any kind the pack-scoped artifact routes serve; an `agent_profile` is answered by its
  own literal operation. The refusals are judged in order: `activation_not_found`, `kind_not_found` and, for an id that
  no pack of the closure holds, `artifact_not_found`, before any `If-None-Match`. The answer carries
  `Charter-Source-Fingerprint` and a strong `ETag` over the activation's revision, the packs of its closure and the kind,
  the id and the `depth`; a matching `If-None-Match` is `304`, and `depth` outside 0 to 1 is a `400`.
