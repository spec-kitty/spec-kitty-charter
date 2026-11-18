---
work_package_id: WP05
title: Catalog domain, binding and content rules
dependencies:
- WP01
- WP02
- WP03
requirement_refs:
- FR-001
- FR-002
- FR-005
- C-003
planning_base_branch: feat/108-model-task-routing
merge_target_branch: feat/108-model-task-routing
branch_strategy: Planning artifacts for this mission were generated on feat/108-model-task-routing. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/108-model-task-routing unless the human explicitly redirects the landing branch.
subtasks:
- T016
- T017
- T018
- T019
phase: Phase 2 - The kind
history:
- at: '2026-11-18T08:55:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/routing/src/main/java/ai/speckitty/charter/routing/domain/
create_intent:
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingCatalog.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TaskType.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ModelEntry.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TaskFit.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ModelCost.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingPolicy.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingObjective.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingWeights.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TierConstraint.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/CatalogSource.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RoutingCatalogRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RoutingCatalogFacts.java
execution_mode: code_change
owned_files:
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingCatalog.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TaskType.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ModelEntry.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TaskFit.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/ModelCost.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingPolicy.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingObjective.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/RoutingWeights.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/TierConstraint.java
- service/routing/src/main/java/ai/speckitty/charter/routing/domain/CatalogSource.java
- service/routing/src/test/java/ai/speckitty/charter/routing/domain/*Test.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogDso.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RoutingCatalogYaml.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/KindBindings.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/YamlFormats.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/RoutingCatalogRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RoutingCatalogFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/PresetMapper.java
- service/packs/build.gradle.kts
- service/packs/src/test/java/ai/speckitty/charter/packs/**/RoutingCatalog*Test.java
- service/packs/src/test/resources/routing-catalog-fixtures/**
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP05 – Catalog domain, binding and content rules

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according to
its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## ⚠️ Review Feedback

Check the `review_ref` field in the event log (`spec-kitty agent tasks status`). Address every feedback item, and record
each change with `spec-kitty agent tasks add-history WP05 --note "..."`.

## Branch Strategy

- **Planning base branch**: `feat/108-model-task-routing`
- **Merge target branch**: `feat/108-model-task-routing`
- The execution worktree is allocated per computed lane from `lanes.json`; never pick a base branch by hand.

```bash
spec-kitty agent action implement WP05 --agent claude --mission model-task-routing-01M7PVHQ
```

## Before you start (every WP)

1. **#355 must be merged.** No code starts before #355 (PR #599) has merged and this branch is rebased onto that main
   (C-001). If it has not merged, stop and report.
2. **Recheck every path below on the merged main.** This prompt was written against main `8284bd36` with #599 open. #599
   rewrites `PackContents`, `PackRulesets`, `PackAssembly`, `StoreRead`, `StorageRulesets` and
   `ReferenceFacts`/`ReferenceClosure`, and deletes `MissionTemplates` and `GuardClosure` (research R1). Follow the
   merged code, not this text, where they differ, and record the difference in the WP history.
3. **Read the authority:**
   - `docs/adr/0080-the-routing-catalog-kind-and-the-model-recommendation.md` (accepted);
   - `kitty-specs/model-task-routing-01M7PVHQ/{plan.md,data-model.md,research.md,contracts/operations.md}`;
   - the charter companion `.kittify/charter/charter.md`.

   Core's `/opt/spec-kitty/src/charter/offering/model_task_routing/` is evidence only (ADR-0015).
4. **Tests use the canonical shape only**, except tests of invalid content (charter companion; ADR-0070). Never exclude
   a field from a parity or round-trip check to make a test pass.
5. **Commits:** signed (`git commit -S`), ending with the `Claude-Session:` trailer, with no model names and no
   `Co-Authored-By`.

## Objective

The store loads, validates, writes and round-trips a routing catalog. Registration and `pack-check` refuse inconsistent
catalog content as invalid content (FR-001, FR-002, FR-005; plan IC-01; squad finding A2).

## Subtasks

### T016 – The `routing` domain value types

In `service/routing/src/main/java/ai/speckitty/charter/routing/domain/`:

- **Records.** Write immutable records with compact-constructor checks, following `data-model.md` "RoutingCatalog":
  - `RoutingCatalog(String id, Optional<String> description, String generatedAt, List<TaskType> taskTypes,
    List<ModelEntry> models, RoutingPolicy policy, List<CatalogSource> sources)`; it implements the kernel's
    `CharterArtifact`, as `Skill` does;
  - `TaskType`, `ModelEntry`, `TaskFit`, `ModelCost`, `RoutingPolicy`, `RoutingWeights`, `TierConstraint` and
    `CatalogSource`.
- **Enums.** `RoutingObjective` is `QUALITY_FIRST`, `BALANCED` or `COST_FIRST`; each carries its token and a Javadoc
  stating its meaning (ADR-0080 decision 10: two compute alike today and differ in meaning). Use the kernel `CostTier`
  for every cost tier. A local enum is enough for the latency tier and the confidence.
- **Numbers are `BigDecimal`.** `TaskFit.score` and the weights are decimals, never `double` (NFR-002).
- **Optionals.** Keep `Optional` for every optional field, so the binding writes back only what was present.
- **Cross-reference checks are not in the records.** They are the content rules (T018), so a whole pack reports every
  fault, not the first a constructor throws. The records check only what a single value can check (non-empty ids,
  0 ≤ score ≤ 1).

### T017 – Storage binding, DSO and YAML format

- **The binding.** Following `SkillBinding`, `SkillDso` and `SkillYaml`
  (`service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/`), write `RoutingCatalogBinding`
  (`KindBinding<RoutingCatalogDso, RoutingCatalog>`, kind `ROUTING_CATALOG`), `RoutingCatalogDso` and
  `RoutingCatalogYaml`. Register them in `KindBindings.ALL` and `YamlFormats.ALL`.
- **Numbers.** Read them as `BigDecimal` from the YAML text, with no `double` step: configure the mapper for the DSO
  (`DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS`, or a `BigDecimal` field type with the YAML scalar read as
  text). Write them back in plain notation, so `0.66` stays `0.66` (ADR-0036: equality of meaning).
- **Placement.** The file lives under `routing_catalogs/` and is placed by id (`<id>.routing-catalog.yaml`). Check
  `DomainRoundTrip.NOT_PLACED_BY_ID`: the catalog is placed by id, so it is not listed there.
- **Dependency.** Add `api(project(":routing"))` to `service/packs/build.gradle.kts` (the dependency WP01 deferred).
- **Tests.** A binding test reads a full catalog (every optional field present), writes it, and reads the same value
  back. Another reads a minimal catalog and writes no absent key.

### T018 – The content rules

- **Where they live (squad A2).** Not in `routing.domain`: `PACKS_DOMAIN_IS_PURE` forbids `packs.domain` from naming
  it.
  - Write a packs-owned `RoutingCatalogFacts` view in `PackContents`, with the plain strings and lists a rule needs,
    as `MissionTypeFacts` does.
  - Write `RoutingCatalogRules` in `packs.domain.rules` and register it in `PackRulesets.content()` (ADR-0075), so the
    load, the whole-pack write, validate mode, the per-file write and `pack-check` all run it.
  - **#599 rewrites `PackContents` and `PackRulesets`.** Build on the merged shape, and follow how #599's
    `ReferenceClosure` registers itself.
- **The record component.** On main after #599, `PackContents` is a record whose hand-written-facts constructor takes
  the lists positionally. Add `List<RoutingCatalogFacts> routingCatalogs` after `missionTypes`, in both the canonical
  and the test constructor. Update every call site; do not add an overload that hides the new list. Register the rules
  in `content()` before `ReferenceRules`. #601 (the commit store's pre-lock race) is not in this WP's map.
- **What is refused**, each as invalid content with a rule id and a message naming the catalog, the field and the
  value (match the existing rules' violation shape):
  1. a `task_fit.task_type` or `tier_constraints.task_type` that the catalog does not declare;
  2. a duplicate task type id, or a duplicate model id;
  3. two `task_fit` entries for one task type in one model;
  4. two `tier_constraints` for one task type.
- **No outward references.** A catalog references nothing outside itself, so `ReferenceFacts` needs no new subject.
  State this in the rule's Javadoc.
- **Tests.** Unit-test each rule with a fixture under `service/packs/src/test/resources/routing-catalog-fixtures/`.
  These are invalid-content tests, the only place a non-canonical catalog may appear. One test per rule, each asserting
  the exact violation. Add one positive control: the valid catalog passes every rule.

### T019 – Read-service registrations

- **`ArtifactReadService`** (`packs/application`):
  - add `ROUTING_CATALOG` to `SERVED` (lines 85–95). The order is the `?kind=` filter order and the 400 detail order:
    append last;
  - add it to `LITERAL_ITEM_KINDS` (lines 102–103), because the templated item read does not render it (ADR-0080
    decision 6);
  - map its refusal (line 147) to `ARTIFACT_NOT_FOUND`.
- **`PresetMapper`** (`packs/infra/api`, near line 102): `case ROUTING_CATALOG -> setActivatedRoutingCatalogs`.
- **Removal.** Like a skill, the catalog is not removable through `DELETE …/artifacts/{kind}/{id}` (ADR-0074
  amendment, research R14 there). Check `ArtifactRemovalService`, and record in the history whether its kind set needs
  a change. Removing a catalog is out of scope.
- **Scenarios this breaks.** Fix the scenarios whose `?kind=` 400 detail lists every kind (`artifacts.feature` line 433,
  `effective-reads.feature` near line 3468) as recorded edits outside the map.

## Definition of Done

- A catalog loads, round-trips and is refused for each content fault, on every write path and in `pack-check`.
- `./gradlew verifyAll` passes on Java 25 for the modules this WP touches, with no new style-gate finding.
- Every subtask's status is recorded with `spec-kitty agent tasks mark-status`.
- Nothing outside `owned_files` changed, except a recorded edit outside the map, named in the WP history with its
  reason.

## Reviewer Guidance

- Check that no `double` sits anywhere on the catalog's path, from YAML to domain.
- Check that the content rules run on every path, `pack-check` included.
- **Independent reviewer.** Review is by a separate reviewer session, never by the implementer.
