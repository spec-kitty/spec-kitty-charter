# A7 code-shape review: per-kind special-casing (fold-b @ 1d37ccd9, read-only)

Paths are relative to `service/`; `act/` is `activations/src/main/java/.../activations`, `pk/` is `packs/src/main/java/.../packs`.
Scope: the reads (E2, E9, E16, action context) are still `501` stubs (`act/infra/api/ActivationController.java:144-252`),
so no in-force or action-context code exists yet. All the special-casing is on the write and preset paths.
**agent_profile and step:** the activation code never branches on them. A step lives inside `MissionType`, which is (b). `MISSION_STEP_CONTRACT`
is handled the same way as the other kinds. The special case is **mission_type** plus the **kind gate**.

## Special-cased paths  [(a) accidental/core remnant, (b) genuine, (c) dead]
| file:line | what it does | class |
|---|---|---|
| kernel/.../ArtifactKind.java:43 | MISSION_TYPE `activatable=true, orgRequirable=false`, but RequiredFloor accepts must-have mission types | a (flag lies) |
| ArtifactKind.java:52-61,69-71,121-140 | `orgRequirable`, `selectionOverlayable`, `InForceWhenAbsent{NONE}`: no main-code reader | c (~45 lines) |
| kernel/.../NodeKind.java:100 | `case MISSION_TYPE -> "mission_types"` repeats ArtifactKind.plural | c |
| kernel/.../Urn.java:71 | `Urn.missionType(id)` factory used only for the parallel list | a |
| act/domain/ActivationDocument.java:51-55 | `KINDS = activatable \ {MISSION_TYPE}` (contract `ActivatableKind`) | a |
| ActivationDocument.java:59-60,117-138,285-286,537-557 | separate `activatedKinds` and `missionTypes` members, builder, canonical tree | a |
| ActivationDocument.java:127 | `inForce(kind)`: the kind gate check, used only by tests | c |
| ActivationDocument.java:241-244 | `withoutSelected` MISSION_TYPE branch | a |
| ActivationDocument.java:419-447,628 | `Written.missionTypes` parallel list | a |
| act/application/ActivationWrites.java:161,174,178-184 | `selectable` vs `deniable`: boolean flag adds MISSION_TYPE | a |
| ActivationWrites.java:246-248 | `declared`: a mission type id skips normalization (spelling matched exactly) | a |
| ActivationWrites.java:278-279,305-311 | `selectedOf`/`requireHeldOrStale` branch to `missionTypes`, `MISSION_TYPE_NOT_FOUND` | a (the refusal code is contract) |
| act/application/ActivationSelectionService.java:70 | a mission type never cascades (W32) | b? (ruling needed; a uniform rule cascades) |
| act/domain/CascadeRules.java:302-314,325-346,354-363 | `withMissionType`, `unselected` MT branch, `rebuilt(..missionTypes)`, `selectable()`, `active()` | a |
| CascadeRules.java:293 / CascadeScope.java:49,79 | deny and scope admit `kinds()` only, so a reached mission type is always out of scope | a |
| act/domain/StaleIds.java:64-69 | a separate stale-id loop for missionTypes | a |
| act/domain/ActivationChange.java:93,105-110 | a separate diff for missionTypes | a |
| act/domain/DocumentRules.java:170-174,237 | `unknown_mission_type` check separate from `unknown_artifact` | a |
| act/domain/PresetRules.java:51,74-85 | **W17 strip**: the gate is filtered to `kinds()`, and mission types use their own key | a |
| act/domain/ClosureSnapshot.java:33,55,272-295 | `snapshot.missionTypes()` (= `held(MISSION_TYPE)`), PresetView gate and MT set | a |
| act/infra/packs/PacksAvailability.java:115,179; pk/application/ClosureView.java:45,93,121 | the same duplicated projection | a |
| pk/application/ClosureQueries.java:109 | `heldIds.get(MISSION_TYPE)` copied into the separate set | a |
| pk/domain/Preset.java:40-42,49-58,101-103,159-170 | `ACTIVATION_KINDS` (9, no MT/skill/glossary), separate `missionTypes` list and `requiredMissionTypes()` | a (the skill/glossary exclusion is a ruling) |
| pk/infra/storage/PresetFiles.java:30,85-87,108-110 | `"activated_mission_types"` branch, though the key already follows `activated_<plural>` | a |
| pk/infra/storage/PresetYaml.java:25-35 | `LIST_KEYS`: 10 hand-written strings | a |
| pk/domain/RequiredFloor.java:81; pk/application/OfferingReadService.java:307 | the extra MT loop | a |
| act/infra/api/ActivationMapper.java:74-76; act/infra/storage/ActivationFileCodec.java:32,82-85 | wire mapping for both keys | a (contract) |
| pk/infra/storage/MissionType{Binding,Yaml}.java, MissionTemplates.java | storage of steps, prompts, templates | b |
| E2 `mt(u)` / E16 (not built) | artifacts bound to a mission type follow it; action contexts belong to a mission type | b |

## Primitive obsession and parallel structures
- **Seven kind sets** that disagree: `ArtifactKind.activatable`; `ActivationDocument.KINDS` (11); `SelectableKind`, which is
  `CascadeRules.selectable` = `ActivationWrites.kindOf(..,true)` (12); `Preset.ACTIVATION_KINDS` (9); `PresetYaml.LIST_KEYS` (10 strings);
  the `activated_kinds` enum in `preset.schema.json` (13 plurals, **including templates and assets, excluding mission_types**);
  the contract's `ActivatableKind.yaml` and `SelectableKind.yaml`, kept as hand-maintained enum copies.
- Mission types are held as `Set<String>` beside `Map<ArtifactKind,Set<String>>` in 5 types: Document, Written, PresetView,
  ClosureSnapshot and ClosureView. `ArtifactRef(MISSION_TYPE,id)` is re-synthesised in 4 places.
- Kind names are held as strings: `"activated_"` plus prefix slicing, `"activated_mission_types"`, `"missionTypes"` and `"activatedKinds"` codec keys,
  and `/missionTypes` pointers.
- The kind gate is a second switch over `sel(k)`, which already means "none" when absent (W2). It is redundant and
  causes B7. `activatedKinds` has **no semantic reader** in main code: it is stored, mapped, copied and stripped only.

## Uniform refactor estimate (mission_type ∈ selected/deactivated; activatedKinds removed)
- Main code: about **-300 to -350 lines**. activations about 180 (Document ~80, CascadeRules ~35, Writes ~20, Stale/Change/
  DocumentRules/PresetRules ~40), packs about 70 (Preset ~35, PresetFiles/Yaml/Dso ~25, Floor/Offering/Closure ~10),
  and kernel about 50 (dead flags, NodeKind case, `Urn.missionType`). Two whole classes become trivial: `ClosureSnapshot.PresetView`
  and `PresetDso.activatedKinds`.
- Contract: `ActivatableKind` and `SelectableKind` merge into one enum. `ActivationConfig.activatedKinds` and `missionTypes`
  are removed. `unknown_mission_type` and `mission_type_not_found` fold into `unknown_artifact` and `artifact_not_found`, or stay as aliases.
  The `kind_not_activated` reason (`EffectiveArtifactReason.yaml:11`) is removed. The preset schema loses `activated_kinds`.
  Corpus files `ActivationConfig/{valid/08-activated-kinds-empty, invalid/14-activated-kind-mission-type}.json` go.
- Tests: about **45 test methods across 22 classes** touch this. Roughly 15 are deleted (kind-gate and dead-flag tests) and about 30 are rewritten
  to use `selected.mission_type` and the generic path.

## Tests that pin the special-casing
- ActivationSelectionServiceTest: aMissionTypeJoinsMissionTypesAndNeverCascades, unselectingAMissionTypeRemovesItFromMissionTypes,
  aMissionTypePathIdIsMatchedExactlyAsMissionTypesIs, aPathIdTheClosureDoesNotHoldIs404ByItsKind, anUnselectOfAStaleMissionType...
- ActivationExclusionServiceTest: aMissionTypeHasNoDenyListSoItsKindIs422AtThePath
- CascadeRulesSelectTest: selectingAMissionTypeAddsItToMissionTypes, aReachedMissionTypeIsOutsideEveryCascadeScope, aMissionTypeChangeIsApplied
- PresetRulesTest: anEmptyKindGateIsNotListed..., anAbsentKindGateStaysAbsent..., aKindGateTheDocumentCannotHoldIsLeftOutOfIt (pins the W17 strip),
  listedMissionTypesReplace..., unlistedMissionTypesAreUntouched
- ActivationDocumentTest: aKindIsInForceOnlyWhenActivatedKindsNamesIt, absentAndEmptyAreBothNotInForce...,
  theDocumentKindsAreTheActivatableKindsLessTheMissionType, theCanonicalTreeKeepsTheSchemaMemberOrder
- DocumentRulesTest: anUnknownMissionTypeIsNamed, aKindOutsideTheActivatableKindsIsInvalid; StaleIdsTest (2); CascadeScopeTest
  allCoversEveryActivatableKind; ActivationMapperTest (3); PresetTest everyGovernedKindIsAccepted; PresetYamlTest (3-way key parity);
  ArtifactKindTest factsMatchTheLogicalModelTable plus the InForceWhenAbsent asserts (:39-41); ActivationConfigSchemaParityTest (2).

## Rulings still needed (from the code)
1. Should a mission-type select cascade (to steps and action contexts) or not (W32)? This is the only candidate (b) on the write path.
2. Should mission types get a deny list? The uniform answer is yes.
3. Should presets keep excluding skill and glossary_pack (`Preset.ACTIVATION_KINDS`)? This is core's `preset_activation_keys`, which is likely (a).
