### Changed

- `prompt_fragment_invalid` is raised (#428; Refs #424). A pack registration refuses an invalid step enhancement or
  prompt fragment with `422 validation_failed`, listing every fault as a violation `{pointer: <pack-relative file>,
  code: prompt_fragment_invalid}`; a verified read of a closure that holds such a pack answers `500 pack_unreadable`
  naming the file. The code's description now reads "an invalid step enhancement or prompt fragment".
- The definition of `raised` in `x-refusal` (`CharterRefusalCode`) is amended: a code is also raised when it is
  raised as a violation code of a `422 validation_failed`. The declared `status` of such a code is kept and is read
  as the status of the read that meets the fault.
- `drg_schema_error` is raised by the same definition: a registration refuses a graph document that breaks its schema
  with it, as a violation of `422 validation_failed` (operator ruling OD-3 (a)). Only `pack_level_mismatch` stays
  unraised among the codes the earlier text listed. The raised-code table of `errors.feature` lists both codes.
- The shared texts say what a registration lists: `Problem.violations` also covers the `422 validation_failed` of a
  registration, one entry per fault of the pack, and `RefusalViolation.pointer` is the pack-relative path of the file at
  fault on such a violation. The reserved paragraph of the description now keeps only `prompt_slot_malformed`,
  `prompt_slot_not_empty` and `prompt_slot_missing` reserved, and no longer suggests `artifact_locked` for an ADR-0066
  refusal.
- The `step-enhancement` schema's description (contract text, no property changes) lists the rules that answer
  `prompt_fragment_invalid`: an enhancement whose id differs from its file stem is refused by the store's stem rule as a
  schema violation, not `prompt_fragment_invalid`, as a slot that breaks `<namespace>:<name>` and
  `prompt_fragments: []` (`minItems`) are; a repeated slot, a missing, shared or orphan companion, the edge rules, the
  directory, a repeated id and an absent `prompt_fragments` key are `prompt_fragment_invalid`. It also says that guards
  and emits are fields (`add_guards`, `add_lifecycle_guards`, `emits`) built under #434, not edges. The `path`
  description no longer binds a companion to its enhancement's id (one companion per entry, any stem matching the
  pattern, each named by exactly one entry), and `file-kinds.yaml` carries the same wording.
