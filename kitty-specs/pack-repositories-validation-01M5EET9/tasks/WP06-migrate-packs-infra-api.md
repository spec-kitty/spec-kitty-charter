---
work_package_id: WP06
title: Migrate adapter-in-rest → packs.infra.api
dependencies:
- WP05
requirement_refs:
- FR-013
- NFR-002
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 64a38ee5b60ab8739ee9797bf7b3e8a0fa5aa3e0
created_at: '2026-10-21T14:00:09.910727+00:00'
subtasks:
- T017
- T018
- T019
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/package-info.java
execution_mode: code_change
owned_files:
- service/adapter-in-rest/**
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 – Migrate adapter-in-rest → packs.infra.api

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Relocate the REST inbound adapter from `ai.speckitty.charter.rest` to `ai.speckitty.charter.packs.infra.api`, verbatim, with its tests, keeping `verifyAll` green. Step 5 of the bottom-up migration (IC-02). No logic change; the inbound HTTP contract is unchanged.

## Context

- The `TRANSPORT_STAYS_IN_ADAPTER` rule (web transport types confined to `packs.infra.api` + `charter-api`) and `onlyTheInboundAdapterRefusesWithInternalError`: [occurrence_map.yaml](../occurrence_map.yaml) `archunit_rules_rewrite`.
- `user_facing_strings` action is `verify-none`: REST paths / operationIds / HATEOAS relations must not embed the old package name. See [occurrence_map.yaml](../occurrence_map.yaml) `categories.user_facing_strings`.

### Subtask T017 — Move the web adapter main
- **Steps**:
  1. Move all `ai.speckitty.charter.rest` main sources (controllers `ContextController`/`InfoController`/`PacksController`, the `Problem*`/`Refusal*` handlers, `WorktreeGuard`/`WorktreeToken`, `CharterHeaders`, `ContractVersion`, `ServiceVersion`, `SupportedMethods`, `Absent`, `ConnectorProblems`) to `service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/`.
  2. Rewrite `package` to `ai.speckitty.charter.packs.infra.api`; rewrite imports to `kernel.domain`/`packs.domain`/`packs.application`/`charter-api`.
  3. Add `infra/api/package-info.java` (`@NullMarked`).
  4. Activate the Spring webmvc + `charter-api` dependency placements in `service/packs/build.gradle.kts`.
- **Files**: the web adapter main tree → `packs/infra/api/`.
- **Validation**: `./gradlew :packs:compileJava`.

### Subtask T018 — Move the 6 web tests
- **Steps**: move `ProblemsTest`, `RefusalHandlerLoggingTest`, `ServiceVersionTest`, `WireTokenParityTest`, `WorktreeGuardTest`, `WorktreeTokenTest` to `service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/`, rewriting packages/imports.
- **Files**: 6 test files.
- **Validation**: `./gradlew :packs:test`.

### Subtask T019 — Downstream rewrites + de-registration
- **Steps**:
  1. Rewrite the composition root's imports of `charter.rest.*` to `packs.infra.api.*`.
  2. Remove the `adapter-in-rest` module directory once drained; `settings.gradle.kts` de-registration is WP07's — do not edit it here.
  3. Verify no user-facing string (REST path, operationId, HATEOAS relation, error body) embeds the old `rest` package name. `verifyAll`.
- **Files**: `service/adapter-in-rest/**` (removed), composition-root import edits.
- **Validation**: `./gradlew verifyAll`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP05; precedes WP07.

## Definition of Done

- The web adapter lives in `packs.infra.api` with its contract unchanged; 6 tests moved.
- `./gradlew verifyAll` green; no user-facing string names the old package.

## Risks

- **Contract drift** → the inbound surface keeps its paths/operationIds/relations exactly; the existing web tests are the control.
- **Transport leak** → `TRANSPORT_STAYS_IN_ADAPTER` (WP07) must cover `packs.infra.api` + `charter-api`.

## Reviewer guidance

- Confirm move-only diffs; no behavioural edit to controllers/handlers.
- Confirm the inbound HTTP contract is byte-identical.
