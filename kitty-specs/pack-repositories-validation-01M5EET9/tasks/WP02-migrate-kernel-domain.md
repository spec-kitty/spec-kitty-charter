---
work_package_id: WP02
title: Migrate charter-domain → kernel.domain
dependencies:
- WP01
requirement_refs:
- FR-013
- NFR-005
- SC-002
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 77754d2af649c37f54259c8d55b6a58da4e8098a
created_at: '2026-10-21T13:19:20.908646+00:00'
subtasks:
- T005
- T006
- T007
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/
create_intent:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/package-info.java
execution_mode: code_change
owned_files:
- service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/**
- service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/**
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/ArtifactKind.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterRefusal.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/DoctrineGraph.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/DrgEdge.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/DrgNode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Fingerprint.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/NodeKind.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackDescriptor.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackLevel.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Pep440.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/RefusalCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Relation.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Urn.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/ArtifactKindTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/DoctrineGraphTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/DrgEdgeTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/GovernanceFingerprintTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/NodeKindTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/Pep440Test.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/UrnTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP02 – Migrate charter-domain → kernel.domain

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Relocate the 13 architect-verified firm-kernel classes from `ai.speckitty.charter.domain` to `ai.speckitty.charter.kernel.domain`, verbatim and behaviour-preserving, with their unit tests, keeping `verifyAll` green. This is step 1 (innermost) of the bottom-up slice migration (IC-02).

**No logic change. No retirement. No reshape.** Only module/package relocation + import rewrites.

## Context

- The exact 13-class carve (architect-verified clean of packs references): [data-model.md](../data-model.md) `kernel.domain` section.
- Migration categories and the bottom-up order: [occurrence_map.yaml](../occurrence_map.yaml) `module_moves` + `categories.{code_symbols,import_paths,filesystem_paths}`.
- The stable pack classes, the roundtrip cluster and the retiring ports stay in `charter-domain` for now — WP03 / WP09 / WP11 drain them. Do not touch them here.

### Subtask T005 — Move the 13 firm-kernel classes
- **Purpose**: Populate `kernel.domain` with the shared Pack/DRG value vocabulary.
- **Steps**:
  1. Move `DoctrineGraph`, `DrgNode`, `DrgEdge`, `NodeKind`, `Relation`, `PackDescriptor`, `PackLevel`, `Urn`, `Fingerprint`, `Pep440`, `RefusalCode`, `CharterRefusal`, `ArtifactKind` into `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/`.
  2. Rewrite each file's `package` declaration to `ai.speckitty.charter.kernel.domain`.
  3. Update intra-set imports (e.g. `DrgEdge` referencing `NodeKind`).
- **Files**: the 13 source files (removed) → 13 target files (created).
- **Validation**: `./gradlew :kernel:compileJava`.
- **Edge cases**: `PackDescriptor` is consumed widely (reader/writer/app) — its import rewrite ripples into later WPs' modules; T007 fixes the consumers that exist pre-migration.

### Subtask T006 — kernel.domain package-info + tests
- **Purpose**: Null-marking and the moved unit tests.
- **Steps**:
  1. Create `kernel/domain/package-info.java` with `@org.jspecify.annotations.NullMarked` (mirror `charter-domain`'s existing `package-info`).
  2. Move the 7 kernel-domain tests (`ArtifactKindTest`, `DoctrineGraphTest`, `DrgEdgeTest`, `GovernanceFingerprintTest`, `NodeKindTest`, `Pep440Test`, `UrnTest`) to `service/kernel/src/test/java/ai/speckitty/charter/kernel/domain/`, rewriting package + imports.
- **Files**: `package-info.java` (new) + 7 test files (moved).
- **Validation**: `./gradlew :kernel:test`.

### Subtask T007 — Downstream project() + import rewrites
- **Purpose**: Keep every consumer compiling and `verifyAll` green.
- **Steps**:
  1. For every module that imported the moved symbols from `charter.domain` (read-app, adapters, service), rewrite those imports to `ai.speckitty.charter.kernel.domain`.
  2. Add the `project(":kernel")` dependency edge to those modules' `build.gradle.kts` (keep the `charter-domain` edge too — the stable pack classes still live there until WP03).
  3. Run `verifyAll`.
- **Files**: downstream modules' build + source files that reference the 13 symbols (import-only edits; these stay in their home modules — you are editing references, not relocating those files).
- **Validation**: `./gradlew verifyAll`.
- **Edge cases**: the existing `HexagonalArchitectureTest` still lives in `charter-service` and points at old packages — leave it; WP07 moves and rewrites it. If a kernel import makes a rule temporarily broaden, that is the known co-landing window (plan IC-01) closed in WP07.

## Branch Strategy

- Planning branch: `feat/pack-repositories-validation`; final merge target: `feat/pack-repositories-validation`.
- Execution worktrees allocated per lane from `lanes.json`.
- Depends on WP01 (the `kernel` module exists). Run serially ahead of WP03.

## Definition of Done

- The 13 classes live in `kernel.domain`, with `@NullMarked` package-info and their 7 tests.
- All consumers compile against the new location; `./gradlew verifyAll` green.
- `charter-domain` retains only the stable pack classes, the roundtrip cluster and the retiring ports (drained by later WPs).

## Risks

- **`PackDescriptor` ripple** → it is imported by readers/writers/app; rewrite all callers in the same lane and compile the whole build, not just `:kernel`.
- **Split imports** → a consumer that used both kernel and pack types now has two import roots; mechanical, compiler-caught.

## Reviewer guidance

- Confirm no logic diff — only `package`/`import`/file-location changes (diff should be rename-shaped).
- Confirm `kernel` has zero framework imports.
- Confirm the 13 classes carry no reference back into `charter-domain`/`packs` (kernel is innermost).
