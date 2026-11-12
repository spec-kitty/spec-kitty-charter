---
work_package_id: WP05
title: 'Compose check: slots declared (prompt_slot_missing)'
dependencies:
- WP03
- WP04
requirement_refs:
- FR-001
- FR-006
- SC-002
- SC-003
- C-006
planning_base_branch: slice-c/effective-step-context
merge_target_branch: slice-c/effective-step-context
branch_strategy: Planning artifacts for this mission were generated on slice-c/effective-step-context. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-c/effective-step-context unless the human explicitly redirects the landing branch.
subtasks:
- T020
- T021
- T022
- T023
- T024
- T025
history:
- timestamp: '2026-11-12T06:30:00Z'
  agent: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/SlotCompose.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SlotCompose.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/SlotComposeTest.java
- contracts/charter/scenarios/step-enhancement-slots.feature
- contracts/charter/changes/2026-11-12-prompt-slot-missing-raised.md
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/SlotCompose.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/SlotComposeTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ClosureViewTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RegistrationImpact.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/RegistrationImpactTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ActivationImpact.java
- service/activations/src/main/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapter.java
- service/activations/src/test/java/ai/speckitty/charter/activations/infra/packs/ActivationImpactAdapterTest.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationWrites.java
- service/activations/src/main/java/ai/speckitty/charter/activations/application/ActivationService.java
- service/activations/src/test/java/ai/speckitty/charter/activations/application/ActivationServiceTest.java
- service/web-transport/src/main/java/ai/speckitty/charter/web/RefusalHandler.java
- service/web-transport/src/test/java/ai/speckitty/charter/web/RefusalHandlerTest.java
- contracts/charter/schemas/CharterRefusalCode.yaml
- contracts/charter/scenarios/errors.feature
- contracts/charter/scenarios/step-enhancement-slots.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- contracts/charter/changes/2026-11-12-prompt-slot-missing-raised.md
role: implementer
tags: []
tracker_refs: []
---

## ⚡ Do This First: Load Agent Profile

Before anything else, load the agent profile named in the frontmatter and behave according to its guidance before
parsing the rest of this prompt. Use the `/spk-charter-profile-load` skill, or run `spec-kitty agent profile show java-jenny`.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

## Objective

**C38 slot declared** (FR-001, FR-006): every slot an **in-force** step enhancement names is declared by the prompt in
force of its target step. Otherwise `prompt_slot_missing`: `500` on composing reads; `422` at registration, on the
impact-checked writes and on the activation writes (OD-1 (B), OD-4 (i), research R7 revised). An enhancement that is
not in force never refuses. Raise the code in the contract and the transport.

## Context

- Spec: FR-001, FR-006, SC-002, SC-003. Research R7, R8 (the prompt in force is at the merged winner's place of
  `action:M/s`), R9. Squad A1, A2, A3, R4 (operations map), R5.
- Prompt facts come from `PackCompositionFacts.prompts()` (WP03); in-force states from `ClosureView.inForce`.
- Read first: `application/ClosureView.java` (`inForce` ~l.552, `merged`, `winnerPackOf` ~l.690),
  `application/RegistrationImpact.java` (`check` paths ~l.211, ~l.243, ~l.286, `namingActivations`, `judge` ~l.480),
  `application/ActivationImpact.java`, `activations/infra/packs/ActivationImpactAdapter.java` (~l.87-102 swallows
  `CharterRefusal`), `activations/application/{ActivationWrites, ActivationService}.java` (the write commit path,
  `DocumentRules.unsatisfied`, `warned`), `activations/infra/packs/ClosureViewSession.statesOf` (~l.133),
  `web/RefusalHandler` (`statusOf` l.383 already maps `PROMPT_SLOT_MISSING` to 500, `registrationStatusCodes`,
  `STATUS_BY_OPERATION`, `IMPACT_CHECKED_WRITES`), `CharterRefusalCode.yaml` (`prompt_slot_missing` l.192 and l.774;
  the `operations` map format of `pack_reference_artifact_missing` ~l.647), `errors.feature` raised-code table,
  `ReservedRefusalCodesTest`, the rules page G8 and G21.

## Subtasks

### T020 — Scenarios first

`step-enhancement-slots.feature` (`@contract`): a project pack that layer-overrides `mission_type:software-dev` with a
`plan` prompt that does not declare `spdd:reasons-block`; org-base's `spdd-plan` fills it. One failing fixture and
one twin for each surface:

- read: `spdd-plan` selected and the type in force → every composing read of that activation (`/artifacts`,
  `/skipped`, `/graph`, `/context`) answers `500 prompt_slot_missing`, the detail naming enhancement, pack, slot and
  the pack holding the prompt in force; twin: `spdd-plan` held but not selected → 200 (OD-1).
- registration of the overriding project pack while a stored activation selects `spdd-plan` → `422`; twin: no stored
  activation selects it → 201.
- an impact-checked write (`putPackRelationship` adding `spdd-plan`'s `enhances` edge) → `422`; twin.
- an activation write selecting `spdd-plan` → `422`, nothing stored; twin: the override declares the slot → 200.
- `errors.feature`: `prompt_slot_missing` joins the raised-code table (it leaves the reserved list); a declaration pin
  per operation of its map (squad R4).

### T021 — The pure rule

`packs.domain.SlotCompose.missing(merged, facts, inForce)`: for each in-force step enhancement (sole `action:M/s`
target), the prompt facts of the pack at the merged winner's place of `action:M/s` (R8; `StepHolding`), and each fill's
slot not declared there → a violation (code, enhancement URN, its pack, slot, prompt pack). Visit each enhancement once
(linear). `SlotComposeTest`: declared by base; dropped by an override; two enhancements, one missing; not in force ⇒
nothing; a same-id enhancement whose winning copy does not name the slot ⇒ nothing (winning copy only).

### T022 — Reads

- `ClosureView.requireSlots(InForceStates)` throws `CharterRefusal(PROMPT_SLOT_MISSING, ...)` on the first violation in
  a stable order. `inForce` stays pure (squad A3).
- The read session calls it once per request where the states are resolved: `ClosureViewSession.statesOf` (owned by
  WP02; out-of-map, recorded). Order on reads (F6): composition codes, then `pack_unreadable`, then
  `prompt_slot_missing`.

### T023 — The registration gate (squad A2)

- `ActivationImpact` gains `void requireComposable(ClosureChange change)` (default no-op), separate from the notices.
- `RegistrationImpact` calls it on **every** check path (full registration, per-artifact registration, relationship
  writes, deletes), not only when `namingActivations`.
- `ActivationImpactAdapter` implements it: for each stored activation whose closure the change joins, resolve in-force
  on the after-view and call `requireSlots`; raise the first `PROMPT_SLOT_MISSING` with the activation id in the detail
  — never swallowed. An activation whose before-view already failed (G21) is skipped. Tests in
  `ActivationImpactAdapterTest` and `RegistrationImpactTest` (each path).

### T024 — Activation writes

- `ActivationWrites` / `ActivationService`: before a document is stored (create, replace, select, unselect,
  deactivate, reactivate, apply preset), resolve in-force for the new document and call `requireSlots`; the refusal is
  `422` (T025). Not reached "by accident" through `DocumentRules.unsatisfied` (squad A3). `ActivationServiceTest`.

### T025 — Raised in the contract and the transport

- `CharterRefusalCode.yaml`: `prompt_slot_missing` `raised: true`; description narrowed to an **in-force**
  enhancement (OD-1, `### Changed`); an `operations` map listing every 422 operation (`getPackRegistration`, the
  impact-checked writes, the activation writes) — copy the format of `pack_reference_artifact_missing`; rewrite the
  "reserved for #438" paragraph (~l.192) as history.
- `RefusalHandler`: 422 for `PROMPT_SLOT_MISSING` on those operations (`STATUS_BY_OPERATION`); `RefusalHandlerTest`.
- `ReservedRefusalCodesTest`: move the code out of the reserved list; extend the contract-marks test
  (`theContractMarksTheCodesTheWritesAndTheLoadRaiseAsDecided` or its current name) with `PROMPT_SLOT_MISSING`.
- Fragment `changes/2026-11-12-prompt-slot-missing-raised.md`: `### Breaking` (reads, registrations and writes refused
  that answered before; every operation named), `### Changed` (wording).

## Project constraints (every subtask)

- **Test first (C-004)**: the scenario and a failing unit test come before the code; commit the red test first when
  the behaviour is new. Every refusal has a failing fixture and a passing twin that differ only in the faulty part.
- **Canonical shape only (C-005)**: fixtures, examples and scenarios use the canonical shape; an invalid shape appears
  only to exercise a refusal. Never exclude a field from a parity or round-trip check.
- **Stay out of slice B (C-006)**: do not edit `PackCommitStore`, `PackLocator`, `PackDirectories`,
  `CommitStorePackWriteStore`, `FileSystemPackSources`, `Loader`/`LoadedPack`, `CharterWiring`, `file-kinds.yaml`, or
  the example packs' `pack.md`/`README.md`/`org-charter.yaml`. `DomainRepositoryService` only in WP03's one hunk.
- **Hexagonal**: `HexagonalArchitectureTest` must stay green: only `activations.infra.packs` names packs, and only
  `packs.application`/`packs.domain`; generated `api` types only in `*.infra.api` and `web`.
- **Quality gates**: Checkstyle, palantir-java-format (Spotless), Error Prone, NullAway and doclint are on; no
  suppression, no disabled test, no new dependency.
- **Gradle**: `JAVA_HOME=/usr/lib/jvm/temurin-25 ./gradlew --no-daemon --max-workers=1 <module>:test` (or
  `:contracts:...`, `:service:deployable:test --tests ...`), foreground, one run at a time, never `--stop`, never
  `verifyAll` locally.
- **Commits**: signed, conventional (`feat(...)`, `test(...)`, `docs(...)`), no AI model name and no
  `Co-Authored-By` trailer; an edit to a file another WP owns is recorded in the message as
  `out-of-map: <file> — <why>`.
- **Contract changes** each carry a dated fragment under `contracts/charter/changes/2026-11-12-*.md` with the class
  `contracts/README.md` of the mission names (Added, Changed wording only, Breaking).

## Branch Strategy

- Planning branch: `kitty/mission-effective-step-and-action-context-01M774HB`; final merge target: `slice-c/effective-step-context` (then a pull request into `main`).
- Execution worktrees are allocated per computed lane from `lanes.json`; never pick a base by hand.
- Implementation command: `spec-kitty agent action implement WP05 --agent claude --mission effective-step-and-action-context-01M774HB`

## Definition of Done

- Every C38 cell of the twin matrix has a failing fixture and a passing twin; the scenarios and the reserved-code tests
  pass; `prompt_slot_missing` is raised.
- `./gradlew :service:packs:test :service:activations:test :service:web-transport:test` and the scenario run green.

## Risks and reviewer guidance

- The gate must not swallow; the notices must not refuse.
- Only in-force enhancements refuse; the winning copy only; the prompt at the winner's place.
