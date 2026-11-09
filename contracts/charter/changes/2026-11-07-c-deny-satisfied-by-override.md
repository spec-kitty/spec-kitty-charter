### Changed

- A deny entry on an artifact a pack requires is accepted when an active override of it satisfies the requirement, so
  the write behaviour #399 shipped changes: `createActivation`, `replaceActivation`, `deactivateActivationArtifact`
  and the other writes that add a deny entry (apply-preset with `force`, select with `includeDeactivated`) answered
  `409` with `artifact_required` for every deny entry on a required artifact, and now answer `200` (or `201`) when an
  artifact that overrides it is in force in the document as it will be stored (selected or required, within the
  language filter and bound to a mission type in force, not denied and not hidden by an override itself; operator
  ruling Q9, research R12). An enhancement never satisfies, so a document with only an enhancer of the artifact is still
  `409 artifact_required`, and a locked artifact is still `409 artifact_locked` whatever overrides it. A create judges
  every deny entry of the body; a replace, a deactivate, a force preset and a select with `includeDeactivated` judge
  only the deny entries they add (DIV-204). A later write that removes the override (an unselect of it) is not refused:
  the stored deny entry is then read as not applied, and `getActivationSkippedReport` lists it in `denyNotApplied`
  with the reason `required`. The `ArtifactPolicyRefused` response, the `artifact_required` text of
  `CharterRefusalCode` and the descriptions of the three writes say so. A merge refusal of the closure (a forbidden
  layer override) now reaches a write that adds a deny entry on a required artifact, as it reaches the reads.
