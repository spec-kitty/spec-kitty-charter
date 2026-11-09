### Changed

- `prompt_slot_malformed` and `prompt_slot_not_empty` are raised (#424; Refs #428), as violation codes of a refused
  registration under the amended definition of `raised`: a registration lists the fault at the step prompt's
  pack-relative file inside `422 validation_failed`, `{pointer: <prompt file>, code: prompt_slot_malformed}` or
  `{pointer: <prompt file>, code: prompt_slot_not_empty}`, and a verified read of a closure holding such a pack
  answers `500 pack_unreadable` naming the file. The reserved paragraph of the description now keeps only
  `prompt_slot_missing` reserved, raised by the phase-2 mission (#438). `errors.feature`'s raised-code table lists
  both codes, and `prompt-slots.feature` covers them at registration and on a read.
- `DiagnosticCode`'s description no longer says the `prompt_slot_not_empty` warning is reserved for #424: it is listed
  in the `diagnostics` of the result of a succeeded registration for a pack whose descriptor declares no
  `schema_version`; the effective-prompt read's `diagnostics` arrive with the phase-2 mission (#438).
