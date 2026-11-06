# A7 squad: kind model (2026-11-06)

Ruling (DM a7-kinds): mission types, steps and agent profiles follow the same activation and override/enhancement
rules as any artifact; `activatedKinds` is a core remnant, not a domain rule. Three read-only lenses on fold-b @ 1d37ccd9.

## Synthesis

- The ruling is already ADR policy, but it is not built. ADR-0050 decision 6 makes a mission type an ordinary kind. The code, the contracts, the kernel and rules W32/E2/E16/W17 still keep a separate `missionTypes` slot.
- `activatedKinds` gates nothing in the main code. `ActivationDocument.inForce` is called only by tests, and the write cascade already ignores the gate. Only the planned reads (E9, which still answers 501) would enforce it. The gate is a second switch over `selected`, and that redundancy is what causes B7.
- Mission types are a parallel `Set<String>` in five types. They are re-wrapped as `ArtifactRef` in four places, and seven kind sets disagree with each other. The listing cannot show a mission type, because `EffectiveArtifact.kind` is typed `ActivatableKind`.
- There are two definitions of "mission type in force": `mts` (used by E2 and E16) and the effective set (used by E9). That is shadow logic, and it is the cause of A7(b).
- Genuine per-kind semantics: a binding. An artifact bound to mission type M, including its steps `action:M/s`, is in force only while M is. It prunes with the same shape as an enhancer that needs its target, and it replaces the `stepEnh` exemption. E16 stays packs-side, with "M is in force" as its predicate.

## Proposed revision (PR A or B slice, pending placement)

1. Document `⟨packs, sel, deny, langs⟩`: add `mission_type` to `selected` and `deactivated`. Merge `ActivatableKind` and `SelectableKind` into one. Withdraw W32 (or keep a cascade from M to its steps, see Q1). Drop the reasons `kind_not_activated` and `kind_default`, and drop the `skippedByScope` kind bucket.
2. Deprecate `activatedKinds` and `missionTypes` for one 0.x minor. Both stay accepted on write: `activatedKinds` is ignored, and `missionTypes` is merged into `selected.mission_type`. The change is reported in `ActivationChange`, and neither is ever stored or answered. They are refused in the next minor. Presets: the `activated_kinds` key is accepted, ignored and reported as a diagnostic, and `activated_mission_types` becomes a plain member list.
3. Storage `schemaVersion: 2` with an upgrade on read (gated-off selections are dropped, and missionTypes move to `selected.mission_type`). What is in force does not change, because locks and the floor were never gated. Every ETag changes once (one 412 per client).
4. Kernel: one kind registry. Delete the unused `orgRequirable`, `selectionOverlayable` and `InForceWhenAbsent`. Make `MISSION_TYPE` requirable, which matches `RequiredFloor`.
5. Estimate: −300 to −350 main lines. About 45 test methods in 22 classes would be rewritten or deleted. Contract corpus `valid/02,03,08` and `invalid/14` change, DIV-203 and DIV-501 change, and there is one new DIV.
6. ADRs to amend: ADR-0020 (D4, D7, rulings 4-5), ADR-0042 D2, ADR-0050 decision 6 (mark it built and reconcile "absent" with ADR-0053 decision 10), ADR-0059 D2.2/D6. Also spec FR-013, plan IC-09/IC-13, and rules W2, W5, W17, W32, E2, E9, E14, E16.

The change is breaking by `severity-levels.txt`, but allowed: there is no `contract-charter-v*` baseline, the version is 0.2.0-SNAPSHOT, and the reads still answer 501. Core's strict DTOs must regenerate (C-003: no core changes in this mission). A core project that narrows `activated_kinds` sees a larger effective set, which goes in core's migration notes.

## Open questions for the operator

- Q1: Does selecting a mission type cascade to its steps and action contexts (ADR-0050 says it is the cascade source; the code and W32 say it never cascades)?
- Q2: Can mission types be denied (`deactivated.mission_type`), with the floor and locks applying as for other kinds?
- Q3: Presets exclude skill and glossary_pack from their kinds today. Is that a core leftover to drop?
- Q4: The deprecation window: one 0.x minor that accepts and ignores (proposed), or refuse now?

Side finding (an existing hazard, not A7): the generated client `ActivationConfig.java:63-73` turns an absent list into `[]`, so a GET then PUT changes the revision. No test covers this.

Lens reports: see a7-architecture.md, a7-contract.md, a7-codeshape.md (copied alongside).
