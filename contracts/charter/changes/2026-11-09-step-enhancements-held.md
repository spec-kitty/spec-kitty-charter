### Changed

- The service holds step enhancements and their `*.prompt-fragment.md` companions (#428; Refs #424), so a step
  enhancement is selectable like any other artifact:
  - `PUT /activations/{activationId}/selections/step_enhancement/{artifactId}` answers `200` for a held enhancement
    instead of `404 artifact_not_found` (scenario "A held step enhancement is selectable", `step-enhancements.feature`).
  - A preset's `activated_step_enhancements` ids are kept, no longer reported stale, when the activation is written
    again (`activation-presets.feature`).
  - A registration of a pack that carries step enhancements is accepted, where it was refused `422` as an unmodelled
    file (`paths/packs_packId.yaml`), and the whole-pack write keeps the enhancement files and the fragments beside
    them (scenario "A pack that carries a step enhancement and its fragment registers", `step-enhancements.feature`).
  - Now that the service holds step enhancements, a selected one is in force exactly when the mission type of the step
    it enhances is in force (rule E6 as amended by OD-6, `2026-11-09-c-step-enhancer-binding.md`).
  - `PUT /packs/{packId}/relationships` accepts the one `enhances` edge of a held step enhancement, where it answered
    `409 registration_conflict`.
