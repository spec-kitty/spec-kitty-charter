---
work_package_id: WP05
title: validate --full (#632 item 3) - HELD pending the operator
dependencies:
- WP03
requirement_refs:
- FR-006
- SC-001
planning_base_branch: feat/pack-doctor-coverage
merge_target_branch: feat/pack-doctor-coverage
branch_strategy: Planning artifacts for this mission were generated on feat/pack-doctor-coverage (Mission topic branch kitty/mission-pack-doctor-coverage-01M7YMQK). During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-doctor-coverage, which is PR'd to main.
subtasks:
- T022
- T023
- T024
phase: Phase 2 - The rules
agent: claude
history:
- at: '2026-11-21T13:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/ClosureWalkPhase.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionResolver.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/EnhancementCompose.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/OfferingReadService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCompositionResolverTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/EnhancementComposeTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/ClosureWalkPhase.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP05 - validate --full (#632 item 3) - HELD pending the operator

## ⚡ Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before you read anything else. Then read
`AGENTS.md`, `.kittify/charter/charter.md`, ADR-0082, the mission `spec.md` and `plan.md` (the decisions this WP
names), and `research/squad-dispositions.md`.

## Objective — HELD

**Do not start this WP until the dispatcher relays the operator's ruling on plan D5.** The squad found that a
`--full` matching everything registration judges needs ADR-0082 slices 2-4 and 7. The `specializes_from` cycle check
would be a new walk outside `PackGraph`.

Proposal (a), recommended:

- `--full` adds delegations, references and enhancement targets: existing domain functions, run per own closure, in
  registration's phase order, with no new walk.
- Registration and the offering take the first fault of the same collect-all forms.
- The PR writes `Refs #632`.

Alternative (b): this WP is dropped, and `--full` stays design-only until slice 7.

If (a) is ruled, update this prompt with the ruling's quote before claiming it.

## Branch Strategy

- Planning branch and merge target: `feat/pack-doctor-coverage` (PR to `main`). Mission topic branch:
  `kitty/mission-pack-doctor-coverage-01M7YMQK`.
- The workspace comes from `lanes.json`. Implement with
  `spec-kitty agent action implement WP05 --agent claude --mission pack-doctor-coverage-01M7YMQK`.
- Commits: no model names and no Co-Authored-By trailer (repository rule). Never push to `main`.

## Subtasks (under proposal a)

### T022 — Collect-all forms
- `PackCompositionResolver`: a collect-all `referenceFaults(...)` beside the existing `checkReferences`, which then
  becomes `referenceFaults(...).getFirst()`, so one body remains. Do the same for `EnhancementCompose.targetFaults`
  and the delegation check (`unresolvedDelegations`).
- `OfferingReadService.judged` phases 2-4 and `ClosureImpactCheck` call the collect-all forms and keep their
  first-phase-refuses behavior. The existing scenarios stay green unchanged.

### T023 — `validate --full`
- `Arguments` gains `--full` (owned by WP02; a justified edit). `PackSetCheck` runs the phase per pack over its own
  closure after CLOSURE, gated on no CLOSURE violation, and adds the `PackSetVerdict.Check` value (name it after the
  ruling: `CLOSURE_WALK`, or `REFERENCES`).
- Fill the second timed CI step that WP02 left inert (NFR-002, 30 s).

### T024 — Parity table (SC-001)
- `FullPassParityTest`: one parameterized table of planted faults (an unqualified reference, a missing referenced
  artifact, a missing target step, an unresolved delegation, plus WP03's preset items and WP04's `manifest_stale`).
  It drives both registration (through the deployable test harness) and `pack-doctor validate --full`, and asserts
  equal codes per row.

## Definition of Done
- [ ] Ruling quoted here.
- [ ] One body per check.
- [ ] Parity table green.
- [ ] No new traversal.
- [ ] The CI step is timed.
