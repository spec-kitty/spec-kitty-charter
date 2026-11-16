---
work_package_id: WP07
title: Per-pack rules part 2, PACK with surface profiles, and the mappers
dependencies:
- WP06
requirement_refs:
- FR-002
- FR-008
- FR-011
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
subtasks:
- T035
- T036
- T037
- T038
- T039
- T040
- T041
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReadRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ManifestRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReservedShardRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/NotHeldRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackVerdicts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRefusals.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReadRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/FragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/LoadOnlyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackVerdictsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackRefusalsTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReadRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/FragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ManifestRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ReservedShardRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/NotHeldRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/PackRulesets.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackVerdicts.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackRefusals.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/StepEnhancementRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/RelationshipEndpointRule.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ReadRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/FragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PromptSlotRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/LoadOnlyRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PackCompositionTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackVerdictsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackRefusalsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/StepEnhancementRulesTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP07 - Per-pack rules part 2, PACK with surface profiles, and the mappers

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md`;
- `research/design-r3.md` (with its §7 folds) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP07 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Finish the per-kind rules. Compose them into `PACK` with the R1 surface profiles, and write the two mappers. When this
work package is done, `PackRulesets.pack(surface)` with its mapper reproduces each surface's golden answer. **Nothing is
switched yet.** Spec FR-001, FR-002, FR-008, FR-011 and FR-012; plan IC-06 (part 2).

## Subtasks

### T035: `ReadRules`

- **What it covers:** the read faults, from `PackContents` facts (WP05): schema and parse, `retired`, `domain`,
  `reference`, `companion` and the descriptor or not-a-pack faults. The codes and wording follow the R2 table.
- **The edges-read gate:** keep it. A graph that could not be read switches off the edge rules (`graphsRead`). The
  load's gate was `graphs.violations().isEmpty()`. Unify the gate on the inspector's fact, and pin it with a
  composition test.

### T036: `GraphRules`

- **What it covers:** `drg_schema_error` and the endpoint rule, through slice F's bridge (`RelationshipEndpointRule` /
  `EndpointRule`; FR-011). The wire code `relationship_endpoint_not_allowed` stays.
- **What it reads:** the bound graphs `PackContents` carries from WP03's `GraphBinding`. The rule never builds a graph.
- **`RelationshipEndpointRule`:** becomes, or feeds, this rule, with its constants kept.
- **The compose read's endpoint prefix** (`readable`'s special case) is kept by `PackRefusals` / the compose mapping
  (WP09).

### T037: `FragmentRules` and `PromptSlotRules`

- **`StepEnhancementRules`** (13 fault kinds) becomes `FragmentRules`.
- **Orphan fragments:** from the not-held and unmodelled facts, never listed twice (`unreportedFiles` dedup;
  `prompt-fragment-refusals.feature:15`).
- **The companion remap** to `prompt_fragment_invalid` (`DomainRepositoryService:~1199`).
- **Precedence:** a binding-level fault is named before a rule fault.
- **Prompt slots:** `prompt_slot_malformed`, and `prompt_slot_not_empty` as two constants (VIOLATION, and WARNING for
  an unversioned descriptor).
- **Single source:** the unversioned warning comes from `PACK` only, never also from the reader (C-9).

### T038: `ManifestRules`, `ReservedShardRules` and `NotHeldRules`

- **Manifest:** the file must be a regular file (`unreadable`), plus membership over `PackManifestDerivation`.
- **The reserved shard:** comes from the stamp fact, not an unreadable graph (C-4). Registration lists it last.
- **Not held:** `file_not_held` WARNING per not-held file, with the reason. Its detail matches WP02's.

### T039: `PackRulesets`

- **The entry point:** `pack(Surface)`, with `Surface {VALIDATE, REGISTER, COMPOSE_READ}`. It composes the rule groups
  per `data-model.md`'s R1 table.
- **The manifest gate (A-D1):** a plain sequence. The base `Rationale` comes first; membership is judged only when the
  base has no failing `VIOLATION`. The base includes every violation registration counts today (C-12).
- **No rule carries a flag.** The profile is the only selector.

### T040: `PackVerdicts` and `PackRefusals`

- **`PackVerdicts.of(Rationale) -> PackVerdict`:** sorted by `Precedence` (WP03).
- **`PackRefusals.of(Rationale)`:**
  - the violations, in registration's order: graph faults by path, then the read and rule faults in reader order,
    then the orphans by path, the reserved shard, and manifest membership;
  - the problem detail `"The pack cannot be registered: N fault(s), the first at X."`;
  - the diagnostics: the warnings in today's order, then the merged, path-sorted not-held list.
- **Both mappers** read `failures()` only and switch on the code constant.

### T041: Composition and mapper tests

- **`PackCompositionTest` (M-7):** one test per interaction:
  - a schema-invalid file gets no semantic rule;
  - a codec fault wins over a marker;
  - the companions of a refused enhancement are not orphans;
  - the edges-read gate;
  - the manifest gate;
  - a binding-level fault comes before a rule fault;
  - a not-held file plus a manifest mismatch;
  - the two `prompt_slot_not_empty` codes.
- **Golden equivalence:** for every golden pack, each surface's mapper over `pack(surface)` on the inspector's
  contents equals that surface's golden answer. A gap here is a bug to fix now, before WP09.

## Definition of Done

- `PACK` with its mappers reproduces every surface's golden answer.
- The composition tests pass.
- `verifyAll` is green.
- A narrowed PIT run on `packs.domain.rules` and the mappers is at or above 85.

## Reviewer guidance

- Is the profile table followed exactly?
- Is the manifest gate a plain sequence?
- Is there a single source for each warning?
