---
work_package_id: WP11
title: Persistence — repository @Service, mapping, port retirement
dependencies:
- WP10
requirement_refs:
- FR-001
- FR-003
- FR-004
- FR-005
- NFR-002
- SC-001
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: e37b7fb60ea053a2e1d5b0e424e66340a2c2b137
created_at: '2026-10-21T15:31:54.151114+00:00'
subtasks:
- T047
- T048
- T049
- T050
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementRepository.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackLocator.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP11 – Persistence: repository @Service, mapping, port retirement

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Realize the domain ports at the edge: one `@Service DomainRepositoryService` ctor-injecting a `SerializationStrategy` + `FilesystemPackStore`, with hand-written DSO↔entity mapping, serving all packs via explicit `PackIdentifier`. Retire the superseded `PackElementRepository`/`PackLocator` and replace their test battery with the new ports' tests. This is IC-05b.

## Context

- The DIP realization (ports in domain, `@Service` at the edge, ctor injection) and the deliberate retirement: [data-model.md](../data-model.md) `packs.infra.storage`; [research.md](../research.md) D3/D4; [spec.md](../spec.md) FR-001/003/004/005, NFR-002, C-002/008.
- The ports + entities are WP09's; the strategy/DSO/store are WP10's — this WP wires them into one adapter.

### Subtask T047 — `DomainRepositoryService` @Service
- **Purpose**: The single edge adapter realizing all four ports (FR-001/005).
- **Steps**: `@Service class DomainRepositoryService implements TacticRepository, PackDescriptorRepository, PackRepository, PackInspectionQuery`; ctor-inject `SerializationStrategy` + `FilesystemPackStore`. `load(packId, id)` returns `Optional.empty()` for an absent id (FR-003, never throws); `PackRepository.load` composes descriptor + tactics into the `Pack`; `PackInspectionQuery.partialReads` returns the off-path violations (FR-004). The stereotype lives only here (C-002); the domain ports stay plain interfaces.
- **Files**: `DomainRepositoryService.java`.
- **Edge cases**: multi-pack via explicit `PackIdentifier` — no per-pack singleton; a missing/unreadable `pack.yaml` refuses the descriptor fetch (US3 scenario 3).

### Subtask T048 — Hand-written DSO↔entity mapping
- **Steps**: map `TacticDso`↔`Tactic`, `PackDescriptorDso`↔`PackDescriptor` by hand (no MapStruct); reconstruct entities via the package-private all-args ctor (adapter is out-of-package, so expose reconstruction via the builder or a package-visible seam per WP09's design).
- **Files**: within `DomainRepositoryService.java` (or a package-private mapper helper co-located).
- **Edge cases**: a schema-broken element file is excluded from `load` and reported via the inspection query, not thrown (US3 scenario 2).

### Subtask T049 — Retire PackElementRepository + PackLocator
- **Purpose**: Deliberate retirement (NFR-002).
- **Steps**: delete `PackElementRepository` + `PackLocator` from `charter-domain`; delete the moved `FilesystemPackElementRepository` from `packs.infra.storage` (superseded by `DomainRepositoryService`); remove any remaining references. This empties the last of `charter-domain`.
- **Files**: `charter-domain/.../PackElementRepository.java`, `.../PackLocator.java`, `packs/.../infra/storage/FilesystemPackElementRepository.java` (all removed).
- **Edge cases**: confirm no consumer still imports them; the app/listing paths use the surviving `CharterPackRepository` (unchanged, NFR-002).

### Subtask T050 — Replace the PackElement*Test battery with the new ports' tests
- **Purpose**: The round-trip/behaviour invariant moves to the new repositories (NFR-002, IC-08).
- **Steps**: `DomainRepositoryServiceTest` — `load(packId, absentId)` returns `Optional.empty()` **paired on the same fixture** with a present-id row that returns a populated `Tactic` (FR-003 positive control); `save(tactic)` then `load` reflects the mutation (US1 scenario 2); `PackRepository.load` assembles the `Pack`; a broken tactic file surfaces via `PackInspectionQuery`, not `load`.
- **Files**: `DomainRepositoryServiceTest.java`.
- **Validation**: `./gradlew :packs:test`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP10. With WP09, completing this empties `charter-domain` (its build file + settings de-registration are handled in WP03/WP07 once the module is empty).

## Definition of Done

- `DomainRepositoryService` realizes the four ports with hand-written mapping; absent id returns empty (paired with a present control).
- `PackElementRepository`/`PackLocator`/`FilesystemPackElementRepository` are retired; untouched read paths unchanged.
- `./gradlew :packs:test` green.

## Risks

- **Reconstruction access** → the adapter is out-of-package; use WP09's builder/package-visible seam, not a widened public ctor (keeps FR-008).
- **Leftover references** → grep for the retired types before deleting; a dangling import breaks the build.

## Reviewer guidance

- Confirm the stereotype is only on the edge and the ports stay plain interfaces.
- Confirm absent-id-empty has a named present-id positive control.
- Confirm the retired types are gone and the surviving read paths (NFR-002) are untouched.
