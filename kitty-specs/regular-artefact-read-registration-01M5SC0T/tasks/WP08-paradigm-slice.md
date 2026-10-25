---
work_package_id: WP08
title: Paradigm slice — read + functional registration
dependencies:
- WP05
requirement_refs:
- FR-014
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-regular-artefact-read-registration-01M5SC0T
base_commit: 9dab6b9786a8a45faf69c6c989fad13eff24811d
created_at: '2026-10-25T16:38:39.832031+00:00'
subtasks:
- T044
- T045
- T046
- T047
- T048
phase: Phase 7 - Replicated slices
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ParadigmController.java
- contracts/charter/paths/packs_packId_paradigms.yaml
- contracts/charter/paths/packs_packId_paradigms_paradigmId.yaml
- contracts/charter/schemas/Paradigm.yaml
- contracts/charter/schemas/ParadigmReference.yaml
- contracts/charter/schemas/ParadigmRegistration.yaml
- contracts/charter/responses/ParadigmNotFound.yaml
- contracts/charter/scenarios/paradigms.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmWalkSliceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ParadigmControllerTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ParadigmRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/ParadigmController.java
- contracts/charter/paths/packs_packId_paradigms.yaml
- contracts/charter/paths/packs_packId_paradigms_paradigmId.yaml
- contracts/charter/schemas/Paradigm.yaml
- contracts/charter/schemas/ParadigmReference.yaml
- contracts/charter/schemas/ParadigmRegistration.yaml
- contracts/charter/responses/ParadigmNotFound.yaml
- contracts/charter/scenarios/paradigms.feature
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ParadigmWalkSliceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/ParadigmControllerTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP08 — Paradigm slice

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Bring **paradigm** to read + functional-registration parity (FR-014) by copying the directive
reference slice (WP05), kept independent (D5). A paradigm DOES originate relationships — its
`directiveRefs` become `requires` relationships to directives (WP03) and its typed references become
`suggests` — so paradigm has a **walk** half (US2: read a paradigm and walk its relationships to its
directives, resolved on demand).

Ground truth: [spec.md](../spec.md) US2 + FR-014 + SC-001/002/003/004; [data-model.md](../data-model.md)
Paradigm row; **WP05 is the template**. Domain (`Paradigm`, `ParadigmIdentifier`, `ParadigmReference`,
`ParadigmDso`, `ParadigmRepository`) already exists; add read service, registration service,
controller, fragments, scenarios, tests only.

## Context & Constraints

- Copy WP05's services/controller, substituting `Paradigm*` types. Same write discipline: load →
  mutate → revalidate (rebuild `Pack`) → atomic commit (`PackCommitStore`), `WritePolicyGate.authorize`
  before mutating, `PackRevision` ETag, 409/422 refusals with a valid control, 412/428 plain `Problem`.
  Do NOT edit domain/DSO/`DomainRepositoryService`/`Pack`/commit-store/storage-seam files.
- **Body-carried relationships**: a paradigm registration may carry relationships (its directive refs
  and typed refs); persist them to the shard via `RelationshipShard` (WP02), converging with the
  standalone relationship endpoint on the unordered pair. Read-back is value-equal (NFR-003, SC-001).
- **Walk (US2, SC-002)**: provide a `ParadigmWalkSliceTest` using WP03's `GraphWalk` + a
  `ComponentResolver` that resolves `directive:` URNs via the directive read path (WP05) and `paradigm:`
  via this slice, empty for unwired kinds (D6). Walking from a paradigm resolves its referenced
  directives on demand and reads strictly fewer than the pack total.
- Controller implements generated `ParadigmsApi` (tag `Paradigms`); `Paradigm`/`Paradigm1` DTOs.
  Closed schemas, camelCase, additive; never expose the DSO; no DELETE/PATCH. Reuse WP05's
  `RelationshipRegistration.yaml` by `$ref`. **Root wiring + DTO regen are WP10.**

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP08 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted: `./gradlew :packs:test --tests '*ParadigmReadServiceTest' --tests '*ParadigmRegistrationServiceTest' --tests '*ParadigmWalkSliceTest' --tests '*ParadigmControllerTest'`. No full heavy suites.

## Subtasks & Detailed Guidance

### T044 — `paradigms.feature` scenarios (author first)
- `@contract` feature: register a paradigm referencing directives (PUT) then GET it back value-equal
  (SC-001); read + walk → referenced directives resolved on demand (US2/SC-002); duplicate-id → 409;
  malformed → 422 with the pack unchanged and a valid control; stale `If-Match` → 412. Reuse the
  shared steps + WP05's `RegistrationSteps`.

### T045 — `ParadigmReadService` (+ test)
- Copy `DirectiveReadService`: `paradigmsOf` + `paradigm`, `ConsistentRead`-wrapped, not-found/stale
  refusals, relationships populated (the paradigm's directive/typed refs). Test mirrors the directive
  read test, asserting the `requires`→directive relationships are populated.

### T046 — `ParadigmRegistrationService` (+ test)
- Copy `DirectiveRegistrationService`: body-carried relationships merged to the shard; gate call;
  rebuild + atomic commit; ETag. Test: register-with-refs → read-back value-equal (SC-001);
  duplicate-id refused + valid control; malformed/unknown-endpoint-kind → 422, pack unchanged,
  well-formed succeeds (SC-003).

### T047 — `ParadigmController` (+ MockMvc test) + `ParadigmWalkSliceTest`
- Controller: copy `DirectiveController`, implements `ParadigmsApi`, `Paradigm`/`Paradigm1` mapping.
- `ParadigmWalkSliceTest`: walk from a registered paradigm resolves its referenced directives on
  demand (SC-002), reads fewer than the pack total, continues past an unwired target (D6).

### T048 — Paradigm contract fragments
- `paths/packs_packId_paradigms.yaml` (GET+POST), `paths/packs_packId_paradigms_paradigmId.yaml`
  (GET+PUT), `schemas/Paradigm.yaml` (+ `ParadigmReference.yaml`), `schemas/ParadigmRegistration.yaml`
  (fields + `relationships` array via `$ref` to WP05's `RelationshipRegistration.yaml`),
  `responses/ParadigmNotFound.yaml`. Tag `Paradigms`. Closed objects, additive. Do NOT edit the root.

## Test strategy

Test-first SbE; same-fixture controls on refusals; walk test proves on-demand bounded resolution
(SC-002). Targeted `:packs:test` (NO_FULL_HEAVY_SUITES_IN_MISSION). `@contract` suite in WP10.

## Definition of Done

- [ ] Paradigm read (all/by-id) returns value-equal paradigms with relationships populated (FR-014,
  SC-001); registration revalidates + commits atomically with zero-change-on-refusal + valid control.
- [ ] Walk from a paradigm resolves referenced directives on demand, reads fewer than the pack total
  (SC-002), continues past unwired kinds (D6).
- [ ] Controller implements `ParadigmsApi`, no DELETE/PATCH, no DSO on wire. Fragments authored (not
  the root). Red+green hashes recorded; targeted tests green; no WP03/WP04-owned file edited.

## Risks

- **Relationship convergence**: paradigm's body refs and the standalone endpoint must converge on the
  shard pair — reuse the WP02 upsert, don't re-implement.
- **Unbounded walk**: rely on WP03's cycle guard; the walk test must assert bounded reads.

## Reviewer guidance

Confirm value-equal round-trip with relationships populated, the on-demand bounded walk (SC-002),
write discipline parity with WP05, closed additive schemas, no DELETE/PATCH or DSO on wire, and that
the root `openapi.yaml` was not edited.
