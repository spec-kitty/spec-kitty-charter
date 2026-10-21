---
work_package_id: WP06
title: Inbound API + OpenAPI contract change
dependencies:
- WP02
- WP05
requirement_refs:
- FR-001
- FR-002
- FR-005
- C-004
planning_base_branch: feat/pack-repositories-validation
merge_target_branch: feat/pack-repositories-validation
branch_strategy: Planning artifacts for this mission were generated on feat/pack-repositories-validation. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-repositories-validation unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-application-edge-read-path-01M5FQXC
base_commit: 87debc5214f6610efaae92f985e8f01c990dfe2a
created_at: '2026-10-21T20:04:05.214628+00:00'
subtasks:
- T029
- T030
- T031
- T032
- T033
- T034
history:
- date: '2026-10-21'
  note: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/config/ApiConfiguration.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/config/package-info.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/TacticController.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/TacticControllerTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/**
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/**
- contracts/charter/**
role: implementer
tags: []
tracker_refs: []
---

# Work Package Prompt: WP06 – Inbound API + OpenAPI contract change

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter **before anything else**.

- **Profile**: `java-jenny` · **Role**: `implementer` · **Agent/tool**: `claude`

If the skill is unavailable, run `spec-kitty agent profile list` and adopt the closest match.

---

## Objective

Expose the tactic read path over HTTP and change the inbound contract: add the two tactic operations and **remove** pack-listing + action-context, regenerate the API, and delete the controllers whose surface is retired. IC-04. The canonical contract is `contracts/charter/openapi.yaml` (split-file style, `_shared/` components). See [contracts/openapi-delta.md](../contracts/openapi-delta.md) and `tactics.yaml`.

## Context
- Controllers today: `InfoController` (keep), `PacksController` (GET `/packs`, POST `/packs/refresh` — remove), `ContextController` (GET `/mission-types/{…}/context` — remove), problem handlers (keep).
- Generated API interfaces live under the generated `ai.speckitty.charter.api.*` package (regenerated from the OpenAPI). Transport types stay in the inbound adapter (gate `TRANSPORT_STAYS_IN_ADAPTER`, `GENERATED_API_NOT_IN_DOMAIN`).

### T029 — `ApiConfiguration`
- New `...infra/api/config/ApiConfiguration.java` (`@Configuration`) `@ComponentScan("ai.speckitty.charter.packs.infra.api")`. This replaces the role the `scanBasePackageClasses = {…, ContextController}` entry played; WP02's `DeployableConfiguration` scans this config package.

### T030 — `TacticController` (thin)
- Implement `GET /api/v1/packs/{packId}/tactics` → `200` list, and `GET /api/v1/packs/{packId}/tactics/{tacticId}` → `200` one; `404` (shared `Problem`) when the pack/tactic is absent. The controller is a thin projection over `TacticReadService` — **no business logic**. Map domain `Tactic` → the generated transport `Tactic` schema in the adapter only.

### T031 — OpenAPI change + regenerate
- Edit `contracts/charter/openapi.yaml`: add the two tactic operations (merge `tactics.yaml`, reconciling with the `_shared/` component style + a `Tactic` schema), and **remove** `/packs`, `/packs/refresh`, `/mission-types/{missionType}/actions/{action}/context` and their now-unreferenced response schemas. Regenerate the API interfaces; confirm the generated `ai.speckitty.charter.api.*` matches and the ArchUnit transport rules stay green. Report the exact regeneration command.

### T032 — Delete retired controllers
- Delete `PacksController` and `ContextController`. Keep `InfoController` + problem handlers. `CharterServiceApplication` no longer needs `ContextController` in its scan base (WP02 moved scanning to `ApiConfiguration`).

### T033 — Contract changelog
- Update `contracts/charter/CHANGELOG.md` for the added + removed operations.

### T034 — API tests
- `TacticControllerTest`: `200` with the expected body for a known pack/tactic, `404` for an absent one — non-vacuous (the 404 arm is a real control). Update/remove tests that covered the deleted endpoints. If a BDD contract-conformance suite references the removed operations, update it (note: those suites live under `deployable` test, owned by WP02 — coordinate a small out-of-map edit with a one-line rationale, or flag for WP07).

## Definition of Done
- `:packs:test` api tests green; the tactic routes answer 200/404; the removed operations are gone from `openapi.yaml` and the generated API; contract CHANGELOG updated; transport gates green.

## Reviewer guidance (test economy — DIRECTIVE_041)
- Controller test asserts HTTP contract (status + body shape), not business logic (that's WP05). The 404 arm must be a genuine control. Reject tests re-asserting the deleted endpoints or pinning generated-class internals.
