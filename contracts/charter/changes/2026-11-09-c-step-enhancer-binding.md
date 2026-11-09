### Changed

- A selected step enhancer is grounded exactly when the mission type of the step it enhances is in force (rule E6, as
  amended by operator ruling OD-6). It applies to a `step_enhancement` with exactly one `enhances` edge, to an `action:`
  URN, and to no other shape: an enhancer with two targets, with an action among other targets, or of another kind
  whose target is an action keeps the enhancer rule as before. When the step is one the closure holds (a pack defines
  `mission_type:M` and declares `action:M/s`), the enhancer is bound to `mission_type:M`: it is in force while `M` is,
  and `EffectiveArtifact.reason` is `selected` (or `required`, `locked`); with `M` out of force its reason is
  `mission_type_not_in_force` and the `SkippedReport` lists it in `missionTypeNotInForce`. When no pack holds the step,
  the edge stays and the enhancer is out of force with the reason `enhanced_target_not_in_force`, as any enhancer whose
  target is not in force. An enhancer selected next to its mission type, with no step in the selection (the selection
  cannot name an action), was listed in `enhancedTargetNotInForce` on a read and warned about with
  `enhanced_target_not_in_force` in `ActivationChange.warnings` on a write; it is now in force, and neither carries it.
  `requiredNotInForce` lists a required or locked step enhancer with the reason `mission_type_not_in_force`. No
  operation, schema or refusal code changes.
