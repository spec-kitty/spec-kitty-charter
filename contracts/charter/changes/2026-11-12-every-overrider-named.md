### Breaking

- `satisfiedBy` and `overriddenBy` name every overrider in force, where they named the one with the smallest URN
  (operator ruling of 2026-11-12 on #439, option (b); rules E5, E9 and E14). Each is now an array of
  `ArtifactReference` (`minItems: 1`), sorted by the level of the pack that holds the version in force of each overrider
  (built-in, then org, then project), then by full URN in Unicode code point order. `satisfiedBy` stays absent when no
  overrider satisfies the artifact. The state and the reason of every artifact are unchanged.
- The schemas that change: `EffectiveArtifact.satisfiedBy` and its copies `EffectiveArtifactRead`,
  `EffectiveContextArtifact`, `EffectiveGovernanceEntry` and `EffectiveAgentProfileRead`; and
  `OverriddenArtifact.overriddenBy`, the item of the skipped report's `overridden` and `overrideNotApplied`. An entry of
  `overrideNotApplied` now names its locked target once, with every overrider in force whose edge was not applied,
  where it was one entry per edge, so `ActivationSummary.skipped` counts such a target once. The schemas are closed
  (ADR-0022 D4), so a consumer that reads either member as an object refuses the array.
- The operations whose answer changes shape: `listEffectiveArtifacts`, `getActivationSkippedReport`,
  `getEffectiveActionContext` (its items and its `skipped`), `getEffectiveArtifact` and `getEffectiveAgentProfile` (the
  answer and its governance entries). The effective mission-type reads carry neither member. A write's
  `ActivationChange.skipped` answers `overridden` and `overrideNotApplied` as `[]`, as before.
- Core: nothing while it is not a consumer (epic #186).
