### Added

- `listEffectiveArtifacts` (`GET /activations/{activationId}/artifacts`) and `getActivationSkippedReport`
  (`GET /activations/{activationId}/skipped`) lose `x-reserved` and their `501`, so the contract declares them as
  operations that answer (IC-08). `ReservedOperationCoverageTest` counts two reserved operations of the `activation`
  set (the effective graph and the effective action context, PR C), and `activations.feature` loses the two `501`
  pins; `effective-reads.feature` and `layer-merge.feature` replace them with `@WIP` scenarios that the serving work
  package binds and turns green.
  - The list answers every artifact of the merged closure with its `state` (ADR-0020 D7, operator ruling Q1): "in
    force" is `state=active`. It is filtered by `kind` and `state`, paged by a keyset cursor over `(kind, id)` bound to
    the tag of the set it pages (a cursor from another tag is `400`), and answers the `summary` of the whole set,
    identical on every page. `ETag` and `If-None-Match` follow the representation (revision, the packs of the
    activation's closure, `kind`, `state` and the cursor), so one representation never answers `304` to the tag of
    another. A `500 pack_unreadable` is documented for an override the mutability level forbids (ADR-0063 D3) and for
    an `overrides` cycle among active artifacts; the language filter applies at read only (#408 ruling 2).
  - The skipped report lists each artifact not in force once, in the typed bucket of its first deciding rule (data
    model: EffectiveSkippedReport).
- `EffectiveArtifact.satisfiedBy` (`ArtifactReference`, optional): for a required artifact that is out of force, the
  active artifact that overrides it and so satisfies the requirement. It never names an enhancer.
- `EffectiveArtifactReason` gains `overridden`, `mission_type_not_in_force` and `enhanced_target_not_in_force` (the
  last for an enhancer none of whose targets is in force, operator ruling Q8). `EffectiveArtifact.required` is true
  for a required artifact that stays out of force for either of the last two reasons.
- `SkippedReport` item schemas: `DenyNotApplied`, `OverriddenArtifact`, `EnhancerWithoutTarget` and
  `RequiredNotInForce`. `pinNotHeld` reuses `PackPin`.
- `ActivationWarning` (`{code, artifact}`, codes `enhanced_target_not_in_force` and `overridden`).
- `Activation.denyNotApplied` (`[Urn]`, optional, B4, FR-027): the deny entries of the stored document that the required
  floor or a lock overrides, present only when there is one.
- `PackRegistration.notices.activationsAffected` (`[ActivationId]`, optional, B4, FR-027): the stored activations whose
  in-force set a registration changes. The other notice lists still name activatable kinds only (issue #417).
- Examples: `EffectiveArtifactList.first-page` and `EffectiveActionContext.implement` carry `packId` and `level`,
  `ActivationSkippedReport.nothing-skipped` carries the empty buckets, and `ActivationSkippedReport.each-bucket` is new.
