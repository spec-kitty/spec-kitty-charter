---
work_package_id: WP01
title: The closure rule in the PACK ruleset
dependencies: []
requirement_refs:
- FR-001
- FR-002
- FR-003
- FR-005
planning_base_branch: feat/355-closure-in-pack-ruleset
merge_target_branch: feat/355-closure-in-pack-ruleset
branch_strategy: Planning artifacts for this mission were generated on feat/355-closure-in-pack-ruleset. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/355-closure-in-pack-ruleset unless the human explicitly redirects the landing branch.
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
- T007
- T008
history:
- at: '2026-11-18T07:00:00Z'
  actor: java-jenny
  action: Prompt generated from plan.md and research.md
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ConfigSchemas.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ReferenceFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReferenceClosure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReferenceRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferenceClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferenceRulesTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ConfigSchemas.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCodes.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/ReferenceFacts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReferenceClosure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReferenceRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/EmbeddedSchemas.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GuardClosure.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/MissionTemplates.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackAssembly.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/StoreRead.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/WholePackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/Wording.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GuardBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/LifecycleBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/AssemblyRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodes.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferenceClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReferenceRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackCodesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCodesCoverageTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StoreHalfFaultsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageCodesTest.java
- service/pack-validation/src/test/resources/**/golden/**
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/golden/**
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAggregateTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackAssetFilesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackKindMapTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetIdNotKebabTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetStoreTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DoctrineRepositoryContractTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeBindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypePlantsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackDocumentRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackManifestRoundTripTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackRootSpellingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ProcedureEdgesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementSurfacesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SubstepDelegationPlantsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ToolguideGuideTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/WordingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/AssemblyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/rules/StorageRulesetsTest.java
role: implementer
tags: []
tracker_refs:
- '#355'
---
# Work Package Prompt: WP01 - The closure rule in the PACK ruleset

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else. Then read, in
`kitty-specs/closure-symmetry-01M7PNFT/`: `spec.md`, `plan.md` (the Implementation Concern Map), `research.md` (D1 to D6) and `data-model.md`.

## Branch Strategy

Planning base: `feat/355-closure-in-pack-ruleset`. Final merge target: `feat/355-closure-in-pack-ruleset`, then `main`
by the pull request. The execution worktree is allocated per computed lane from `lanes.json`.

```bash
spec-kitty agent action implement WP01 --agent claude --mission closure-symmetry-01M7PNFT
```

## Objective

One pure domain rule, `ReferenceClosure`, decides the whole closure (asset entry; guard asset, executable block and
`config_schema`; lifecycle guard entries and `config`; mission-type output templates; pack-qualified references; the
cascade). The load (`PackAssembly.collect`) and the whole-pack write (`WholePackStore.write`) call it. `content()`
runs it as `ReferenceRules.RULES` over a new fact of the one read, so validate mode and whole-pack registration state the
closure as a PACK-ruleset finding. `GuardClosure` and `MissionTemplates` are deleted.

## Subtasks

### T001 Red tests for the rule (`ReferenceClosureTest`, `ReferenceRulesTest`)

- `ReferenceClosureTest`: build domain values directly (assets with and without `executable`, guards built-in and
  asset-backed, a lifecycle with guard entries and `config`, a mission type naming an own and a top-level template).
  One test per case of data-model.md's invariants 1 to 4, plus the cascade (asset → guard → lifecycle), first-by-id,
  and the pack-qualified references. Assert the exact reason strings the load records today (copy them from
  `GuardClosure`, `MissionTemplates.reason` and `GuardAndLifecycleLoadTest`).
- `ReferenceRulesTest`: `PackContents` with a hand-built `ReferenceFacts`; the rule yields one `Reason`
  (`artifact_schema_error`, the candidate's file, the load's reason) per finding, and nothing for `ReferenceFacts.NONE`.
- Run them red (they do not compile, then fail) before T002.

### T002 The `ConfigSchemas` port

`packs.domain.ConfigSchemas` with `schemaProblems(ConfigValue)` and `valueProblems(ConfigValue, Optional<ConfigValue>)`.
`EmbeddedSchemas` implements it through a public singleton (`EmbeddedSchemas.PORT`), keeping its static helpers private
to the class or removing them if nothing else calls them.

### T003 `ReferenceClosure`

Move the logic of `GuardClosure` (constructor maps, `unresolved` overloads, `configProblem`) and of
`MissionTemplates` (`namable`, `firstUnresolved`, `reason`) into `packs.domain.rules.ReferenceClosure`, unchanged in meaning
and wording. API: `of(assets, payloadPaths, guards, templates, schemas)`, `of(Pack, schemas)`,
`firstUnresolved(CharterArtifact) -> Optional<String>` (mission type first), `findings(List<Placed or artifacts>)`.

### T004 Bulk edit: `artifact_schema_error` moves to `PackCodes`

Follow `occurrence_map.yaml`. Add `PackCodes.ARTIFACT_SCHEMA_ERROR` to `CONTENT`; delete it from `StorageCodes` (and
`ORDERED`). Rename every reference, main and test. No expected value of any assertion changes. `PackCodesTest`,
`PackCodesCoverageTest`, `StorageCodesTest` and `VocabularyEqualityTest` stay green (the coverage test needs a firing
fixture for the code; the closure gives one).

### T005 Load and whole-pack write call `ReferenceClosure`; delete the old classes

`PackAssembly.assembly` builds the candidates once (the entities that build, with their files) and a `ReferenceClosure`
from them; `collect` asks `firstUnresolved`. `WholePackStore.write` keeps today's refusal order and wording: the first
mission type's `mission_type '<id>' names <reason>`, then the first other finding's reason. Delete `GuardClosure.java`
and `MissionTemplates.java`; fix the Javadoc links in `GuardBinding`, `LifecycleBinding`.

### T006 `ReferenceFacts` on `PackContents`, `ReferenceRules` in `content()`

A new record component `closure` (`ReferenceFacts`, `NONE` from the convenience constructor). `ReferenceRules.RULES` is
added to `PackRulesets.CONTENT` before `FragmentRules.ORPHAN`.

### T007 The store half fills the facts; closure faults leave the store-only list

`StoreRead` passes the candidates, payload paths, namable template ids and `EmbeddedSchemas.PORT` into the contents'
`ReferenceFacts` (where `FileSystemPackInspector` builds `PackContents`). The assembly no longer hands closure faults to
`storeOnly` (they are the rule's now); value-object breaches stay there. Update `AssemblyRules.held`'s Javadoc.

### T008 Validate mode over the example packs and the golden snapshot

Run `:pack-validation:test` (golden) and the validate-mode tests. Any change in order is listed in the PR body; an
added or lost finding is a defect, not a diff. Run validate mode over charter's own example packs (ADR-0070): no new
finding.

## Definition of Done

- `ReferenceClosureTest`, `ReferenceRulesTest` green; red-first recorded in the commit history (test commit before the fix).
- `GuardAndLifecycleLoadTest`, `StoreHalfFaultsTest`, the whole-pack round trip and the golden tests green, with no
  expected value changed.
- `grep -r GuardClosure\|MissionTemplates service/*/src/main` is empty.

## Risks

- Verdict order in validate mode: rule findings come before the store-only faults; compare the golden snapshot.
- ArchUnit: `packs.domain` must name nothing in `infra`. `ReferenceFacts` holds the port interface only.
