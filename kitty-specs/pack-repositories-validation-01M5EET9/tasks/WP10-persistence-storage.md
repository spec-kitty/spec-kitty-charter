---
work_package_id: WP10
title: Persistence — strategy, DSO, filesystem storage + fidelity controls
dependencies:
- WP09
requirement_refs:
- FR-003
- FR-004
- FR-007
- FR-009
- FR-011
- FR-012
- NFR-003
- NFR-004
- SC-003
- SC-004
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 917bbb05d0571aefbdf83d3573ca436613bad781
created_at: '2026-10-21T15:21:53.964936+00:00'
subtasks:
- T040
- T041
- T042
- T043
- T044
- T045
- T046
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/TacticDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackWriteSafetyTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlSerializationStrategy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/TacticDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackDescriptorDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackWriteSafetyTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP10 – Persistence: strategy, DSO, filesystem storage + fidelity controls

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Build the persistence mechanics at `packs.infra.storage`: a pluggable `SerializationStrategy` (one `YamlSerializationStrategy`), value-equal DSO records carrying the round-trip fidelity assertion, and the filesystem storage (atomic temp→`ATOMIC_MOVE`, `PackIdentifier`→path, pack-root containment). Ship the fidelity/write-safety/symlink controls here, each with a named positive control. This is IC-05a (and the IC-08 persistence controls).

## Context

- The strategy/DSO design and the fidelity property: [data-model.md](../data-model.md) `packs.infra.storage` + "Round-trip fidelity (NFR-004)" / "Atomicity (NFR-003)"; [research.md](../research.md) D2/D5; [spec.md](../spec.md) FR-011/012, NFR-003/004, C-004/008.
- Reuse the moved `YamlDumpEmitter`, `PackSchemas`, `PackFileKind`, `PackLocation` (relocated by WP05) — do not re-implement YAML emission.
- The old `PackElementRoundTripTest`/`PackElementWriteSafetyTest` (moved by WP05) are **replaced** here against the new ports — this WP owns their replacement.

### Subtask T040 — `SerializationStrategy` interface *(parallel-safe)*
- **Steps**: a format-neutral interface mapping entity↔DSO↔text for a tactic and a descriptor (e.g. `String writeTactic(TacticDso)`, `TacticDso readTactic(String)`, and the descriptor pair). Framework-free.
- **Files**: `SerializationStrategy.java`.

### Subtask T041 — `YamlSerializationStrategy`
- **Steps**: the one concrete strategy, reusing `YamlDumpEmitter`/`PackSchemas`; maps DSO↔YAML text. No MapStruct; hand-written.
- **Files**: `YamlSerializationStrategy.java`.
- **Edge cases**: undeclared top-level `pack.yaml` keys are captured on read and dropped on write-back (pinned exception — round-trip equality is scoped to declared content).

### Subtask T042 — DSO records *(parallel-safe)*
- **Purpose**: The persistence shape whose **value equality carries the fidelity assertion** (D2).
- **Steps**: `record TacticDso(...)` and `record PackDescriptorDso(...)` mirroring the on-disk structure incl. `active_charter`/`pack_drg`; value equality, order- and duplicate-sensitive lists.
- **Files**: `TacticDso.java`, `PackDescriptorDso.java`.

### Subtask T043 — Filesystem storage mechanics
- **Steps**: `FilesystemPackStore` resolving `PackIdentifier`→path via `PackFileKind` classification; writes via temp→`ATOMIC_MOVE`; pack-root containment by `normalize()` + realpath symlink check, refusing any resolved target outside the pack root **before** any bytes.
- **Files**: `FilesystemPackStore.java`.
- **Edge cases**: replace-existing by id only (C-008); a brand-new element id is refused deterministically, not silently created.

### Subtask T044 — Round-trip literal oracle + mutation control
- **Purpose**: FR-012 / NFR-004 / SC-003 — falsifiable fidelity.
- **Steps**: `PackRoundTripTest` — fetch a fixture pack's descriptor + tactics → DSOs, write to a **fresh** directory holding none of their content, refetch, assert `dsoAfter.equals(dsoBefore)` (order/duplicate-sensitive, incl. `active_charter`/`pack_drg`), against a **literal-value oracle** (guards reader-dropped fields). Pair with a write→refetch **mutation control** proving equality is not a tautology.
- **Files**: `PackRoundTripTest.java` (replaces the old `PackElementRoundTripTest`).
- **Validation**: `./gradlew :packs:test --tests '*RoundTrip*'`.

### Subtask T045 — Write-safety failure-injection control
- **Purpose**: NFR-003 atomicity.
- **Steps**: `PackWriteSafetyTest` — inject a write failure and assert the target file is untouched and no stray temp remains; pair with a positive control where the write succeeds and the file updates.
- **Files**: `PackWriteSafetyTest.java` (replaces the old `PackElementWriteSafetyTest`).

### Subtask T046 — Symlink-escape control + in-root positive control
- **Purpose**: NFR-003 containment (spec US1 scenario 4).
- **Steps**: in `PackWriteSafetyTest` (or alongside), a symlink resolving outside the pack root is refused before any write (target untouched); pair with an **in-root positive control** that writes successfully.
- **Files**: within `PackWriteSafetyTest.java`.
- **Validation**: `./gradlew :packs:test --tests '*WriteSafety*'`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP09 (entities/DSO-source types) and WP05 (the moved YAML adapter). Precedes WP11.

## Definition of Done

- `SerializationStrategy` + `YamlSerializationStrategy` + DSO records + `FilesystemPackStore` exist; emission reuses the moved YAML helpers.
- The round-trip, write-safety and symlink controls pass, each with a named positive control; the old `PackElement*Test` battery is replaced.
- `./gradlew :packs:test` green.

## Risks

- **Vacuity** → the literal oracle guards against the round-trip being blind to reader-dropped fields.
- **Fake failure-injection** → the write-safety seam must be a real injected failure, not a toggle.

## Reviewer guidance

- Confirm the fidelity assertion is on the value-equal DSO, not the entity.
- Confirm each absence/refusal control has a named positive control.
- Confirm containment refuses before any bytes and no stray temp survives a failed write.
