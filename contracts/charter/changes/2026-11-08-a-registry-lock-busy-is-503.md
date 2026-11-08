### Breaking

- A write that meets the pack registry lock for longer than its timeout answers `503` with `Retry-After` and a plain
  `Problem`, as the registration job's `refused` status does: a per-artifact registration (`registerPack*`, `putPack*`,
  `putPackRelationship`) answered `500 internal_error` before. A client that treated that `500` as a fault now sees a
  `503` it must retry: wait the seconds `Retry-After` names and send the same write again; the write changed nothing.
- The activation writes (`createActivation`, `replaceActivation`, `deleteActivation`, `selectActivationArtifact`,
  `unselectActivationArtifact`, `deactivateActivationArtifact`, `reactivateActivationArtifact` and
  `applyActivationPreset`) run under the pack registry's write lock, as the registrations do, so that a document is never
  judged against a pack set a registration is replacing (the WP15 hand-off). A write that meets the lock for longer than its
  timeout now answers `503` with `Retry-After` and a plain `Problem`, declared on each operation as the new
  `RegistryBusy` response; before, an activation write never met the lock. A client must retry such a write after
  `Retry-After`, the same as the per-artifact writes above.
- The `503` is declared on the nine per-artifact and relationship writes of a pack (`registerPackDirective`,
  `putPackDirective`, `registerPackParadigm`, `putPackParadigm`, `registerPackStyleguide`, `putPackStyleguide`,
  `registerPackToolguide`, `putPackToolguide`, `putPackRelationship`) with the `RegistryBusy` response, and on the two
  `DELETE`s of a pack (`deletePackArtifact`, `deletePackRelationship`), which are first built in this release, with
  `PackRemovalUnavailable`: that response also covers `503 pack_source_unavailable` while registration is switched off.
