---
work_package_id: WP06
title: pack-doctor picks up the specialization cycle and the relations (#632 item 3)
dependencies:
- WP05
requirement_refs:
- FR-012
- SC-004
planning_base_branch: feat/adr-0082-graph-walk-slices
merge_target_branch: feat/adr-0082-graph-walk-slices
branch_strategy: Planning artifacts for this mission were generated on feat/adr-0082-graph-walk-slices. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/adr-0082-graph-walk-slices unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-adr-0082-graph-walk-slices-01M8A867
base_commit: 6a342f4929ebb9ec7641aeb298556d4e397ee7c6
created_at: '2026-11-27T07:50:56.532580+00:00'
subtasks:
- T024
- T025
phase: Phase 4 - pack-doctor
history:
- at: '2026-11-25T22:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/
create_intent:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/RelationsPhase.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/RelationsPhaseTest.java
- docs/changelog/2026-11-26-pack-doctor-judges-relations.md
execution_mode: code_change
owned_files:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/RelationsPhase.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/set/PackSetCheck.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/set/RelationsPhaseTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/FullPassParityTest.java
- docs/guides/check-packs.md
- docs/changelog/2026-11-26-pack-doctor-judges-relations.md
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 - pack-doctor picks up the specialization cycle and the relations (#632 item 3)

## Do This First: Load Agent Profile

Load the `java-jenny` profile with the `/spk-charter-profile-load` skill before reading anything else.

## Objective

`pack-doctor validate --full` runs the factory's relations phase over each root's composed closure, as `REFERENCES`
does (ruling R-ae), reporting a fault several roots find once, with registration's codes and pointers (operator ruling
D3). The own-closure rules stay with slice 7 (pack-doctor has no profile reader or merge).

Requirements: FR-012, SC-004.

## Subtasks

### T024 The relations phase in pack-validation

- A `RelationsPhase` beside `ReferencesPhase`: per root of the set, over the rooted composed closure, run
  `PackRulesets.relations()` (and the edge-index phase behind its gate) and map the faults with `PackVerdicts` as the
  other phases do. It runs when no rooted closure fails to compose (R-aa); decide and record where it sits relative to
  `REFERENCES` so the first finding matches registration's phase order.
- `--full` gains it under the existing `REFERENCES` check value or a new check value: record the choice (ADR-0083
  decision 8 reserved `CLOSURE_WALK` for the own-closure walk). A new check value changes `PackDoctorReport`: update the
  schema and its contract test if so.

### T025 Parity rows and the guide

- `FullPassParityTest`: a cross-pack `specializes_from` cycle (`specialization_cycle`), an override cycle, a
  self-override and a conflicting relation: same code and pointer on registration and on `pack-doctor`.
- `docs/guides/check-packs.md`: what `--full` now judges; ADR-0083 decision 9's note (Proposed amendment, out-of-map:
  WP01 owns the ADR file, recorded); changelog fragment.
- Write `Refs #632` (or `Closes #632` if every item 3 rule is then covered or recorded as not the charter's), and say
  which open-packs Python rules can retire.

## Definition of Done

- Parity rows green on both surfaces; `:pack-validation:test :pack-doctor:test`, `verifyAll`, docs checks.

Implementation command: `spec-kitty agent action implement WP06 --agent claude --mission adr-0082-graph-walk-slices-01M8A867`
