### Breaking

- A deny entry on an artifact a pack requires is accepted when an active override of it satisfies the requirement, so
  the write behaviour #399 shipped changes in two answers. First, `createActivation`, `replaceActivation` and
  `deactivateActivationArtifact` answered `409` with `artifact_required` for every deny entry they judged on a
  required artifact, and now answer `200` (or `201`) when an artifact that overrides it is in force in the document as
  it will be stored (selected or required, within the language filter and bound to a mission type in force, not
  denied and not hidden by an override itself; operator ruling Q9, research R12). Second, a write that judges such a
  deny entry reads the closure through the in-force resolution, so when the closure's layer merge refuses (a
  forbidden layer override) the write answers `500` with `pack_unreadable` where it answered `409`; the `500` of
  `OfferingUnreadable` is declared on these writes already, and the other per-artifact writes that apply anything
  reach the same refusal (DIV-230), as the effective reads do. An enhancement never satisfies, so a document with only
  an enhancer of the artifact is still `409 artifact_required`, and a locked artifact is still `409 artifact_locked`
  whatever overrides it. A create judges every deny entry of the body. A replace judges only the deny entries it adds.
  A deactivate always judges the named target, whether or not its deny entry is already stored, so a second
  deactivate of a locked artifact is `409 artifact_locked`, and of a required artifact whose override was unselected
  is `409 artifact_required`. A preset applied with `force` and a select with `includeDeactivated` add no deny entry
  and judge none (DIV-204). A later write that removes the override (an unselect of it) is not refused: the stored
  deny entry is then read as not applied, and `getActivationSkippedReport` lists it in `denyNotApplied` with the
  reason `required`. The `ArtifactPolicyRefused` response, the `artifact_required` text of `CharterRefusalCode` and
  the descriptions of the three writes say so. A client that relied on the documented `409
  artifact_required` of `createActivation`, `replaceActivation` or `deactivateActivationArtifact` now gets `200` (or
  `201`) when an active override satisfies the requirement, and the declared `500 pack_unreadable` (the `500` of
  `OfferingUnreadable`) when the closure's layer merge refuses; it must treat both as outcomes of those writes. Core's
  in-process backend still refuses the deny entry (DIV-204).
