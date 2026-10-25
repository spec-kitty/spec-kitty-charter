---
work_package_id: WP09
title: Toolguide slice — read + functional registration (services/controllers)
dependencies:
- WP04
- WP05
requirement_refs:
- FR-015
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regular-artefact-read-registration-01M5SC0T
base_commit: 8e10d7051d0ca5dac50c36bac1bfd82ee5c16433
created_at: '2026-10-25T16:38:47.529436+00:00'
subtasks:
- T049
- T050
- T051
- T052
- T053
phase: Phase 7 - Replicated slices
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ToolguideController.java
- contracts/charter/paths/packs_packId_toolguides.yaml
- contracts/charter/paths/packs_packId_toolguides_toolguideId.yaml
- contracts/charter/schemas/Toolguide.yaml
- contracts/charter/schemas/ToolguideRegistration.yaml
- contracts/charter/responses/ToolguideNotFound.yaml
- contracts/charter/scenarios/toolguides.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideControllerTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ToolguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ToolguideController.java
- contracts/charter/paths/packs_packId_toolguides.yaml
- contracts/charter/paths/packs_packId_toolguides_toolguideId.yaml
- contracts/charter/schemas/Toolguide.yaml
- contracts/charter/schemas/ToolguideRegistration.yaml
- contracts/charter/responses/ToolguideNotFound.yaml
- contracts/charter/scenarios/toolguides.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ToolguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ToolguideControllerTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP09 — Toolguide slice

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Expose the net-new **toolguide** kind through read + functional registration (FR-015, US3). The
`Toolguide` entity + `ToolguideIdentifier` + `ToolguideDso` + `ToolguideRepository` + the `Pack`
toolguides collection + the enumerated storage seam **already exist from WP04** — this WP adds only
the **read service, registration service, controller, contract fragments, scenarios and tests**,
copying the directive reference slice (WP05). Toolguide originates no relationships
(`relationships()` is empty), so there is no walk half.

Ground truth: [spec.md](../spec.md) US3 + FR-015 + SC-001/003/004; [data-model.md](../data-model.md)
Toolguide row; `contracts/packs/schemas/toolguide.schema.json`; **WP05 is the template**.

## Context & Constraints

- Copy WP05's services/controller, substituting `Toolguide*` types. Write discipline identical: load →
  mutate → revalidate (rebuild `Pack`, which now carries the toolguides invariants from WP04) → atomic
  commit (`PackCommitStore`); `WritePolicyGate.authorize` before mutating; `PackRevision` ETag; 409/422
  refusals with a valid control; 412/428 plain `Problem`. Do NOT edit any WP04-owned file (entity, DSO,
  repo, `Pack`, storage seam, commit store) — compose them.
- **US3 duplicate-id invariant (FR-015/SC-003)**: registering a second toolguide with an existing id →
  refused (`409`), pack unchanged; a distinct id succeeds. This exercises WP04's new Pack toolguide
  invariant through the aggregate revalidation.
- Toolguide fields (from `toolguide.schema.json`): `id, schemaVersion, tool, title, guidePath, summary,
  commands[], appliesToLanguages[], lastUpdated?, references[]`. `relationships()` empty → the
  registration body has no originating relationships (the shared relationship endpoint still works for
  standalone edges touching a toolguide).
- Controller implements generated `ToolguidesApi` (tag `Toolguides`); `Toolguide`/`Toolguide1` DTOs.
  Closed schema, camelCase, additive; never expose `ToolguideDso` (C-002); no DELETE/PATCH. **Root
  wiring + DTO regen are WP10.** Reuse WP05's `RelationshipRegistration.yaml` by `$ref` if the body
  offers a relationships array (optional for toolguide).

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP09 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted: `./gradlew :packs:test --tests '*ToolguideReadServiceTest' --tests '*ToolguideRegistrationServiceTest' --tests '*ToolguideControllerTest'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T049 — `toolguides.feature` scenarios (author first)
- `@contract` feature (US3): register a toolguide (PUT) then GET it back value-equal (SC-001);
  register a second toolguide with the **same id** → refused, pack unchanged; a distinct id → `201`
  (FR-015 paired control); malformed body → `422`, pack unchanged, well-formed succeeds; stale
  `If-Match` → `412`. Reuse the shared steps + WP05's `RegistrationSteps`.

### T050 — `ToolguideReadService` (+ test)
- Copy `DirectiveReadService`: `toolguidesOf` + `toolguide`, `ConsistentRead`-wrapped, not-found/stale
  refusals. Test: list + by-id value-equal; `relationships()` empty asserted.

### T051 — `ToolguideRegistrationService` (+ test)
- Copy `DirectiveRegistrationService` (no body-carried relationships). Gate call; rebuild Pack
  (revalidate, exercising the toolguide no-duplicate-id invariant); atomic commit; ETag. Test:
  register→read-back value-equal (SC-001); **same-id second registration refused + distinct id
  succeeds** (FR-015/SC-003 control); malformed → 422, pack byte-for-byte unchanged, well-formed
  succeeds.

### T052 — `ToolguideController` (+ MockMvc test)
- Copy `DirectiveController`: GET list/one + POST/PUT, implements `ToolguidesApi`,
  `Toolguide`/`Toolguide1` mapping, `ETag`/`Location`, no DELETE/PATCH, no DSO on wire. Test mirrors
  the directive controller test.

### T053 — Toolguide contract fragments
- `paths/packs_packId_toolguides.yaml` (GET+POST), `paths/packs_packId_toolguides_toolguideId.yaml`
  (GET+PUT), `schemas/Toolguide.yaml` (closed object mirroring `toolguide.schema.json`, camelCase:
  `schemaVersion`, `guidePath`, `appliesToLanguages`, `lastUpdated`),
  `schemas/ToolguideRegistration.yaml`, `responses/ToolguideNotFound.yaml`. Tag `Toolguides`.
  Additive. Do NOT edit the root.

## Test strategy

Test-first SbE; the FR-015 duplicate-id refusal carries a distinct-id positive control (SC-003).
Targeted `:packs:test` (NO_FULL_HEAVY_SUITES_IN_MISSION). `@contract` suite in WP10.

## Definition of Done

- [ ] Toolguide read (all/by-id) returns value-equal toolguides (FR-015, SC-001); registration
  revalidates the aggregate + commits atomically; a duplicate-id registration is refused with a
  distinct-id control passing (SC-003), pack unchanged.
- [ ] Controller implements `ToolguidesApi`, no DELETE/PATCH, no DSO on wire (C-001/C-002). Fragments
  authored (not the root). Red+green hashes recorded; targeted tests green; no WP04-owned file edited.

## Risks

- **Editing WP04's toolguide domain/seam**: those exist already — only compose them; editing them
  collides with WP04 ownership.
- **Proving US3 against a real aggregate**: the duplicate-id refusal must go through the Pack
  revalidation (not a controller-local check) so it reflects FR-008's aggregate invariant.

## Reviewer guidance

Confirm value-equal round-trip, the duplicate-id refusal via aggregate revalidation with a distinct-id
control, write-discipline parity with WP05, closed additive schema, no DELETE/PATCH or DSO on wire,
and that neither WP04-owned files nor the root `openapi.yaml` were edited.
