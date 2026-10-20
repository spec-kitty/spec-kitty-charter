---
work_package_id: WP01
title: Domain models, extended descriptor & port
dependencies: []
requirement_refs:
- FR-002
- NFR-002
- C-002
- C-004
- C-006
- FR-006
- FR-009
planning_base_branch: feat/pack-roundtrip-io
merge_target_branch: feat/pack-roundtrip-io
branch_strategy: Planning artifacts for this mission were generated on feat/pack-roundtrip-io. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-roundtrip-io unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-roundtrip-io-01M5DEH8
base_commit: 4fda81b07489644b9b235d9edeada0c8609571b9
created_at: '2026-10-20T20:51:26.474255+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
phase: Phase 1 - Domain
history:
- at: '2026-10-20T20:38:22Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/charter-domain/src/main/java/ai/speckitty/charter/domain/
create_intent:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Tactic.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticStep.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticReference.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackSnapshot.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementViolation.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackLocator.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementRepository.java
execution_mode: code_change
model: ''
owned_files:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Tactic.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticStep.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/TacticReference.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackSnapshot.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementViolation.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackLocator.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementRepository.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackDescriptor.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP01 – Domain models, extended descriptor & port

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match.

---

## Objectives & Success Criteria

Introduce the graph-free domain vocabulary for the descriptor+tactic round-trip and the domain-owned repository port. Done when:

- `Tactic`, `TacticStep`, `TacticReference`, `PackSnapshot`, `PackElementViolation`, `PackLocator`, and `PackElementRepository` exist in `charter-domain`, plain Java only.
- `PackDescriptor` is extended with `activeCharter` and `packDrg` **without** breaking its 8 existing constructor callers.
- Equality semantics are pinned exactly as the data model requires; no storage coordinate (no path/`Path`) appears on any model.
- `./gradlew :charter-domain:test` and `:charter-domain:compileJava` stay green. (Palantir java-format: run `./gradlew :charter-domain:spotlessApply` or match the style.)

## Context & Constraints

- Read [data-model.md](../data-model.md) (binding field lists + invariants) and [research.md](../research.md) D4 (equality), D5 (opaque structs), D8 (descriptor fields). Read [spec.md](../spec.md) FR-002/006/009, NFR-002, C-002/004/006.
- **Onion layering (ADR-0006)**: `charter-domain` may depend only on `java..` + jspecify. `HexagonalArchitectureTest.theCoreIsPlainJava` already enforces this — a stray import fails the build.
- **No storage coordinate on the model** (plan-review MAJOR-3): the id→path mapping lives in the adapter (WP02), not here.
- Mirror existing domain record style (see `PackDescriptor.java`, `PackContents.java`, `PackRoot.java`): compact canonical constructor doing `Objects.requireNonNull` + `List.copyOf`, jspecify `@Nullable`/`Optional` for optionals.

## Branch Strategy

- **Strategy**: (populated by finalize-tasks)
- **Planning base branch**: feat/pack-roundtrip-io
- **Merge target branch**: feat/pack-roundtrip-io

## Subtasks & Detailed Guidance

### Subtask T001 – `TacticReference` record  *(parallel-safe)*
- **Purpose**: The opaque, verbatim reference struct carried on tactics and steps (C-004; schema `TacticReference`).
- **Steps**: `record TacticReference(String name, String type, String id, String when)`. All four required (schema requires all four). Compact constructor `requireNonNull` each. `type` is stored as a **plain String token**, never resolved into a relationship. Structural equality is the record default — do not override.
- **Files**: `TacticReference.java`.
- **Notes**: No enum for `type` — keep it a string so an unknown artifact-kind token round-trips faithfully.

### Subtask T002 – `TacticStep` record  *(parallel-safe)*
- **Purpose**: A structured step (schema `TacticStep`).
- **Steps**: `record TacticStep(String title, Optional<String> description, List<String> examples, List<TacticReference> references)`. `title` required (`requireNonNull`). `examples`/`references` → `List.copyOf` (empty when absent; order- and duplicate-sensitive). `description` → `Optional` (empty when absent OR explicit null — see D4 collapse).
- **Files**: `TacticStep.java`.

### Subtask T003 – `Tactic` record
- **Purpose**: The leaf artifact aggregate (schema `Tactic`).
- **Steps**: `record Tactic(String id, String schemaVersion, String name, List<TacticStep> steps, Optional<String> purpose, Optional<String> notes, List<String> failureModes, List<String> appliesToLanguages, List<TacticReference> references)`.
  - `id`/`schemaVersion`/`name` required; `steps` required and **non-empty** (schema `minItems: 1`) — throw `IllegalArgumentException` on empty.
  - All lists `List.copyOf` (order- and duplicate-sensitive; empty==absent per D4).
  - Optionals empty==absent==explicit-null (D4).
  - **No `sourcePath` / storage field** — equality is purely semantic (two identical tactics from different files compare equal).
- **Files**: `Tactic.java`.

### Subtask T004 – Extend `PackDescriptor` (back-compat)
- **Purpose**: Model the two schema-declared path fields the current record drops, so they survive write-back (D8; MAJOR-1). Both are `anyOf[string,null]` pack-relative paths — plain strings, no graph.
- **Steps**:
  1. **Append** two components to the record, after `undeclaredKeys`: `Optional<String> activeCharter, Optional<String> packDrg`. (Append, not insert — keeps a clean arity split.)
  2. In the compact canonical constructor, `requireNonNull` both (they are `Optional`, never null) and keep the existing `undeclaredKeys = List.copyOf(...)`.
  3. **Add a backward-compatible constructor** with the OLD 7-arg signature (`packId, packVersion, parentPack, name, declaredLevel, requiresCore, undeclaredKeys`) that delegates to the canonical 9-arg with `Optional.empty(), Optional.empty()`. This keeps the 7 existing test callers and any other caller compiling unchanged.
- **Files**: `PackDescriptor.java`.
- **Notes**: Verify compile across modules: `./gradlew compileJava compileTestJava`. The adapter's `PackDescriptorReader` is updated in WP02 to populate the new fields via the 9-arg constructor; do not touch it here.

### Subtask T005 – `PackSnapshot` aggregate + `PackElementViolation`
- **Purpose**: The partial-tolerant fetch result (distinct from `CharterPack`, which requires a `DoctrineGraph`).
- **Steps**:
  - `PackElementViolation`: a reported (not thrown) problem — `record PackElementViolation(String file, String kind, String reason)`. (If `PackContents.SchemaViolation`/`Unreadable` fit cleanly as the reported shape, you MAY reuse them instead of a new type — but they are tied to the facts model; a thin new record keeps the new aggregate uncoupled. Prefer the new record.)
  - `PackSnapshot`: `record PackSnapshot(PackDescriptor descriptor, List<Tactic> tactics, List<PackElementViolation> violations)`. `tactics` empty (not null) for a descriptor-only pack. `List.copyOf` all.
- **Files**: `PackSnapshot.java`, `PackElementViolation.java`.

### Subtask T006 – `PackLocator` + `PackElementRepository` port
- **Purpose**: The domain-owned port and its location value (NFR-001; C-006).
- **Steps**:
  - `PackLocator`: `record PackLocator(String locator)` — opaque to the domain; the adapter resolves it. (Rationale over reusing `PackRoot`: `PackRoot` carries offering-config semantics — `name`, `configuredLevel` — this bare-location vertical does not need. A thin new value keeps the aggregate uncoupled. If you judge `PackRoot` a better fit, that is acceptable — but do NOT use the infra `PackLocation`, which lives in `adapter-out-yaml`.)
  - `PackElementRepository` (interface):
    - `PackSnapshot fetch(PackLocator location);` — partial-tolerant: returns valid descriptor + tactics and reports violations; never refuses the whole pack on one bad file.
    - `void write(PackLocator location, Tactic tactic);` and `void write(PackLocator location, PackDescriptor descriptor);` — replace-existing by id; the adapter resolves id→path. (Two overloads, or a sealed `PackElement` wrapper — your call; keep signatures domain-only.)
  - Javadoc the partial-tolerant and replace-existing contracts explicitly (contrast with `CharterPackRepository.load()` all-or-nothing).
- **Files**: `PackLocator.java`, `PackElementRepository.java`.
- **Notes**: Every parameter/return type is a domain model — no `Path`, stream, or infra DTO.

## Test Strategy

No NEW test file in this WP (test economy): the models' equality and the port contract are proven end-to-end through the port in WP03 (fetch-fidelity oracle + round-trip). Your gate here is **compilation + the existing `:charter-domain` suite staying green** via the back-compat constructor. Run:

```bash
./gradlew :charter-domain:compileJava :charter-domain:compileTestJava :charter-domain:test
```

A passing test runner does not replace the compiler check — both must pass.

## Risks & Mitigations

- **Ripple from the descriptor change** → the back-compat 7-arg constructor (T004) confines real edits to this file; verify a full `compileTestJava` across modules.
- **Accidental storage leak** → do not add any path field to `Tactic`/`PackSnapshot`; the mapping is WP02's.
- **Overriding equality** → never override record `equals`; rely on structural equality with `List.copyOf` value lists.

## Review Guidance

- Confirm no `charter-domain` import outside `java..`/jspecify (ArchUnit will also check).
- Confirm the back-compat constructor keeps all 8 existing `new PackDescriptor(...)` callers compiling.
- Confirm `steps` empty is rejected and optional/list collapse matches D4.
- Confirm the port signatures are domain-only and the partial-tolerant/replace-existing contracts are javadoc'd.

## Progress & Status

Record progress in the status event log, not this file:
- `spec-kitty agent tasks add-history WP01 --note "<what you did>"`
- `spec-kitty agent tasks move-task WP01 --to <status>`
