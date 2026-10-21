---
work_package_id: WP01
title: Slice skeleton & module scaffolding
dependencies: []
requirement_refs:
- FR-013
- NFR-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 2c8899caa3c4980309baa01759799c744e666005
created_at: '2026-10-21T13:10:21.066323+00:00'
subtasks:
- T001
- T002
- T003
- T004
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/bootstrap/
create_intent:
- service/kernel/build.gradle.kts
- service/packs/build.gradle.kts
- service/bootstrap/build.gradle.kts
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/SliceSkeletonArchTest.java
execution_mode: code_change
owned_files:
- service/kernel/build.gradle.kts
- service/packs/build.gradle.kts
- service/bootstrap/build.gradle.kts
- service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/SliceSkeletonArchTest.java
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP01 – Slice skeleton & module scaffolding

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else** — before reading further, before touching a file.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match. Behave according to the profile for the remainder of this WP.

---

## Objective

Stand up the three new gradle modules — `service/kernel`, `service/packs`, `service/bootstrap` — and the `ai.speckitty.charter.<concern>.<layer>` package homes the migration (WP02..WP07) will land code into, plus a bootstrap ArchUnit **skeleton shell** test. This is IC-01's scaffolding half; the biting ArchUnit inventory is finalized in WP07 once code is present.

This WP adds empty modules only. It does **not** move any existing code, and it does **not** edit `settings.gradle` (WP07 owns the module-set rewrite — see Branch Strategy / Risks).

## Context

- Target layout, module roles and the `kernel`/`packs`/`bootstrap` split: [plan.md](../plan.md) "Project Structure" and IC-01.
- The two kernel sub-packages (`kernel.domain`, `kernel.validation`) and the four `packs` layers: [data-model.md](../data-model.md) slice carve.
- Do not duplicate the design here — read those two files. Mirror the plugin/dependency shape of the existing modules (`service/charter-domain/build.gradle.kts`, `service/charter-service/build.gradle.kts`) rather than inventing new conventions.

### Subtask T001 — `service/kernel` gradle module
- **Purpose**: The pure inner module hosting `kernel.domain` (shared Pack/DRG vocab) and `kernel.validation` (guard lib, filled by WP08).
- **Steps**:
  1. Create `service/kernel/build.gradle.kts` applying the same java-library convention plugin the existing domain module uses (inspect `service/charter-domain/build.gradle.kts`). Dependencies: `jspecify` and the test stack only; **no** Spring, SnakeYAML or networknt.
  2. Create the empty source roots `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/` and `.../kernel/validation/` (a `.gitkeep` is acceptable until WP02/WP08 add files).
- **Files**: `service/kernel/build.gradle.kts`.
- **Validation**: `./gradlew :kernel:help` resolves the project (it need not be in `settings.gradle` yet — see Risks).
- **Edge cases**: do not add a `package-info.java` here — WP02 owns `kernel.domain` scaffolding and WP08 owns `kernel.validation`.

### Subtask T002 — `service/packs` gradle module
- **Purpose**: The pack vertical slice hosting `domain`, `application`, `infra.storage`, `infra.api`.
- **Steps**:
  1. Create `service/packs/build.gradle.kts`: `project(":kernel")` dependency; stage (but keep commented or minimal until the migration needs them) the SnakeYAML / networknt / Spring placements the adapters require — WP05/WP06 activate them when the code moves.
  2. Create the four empty layer source roots under `service/packs/src/main/java/ai/speckitty/charter/packs/{domain,application,infra/storage,infra/api}/`.
- **Files**: `service/packs/build.gradle.kts`.
- **Validation**: `./gradlew :packs:help` resolves.
- **Edge cases**: no `package-info.java` and no classes — the migration WPs bring those with their moves.

### Subtask T003 — `service/bootstrap` gradle module
- **Purpose**: The composition root (Spring Boot app, component-scan, the ArchUnit gate home).
- **Steps**:
  1. Create `service/bootstrap/build.gradle.kts`: Spring Boot application plugin as `charter-service` uses it; `project(":kernel")` + `project(":packs")`; test deps ArchUnit, JUnit 5, AssertJ.
  2. Create the main + test source roots under `ai/speckitty/charter/bootstrap/`.
- **Files**: `service/bootstrap/build.gradle.kts`.
- **Validation**: `./gradlew :bootstrap:help` resolves.

### Subtask T004 — Bootstrap ArchUnit skeleton shell
- **Purpose**: A placeholder gate asserting the new module/package homes exist, so the skeleton phase has a green anchor; WP07 replaces it with the full ADR-0006 inventory.
- **Steps**:
  1. Create `service/bootstrap/src/test/java/ai/speckitty/charter/bootstrap/SliceSkeletonArchTest.java`.
  2. Assert only structural existence: the packages `ai.speckitty.charter.kernel.domain`, `ai.speckitty.charter.kernel.validation`, and the four `packs` layers are importable targets (an ArchUnit `classes().that().resideInAPackage(...)` existence/empty-tolerant check, OR a trivial directory-presence assertion). Keep it non-vacuous-safe: it must not claim to enforce layering (that is WP07).
  3. Javadoc it clearly as a **skeleton shell superseded by WP07's `HexagonalArchitectureTest`**.
- **Files**: `SliceSkeletonArchTest.java`.
- **Validation**: `./gradlew :bootstrap:test --tests '*SliceSkeletonArchTest'`.
- **Edge cases**: do NOT name this `HexagonalArchitectureTest` — that file is WP07's sole property; co-owning it would collide.

## Branch Strategy

- Planning branch: `feat/pack-repositories-validation`. Final merge target: `feat/pack-repositories-validation`.
- Execution worktrees are allocated per lane from `lanes.json` (lanes are computed by `finalize-tasks` from the dependency graph).
- **`settings.gradle` is intentionally NOT in this WP's surface.** WP07 performs the single-writer `settings.gradle` rewrite to the `{kernel, packs, bootstrap}` module set. Until then these modules are driven by direct task invocation; this is a deliberate ownership compromise to keep `settings.gradle` single-owner.

## Definition of Done

- The three `build.gradle.kts` files exist and their projects resolve.
- The package homes exist for all six target packages.
- `SliceSkeletonArchTest` runs green and is documented as superseded by WP07.
- No existing module or `settings.gradle` is modified by this WP.

## Risks

- **Modules not yet in `settings.gradle`** → they will not participate in `verifyAll` until WP07 registers them; acceptable for a skeleton. Flag to the WP07 implementer that the files already exist.
- **Vacuous gate** → the shell asserts existence only; the biting rules land in WP07 with the code.

## Reviewer guidance

- Confirm the modules carry no code and `kernel` pulls in no framework deps.
- Confirm the shell test is clearly labelled a placeholder and is not named `HexagonalArchitectureTest`.
- Confirm `settings.gradle` is untouched.
