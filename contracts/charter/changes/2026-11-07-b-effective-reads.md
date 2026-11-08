### Breaking

- `listEffectiveArtifacts` and `getActivationSkippedReport` are declared as built operations, and four documented
  responses of operations already on the contract change with them (IC-08, IC-17, IC-20; FR-013 to FR-017, FR-022,
  FR-027, FR-028, FR-032). Allowed on the 0.x line (ADR-0022): no `contract-charter-v*` baseline exists yet.
  - `EffectiveArtifact` gains the required `packId` (the configured identifier of the pack that holds the version in
    force) and `level` (`PackLevel`), the winner's provenance after the layer merge (FR-013). It also reaches the
    `artifacts` of `EffectiveActionContext`, which uses the same schema. A client that builds the closed object from
    a response before regenerating fails on the new members.
  - `EffectiveArtifactReason` loses `kind_default` (FR-022: ADR-0053 d10 made it unreachable) and `kind_not_activated`
    (the kind gate is gone with IC-20). A client that switches on the closed enum and still lists either value no
    longer compiles after regeneration.
  - `SkippedReport` gains ten required buckets: `staleIds`, `denyNotApplied`, `languageFiltered`, `overridden`,
    `missionTypeNotInForce`, `overrideNotApplied`, `enhancedTargetNotInForce`, `pinNotHeld`, `requiredNotHeld` and
    `requiredNotInForce`. Every answer that carries the schema now carries them: a write's `ActivationChange.skipped`
    and the `skipped` of `EffectiveActionContext` answer them as `[]`. `skippedByScope` stays: it is the write report
    of the `cascade` scope and is always empty at read (decision `DM-01M6RN5MTWC98PXEVJNFQSRRJX`).
  - `ActivationChange` gains the required `warnings` (`[{code, artifact}]`, `[]` when none), FR-032 (B6/B7): each
    artifact the write applied that a read will not put in force.
  - Core's seam: core must regenerate its strict DTOs from this contract before it calls a server with this change,
    and must stop reading `kind_default` or `kind_not_activated` as a reason. Nothing is edited in core by this change
    (C-003).
  - `Activation` gains the optional `denyNotApplied` (`[Urn]`, B4, FR-027) and `PackRegistrationNotices` the optional `activationsAffected` (`[ActivationId]`, B4, FR-027). Both objects are closed responses of operations already on the contract (`getActivation`, `createActivation`, `replaceActivation`, a succeeded registration), so a new member is breaking (`contracts/lint/README.md`; the A0 precedent `Activation.dropped`): a strict client must regenerate.
  - The `ETag` of `getActivation` is no longer the bare quoted revision (decision `01M6Y11E55YP5TG1EJ1MDKCFRP`): it covers the revision and the `governanceFingerprint` of the packs of the closure, because `denyNotApplied` is judged against those packs. It changes when a pack of the closure changes, and not when a pack outside it does; `getActivation` can now also answer `500` with a composition code or `pack_unreadable`.
  - What a write's `If-Match` takes (decision `01M6Y3GZFRC5WEQQ7G6GT0YMW3`): `replaceActivation`, `deleteActivation` and the sub-resource writes accept the quoted revision or the current `getActivation` tag, so a client may send what it read; a closure tag that no longer matches is `412`. A write's answer keeps the revision as its `ETag` and carries no `denyNotApplied`. A client that compared the `getActivation` `ETag` to `revision` must stop doing so. ADR-0020 D6 and ADR-0059 D5 are amended by the docs work package.
