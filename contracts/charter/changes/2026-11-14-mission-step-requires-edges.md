### Breaking

- Every step and on-demand step of a mission type is linked by an authored `mission_type:<type> requires
  action:<type>/<step>` edge, and no such edge names an action that is not one of the type's steps (operator ruling
  DM-01M7B1YQR5ZKQPDW6DX64KPNRN). A registration answers `422 validation_failed` with one violation per unlinked
  step at the mission type file and one per stray edge at its graph document, code `mission_step_unlinked` (example
  `PackRegistrationStatus.mission-step-unlinked`). A composing read answers `500 pack_unreadable`, also when another
  pack writes a `requires` edge at the mission type in force that names no step of the type. `putPackRelationship` of
  a stray edge, and `deletePackRelationship` of the last edge that links a step, answer `422 validation_failed` with
  the violation at `/to`. A mission type that authors `action_sequence`
  or `template_set` is refused as an unknown field by name, and the project charter schema no longer has
  `template_set` (#520): a `charter.yaml` whose catalog carries it is refused. `deletePackArtifact` of a mission type
  drops the type's own step links from the author-written graph documents that hold them (operator ruling of
  2026-11-14), the one case where a removal rewrites such a document. Core's seam: a client that reads the
  violations of `validation_failed` meets one more code; no `CharterRefusalCode` is added.
