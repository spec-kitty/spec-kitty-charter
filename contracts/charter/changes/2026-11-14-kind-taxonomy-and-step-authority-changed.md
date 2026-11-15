### Changed

- The `Offering` tag's description covers the model's kind taxonomy as well as the configured packs, and the lint
  rules `fingerprint-on-verified-reads` and `fingerprint-only-on-verified-reads` exempt `/offering/kinds`, which reads
  no pack file (`docs/development/rest-resource-conventions.md`; planted fixture
  `fingerprint-only-on-verified-reads.kinds.yaml`). No answer changes.
- `deletePackRelationship`'s `422` description names `substep_delegation_unscoped` at `/to`, the refusal of removing
  the last `scope` edge a substep's delegation candidate needs, which the service already answers; a scenario of
  `pack-removal.feature` pins it, with a control that removes a `scope` edge no substep needs. The composing-read
  scenario of `mission-types.feature` for an unlinked step also asserts that the detail names the step. No answer
  changes.
