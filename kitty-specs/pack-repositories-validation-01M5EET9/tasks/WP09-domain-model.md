---
work_package_id: WP09
title: Domain model — aggregate, entity, value objects, ports
dependencies:
- WP08
requirement_refs:
- FR-001
- FR-002
- FR-004
- FR-005
- FR-007
- FR-008
- FR-009
- SC-001
- SC-004
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 9e5c7d5865942c6e087d3b1401a5e4b07e94a234
created_at: '2026-10-21T15:13:14.116230+00:00'
subtasks:
- T033
- T034
- T035
- T036
- T037
- T038
- T039
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPaths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDescriptorRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspectionQuery.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Tactic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticReference.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/TacticEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Pack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPaths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticIdentifier.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Tactic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticStep.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticReference.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/TacticRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackDescriptorRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspectionQuery.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/TacticEntityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Tactic.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticStep.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticReference.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackSnapshot.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP09 – Domain model: aggregate, entity, value objects, ports

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Model the pack concern as a DDD aggregate: a `Pack` aggregate root (identity `PackIdentifier`) composing a `PackDescriptor` (kernel VO) + `Tactic` child entities (identity `TacticIdentifier`) + read violations; VO/identifier records; and the four framework-free domain ports. Constructor guards go through the `kernel.validation` vocabulary. This is IC-04.

This WP also carries the relocation of the roundtrip cluster (`Tactic`/`TacticStep`/`TacticReference`/`PackSnapshot`) out of `charter-domain`: reshape `Tactic` into an identity entity, `TacticStep`/`TacticReference` into VO records at `packs.domain`, and fold `PackSnapshot`'s role into `Pack`.

## Context

- The aggregate boundary, entity/VO classification, equality rules and the port signatures: [data-model.md](../data-model.md) `packs.domain` "New" + "Invariants & equality"; [research.md](../research.md) D1/D2/D3/D8; [spec.md](../spec.md) FR-001/002/004/005/007/008/009, C-005/009.
- Construction style (records for VOs; final class + fluent builder + package-private all-args ctor for entities): [research.md](../research.md) D8.
- `PackElementViolation` (the inspection return type) was moved to `packs.domain` by WP03 — reuse it, do not recreate.

### Subtask T033 — Identifier VOs *(parallel-safe)*
- **Purpose**: Future-proof typed identity (D3).
- **Steps**: `record PackIdentifier(String value)` and `record TacticIdentifier(String value)`; `requireNonNull`/non-blank guard in the compact ctor (via `Checks`). Value equality by component.
- **Files**: `PackIdentifier.java`, `TacticIdentifier.java`.
- **Edge cases**: wrap the raw id so a later ULID/GUID change does not ripple to the ports.

### Subtask T034 — Value-object records *(parallel-safe)*
- **Purpose**: The owned value objects (C-005).
- **Steps**: reshape the moved `TacticStep` and `TacticReference` into value records (keep their fields; value equality; `List.copyOf`, order- and duplicate-sensitive). Add `record PackPaths(Optional<String> activeCharter, Optional<String> packDrg)` grouping the two descriptor path strings.
- **Files**: `TacticStep.java`, `TacticReference.java` (reshaped), `PackPaths.java` (new).

### Subtask T035 — `Tactic` child entity
- **Purpose**: Reshape the old value-equal record into an identity entity (D1/D2).
- **Steps**: `final class Tactic` with identity `TacticIdentifier`; **identity-based** `equals`/`hashCode` (NOT field-wise); holds its `PackIdentifier`; owns `List<TacticStep>` + `List<TacticReference>` (order + duplicates significant); fields `schemaVersion`, `name`, `purpose?`, `notes?`, `failureModes[]`, `appliesToLanguages[]`. Public fluent builder; package-private all-args ctor (for the adapter to reconstruct). Guards via `ModularRuleset.guard` / `Checks`.
- **Files**: `Tactic.java` (reshaped from the moved record).
- **Validation**: two tactics with the same identifier are equal regardless of fields; builder constructs an equal entity; the package-private ctor is unreachable outside the package.
- **Edge cases**: the round-trip no longer leans on entity equals — it moves to the DSO (WP10). Remove the old value-equality semantics.

### Subtask T036 — `Pack` aggregate root
- **Purpose**: The DDD root (C-009).
- **Steps**: `final class Pack` with identity `PackIdentifier`; composes a `PackDescriptor` (kernel VO) + `List<Tactic>` + `List<PackElementViolation>` (read violations off the load path). Public fluent builder; package-private all-args ctor. Identity-based equals. Fold `PackSnapshot`'s descriptor+tactics+violations role into `Pack`; remove `PackSnapshot`.
- **Files**: `Pack.java` (new); delete the moved `PackSnapshot`.
- **Validation**: `PackAggregateTest` composes a descriptor + tactics + violations and asserts identity equality + builder construction.

### Subtask T037 — Leaf ports
- **Purpose**: Storage-transparent per-type repositories (FR-001/002).
- **Steps**:
  - `interface TacticRepository { void save(Tactic tactic); Optional<Tactic> load(PackIdentifier packId, TacticIdentifier id); }`
  - `interface PackDescriptorRepository { PackDescriptor get(PackIdentifier packId); void save(PackIdentifier packId, PackDescriptor descriptor); }`
  - Every parameter/return type is a domain type (no `Path`, no infra DTO) — this is the FR-002 control surface the port-signature ArchUnit rule (WP07) checks.
- **Files**: `TacticRepository.java`, `PackDescriptorRepository.java`.

### Subtask T038 — Aggregating repository + inspection query
- **Steps**:
  - `interface PackRepository { Pack load(PackIdentifier packId); }` (composes the leaf repositories into the aggregate).
  - `interface PackInspectionQuery { List<PackElementViolation> partialReads(PackIdentifier packId); }` — violations off the `load` path, reusing the KEPT `PackElementViolation`.
- **Files**: `PackRepository.java`, `PackInspectionQuery.java`.

### Subtask T039 — Constructor guards via validation
- **Purpose**: Uniform fail-fast invariants (FR-007).
- **Steps**: express the entity/VO ctor guards through `ModularRuleset`/`Checks` from `kernel.validation` (e.g. non-empty steps, required identity) rather than ad-hoc `if`-throws. The domain depends on `kernel.validation` (SC-002).
- **Files**: edits within the entity/VO files above.
- **Validation**: a breach throws the bespoke `ConstraintBreachException`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP03 (packs.domain home + `PackElementViolation`) and WP08 (`kernel.validation`). Completing this drains the roundtrip cluster from `charter-domain` (with WP11 retiring the last two ports).

## Definition of Done

- `Pack`/`Tactic` are identity entities (final class + fluent builder + package-private ctor); `TacticStep`/`TacticReference`/`PackPaths`/identifiers are records.
- The four ports name only domain types.
- Guards run through `kernel.validation`; `PackSnapshot` is folded into `Pack`.
- `./gradlew :packs:test` green.

## Risks

- **Entity-vs-value confusion** → `Tactic`/`Pack` equal by identity only; the fidelity property lives on the DSO (WP10), not here.
- **Port purity** → an infra type leaking into a port signature reds the port-signature rule; keep them domain-only.

## Reviewer guidance

- Confirm identity equals (not field-wise) and the package-private ctor is unreachable outside the package (paired positive control: builder yields an equal entity).
- Confirm ports are domain-only and `PackElementViolation` is reused, not recreated.
- Confirm guards use `kernel.validation`, and `PackSnapshot` is removed.
