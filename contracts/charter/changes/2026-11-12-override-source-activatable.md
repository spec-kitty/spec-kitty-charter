### Breaking

- An `overrides` edge whose source is not of an activatable kind (a template, an asset, a guard or a lifecycle) is
  refused (FR-014, operator ruling of 2026-11-12 on #447, option 1). Such an artifact has no activation state, so the
  read dropped it as an overrider while a lock could still put it in force, and the deactivation then found no
  satisfier. A registration answers `422 validation_failed` with one violation per such edge, code
  `overrides_source_not_activatable`, at its pack-relative graph document (example
  `PackRegistrationStatus.overrides-source-not-activatable`). `putPackRelationship` answers `422 validation_failed` with
  the violation at `/from` and writes nothing (example `CharterRefusal.overrides-source-not-activatable`). A composing
  read of a closure that holds such a pack answers `500 pack_unreadable`, naming the graph document
  (`override-source-kind.feature`). Registrations, relationship writes and reads that answered before are refused now.
  An `overrides` edge from an activatable kind is unchanged. spec-kitty-open-packs authors no `overrides` edge, so no
  pack content changes. Core's seam: a client that reads the violations of `validation_failed` meets one more code;
  no `CharterRefusalCode` is added.
