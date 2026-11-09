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
  - A selected step enhancement is still reported not in force until PR C's E6 amendment lands (operator ruling OD-6).
    What `listEffectiveArtifacts`, `getActivationSkippedReport` and `ActivationChange.dropped` show for it is
    delivered, with its scenarios, by PR C and the phase-2 mission (#438).
