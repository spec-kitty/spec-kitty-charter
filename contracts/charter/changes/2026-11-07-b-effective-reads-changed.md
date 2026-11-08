### Changed

- `required` is reworded from "a required artifact is always active" to the rule of the layer model: a governing
  preset requires it or a pack locks it; a locked one is always active; a required one is active unless an active
  override of it satisfies the requirement, or it cannot stand in force (a required enhancer with no target in force,
  or a required artifact bound to a mission type not in force, decisions `DM-01M6R4W3ZMSESETMQ0ZQTCZCFJ` and
  `DM-01M6RFGFW3S0YJ222QMHD2W11B`). It is never satisfied by an enhancement. `ActivationSummary.required` and `locked`
  follow ("in force or satisfied"), and `ActivationSummary.skipped` counts the entries across every bucket of the
  skipped report.
- `SkippedReport.skippedByScope` says it is always empty at read; `SkippedDeactivated` says that at read `viaRequires`
  is `false` and `requiredBy` is `[]`. `EffectiveArtifact.kind` takes the merged kind enum, so the list shows mission
  types.
- `EffectiveArtifactList.governanceFingerprint` and `ActivationSkippedReport.governanceFingerprint` name the packs of
  the activation's closure that the answer was computed from.
- `Activation` and `getActivation` say that `denyNotApplied` is judged against the packs of the closure and that the `ETag`
  covers the revision and the closure's fingerprint (see the Breaking entry in `2026-11-07-b-activation-etag.md`).
- The relaxed write rule (IC-13, operator ruling Q9) is declared by the work package that serves it; this fragment
  adds nothing for it.
