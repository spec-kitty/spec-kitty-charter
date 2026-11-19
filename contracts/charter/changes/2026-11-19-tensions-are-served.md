### Added

- `getActivationTensions`, `GET /activations/{activationId}/tensions` (#602, ADR-0045 decisions 11 and 12): every
  tension the activation puts in force (`ActivationTensions.tensions`, each a `ContextTension`) and one
  `unreconciled_tension` finding (`TensionFinding`) per tension no artifact in force reconciles. A finding offers
  `reconcile` always and `deactivate_one_side` when the exclusion write would accept a side; it links
  `effective-source` and `effective-target`, and `exclude-source` or `exclude-target` (a concrete `PUT`) for each side
  that write accepts. A finding never refuses. The read has its own entity tag, never the skipped report's. The
  `Activation` answer links it as `tensions`; the relations `tensions`, `effective-source`, `effective-target`,
  `exclude-source` and `exclude-target` join the relation table. New schemas: `ActivationTensions`, `TensionFinding`,
  `ContextTension`, `TensionCondition`, `TensionReason`, `TensionReconcilers`.
