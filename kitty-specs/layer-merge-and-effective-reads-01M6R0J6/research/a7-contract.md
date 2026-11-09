# A7 contract lens: activatedKinds, missionTypes, steps, agent profiles (fold-b @ 1d37ccd9)
Paths are relative to .worktrees/fold-b; `rules.md` is composition-and-activation-rules.md on issue-10-layer-merge-effective-reads.

## Inventory
- Document: `ActivationConfig.yaml:7-9,46-65` holds the kind gate `activatedKinds` (absent = none) and a separate `missionTypes`.
  `ActivatableKind.yaml:3-5` leaves out mission_type ("which missionTypes holds"). `SelectableKind.yaml:3-4,19` exists only to add it back
  for selections. `SelectedArtifacts`/`DeactivatedArtifacts` have no mission_type member. Steps (`mission_step_contract`) and `agent_profile` are
  ordinary members there (`SelectedArtifacts.yaml:51-65`), so they are already uniform except for the kind gate.
- Drift: `offering/schemas/ActivationConfig.schema.json:31` still says activatedKinds "Absent means every kind". The charter schema (:51) and
  ADR-0053 d10 say none. ADR-0020:198 matches the offering schema. Core's own `activated_kinds` means absent = unrestricted (ADR-0042:207-211).
- Reads: `EffectiveArtifact.yaml:13-15` and `EffectiveKindQuery.yaml:6` are typed by ActivatableKind, so **a mission type cannot appear in
  the listing at all**. That contradicts A7(b) and E9's "active, required" mission type. `EffectiveArtifactReason.yaml:6,11` still has
  `kind_default` (dropped by DM-01M6R0TNEBR7KGJMW2SQXQGJ0A) and `kind_not_activated`. It lacks E9's `mission_type_not_in_force`, `overridden` and
  `enhanced_target_not_in_force`. The action context is keyed on missionTypes (`paths/...missionTypes_missionType_actions_action_context.yaml:11`, 501).
- Writes: the select path names `mission_type` (no cascade; 404 mission_type_not_found: `paths/..._selections_kind_artifactId.yaml:9-10,21`). The exclusions path
  refuses it with 422 (`..._exclusions_kind_artifactId.yaml:23`; feature `activation-selections.feature:1535-1540`). `ActivationChange.yaml:27-33`: "a kind is
  never an entry", so a write that changes the gate reports nothing. `StaleIds.java:12-13` never drops a kind.
- W17 (presets): `paths/..._presets_presetId.yaml:9` replaces activatedKinds/missionTypes. `PresetRules.java:74-81` filters the preset gate through
  `ActivationDocument.kinds()` (= activatable minus mission_type, `ActivationDocument.java:50-55`), so a mission_type in a preset's activated_kinds is
  silently stripped (rules.md:478-480 `kinds' = π.kinds ∩ Kₛ`). `Preset.java:45-58` holds mission types in their own list (and excludes skill and glossary_pack).
  Features: `activation-presets.feature:183-218` (preset rewrites activatedKinds to [directive], "no kind" in applied) and :220-241.
- Gate usage in code: `ActivationDocument.inForce` (:122-129) is called only by tests (`ActivationDocumentTest.java:61-80`). The write side already treats
  "selected or in missionTypes" as in force, with no gate (`CascadeRules.java:358-363`). The corpus refuses `activatedKinds:[mission_type]`
  (`offering/corpus/ActivationConfig/invalid/14-activated-kind-mission-type.json`).
- Client: `TolerantClientNormalizer.kt:24-27` opens closed objects. `charter.client-java.gradle.kts:64-65` decodes an unknown enum value to UNKNOWN_DEFAULT_OPEN_API.
  The generated `ActivationConfig.java:63-73` initialises packs, activatedKinds and missionTypes to `new ArrayList<>()`, so a GET of a document that omits them decodes to [].
  A GET then PUT round trip therefore sends `[]`, which changes the revision, against "absent and [] stay distinct" (ActivationConfig.yaml:8). This is read from the
  generated code; no client test covers it.

## (1) What a consumer sees today
1. On `{packs}` with no activatedKinds: select `directive:D` answers 200 with applied=[directive:D] and a new ETag. Under E2/E9 (rules.md:594,646), D is
   `inactive, kind_not_activated`: nothing comes into force, the write reports nothing wrong, and no write except a full PUT or a preset sets the gate (B7).
2. The write side and the read side disagree. The deactivate cascade counts D as active with no gate (CascadeRules:358-363), while the read would call it inactive.
3. A preset that gates `[directive]` applied over `activatedKinds:[directive,tactic]` takes every selected tactic out of force. applied lists no
   kind (presets.feature:185), so the change is invisible in the answer.
4. A preset authored with `activated_kinds:[mission_types,…]` loses that entry (PresetRules:78-80), but its mission types still land in missionTypes.
5. A mission type made required by a preset's must_have (`Preset.requiredMissionTypes`, Preset.java:160) is ruled "active, required". The listing cannot carry it
   (the kind enum has no mission_type). Its action context is `artifacts:[]` with `missionTypeNotInForce` because E16 reads only the document's
   missionTypes (rules.md:690), so required steps and agent profiles of that mission type never surface.
6. A mission type cannot be denied, overridden away or cascaded (W32, rules.md:577). A step contract bound to a mission type is filtered by
   `mt(u) ⇔ bound type ∈ ⟦mts⟧` (E2), which uses the document list, not the effective set.
7. The skipped report reuses `skippedByScope` for two meanings: "outside the cascade's kinds" at write time (SkippedReport.yaml:16) and "kind not activated" at read time (E14).

## (2) Smallest uniform revision
- `ActivatableKind` += `mission_type`. `SelectableKind` becomes `$ref: ActivatableKind.yaml`, kept by name so the parameter is unchanged.
  `SelectedArtifacts`/`DeactivatedArtifacts` += `mission_type`. The exclusions path then accepts it (a mission type gets a deny list).
  `EffectiveArtifact.kind`/`EffectiveKindQuery` then cover mission types with no further change. Optional: `CascadeKind` += mission_type once W32's "never cascades" is dropped.
- `activatedKinds`: **deprecated and ignored**, not removed and not derived. A write still accepts it (old core and old clients send it), but it gates nothing
  and is left out of the canonical form, so it is never stored, never in the revision and never answered. Not derived: a derived copy would be a second
  source of truth that a GET then PUT re-sends. A per-kind view is the effective listing filtered by `kind`. Remove the member in the next 0.x minor.
- `missionTypes`: **deprecated write-only alias** of `selected.mission_type`. On write it is merged (union) into that list. The canonical form answers
  only `selected.mission_type`. The action-context path keeps its URL. E16 tests the mission type's *effective* state, not the document list.
- `EffectiveArtifactReason`: remove `kind_not_activated` and `kind_default`, and add E9's missing values. `ActivationChange`: no change (mission types are
  already ArtifactReferences). Keep `mission_type_not_found` on select/unselect of kind mission_type (smallest), or fold it into artifact_not_found later.
- W17: a preset sets `selected.<kind>` for its members only. Its `activated_kinds` is tolerated in pack files (ADR-0042 shape kept) and ignored, and its
  `activated_mission_types` become `π[mission_type]`. Rules: delete the gate from E2/E9/E14, set `Kₛ = Kₐ`, and drop W32.
- Stored activations: on load, rewrite `missionTypes` into `selected.mission_type` and drop `activatedKinds`. Each such document gets one new revision, so a client holding the old ETag gets 412 once.

## (3) Breaking or not
- Formal baseline: there is no `contract-charter-v*` tag, so the release gate has nothing to diff against. 0.2.0-SNAPSHOT may break in a minor
  (CHANGELOG.md:13-15, ADR-0022, ADR-0020:100-103). Nothing released breaks.
- By the project's own rules it is **breaking**. `selected.mission_type` is a new response property (ERR per `compat/severity-levels.txt:6-7`). New enum values reach
  response enums, and the meaning of activatedKinds and missionTypes changes. Request-side widening (mission_type accepted, deprecated members still accepted)
  is compatible. Record it under `### Breaking` in a `changes/` fragment. The effective reads are 501, so no consumer has seen the gate's effect yet.
- Tolerant client: an old client keeps `selected.mission_type` in additionalProperties and writes it back on PUT, so round trips survive. It reads
  `kind: mission_type` as UNKNOWN_DEFAULT_OPEN_API, which is safe because the listing is read-only. A client that sends activatedKinds or missionTypes is still accepted. Dropping both from the canonical
  form also removes their half of the `[]`-for-absent hazard in the generated model.
- Core: core's strict DTOs (the reason for severity-levels.txt) must regenerate. Mapping becomes simpler. Charter to core writes no `activated_kinds`
  (core's absent = unrestricted, which now matches) and maps `selected.mission_type` to `activated_mission_types`/`mission_type_activations`. Core to charter may
  still send `activated_kinds`, which is accepted and ignored. A core project that narrows `activated_kinds` will see a larger effective set from the charter,
  as the ruling intends; flag this in core's migration notes.
