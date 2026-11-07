# A7: architecture review of the kind gate and the mission-type exemption (read-only)

Code: `.worktrees/fold-b` @ 1d37ccd9. Rules: `composition-and-activation-rules.md` on branch issue-10 (cited as `rules:`).
Paths are shortened: `act/` = `service/activations/src/main/java/ai/speckitty/charter/activations`, `pk/` = `service/packs/src/main/java/ai/speckitty/charter/packs`.

## Findings, most important first
1. **The ruling is already accepted, but the code and rules ignore it.** ADR-0050 d6 (`docs/adr/0050-*.md:186-188`) says: "A mission type ... is otherwise an ordinary kind: presets, the deny list and listing apply to it as to any kind". Its supersession notes are already in ADR-0020 (`0020:206,597,600`) and ADR-0042 (`0042:194-195`). Even so, the document, the contracts, the kernel and the W32/E2/E16/W17 rules all keep the separate `missionTypes` slot: `ActivationDocument.java:51-55,60,241`; `ActivatableKind.yaml:3-4`; `ArtifactKind.java:37-43`. The operator's ruling only needs ADR-0050 d6 to be built, plus one new step: dropping `activatedKinds`.
2. **The kind gate does not cross the port. The mission-type exemption does.** The gate is pure `activations.domain`: `ActivationDocument.inForce(kind)` (`act/domain/ActivationDocument.java:127`) has no caller yet, because the reads are not built, and `PresetRules.activatedKinds` implements W17 (`act/domain/PresetRules.java:74-81`). The exemption is carried across the `AvailablePacks` port (`act/application/AvailablePacks.java:13`) as values:
   - `ClosureSnapshot.missionTypes` (`act/domain/ClosureSnapshot.java:33`) duplicates `held[MISSION_TYPE]` (`pk/application/ClosureQueries.java:109`);
   - `PresetView` carries both `activatedKinds` and a separate `missionTypes` (`act/infra/packs/PacksAvailability.java:176-179`).
   This is not a C-002 breach today, because no walk or merge is re-implemented. But the activations side keeps two definitions of "mission type in force": `mts` (E2 `mt(u)`, E16) and `Eff` (E9). That is shadow logic inside `activations`, and the A7(b) defect follows from it.
3. **E16 crosses the port in IC-10.** `ActionContextWalk.walk(mt, a, depth, inForce)` runs in `packs`, and `activations` supplies the predicate (plan IC-10, C-002). E16 gates the walk on `mt ∈ ⟦mts⟧` (`rules:690-692`), not on `mission_type:mt ∈ Eff`. So a required or locked mission type is reported active but returns an empty context: A7(b). Under C-002 the predicate should be the single E8 function, so the gate must be `Eff` membership.
4. **The floor already treats mission types as an ordinary kind, but the kernel says they are not requirable.** `RequiredFloor.mustHaveOf` adds `mission_type:` URNs to `Req` (`pk/domain/RequiredFloor.java:81`). Yet `ArtifactKind.MISSION_TYPE` declares `orgRequirable=false` (`ArtifactKind.java:43`). The E2/E5/E9 conflict comes from these two facts disagreeing.
5. **Mission types do not cascade, which contradicts ADR-0050.** ADR-0050 d6 makes a mission type "the cascade source", and `SelectableKind.yaml:3-4` says the same. The code forces `CascadeScope.none()` for a mission type (`act/application/ActivationSelectionService.java:70`), and W32 rules "never cascades" (`rules:577-579`). This is the one place where mission-type-specific write behaviour belongs; see Q2.
6. **Steps are already uniform, apart from one exemption.** An action node is not an artifact: `NodeKind.java:6,34-35` describes `NodeKind` as "every artifact kind plus `action`". Step enhancements and overrides go through ordinary CRG `enhances` and `overrides` edges (ADR-0050 d22-24 as amended by ADR-0053 d22). What is left is the `stepEnh(u)` exemption in E6 and I11 (`rules:624,748`) and the "step target not put in force" rule (ADR-0059 `0059:124-126`).
7. **Agent profiles are an ordinary kind** (`ArtifactKind.java:24`). Only the kind gate makes them different. ADR-0032's selection order (`0032:51-68`) never says it runs over in-force profiles. Its role pins are pack policy, not activation.

## Q1: Boundaries and ports the gate touches
- The gate is in the document and its rules: `ActivationDocument` (`act/domain/ActivationDocument.java:51-59,117-128`), `PresetRules` (W17), `CascadeRules.java:344,355,361`, and the write kind parsing (`ActivationWrites.java:179-183`).
- The mission-type exemption reaches through the `AvailablePacks`/`PacksSession` port (findings 2 and 3), the `pk` `Preset` and `ClosureView` domain (`pk/domain/Preset.java:42,160-170`; `PresetFiles.java:30,85`), and storage (`ActivationFileCodec.java:31-32,82-85`).
- Under C-002, the gate belongs in `EffectiveSet` (activations) only. The port should hand over held ids keyed by kind, with no separate mission-type set and no preset `activatedKinds`.

## Q2: The uniform model
- **Document** `D = ⟨packs, sel, deny, langs⟩`, with `sel` and `deny` defined over `Kₐ`, `mission_type` included. There is no `kinds` and no `mts`. Absent means none for every kind (ADR-0053 d10). This overrides ADR-0050's "absent means all" (`0050:67`), which must be reconciled.
- **Candidates** `Cand = {u ∈ W ∩ Sel(D) | lang(u) ∧ u ∉ Den(D)}`. Then `Base = Cand ∪ Lk`, and E4-E7 apply unchanged to mission types: whole-type `overrides` (ADR-0050 d24), the required floor (E5), deny entries (ADR-0050 d6), and pruning.
- **Binding** (the only mission-type rule in `EffectiveSet`). An artifact bound to mission type M is pruned while `mission_type:M ∉ Eff`. This uses the same fixed-point shape as E6's enhancer pruning, and its reason stays `mission_type_not_in_force`. The step targets follow the same rule: `action:M/s` is in force exactly when `mission_type:M` is. This replaces `stepEnh(u)`, and a step enhancer is judged like any other enhancer. The result is one function (E8), which the reads, the IC-13 write judgement and the E16 predicate share.
- **E16** stays in `packs` (`ActionContextWalk`, IC-10), with the predicate `u ∈ Eff`. If `mission_type:mt ∉ Eff`, it answers `artifacts=[]` and `missionTypeNotInForce={mt}`. A required or locked mission type therefore gets its context.
- **Mission-type cascade** (ADR-0050 d6) is a reach over the type's steps and references. It lives in the `packs` reach (`ReachPlan`/`Reach`), not as a special case in the document.
- **Profiles**: ADR-0032's selection order should run over `agent_profile` ∩ `Eff`. Because of E3, the built-in locked fallback is in force without being selected.
- **What becomes dead**: the `kind_not_activated` reason (`contracts/charter/schemas/EffectiveArtifactReason.yaml:11`) and `skippedByScope` at read (`SkippedReport.yaml:7,12`). Review item B7 goes away, because a select alone puts an artifact in force.

## Q3: What breaks or migrates
- **Stored documents.** The codec accepts only a closed member set and canonical bytes (`ActivationFileCodec.java:31-32,66-68`), and `schemaVersion` must be 1 (`ActivationConfig.yaml:27-30`). A `schemaVersion: 2` with a decode-time upcast is needed:
  - `sel'(k) = sel(k)` when `k ∈ ⟦kinds⟧`, otherwise absent;
  - `selected.mission_type = mts`;
  - the floor and locks were never gated (E3), so `Eff` is preserved exactly;
  - deny entries of gated kinds are kept;
  - every revision and ETag changes once (JCS hash), so clients holding a revision get one `412`.
- **Contracts, breaking 0.x minor (ADR-0022):** `ActivationConfig.yaml:46-65`; `ActivatableKind`, `SelectableKind`, `CascadeKind`, and `SelectedArtifacts`/`DeactivatedArtifacts` (add `mission_type`); the reason and skipped buckets above; corpus files `valid/02,03,08` and `invalid/14-activated-kind-mission-type.json`; DIV-203 and DIV-501 updated, plus a new DIV for the kind gate.
- **Core compatibility.** Core maps `activated_kinds` and `mission_type_activations` (`0020:198-199`), and core cannot change in this mission (spec C-003). Proposal: for one 0.x minor, the write path accepts `activatedKinds` and `missionTypes` as deprecated input. They are upcast as above, the change is reported in `ActivationChange`, and they are never stored or returned. The next minor refuses them. Two things break:
  - the ADR-0020 round trip ("a fetched document registers unchanged") for v1 documents;
  - W1's closed body has to allow the deprecated members during the window.
- **Packs.** The preset `activated_kinds` gate (ADR-0042 `0042:151-160`, `PresetYaml.java:78`) is deprecated: it is accepted and ignored, with a diagnostic. `activated_mission_types` becomes an ordinary member list, as ADR-0050 already says (`0050:71-72`), and `Preset.missionTypes` and `requiredMissionTypes` fold into `artifacts` and `required()`.
- **ADRs to amend:**
  - ADR-0020: the D4 table, the three states, rulings 4-5, the D7 precedence;
  - ADR-0042 D2: the gate and the slot;
  - ADR-0050 d6: mark as built, and reconcile "absent means all" with ADR-0053 d10;
  - ADR-0053 d10: wording;
  - ADR-0059: D2.2 Scope and Step targets (`0059:110-113,124-126`), D6 (`0059:204`), and the follow-ups (`0059:302-304`);
  - kernel: the `ArtifactKind.MISSION_TYPE` javadoc and `orgRequirable`.
- **Spec and plan:** FR-013 and US3 AS4 (spec:111,202); plan IC-09 step 1 and IC-13 "within activatedKinds ... missionTypes"; rules W2, W5, W17, W32, E2, E9, E14 and E16.
