### Changed

- A refusal of the closure checks names every fault, not only the first (#556, ADR-0075 slice 7). The composition,
  reference and relation codes, `specialization_cycle`, the `fallback_profile`, `role_pin` and
  `profile_enhancement_drops_field` codes and `governance_policy_violation` collect every fault of a write or a removal
  into the one refusal (`pack_has_dependents` already named every dependent). A single fault answers exactly as
  before. Several faults keep the first fault's code and status; the detail ends
  `(N fault(s); every one is in violations)`, and `violations` holds a header violation (empty `pointer`, the fault's
  `code` and detail, naming the closure it was found in) followed by that fault's own violations, fault by fault;
  `RefusalViolation`'s `pointer` text says so. No code, status or field is added,
  removed or renamed; the change is additive for a client that reads `violations`.
