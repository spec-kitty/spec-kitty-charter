---
work_package_id: WP05
title: Directive slice — read + functional registration (the reference slice, end-to-end)
dependencies:
- WP04
requirement_refs:
- FR-006
- FR-007
- FR-009
- FR-010
- FR-012
- SC-001
- SC-002
- SC-003
- SC-004
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
subtasks:
- T028
- T029
- T030
- T031
- T032
- T033
- T034
phase: Phase 5 - Reference slice
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/DirectiveController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipController.java
- contracts/charter/paths/packs_packId_directives.yaml
- contracts/charter/paths/packs_packId_directives_directiveId.yaml
- contracts/charter/paths/packs_packId_relationships.yaml
- contracts/charter/schemas/Directive.yaml
- contracts/charter/schemas/DirectiveReference.yaml
- contracts/charter/schemas/DirectiveRegistration.yaml
- contracts/charter/schemas/RelationshipRegistration.yaml
- contracts/charter/responses/DirectiveNotFound.yaml
- contracts/charter/scenarios/directives.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/RegistrationSteps.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveWalkSliceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/DirectiveControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RelationshipControllerTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/DirectiveRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/DirectiveController.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipController.java
- contracts/charter/paths/packs_packId_directives.yaml
- contracts/charter/paths/packs_packId_directives_directiveId.yaml
- contracts/charter/paths/packs_packId_relationships.yaml
- contracts/charter/schemas/Directive.yaml
- contracts/charter/schemas/DirectiveReference.yaml
- contracts/charter/schemas/DirectiveRegistration.yaml
- contracts/charter/schemas/RelationshipRegistration.yaml
- contracts/charter/responses/DirectiveNotFound.yaml
- contracts/charter/scenarios/directives.feature
- service/deployable/src/test/java/ai/speckitty/charter/deployable/bdd/RegistrationSteps.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveReadServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/DirectiveWalkSliceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/DirectiveControllerTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/RelationshipControllerTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 — Directive slice (the reference slice)

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Take **directive** fully through read + functional registration — the end-to-end reference slice
every later kind copies (US1). Read a pack's directives (all / by id) with relationships populated
(FR-006); register a directive (fields + relationships) via POST/PUT with aggregate revalidation and
atomic commit (FR-007, FR-008 via WP04, FR-010); register a standalone relationship edge idempotent on
the unordered pair (FR-009); optimistic concurrency via `If-Match`/`ETag` (FR-012); typed `Problem`
refusals with zero state change on failure (FR-010). Walk a directive's relationships on demand (the
US1 traversal half) using WP03's `GraphWalk` + a `ComponentResolver` backed by this slice.

Ground truth: [quickstart.md](../quickstart.md) (the exact walkthrough), [spec.md](../spec.md) US1 +
FR-006/007/009/010/012 + SC-001/002/003/004 + edge-cases, [data-model.md](../data-model.md)
"Transitions / lifecycle", [contracts/openapi-additions.yaml](../contracts/openapi-additions.yaml)
(illustrative shapes), **ADR-0026** (functional registration), **ADR-0025** (walk). **Study and copy
the Tactic slice**: `TacticReadService`, `TacticController`, `TacticControllerTest`, `TacticReadServiceTest`.

## Context & Constraints

- **The domain + storage for directive already exist** (`Directive`, `DirectiveIdentifier`,
  `DirectiveReference`, `DirectiveDso`, `DirectiveRepository`, `DomainRepositoryService.save/load`).
  WP03 gave `Directive.relationships()`. You add only the **read service, registration service,
  controllers, contract fragments, scenarios, tests** — do NOT edit domain entities, DSOs,
  `DomainRepositoryService`, `Pack`, or the storage seam (WP03/WP04 own those). Compose the existing
  ports.
- **Read (FR-006)** — copy `TacticReadService`: inject `PackRepository`, `DirectiveRepository`,
  `ConsistentRead`. `directivesOf(PackIdentifier)` and `directive(PackIdentifier, DirectiveIdentifier)`
  each wrapped in `read.read(...)` returning `ConsistentRead.Verified<...>`; refuse an absent pack
  (`PACK_NOT_FOUND`) / absent directive (a not-found refusal) exactly like the tactic reads. List
  via the existing ports (e.g. `packs.load(pack).directives()` or `directiveRepository`); do NOT add a
  method to `DomainRepositoryService`. Each returned directive must carry its `relationships()`
  populated (FR-006) — see the relationships-population note below.
- **Registration (FR-007/010, NFR-002)** — `DirectiveRegistrationService` does ADR-0026
  **load → mutate → revalidate → commit**: load the `Pack`, assemble the submitted `Directive`
  (+ its body-carried relationships), rebuild the `Pack` via `Pack.builder()` (which re-runs the
  aggregate invariants = FR-008 revalidation), compute/merge the relationship shard text via WP02's
  `RelationshipShard`, and commit atomically via WP04's `PackCommitStore` (returning a `PackRevision`
  for the `ETag`). POST to the collection = create (duplicate id → `409` conflict); PUT by id =
  idempotent create-or-replace. A malformed body or a relationship naming an unknown component kind →
  `422` validation refusal, **pack unchanged** (NFR-002); the same-fixture well-formed request
  succeeds (FR-010 paired control).
- **Relationship endpoint (FR-009)** — `RelationshipController` serves `PUT /packs/{packId}/relationships`:
  body `from`,`to`,`type`,`rationale`,`when`. Idempotent on the unordered `{from,to}` pair (explicit
  upsert via `RelationshipShard`, last-write-wins — re-registering the reverse replaces). `from` MUST
  resolve within the pack (else `409`/validation refusal); `to` MAY be external (not required to
  resolve). Commit atomically via `PackCommitStore`.
- **Dual-write ownership (IC-05 decision — resolve and document)**: a relationship can arrive inside
  an artifact registration body OR via `PUT /relationships`. Both doors write the SAME pack DRG shard
  keyed by the unordered pair (upsert), so they converge; the body door owns the component's
  originating edges, the relationship door authors standalone edges. Document the chosen rule in the
  registration service javadoc.
- **relationships-population (FR-006) decision**: a read must return each directive with
  `relationships()` populated. Decide and document how the read reconstructs them (the entity's
  authored reference fields give its originating edges via WP03's `relationships()`; standalone
  registered edges live in the shard). Ground the choice in ADR-0025 D4 ("the repository load returns
  the domain object with its relationships() already populated — the storage adapter assembles them
  from the graph shard"). Keep the read value-equal to what was registered (NFR-003, SC-001).
- **Concurrency (FR-012)** — `PUT` accepts `If-Match`; a stale precondition → `412` (plain `Problem`,
  NOT a `RefusalCode`); a sub-resource (relationship) write with no precondition supplied → `428`
  (plain `Problem`); a fresh precondition succeeds. Use WP04's `PackRevision` for the `ETag` on every
  write response and the read `ETag`.
- **Controllers** implement the generated `*Api` interfaces (following `TacticController implements
  TacticsApi`). The tag names you set in the path files determine the interface names — use tag
  `Directives` → `DirectivesApi`, tag `Relationships` → `RelationshipsApi`. DTOs: a `Directive.yaml`
  referenced by both the list and single path yields generated `Directive` + `Directive1` (map both,
  like `TacticController.toDto`/`toSingleDto`). **Never expose a storage DSO on the wire** (C-002);
  bodies are the generated contract types only. **No DELETE/PATCH** (C-001).
- **Sequencing / contract wiring**: you author the path/schema/response **fragment files** under
  `contracts/charter/{paths,schemas,responses}/` (slice-owned). The **root `contracts/charter/openapi.yaml`
  `$ref` registration and the charter-api DTO regeneration are owned by WP10** and land last. So in
  this slice your application-layer tests (read/registration/walk) run green standalone; the
  controller MockMvc test and the full `@contract` Cucumber suite go green once WP10 wires the root.
  Author the controllers + fragments to the agreed generated names above; keep the fragment shapes
  additive and closed (`additionalProperties: false`, camelCase fields, matching the Tactic schema
  conventions).

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`.

```bash
spec-kitty agent action implement WP05 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Targeted build: `./gradlew :packs:test --tests '*DirectiveReadServiceTest' --tests '*DirectiveRegistrationServiceTest' --tests '*DirectiveWalkSliceTest' --tests '*DirectiveControllerTest' --tests '*RelationshipControllerTest'`. The `@contract` Cucumber suite (`:deployable:test`) is run green in WP10. No full heavy suites.

## Subtasks & Detailed Guidance

### T028 — BDD scenarios first (`directives.feature`) `[author first]`
- Write `contracts/charter/scenarios/directives.feature`, `@contract`-tagged, mirroring the US1
  acceptance scenarios + quickstart: (1) register a directive with a relationship (PUT) then GET it
  back value-equal, relationships included; (2) walk from the directive resolves related in-scope
  components on demand and does not load unreachable ones; (3) register a relationship for a pair, then
  re-register the same pair → only one exists (last-write-wins); (4) a malformed body / unknown
  relationship endpoint kind → `422`, pack unchanged, and a well-formed request on the same pack
  succeeds; (5) duplicate-id POST → `409`, distinct id → `201`; (6) stale `If-Match` → `412`, fresh
  succeeds; (7) relationship PUT with no precondition → `428`. Use the existing Gherkin step vocabulary
  (`Given the {word} pack {string} with the file {string}:`, `When I GET/PUT/POST ...`, `Then the
  response status is ...`, header/body/problem-code steps). These scenarios are authored before the
  code (specification-by-example, DIRECTIVE_034).

### T029 — `DirectiveReadService` (copy `TacticReadService`)
- Inject `PackRepository`, `DirectiveRepository`, `ConsistentRead`. Implement `directivesOf` + `directive`
  returning `ConsistentRead.Verified<...>`, each inside `read.read(...)`. Map pack/not-found refusals
  exactly as `TacticReadService.packRefusal`. Ensure each directive carries `relationships()`
  populated (see the FR-006 note). Point reads; do not assemble more than needed.

### T030 — `DirectiveRegistrationService` (load→mutate→revalidate→commit)
- Implement the write use case per ADR-0026 (above). POST semantics (create; duplicate id → conflict)
  and PUT semantics (idempotent create-or-replace). Assemble the `Directive` from the registration DTO
  via the existing domain builder. Before mutating, call WP04's
  `WritePolicyGate.authorize(pack, operation, targetUrn)` (operation `REGISTER`/`REPLACE`; the
  `RelationshipController` uses `RELATIONSHIP`): the `PermissiveWritePolicyGate` default allows
  everything today, and WP06 supplies the real ADR-0012 policy that refuses an over-level write with
  `governance_policy_violation` — wiring the call here is what lets WP06 land without editing this
  service. Then rebuild the `Pack` (revalidation = FR-008); merge body-carried
  relationships into the shard via `RelationshipShard`; commit via `PackCommitStore`; return the new
  `PackRevision`. Translate domain `ConstraintBreachException`/`CharterRefusal` into the validation
  (`422`) / conflict (`409`) refusal codes (WP04). Guarantee zero state change on refusal (NFR-002 —
  the commit store enforces it; the service must refuse before committing).

### T031 — `DirectiveController` (GET + POST/PUT) implements `DirectivesApi`
- GET list + GET one: copy `TacticController` (fingerprint header via `CharterHeaders`, DTO mapping
  `Directive`/`Directive1`). POST: register, return `201` + `Location` + `ETag`. PUT: register-or-
  replace, `200`/`201` + `ETag`, accept `If-Match` (stale → `412` plain `Problem`). Map domain →
  generated DTO field-by-field; never expose `DirectiveDso`. No DELETE/PATCH.

### T032 — `RelationshipController` (PUT /packs/{packId}/relationships) implements `RelationshipsApi`
- Serve the idempotent-on-pair relationship upsert (FR-009): validate the `RelationshipRegistration`
  DTO, enforce `from` resolves in the pack (`409`/validation) and `to` may be external, upsert via
  `RelationshipShard`, commit via `PackCommitStore`, return `200` + `ETag`. No precondition supplied →
  `428` (plain `Problem`); stale `If-Match` → `412`.

### T033 — Directive + relationship contract fragment files
- Author the slice's OpenAPI fragments matching the split-contract convention (see
  `contracts/charter/paths/packs_packId_tactics.yaml` and `schemas/Tactic.yaml` as templates):
  `paths/packs_packId_directives.yaml` (GET list + POST), `paths/packs_packId_directives_directiveId.yaml`
  (GET one + PUT), `paths/packs_packId_relationships.yaml` (PUT); `schemas/Directive.yaml` (closed
  response object, camelCase, `x-source` optional), `schemas/DirectiveReference.yaml`,
  `schemas/DirectiveRegistration.yaml` (fields + `relationships` array), `schemas/RelationshipRegistration.yaml`
  (`from`,`to`,`type`,`rationale`,`when`; `required:[from,to,type]`), `responses/DirectiveNotFound.yaml`.
  Set tags `Directives` and `Relationships`. Reference shared `../../_shared/responses/Problem.yaml`
  for `default`, and `409`/`412`/`422`/`428` → `Problem`. **Do not edit the root `openapi.yaml`** (WP10).
  `RelationshipRegistration.yaml` is shared and reused (by $ref) by WP07/08/09 — author it once here.

### T034 — Tests (test-first, red before green)
- `DirectiveReadServiceTest` (copy `TacticReadServiceTest`): list + by-id reads; relationships
  populated; pack/not-found refusals; stale-input.
- `DirectiveRegistrationServiceTest`: register with relationships → read-back value-equal (SC-001);
  duplicate-id POST refused + distinct id succeeds on the same fixture (FR-008 control); malformed /
  unknown-endpoint-kind → `422`, **pack byte-for-byte unchanged**, well-formed succeeds (FR-010/SC-003
  control); relationship upsert idempotent on the pair + reverse replaces (FR-009); over-stale
  precondition rejected, fresh accepted (FR-012).
- `DirectiveWalkSliceTest` (US1 traversal): build a `ComponentResolver` backed by `DirectiveReadService`
  (resolves `directive:` URNs; empty for other kinds, D6) and WP03's `GraphWalk`; walking from a
  registered directive returns its in-scope related components by on-demand resolution and reads
  strictly fewer than the pack's total when the sub-graph is small (SC-002); a `procedure:` target
  resolves empty and the walk continues.
- `DirectiveControllerTest` + `RelationshipControllerTest` (MockMvc; copy `TacticControllerTest`):
  status codes, headers (`ETag`, `Location`), DTO mapping, refusal bodies. (These go green once WP10
  wires the generated interfaces; author them now.)

## Test strategy

Specification-by-example first (the feature file), then application-layer TDD (read/registration/walk
fully testable in-lane), then the controller MockMvc tests. Every refusal scenario carries a
same-fixture positive control (SC-003, DIRECTIVE_041). The `@contract` conformance suite is WP10's
consolidation gate. Targeted `:packs:test` only here (NO_FULL_HEAVY_SUITES_IN_MISSION; QA_003 — report
real pass/fail counts).

## Definition of Done

- [ ] Directive read (all/by-id) returns relationships-populated directives, value-equal to what was
  registered (FR-006, SC-001).
- [ ] Registration (POST/PUT) revalidates the aggregate and commits atomically; refusals leave the
  pack unchanged with a well-formed control passing (FR-007/010, NFR-002, SC-003).
- [ ] Relationship PUT is idempotent on the unordered pair (reverse replaces), `from` resolves in
  pack, `to` may be external (FR-009).
- [ ] `If-Match`/`ETag` optimistic concurrency: stale → 412, missing on sub-resource → 428, fresh
  succeeds (FR-012). No DELETE/PATCH, no storage DSO on the wire (C-001/C-002).
- [ ] Walk from a directive resolves on demand, reads fewer than the pack total (SC-002), continues
  past unwired kinds (D6).
- [ ] Contract fragments authored (not the root — WP10); scenarios authored first; application-layer
  tests green with red+green hashes recorded.

## Risks

- **Partial write on refusal**: register must refuse BEFORE committing; rely on `PackCommitStore`'s
  stage+swap and prove zero-change in the test.
- **Dual-write divergence**: body-carried vs. endpoint-carried relationships must converge on the same
  shard pair — document and test the upsert convergence.
- **Exposing a DSO**: map domain→generated DTO; never return a `*Dso` (C-002).
- **Contract-wiring sequencing**: controller/BDD green depends on WP10 wiring the root; do not edit
  the root here (ownership). State the dependency in your handoff.

## Reviewer guidance

Confirm the read is value-equal with relationships populated; registration is load→mutate→revalidate→
atomic-commit with zero-change-on-refusal and a same-fixture positive control; the relationship upsert
is last-write-wins on the unordered pair; 412/428 are plain `Problem` (not `RefusalCode`s); no
DELETE/PATCH and no DSO on the wire; the walk is on-demand and bounded; and the root `openapi.yaml` was
NOT edited here.
