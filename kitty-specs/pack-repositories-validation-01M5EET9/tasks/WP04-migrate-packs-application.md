---
work_package_id: WP04
title: Migrate charter-read-app → packs.application
dependencies:
- WP03
requirement_refs:
- FR-013
- NFR-002
- SC-005
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-repositories-validation-01M5EET9
base_commit: 71f5ccc4891428d42f0aa39d0d68013259ffa349
created_at: '2026-10-21T13:38:56.295096+00:00'
subtasks:
- T011
- T012
- T013
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/package-info.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/**
- service/packs/src/test/java/ai/speckitty/charter/packs/application/**
- service/charter-read-app/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP04 – Migrate charter-read-app → packs.application

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Relocate the read/application use cases from `ai.speckitty.charter.read` to `ai.speckitty.charter.packs.application`, verbatim, with their tests, keeping `verifyAll` green. Step 3 of the bottom-up migration (IC-02). `charter-read-app` is fully drained and de-registered.

## Context

- Application-ring role (over domain, contract-neutral) and the `ENTRYPOINT_OVER_APPLICATION` rule: [plan.md](../plan.md) IC-01/IC-02 + [data-model.md](../data-model.md) `packs.application`.
- Migration categories: [occurrence_map.yaml](../occurrence_map.yaml).

### Subtask T011 — Move the application module
- **Purpose**: Populate `packs.application`.
- **Steps**:
  1. Move all `ai.speckitty.charter.read` main sources (`ActionContext`, `ActionContextQuery`, `ActionContextWalk`, `ConsistentLoad`, `GuidanceEntry`, `PackListing`, `PackListingQuery`, `PackSummary`, `ServiceInfo`, `ServiceInfoQuery`, `package-info`) to `service/packs/src/main/java/ai/speckitty/charter/packs/application/`.
  2. Rewrite `package` to `ai.speckitty.charter.packs.application`; rewrite imports of domain types to `kernel.domain` / `packs.domain` as appropriate.
- **Files**: the whole `charter-read-app` main tree → `packs/application/`.
- **Validation**: `./gradlew :packs:compileJava`.
- **Edge cases**: keep the `package-info` `@NullMarked`; application depends only on domain (kernel + packs.domain), never on infra.

### Subtask T012 — Move the 4 query tests
- **Steps**: move `ActionContextQueryTest`, `ConsistentLoadTest`, `PackListingQueryTest`, `ServiceInfoQueryTest` to `service/packs/src/test/java/ai/speckitty/charter/packs/application/`, rewriting package + imports; carry any fixtures.
- **Files**: 4 test files.
- **Validation**: `./gradlew :packs:test`.

### Subtask T013 — Downstream rewrites + module de-registration
- **Steps**:
  1. Rewrite consumers (the inbound adapter, the composition root) that import `charter.read.*` to `packs.application.*`.
  2. Remove the `charter-read-app` module directory once drained; its `settings.gradle` de-registration is WP07's (single-writer) — leave `settings.gradle` untouched.
  3. `verifyAll`.
- **Files**: `service/charter-read-app/**` (removed), downstream import edits in their home modules.
- **Validation**: `./gradlew verifyAll`.

## Branch Strategy

- Planning branch `feat/pack-repositories-validation`; final merge target `feat/pack-repositories-validation`.
- Execution worktrees per lane from `lanes.json`. Depends on WP03; precedes WP05.

## Definition of Done

- The application use cases + 4 tests live in `packs.application`.
- `charter-read-app` is drained; `./gradlew verifyAll` green.

## Risks

- **No regression (NFR-002)** → `ConsistentLoad` and the listing/action-context queries must behave identically — move-only, no logic edits.
- **`settings.gradle`** → do not edit it here (WP07 owns the module-set rewrite).

## Reviewer guidance

- Confirm move-only diffs and that application imports no infra package.
- Confirm the untouched read paths are byte-for-byte behaviourally identical.
