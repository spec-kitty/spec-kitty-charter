### Breaking

- ADR-0066 prompt-fragment contracts (#424; spec-kitty-open-packs#175). Pack descriptors gain optional
  `schema_version: "1.0"`, which switches on empty prompt slots; unversioned descriptors keep legacy non-empty slots
  with a `prompt_slot_not_empty` warning. Step enhancements gain `prompt_fragments: [{slot, path}]` and the
  `<id>.prompt-fragment.md` Markdown companion, with slot and graph vocabulary in the pack schemas and
  `activated_step_enhancements` in presets. `ActivatableKind`, selected and deactivated artifact maps, cascade kinds
  and node kinds gain `step_enhancement`, so core must regenerate its activation DTOs and handle the new kind before
  selecting or applying a preset that names one. `CharterRefusalCode` reserves `prompt_slot_malformed`,
  `prompt_slot_not_empty`, `prompt_fragment_invalid` and `prompt_slot_missing`, all `raised: false` until #424 builds
  the checks; `DiagnosticCode` reserves the legacy-warning spelling of `prompt_slot_not_empty`. As with other 0.x
  contract additions, core must regenerate its refusal-code DTOs; no existing operation raises the new codes.
  The service classifies and validates step-enhancement files but does not store or activate the kind until #424
  (#428): until then, selecting a `step_enhancement` answers `404 artifact_not_found`, and a preset's
  `activated_step_enhancements` ids are stale.
