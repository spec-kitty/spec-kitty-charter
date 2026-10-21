---
work_package_id: WP03
title: Migrate charter-domain pack types → packs.domain
dependencies:
- WP02
requirement_refs:
- FR-013
- NFR-002
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: c1046ab70c11c575fd9d6e8b45a6e6ca0026ad8f
created_at: '2026-10-21T13:29:05.360171+00:00'
subtasks:
- T008
- T009
- T010
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/package-info.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetMimeTable.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterOffering.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPackRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackElementViolation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRoot.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackSources.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackValidation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackVerdict.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterOfferingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyGraphTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyOrderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyVocabularyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackValidationTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/package-info.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/AssetMimeTable.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterOffering.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPack.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/CharterPackRepository.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/Diagnostic.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackElementViolation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRoot.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackSources.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackValidation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackVerdict.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/CharterOfferingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyGraphTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyOrderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicyVocabularyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackValidationTest.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/AssetMimeTable.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterOffering.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterPack.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterPackRepository.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/Diagnostic.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackContents.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackElementViolation.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFinding.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackInspector.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackRoot.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackSources.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackValidation.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackVerdict.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/package-info.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/CharterOfferingTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackFindingTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyGraphTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyOrderTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyRulesTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyVocabularyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackValidationTest.java
- service/charter-domain/build.gradle.kts
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP03 – Migrate charter-domain pack types → packs.domain

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Relocate the 15 stable pack-management classes from `ai.speckitty.charter.domain` to `ai.speckitty.charter.packs.domain`, verbatim, with their 8 unit tests, keeping `verifyAll` green. Step 2 of the bottom-up migration (IC-02).

The **roundtrip cluster** (`Tactic`, `TacticStep`, `TacticReference`, `PackSnapshot`) is reshaped by WP09 and the **retiring ports** (`PackElementRepository`, `PackLocator`) by WP11 — they are **not** moved here. `PackElementViolation` **is** moved here (it is KEPT as the new inspection-query return type).

## Context

- The exact stable/roundtrip/retire split: [data-model.md](../data-model.md) `packs.domain` section ("Existing" vs "Superseded" vs "New").
- Migration categories: [occurrence_map.yaml](../occurrence_map.yaml). Note `PackElementRepository`/`PackLocator` are explicitly SUPERSEDED (not renamed here); `PackElementViolation` is KEPT.
- Verified: the 15 stable classes do **not** reference the roundtrip cluster, so they move independently.

### Subtask T008 — Move the 15 stable pack classes + package-info
- **Purpose**: Populate `packs.domain` with the stable behaviour surface.
- **Steps**:
  1. Move `AssetMimeTable`, `CharterOffering`, `CharterPack`, `CharterPackRepository`, `Diagnostic`, `PackContents`, `PackElementViolation`, `PackFinding`, `PackFindingCode`, `PackInspector`, `PackPolicy`, `PackRoot`, `PackSources`, `PackValidation`, `PackVerdict` to `service/packs/src/main/java/ai/speckitty/charter/packs/domain/`.
  2. Rewrite each `package` to `ai.speckitty.charter.packs.domain`; rewrite imports of kernel types to `ai.speckitty.charter.kernel.domain` (WP02 already moved them).
  3. Add `packs/domain/package-info.java` (`@NullMarked`).
- **Files**: the 15 moved classes + new package-info.
- **Validation**: `./gradlew :packs:compileJava`.
- **Edge cases**: `CharterOffering` → `CharterPack` (packs); `AssetMimeTable`/`Diagnostic` referenced by `PackPolicy`/`PackFinding` (all in packs) — intra-slice, clean.

### Subtask T009 — Move the 8 pack-domain tests + fixtures
- **Steps**: move `CharterOfferingTest`, `PackFindingTest`, `PackPolicyGraphTest`, `PackPolicyOrderTest`, `PackPolicyRulesTest`, `PackPolicyTest`, `PackPolicyVocabularyTest`, `PackValidationTest` to `service/packs/src/test/java/ai/speckitty/charter/packs/domain/`, rewriting package + imports. Carve any test fixtures these use into the packs slice test resources.
- **Files**: the 8 moved test files.
- **Validation**: `./gradlew :packs:test`.

### Subtask T010 — Downstream rewrites + charter-domain build teardown
- **Steps**:
  1. Rewrite every remaining consumer's import of the moved pack types to `ai.speckitty.charter.packs.domain`; add the `project(":packs")` edge where needed.
  2. Remove the now-stale `charter-domain/package-info.java` (its classes have left; only the roundtrip cluster + retiring ports remain, drained by WP09/WP11).
  3. Delete `service/charter-domain/build.gradle.kts` only once the module is fully drained — since WP09/WP11 still remove files from it, coordinate: leave the build file in place if those WPs have not yet landed, and note the dependency. (If WP09/WP11 land first in the integration branch, delete it here.)
  4. `verifyAll`.
- **Files**: downstream import edits (in their home modules), `charter-domain` package-info + build file.
- **Validation**: `./gradlew verifyAll`.
- **Edge cases**: `settings.gradle` de-registration of `charter-domain` is WP07's (single-writer). Do not edit `settings.gradle` here.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP02; precedes WP04.

## Definition of Done

- The 15 stable classes + 8 tests live in `packs.domain`; `PackElementViolation` among them.
- The roundtrip cluster + retiring ports remain in `charter-domain` for WP09/WP11.
- `./gradlew verifyAll` green.

## Risks

- **Premature `charter-domain` deletion** → the module still holds WP09/WP11 files; delete its build file only when empty (T010 step 3).
- **`PackElementViolation` isolation** → only the roundtrip cluster references it today; it still moves (it is the kept query return type) and will be re-wired by WP09's `PackInspectionQuery`.

## Reviewer guidance

- Confirm rename-shaped diffs only (no logic change) across the 15 classes.
- Confirm `packs.domain` imports kernel types from `kernel.domain`, never from `charter.domain`.
- Confirm the roundtrip cluster + retiring ports were left untouched.
