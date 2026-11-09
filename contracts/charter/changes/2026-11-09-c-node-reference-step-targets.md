### Breaking

- `EnhancerWithoutTarget.targets` (`SkippedReport.enhancedTargetNotInForce`) is a list of `NodeReference` with
  `minItems: 1`, where it was a list of `ArtifactReference` (#429; operator ruling D15). Every carrier of `SkippedReport`
  changes: the response of `getActivationSkippedReport` (`report`), the response of `getEffectiveActionContext`
  (`skipped`), and the `ActivationChange.skipped` of the five writes that answer an `ActivationChange`
  (`selectActivationArtifact`, `unselectActivationArtifact`, `deactivateActivationArtifact`,
  `reactivateActivationArtifact` and `applyActivationPreset`). In each, an item of `enhancedTargetNotInForce[].targets`
  renames `artifactId` to `id`, and its `kind` widens from `ActivatableKind` to `NodeKind`, so a target may be a step of
  a mission type (`{kind: action, id: <missionType>/<step>}`), which the list names where it used to drop it. The
  enhancer's own `kind` and `artifactId` do not change, and neither does any other bucket: `ArtifactReference` stays
  the reference of an artifact an activation can select.
- A consumer that reads a target by `artifactId`, or that validates `kind` against `ActivatableKind`, has to read `id`
  and `NodeKind`; the strict DTOs of core have to be regenerated.
