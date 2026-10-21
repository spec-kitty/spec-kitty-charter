---
work_package_id: WP04
title: 'Persistence edge: storage config + upsert + point-read + scan-driven'
dependencies:
- WP02
- WP03
requirement_refs:
- FR-008
- FR-009
- FR-010
- NFR-001
- NFR-003
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: ff06a42995662e3ac74ccb8dcc1905e9bd60a74c
created_at: '2026-10-21T19:47:39.883818+00:00'
subtasks:
- T016
- T017
- T018
- T019
- T020
- T021
- T022
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/config/StorageConfiguration.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/config/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/TacticSaveContractTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 – Persistence edge: storage config + upsert + point-read + scan-driven

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Make the persistence edge own its wiring and fold the squad's storage findings: a `StorageConfiguration` that `@ComponentScan`s the storage package (scan-driven `DomainRepositoryService`) and provides an injected `SerializationStrategy` bean; implement **upsert** `save` with an atomic create arm; resolve a pack identity with a descriptor-only read (no fan-out); collapse `partialReads` into the aggregate owner; delete the storage scaffolding. IC-02 (storage) + IC-06. Depends on WP02 (gate + `@Bean` dropped) and WP03 (upsert contract).

## Context
- `DomainRepositoryService` (`@Service`), `FilesystemPackStore` (atomic temp→ATOMIC_MOVE, pack-root-contained — preserve the fail-closed path/symlink checks the correctness lens verified), `SerializationStrategy` (package-private, hard-newed in the ctor today) + `YamlSerializationStrategy`, DSOs. See [data-model.md](../data-model.md), [research.md](../research.md) MINORs.

### T016 — `StorageConfiguration`
- New `...infra/storage/config/StorageConfiguration.java` (`@Configuration`) `@ComponentScan("ai.speckitty.charter.packs.infra.storage")` to pick up `@Service DomainRepositoryService`, and a `@Bean SerializationStrategy serializationStrategy()` returning `YamlSerializationStrategy`. This is the layer config WP02's `DeployableConfiguration` scans.

### T017 — Scan-driven + injected strategy
- `DomainRepositoryService` is bean-provided by scan (the explicit `@Bean` was dropped in WP02). Change its ctor to **inject** `SerializationStrategy` (promote the package-private test ctor to the wiring path) instead of hard-newing YAML. Exactly one `DomainRepositoryService` bean (assert no duplicate).

### T018 — Upsert `save`
- Implement create-or-update in `FilesystemPackStore`: an unknown tactic id **creates** a new file atomically in the pack dir (same temp→ATOMIC_MOVE, same lexical + real-path containment checks as replace); a known id replaces. Preserve: target untouched on failure, no stray temp, symlink/path-escape refused fail-closed.

### T019 — Point-read identity resolution (NFR-001)
- Resolve a `PackIdentifier`→pack via a **descriptor-only** read; do not walk/parse every tactic of every pack to match `pack_id`. A single-tactic read touches only that pack's files.

### T020 — Collapse `partialReads`
- `DomainRepositoryService` stops exposing a second violations projection; `Pack.violations()` is canonical (WP03). Keep `PackInspectionQuery` compiling if still referenced, but route through the aggregate; WP07 deletes the port.

### T021 — Delete storage scaffolding
- Delete `FileSystemCharterPackRepository` (and any storage-only helper it alone used). Its port `CharterPackRepository` is deleted in WP07.

### T022 — Storage tests
- `TacticSaveContractTest`: the **both-arm** upsert proof — create (unknown id → new file, atomic, contained) and update (known id → replaced), each with a non-vacuity control (a mutation is visible on reload; a path-escape is refused). Preserve the existing round-trip + write-safety tests. Point-read: assert a single-pack read does not open other packs' files.

## Definition of Done
- `:packs:test` storage tests green incl. `TacticSaveContractTest` (both arms, non-vacuous); round-trip + write-safety preserved; exactly one `DomainRepositoryService` bean; `SerializationStrategy` injected; `FileSystemCharterPackRepository` gone.

## Reviewer guidance (test economy — DIRECTIVE_041)
- The both-arm contract test earns its keep (two genuinely different failure modes). Reject a duplicate of the existing round-trip test or a second redundant containment control. Verify the create arm is atomic+contained, not a naive `Files.write`.
