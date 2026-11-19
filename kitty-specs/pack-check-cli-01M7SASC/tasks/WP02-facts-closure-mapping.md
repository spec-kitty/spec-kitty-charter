---
work_package_id: WP02
title: Facts on the one read, one CLOSURE, one refusal mapping
dependencies: []
requirement_refs:
- FR-003
- FR-007
- NFR-005
planning_base_branch: feat/478-pack-check
merge_target_branch: feat/478-pack-check
branch_strategy: Planning artifacts for this mission were generated on feat/478-pack-check. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/478-pack-check unless the human explicitly redirects the landing branch.
subtasks:
- T006
- T007
- T008
- T009
phase: Phase 1 - Foundations
history:
- at: '2026-11-19T08:20:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRefusalMapping.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackRefusalMappingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackRulesetsClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadoutFactsTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackReadout.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRefusalMapping.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRefusals.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/Readouts.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackRefusalMappingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackRulesetsClosureTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadoutFactsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRefusalsTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/Placements.java
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 — Facts on the one read, one CLOSURE, one refusal mapping

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile named in the frontmatter, and behave according
to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`

## Objective

Three moves in the packs slice, so the CLI composes the service's own pieces and adds none of its own:

1. **The facts ride on the one read** (squad S1). `PackReadout` carries the pack's composition facts, or the refusal
   that stopped them, from the same read. There is no new port and no second read.
2. **One `CLOSURE`** (S4). `PackRulesets.closure(presetIdsOf)` is the only definition of the pack-set rules, preset
   phase included.
3. **One refusal mapping** (S3). The mapping from a failing `Rationale` to `CharterRefusal` lives in `packs.domain`.
   Registration and the CLI both call it.

## Context

- Spec: FR-003, FR-007. data-model.md "PackSetCheck". research.md squad S1, S3, S4, S6 and S10.
- `FileSystemPackInspector.readWithStore` (about line 280) builds `OneRead(tree, store)`.
  `CompositionFactsBuilder.composeFacts(PackIdentifier, StoreRead)` (package-private, about line 100) turns that
  `StoreRead` into `PackCompositionFacts`:
  - it throws `CharterRefusal` (`PACK_UNREADABLE` for an unreadable graph, or the load's own code, such as
    `VALIDATION_FAILED` or `DRG_SCHEMA_ERROR`);
  - it throws `IllegalStateException` on `Assembly.None`.
- `CompositionFactsBuilder.underResolution` shows how a `PackUnderResolution` is built: the descriptor comes from
  `((Assembly.Assembled) read.assembly()).descriptor()` (a kernel `PackDescriptor` with `declaredLevel`), and the lists
  from the facts.
- `PackRulesets.closure()` delegates to `CompositionRules.closure()`. `CompositionRules.presets(presetIdsOf)` is the
  preset phase, judged in `mergeOrder`, the offering's order.
- `PackRefusals` (`packs.application`) has `of(Rationale)` and `diagnostics(Rationale)`. Its imports are kernel types
  and `packs.domain.PackCodes`/`Diagnostic` only.

## Branch Strategy

Planning base `feat/478-pack-check`, merge target `feat/478-pack-check`; worktrees per lane from `lanes.json`. Run
`spec-kitty agent action implement WP02 --agent <name> --mission pack-check-cli-01M7SASC`.

## Subtasks

### T006 — `PackReadout.Facts`, filled by the one read

- **Steps:**
  1. In `packs.domain.PackReadout`, add a component `Facts facts`, where `Facts` is a sealed interface nested in
     `PackReadout`:
     - `record Built(PackDescriptor descriptor, PackCompositionFacts facts) implements Facts`;
     - `record Unreadable(RefusalCode code, String detail) implements Facts`.

     Document both. No `Optional` and no null.
  2. In `FileSystemPackInspector.read(PackRoot)`, compute it from the same `OneRead`:
     - `Built` from the assembled descriptor and `CompositionFactsBuilder.composeFacts(id, one.store())`;
     - catch `CharterRefusal` into `Unreadable(refusal.code(), refusal.getMessage())`;
     - map `Assembly.None` to `Unreadable(PACK_UNREADABLE, …)`, worded as the composing read words an unreadable
       pack.

     The pack id passed is the root's configured name, as `underResolution` uses it.
  3. Keep `CompositionFactsBuilder.underResolution` as it is (its test pins the facts), or have it delegate to the
     same code path; never two derivations.
- **Tests** (new `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ReadoutFactsTest.java`):
  - For each example pack, the readout's `Built` facts equal `underResolution`'s, and so the recorded
    `facts/example-packs.json`.
  - An unreadable graph document gives `Unreadable(PACK_UNREADABLE, "a graph document of built-in cannot be read")`.
  - A file the load refuses gives `Unreadable` with the load's code.
  - A pack with no `pack.yaml` gives `Unreadable(PACK_UNREADABLE, …)`, not an exception.
  - Prove one read: the `PackFileSet.walks()` and read counters (as the existing one-read tests sample them) rise by
    exactly one walk per `read`.

### T007 — `PackRulesets.closure(presetIdsOf)`

- **Steps:**
  1. Add `public static ModularRuleset<List<PackUnderResolution>> closure(Function<PackUnderResolution,
     Collection<String>> presetIdsOf)`, returning `closure().must(CompositionRules.presets(presetIdsOf))`. Its javadoc
     says it is the set check pack-check runs (#478), and that #556 extends it here.
  2. Keep `closure()` for the service paths that judge presets elsewhere; do not change them.
- **Tests** (`service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackRulesetsClosureTest.java`):
  - two unrelated packs of one level holding one preset id refuse with the preset conflict code;
  - one extending the other does not;
  - the order of the given packs does not change the answer;
  - `closure()` alone does not see the preset contest (a control that the new composition adds the phase).

### T008 — The refusal mapping moves to `packs.domain`

- **Steps:**
  1. New `packs.domain.PackRefusalMapping` holding `of(Rationale)` and `diagnostics(Rationale)`, moved verbatim from
     `PackRefusals`, with the same wording: `The pack cannot be registered: N fault(s), the first at <file>.`.
  2. `PackRefusals` keeps `packRefusal` and delegates `of`/`diagnostics` to it (or callers call it directly; choose
     the smaller diff).
  3. `PackRefusalsTest` stays green unchanged. Add `PackRefusalMappingTest` for the moved methods; moving the existing
     assertions is fine.
  4. `DOMAIN_IS_PURE` (`HexagonalArchitectureTest`) must still pass: the class names only the kernel and
     `packs.domain`.

### T009 — Construction sites and the registration cost

- **Steps:**
  1. Update every `new PackReadout(` site:
     - `service/packs/src/test/java/ai/speckitty/charter/packs/domain/Readouts.java`;
     - `service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckTest.java`;
     - `service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/Placements.java`;
     - and any other found with `grep -rn "new PackReadout(" service`.
     A test readout uses a `Built` from a canonical example pack's facts, or a documented `Unreadable`.
  2. Registration now computes facts it does not use. Run the registration budget and timing tests
     (`grep -rln "Budget\|budget" service/packs/src/test`). If a budget moves, make `facts` a memoized supplier
     computed on first access, and say so in the commit.

## Definition of Done

- `./gradlew verifyAll` is green. `PackRefusalsTest` and `CompositionFactsBuilderTest` are unchanged and green.
- The readout carries facts from the one read, proven by the walk counter. `closure(presetIdsOf)` exists with its
  control. The mapping lives in `packs.domain` and is used by registration.
- The `:packs` mutation floor holds.

## Risks and reviewer guidance

- **A second derivation.** Check that `Built` is computed only from `one.store()`, never by a new `StoreRead.of`.
- **Exception leaks.** `Unreadable` must cover every throw of `composeFacts`. The fourth test shows the `Assembly.None`
  path.
