---
work_package_id: WP10
title: Contract + deployable wiring consolidation + production ComponentResolver
dependencies:
- WP05
- WP06
- WP07
- WP08
- WP09
requirement_refs:
- NFR-004
- C-001
- C-002
planning_base_branch: feat/regular-artefacts
merge_target_branch: feat/regular-artefacts
branch_strategy: Planning artifacts for this mission were generated on feat/regular-artefacts. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/regular-artefacts unless the human explicitly redirects the landing branch.
subtasks:
- T054
- T055
- T056
- T057
- T058
- T059
phase: Phase 8 - Consolidation
history:
- at: '2026-10-25T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: contracts/charter/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RegularArtefactWiringTest.java
execution_mode: code_change
owned_files:
- contracts/charter/openapi.yaml
- service/packs/src/main/java/ai/speckitty/charter/packs/application/CompositeComponentResolver.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/CompositeComponentResolverTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/RegularArtefactWiringTest.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP10 — Contract + wiring consolidation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and
behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objective

Consolidate the slices into a coherent, contract-described, fully-wired surface (NFR-004, C-001,
C-002). Register every new path in the **root `contracts/charter/openapi.yaml`** (so the `charter-api`
module regenerates the `*Api` interfaces + DTOs the slice controllers implement), provide the
**production `CompositeComponentResolver`** that dispatches a walk's neighbour resolution across all
per-kind read paths (returning empty for unwired kinds, D6), confirm the deployable composition root
wires everything, and prove the whole surface green through the `@contract` Cucumber suite + contract
conformance.

Ground truth: [spec.md](../spec.md) NFR-004 + C-001/C-002 + SC-004; [plan.md](../plan.md) IC-09;
**ADR-0009** (additive-only contract sets), **ADR-0004** (generated DTOs on the wire), **ADR-0025 D4**
(the resolver). This WP is the single owner of the **root `openapi.yaml`**; each slice owns its own
path/schema fragment files.

## Context & Constraints

- **The contract is split** ($ref'd): the root `openapi.yaml` maps each path to a `paths/*.yaml` file;
  schemas/responses/params/headers live in their own fragment dirs. The slices (WP05/07/08/09) have
  authored their fragment files; **your job is to add the `$ref` path entries + tags to the root** so
  the generator (`spring`, `useTags=true`) emits `DirectivesApi`/`StyleguidesApi`/`ParadigmsApi`/
  `ToolguidesApi`/`RelationshipsApi` + their DTOs (`X`/`X1` where a schema is used in both a list and a
  single path). Do NOT edit the slice fragment files (they are slice-owned) — only the root.
- **DTO regeneration is automatic** from the root spec on build (the `charter-api` convention plugin
  `charter.contract-api`); you do not edit `charter-api` sources. After wiring the root, a full build
  regenerates the interfaces and the slice controllers compile against them. If a controller and its
  generated interface disagree on a name/shape, the fix belongs to the slice's fragment or controller
  — coordinate, but the root wiring is yours.
- **Additive only (ADR-0009, never-go-wrong #2)**: add paths/schemas; change **no** existing tactic/
  info operation or response. The `:contracts` build runs vacuum (lint/bundle), oasdiff
  (breaking-change) and the closed-response-objects + release-gate checks — all must pass. Every new
  response object is closed (`additionalProperties: false`).
- **Resolver**: `CompositeComponentResolver` is a `@Service` in `packs.application` implementing WP03's
  `ComponentResolver`. It injects the per-kind read services (`DirectiveReadService`,
  `StyleguideReadService`, `ParadigmReadService`, `ToolguideReadService`, `TacticReadService`) and
  dispatches on `urn.kind()` to the matching read path; for an out-of-scope/unwired/external kind
  (procedure/asset/agent_profile/…) it returns `Optional.empty()` (D6) — never throws. No shared kind
  registry (D5): a `switch` on `NodeKind`/`ArtifactKind` is the right shape here. It is component-
  scanned (no `deployable` `@Bean` needed).
- **Deployable wiring**: controllers (`@RestController implements *Api`) and services (`@Service`) are
  auto-picked by the existing component scans (`ApplicationConfiguration`, `ApiConfiguration`,
  `StorageConfiguration`); `PackCommitStore`/`CompositeComponentResolver`/`PackMutabilityPolicy` are
  `@Service`/`@Component` and auto-wire. You should NOT need a new `@Bean` — verify, and if a plain
  POJO port needs one, raise it rather than silently editing a shared wiring class (prefer making the
  component a stereotype so it auto-scans). Confirm `ProductionWiringTest`/`WiringRoundTripTest` stay
  green and add `RegularArtefactWiringTest`.

## Branch Strategy

- **Planning base / merge target**: `feat/regular-artefacts`. Lane worktree from `lanes.json`. This WP
  depends on all slices (WP05–WP09) + WP06; it runs last.

```bash
spec-kitty agent action implement WP10 --agent claude --mission regular-artefact-read-registration-01M5SC0T
```

Build (consolidation gate): `./gradlew :contracts:check` (lint/bundle/oasdiff/closure/release-gate),
`./gradlew :charter-api:compileJava` (regen), `./gradlew :packs:test` (slice tests), and
`./gradlew :deployable:test` (the `@contract` Cucumber suite + wiring tests). The `@contract` suite is
the mission's acceptance gate — run it in full and report real pass/fail counts (QA_003). This is the
contract-conformance acceptance run, not a heavy architectural/e2e/performance sweep
(NO_FULL_HEAVY_SUITES_IN_MISSION is satisfied — do not add a `tests/architectural` full sweep).

## Subtasks & Detailed Guidance

### T054 — Root `openapi.yaml`: register all new paths + tags
- Add `$ref` entries under `paths:` for each slice's path files: `/packs/{packId}/directives`,
  `/packs/{packId}/directives/{directiveId}`, the same for `styleguides`/`paradigms`/`toolguides`, and
  `/packs/{packId}/relationships`. Add the new `tags:` (`Directives`, `Styleguides`, `Paradigms`,
  `Toolguides`, `Relationships`) so `useTags` emits one `*Api` interface per kind.
- Keep the existing tactic/info/service entries untouched (additive, ADR-0009).

### T055 — Regenerate + reconcile DTOs/interfaces
- Run the `charter-api` generation; confirm `DirectivesApi`/`StyleguidesApi`/`ParadigmsApi`/
  `ToolguidesApi`/`RelationshipsApi` + DTOs (`Directive`/`Directive1`, registration DTOs,
  `RelationshipRegistration`, …) are emitted and the slice controllers compile against them.
- If a mismatch surfaces, note it for the owning slice; your change stays in the root. Confirm no
  existing DTO (Tactic, Problem, ServiceInfo) changed shape (oasdiff clean).

### T056 — `CompositeComponentResolver` (+ test)
- Implement the production `ComponentResolver` dispatching on `urn.kind()` to each read service;
  empty for unwired kinds (D6). `CompositeComponentResolverTest`: resolves a directive/styleguide/
  paradigm/toolguide/tactic URN via the matching read path; returns empty for `procedure:`/`asset:`/
  `agent_profile:` (the walk continues); never throws on an unknown kind.

### T057 — Deployable wiring verification (+ `RegularArtefactWiringTest`)
- Confirm the full application context loads with every new controller/service/resolver bean (run
  `ProductionWiringTest`/`WiringRoundTripTest`). Add `RegularArtefactWiringTest` (`@SpringBootTest`,
  loopback or MockMvc) asserting: a configured pack serves `GET /api/v1/packs/{id}/directives` (and
  styleguides/paradigms/toolguides) with the fingerprint header; a registration `PUT` round-trips;
  the `CompositeComponentResolver` bean is present and dispatches. Keep it targeted (not a full
  architectural sweep).

### T058 — Full `@contract` conformance green across all kinds
- Run the `CharterContractSuite` (`:deployable:test`): all `@contract` features — the new
  `directives`/`styleguides`/`paradigms`/`toolguides` scenarios + the existing ones — pass, and
  `ContractConformance` validates every exchange against the (now-extended) contract. Confirm the
  governance (WP06) over-level refusal scenario passes with the real `PackMutabilityPolicy` wired.
- Run `:contracts:check` (additive/oasdiff/closed-objects/release-gate). Fix any finding at the
  root/wiring level; defects in a slice fragment are the slice's to fix (coordinate).

### T059 — Living-docs sync + final verification (NFR-004, C-001, C-002, SC-004)
- DIRECTIVE_037: update [quickstart.md](../quickstart.md) if the realised shapes drifted; confirm the
  ADR cross-references (0025/0026/0027/0012) still hold; note the pre-merge adversarial squad is owed
  (plan Charter Check). Final verification: every read/registration operation and every refusal is
  contract-described (NFR-004); **no DELETE/PATCH** is offered on any kind (C-001); **no storage DSO**
  appears on the wire (C-002); all four in-scope kinds are readable + registerable through the
  published contract (SC-004).

## Test strategy

The `@contract` Cucumber suite + `:contracts:check` are the mission acceptance + interoperability
gates (NFR-004). Report enumerated pass/fail counts, never a summary line (QA_003). The resolver test
carries the D6 unwired-kind control. Do not run heavy architectural/e2e/performance suites
(NO_FULL_HEAVY_SUITES_IN_MISSION) — the targeted module tests + the contract suite + the named wiring
tests are the local gate; CI owns the cross-cutting matrix.

## Definition of Done

- [ ] Root `openapi.yaml` references every new path with its tag; `charter-api` regenerates all `*Api`
  + DTOs; every slice controller compiles against them; existing operations unchanged (ADR-0009).
- [ ] `CompositeComponentResolver` dispatches across all read paths, empty for unwired kinds (D6).
- [ ] Full application context wires; `RegularArtefactWiringTest` + `ProductionWiringTest`/
  `WiringRoundTripTest` green.
- [ ] `@contract` suite green across all kinds incl. the governance scenario; `:contracts:check`
  (oasdiff/closed-objects/release-gate) passes; NFR-004/C-001/C-002/SC-004 verified; quickstart/ADRs
  synced; real pass/fail counts recorded.

## Risks

- **Breaking change leaks** (never-go-wrong #2): an accidental edit to an existing operation/response
  fails oasdiff — keep additions strictly additive.
- **Open response object**: a non-closed new schema fails the closed-response-objects check — slices
  must author closed objects; verify.
- **Resolver over-coupling**: keep it a simple kind-dispatch (no registry, D5); do not reach into
  storage — it composes read services only.
- **Compile-order**: the slice controllers only compile once the root is wired — expect first-build
  failures to resolve after T054/T055; a residual failure is a slice fragment/controller mismatch to
  route back, not a reason to edit slice-owned files here.

## Reviewer guidance

Confirm the root additions are additive (oasdiff clean) and reference the slice fragments; the resolver
dispatches across all kinds with the D6 empty-on-unwired control; the full context wires and the
`@contract` suite is green across every kind (with enumerated counts); no DELETE/PATCH and no DSO on
the wire (C-001/C-002); and that only the root `openapi.yaml` (not slice fragments) was edited here.
