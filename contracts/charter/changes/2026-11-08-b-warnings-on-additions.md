### Changed

- `ActivationChange.warnings` (the `ActivationWarning` codes `enhanced_target_not_in_force` and `overridden`) warn only
  for the artifacts the write puts in: a selection, or an item a preset adds to `selected`. A removal (an unselect), a
  deny entry and a reactivation never warn, though the artifact's reason at read may be the enhancer's or the
  override's, which E9 ranks before the deny row; a write answered such a warning for them before. A skipped artifact is
  listed once: a required or locked artifact the enhancer rule or the binding rule removes from force is in
  `requiredNotInForce` only, no longer in `enhancedTargetNotInForce` or `missionTypeNotInForce` as well, and
  `ActivationSummary.skipped` counts it once. No operation or schema changes.
