---
work_package_id: WP07
title: Styleguide slice — read + functional registration
dependencies:
- WP05
requirement_refs:
- FR-013
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regular-artefact-read-registration-01M5SC0T
base_commit: fe4e9158d5966e010332e223a97002047171ffea
created_at: '2026-10-25T16:38:32.282187+00:00'
subtasks:
- T039
- T040
- T041
- T042
- T043
phase: Phase 7 - Replicated slices
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/StyleguideController.java
- contracts/charter/paths/packs_packId_styleguides.yaml
- contracts/charter/paths/packs_packId_styleguides_styleguideId.yaml
- contracts/charter/schemas/Styleguide.yaml
- contracts/charter/schemas/StyleguidePattern.yaml
- contracts/charter/schemas/StyleguideAntiPattern.yaml
- contracts/charter/schemas/StyleguideRegistration.yaml
- contracts/charter/responses/StyleguideNotFound.yaml
- contracts/charter/scenarios/styleguides.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StyleguideControllerTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/StyleguideRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/StyleguideController.java
- contracts/charter/paths/packs_packId_styleguides.yaml
- contracts/charter/paths/packs_packId_styleguides_styleguideId.yaml
- contracts/charter/schemas/Styleguide.yaml
- contracts/charter/schemas/StyleguidePattern.yaml
- contracts/charter/schemas/StyleguideAntiPattern.yaml
- contracts/charter/schemas/StyleguideRegistration.yaml
- contracts/charter/responses/StyleguideNotFound.yaml
- contracts/charter/scenarios/styleguides.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/StyleguideRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/StyleguideControllerTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP07 — Styleguide slice

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Bring **styleguide** to read + functional-registration parity (FR-013) by **copying the directive
reference slice** (WP05), kept independent — no shared kind registry (D5). Styleguide has no
originating relationships (`relationships()` is empty — bare citations mint no edges), so there is no
walk half and no body-carried relationships; reuse the shared `RelationshipRegistration` /
`RelationshipController` from WP05 only for the standalone relationship endpoint (unchanged, not
re-authored here).

Ground truth: [spec.md](../spec.md) US2 + FR-013 + SC-001/003/004; [data-model.md](../data-model.md)
Styleguide row; **the WP05 directive slice is the template** — copy its structure, substituting
styleguide types. The domain (`Styleguide`, `StyleguideIdentifier`, `StyleguideDso`,
`StyleguideRepository`) already exists; you add the read service, registration service, controller,
contract fragments, scenarios and tests only.

## Context & Constraints

- Copy `WP05`'s `DirectiveReadService` / `DirectiveRegistrationService` / `DirectiveController` and the
  patterns in [WP05-directive-slice.md](./WP05-directive-slice.md); substitute `Styleguide*` types.
  Do NOT edit domain entities/DSOs/`DomainRepositoryService`/`Pack`/the commit store/the storage seam.
- Registration goes through load → mutate → revalidate → atomic commit (`PackCommitStore`) exactly as
  WP05; call `WritePolicyGate.authorize(pack, REGISTER|REPLACE, target)` before mutating (WP04 port;
  WP06 policy). Duplicate-id POST → `409`; malformed → `422`, pack unchanged; well-formed control
  succeeds. `If-Match`/`ETag` as WP05 (412/428 plain `Problem`).
- **Styleguide response schema is structured**: it carries principles, optional patterns/anti-patterns,
  a tooling map, an optional quality test, an optional `structuralLintConfig` config tree, applies-to-
  languages and bare references. Author the needed nested schema fragments (`StyleguidePattern.yaml`,
  `StyleguideAntiPattern.yaml`, and any further nested fragment the response needs, e.g. a config-value
  shape) as closed objects (`additionalProperties: false`), slice-owned. Never expose the storage DSO
  (C-002).
- Controller implements the generated `StyleguidesApi` (tag `Styleguides`); the `Styleguide.yaml`
  schema used in both list + single paths yields `Styleguide` + `Styleguide1` DTOs — map both.
- **Root `openapi.yaml` wiring + DTO regeneration are WP10.** Author fragments + controller + scenarios
  here; application-layer tests run green in-lane; the controller MockMvc + `@contract` suite go green
  when WP10 wires the root. Reuse WP05's `RelationshipRegistration.yaml` by `$ref` (do not re-create).

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP07 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted: `./gradlew :packs:test --tests '*StyleguideReadServiceTest' --tests '*StyleguideRegistrationServiceTest' --tests '*StyleguideControllerTest'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T039 — `styleguides.feature` scenarios (author first)
- `@contract` feature: register a styleguide (PUT) then GET it back value-equal (SC-001); duplicate-id
  POST → `409`, distinct id → `201`; malformed body → `422`, pack unchanged, well-formed succeeds;
  stale `If-Match` → `412`, fresh succeeds. Reuse the shared Gherkin step vocabulary + WP05's
  `RegistrationSteps`.

### T040 — `StyleguideReadService` (+ test)
- Copy `DirectiveReadService`: `styleguidesOf` + `styleguide`, `ConsistentRead`-wrapped, not-found/
  stale refusals. `StyleguideReadServiceTest` mirrors `DirectiveReadServiceTest` (relationships are
  empty for styleguide — assert that explicitly).

### T041 — `StyleguideRegistrationService` (+ test)
- Copy `DirectiveRegistrationService` (no body-carried relationships for styleguide). Gate call,
  rebuild Pack (revalidate), atomic commit, `PackRevision` ETag. Test: register→read-back value-equal
  (SC-001); duplicate-id refused with a valid control (SC-003); malformed → 422, pack byte-for-byte
  unchanged, well-formed succeeds.

### T042 — `StyleguideController` (+ MockMvc test)
- Copy `DirectiveController`: GET list/one + POST/PUT, implements `StyleguidesApi`, DTO mapping
  `Styleguide`/`Styleguide1`, `ETag`/`Location`, no DELETE/PATCH, no DSO on wire. `StyleguideControllerTest`
  mirrors the directive controller test.

### T043 — Styleguide contract fragments
- Author `paths/packs_packId_styleguides.yaml` (GET+POST), `paths/packs_packId_styleguides_styleguideId.yaml`
  (GET+PUT), `schemas/Styleguide.yaml` (+ nested `StyleguidePattern.yaml`, `StyleguideAntiPattern.yaml`,
  further nested fragments as needed), `schemas/StyleguideRegistration.yaml`,
  `responses/StyleguideNotFound.yaml`. Tag `Styleguides`. Closed objects, camelCase, additive.
  Reference `../../_shared/responses/Problem.yaml` for refusals and `$ref` WP05's
  `RelationshipRegistration.yaml` where a relationships array is offered. Do NOT edit the root.

## Test strategy

Test-first SbE; same-fixture positive controls on refusals (SC-003). Targeted `:packs:test`
(NO_FULL_HEAVY_SUITES_IN_MISSION). `@contract` suite in WP10.

## Definition of Done

- [ ] Styleguide read (all/by-id) returns value-equal styleguides with empty relationships (FR-013,
  SC-001); registration revalidates + commits atomically with zero-change-on-refusal + valid control.
- [ ] Controller implements `StyleguidesApi`, no DELETE/PATCH, no DSO on wire (C-001/C-002).
- [ ] Fragments authored (not the root — WP10); scenarios first; red+green hashes recorded; targeted
  tests green. No WP03/WP04-owned file edited.

## Risks

- **Divergence from the pattern**: keep the slice a faithful copy; independent evolution is accepted
  (D5) but the write discipline (revalidate + atomic + gate) must match WP05.
- **Structured schema**: styleguide's nested fields need closed nested schema fragments; an open
  object fails the closed-response-objects check (WP10).

## Reviewer guidance

Confirm parity with WP05's write discipline, value-equal round-trip, empty relationships, closed
nested schemas, no DELETE/PATCH or DSO on the wire, and that the root `openapi.yaml` was not edited.
