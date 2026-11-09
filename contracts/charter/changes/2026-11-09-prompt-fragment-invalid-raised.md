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
