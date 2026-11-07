### Breaking

- One kind model on the activation document (IC-20, FR-040, FR-041; rulings A7 `DM-01M6RA8BJ0FZV9BHFJ1MV4SVJY`, Q1
  `DM-01M6RCXTS0SMDNTWZ8G7GJ2P5S`, Q2 `DM-01M6RCXZ9HSHT4K3443YYZ8ZFC`, Q3 `DM-01M6RCY40CQQTHC130RWF43W0D`, Q4
  `DM-01M6RCY8MYNGNPJ247M2Y4880N`). The document is `packs`, `selected`, `deactivated` and `languages`; mission types,
  steps and agent profiles are ordinary kinds of it. Allowed on the 0.x line (ADR-0022): no `contract-charter-v*`
  baseline exists yet.
  - `ActivationConfig` loses `activatedKinds` and `missionTypes`. The kind gate is gone: nothing is in force because
    its kind is "activated", and a select brings its artifact into force. Mission types are selected under
    `selected.mission_type` and denied under `deactivated.mission_type`.
  - A write (`createActivation`, `replaceActivation`) that sends `activatedKinds` or `missionTypes` is
    `422 validation_failed`, one violation per member at its pointer naming its replacement (`selected.mission_type`
    for `missionTypes`, `selected.<kind>` for `activatedKinds`). There is no deprecation window. One `removed_member`
    violation makes the whole refusal `validation_failed`, even with other violations such as `unknown_member` listed
    beside it (ruling Q4; `activation-kind-model.feature`, "activatedKinds in a create body is 422 validation_failed
    ...").
  - `ActivatableKind` gains `mission_type`, and `SelectableKind` is removed: `ArtifactReference.kind` and the
    selections path's `kind` (`SelectableKindPath`) name `ActivatableKind`, one enum for every kind.
    The widened enum reaches `EffectiveArtifact.kind`, `SharedSkip.kind`, `SkippedDeactivated.kind` and the
    `EffectiveKindQuery` parameter as well, so a response can carry `mission_type`. The generated class
    `SelectableKind` disappears: client code that names it no longer compiles and uses `ActivatableKind`.
    `SelectedArtifacts` and `DeactivatedArtifacts` gain `mission_type`; `CascadeKind` gains `mission_type` (rule
    W21). The exclusions path accepts `kind=mission_type`: a deny entry on a required or locked mission type is
    `409 artifact_required` / `artifact_locked`, like any kind, and an unknown one is `404 mission_type_not_found`.
  - The stored document's `schemaVersion` is `2` (`minimum` and `maximum` `2` in `ActivationConfig.schema.json` and
    its mirror, so the generated DTO keeps an integer). A document stored at version 1 is answered as its version 2
    upgrade (its `missionTypes` become `selected.mission_type`, every selection is kept, `activatedKinds` is
    discarded) and stored as version 2 on the next write (decision `DM-01M6RFGMDH859CJP3RHHF9YC3H`), so every
    activation's revision and `ETag` change once: a client holding an old `ETag` gets one `412`.
  - `ActivationChange` gains the required `droppedPins` (`[{packId, version}]`, `[]` when none): the four
    per-artifact writes and `applyActivationPreset` drop a version pin no pack of the closure holds instead
    of answering `422 version_not_held` (B1); `createActivation` keeps `422 version_not_held` for a pin in its own
    body (decision `DM-01M6RN5Y406SB4KA13QYMQ2P8J`).
  - The preset schema (`contracts/packs/schemas/preset.schema.json`) loses `activated_kinds`: a preset carrying it
    refuses the pack as `500 pack_unreadable` when it is loaded, as the preset item id grammar does
    (`2026-11-07-preset-item-id-grammar.md`); a `422` on registration is planned for PR A. It gains `activated_skills` and
    `activated_glossary_packs`, so a preset may name any kind; `activated_mission_types` is a plain member list applied as `selected.mission_type`.
  - The canonical form drops an empty list (FR-042): `selected.<kind>: []` is left out on the wire, so a read answers
    the kind absent, not `[]`, and the revision of a document that held one changes. A client that compares
    `selected.<kind>` to `[]` must treat absent as none.
  - The `ActivationConfig` corpus moves to version 2: `valid/02`, `valid/03` and `valid/08` are rewritten, a
    `selected.mission_type` document is now valid, and `invalid/14-activated-kinds-left-out.json` and
    `invalid/18-mission-types-left-out.json` pin the two removed members.
  - Changed with it, compatible on its own: `applyActivationPreset` applies every kind of the preset; selecting a
    mission type with a cascade reaches the steps it holds, crossed like `requires` (Q1, rule W20), and a cascading
    deactivate of a mission type unselects what only it kept (rule W23); a mission type is deniable unless it is
    required or locked (Q2).
  - Core's seam: core must regenerate its strict DTOs from this contract before it calls a server with this change.
    It then selects mission types under `selected.mission_type`, sends neither `activatedKinds` nor `missionTypes`
    (each is refused), and loses `activated_kinds` narrowing: a core project that narrows it sees a larger effective
    set. Nothing is edited in core by this change (C-003); the migration note is handed over in the pull request.
