---
work_package_id: WP06
title: Content rules part 1 (ids, assets, mission types, glossary)
dependencies:
- WP05
requirement_refs:
- FR-001
- FR-004
- FR-017
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 2ad0fa3dffa6bad69c8801fa29b9bb06b35eeb4d
created_at: '2026-11-17T06:49:05.621085+00:00'
subtasks:
- T031
- T032
- T033
- T034
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/StepRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ArtifactRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/AssetRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/StepRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ArtifactRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/AssetRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PlantedFixtures.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/StepRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ArtifactRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/AssetRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/StepRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ArtifactRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/AssetRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PlantedFixtures.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP06 - Content rules part 1 (ids, assets, mission types, glossary)

## ⚡ Do This First: Load Agent Profile

Run `/spk-charter-profile-load java-jenny` and adopt the profile before reading anything else.

Then read, in `kitty-specs/pack-closure-rulesets-01M7FA90/`:

- `spec.md` (the Intent Summary's rulings table, S1 to S3 included);
- `plan.md` (Decisions, and the IC named below);
- `data-model.md` (placement, the one reader, the compositions);
- `research/squad-shaping.md` (the S1 to S3 rulings, their recorded readings and the target shape);
- `research/design-r3.md` (§1 to §7, read with §8, which lists what S1 to S3 and S6 superseded) and `research/squad-plan.md`;
- `research/squad-similar-surfaces.md` (file:line map, as of before slice F; re-check each reference).

Charter rules that bind every subtask:

- The charter's model is canonical. There is no legacy reader and no shim.
- Tests carry only the canonical shape, apart from error-path fixtures.

## Branch Strategy

- Planning base and final merge target: `slice-5-554-pack-closure-rulesets`. The mission branch is `kitty/mission-pack-closure-rulesets-01M7FA90`.
- The execution worktree is allocated per computed lane from `lanes.json`; this mission has one lane.
- Start with `spec-kitty agent action implement WP06 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Write the first half of the **content rules**: the core consistency checks of `PackRulesets.content()` (S1 to S3), as
kernel `Rule`s in `packs.domain.rules`. Each rule runs over the smallest storage-agnostic fact of `PackContents` it
needs. Every finding is a kernel `Reason` with a `PackCodes` code, a `Location` (file and pointer) and the kept
wording. **Nothing is wired yet:** `PackPolicy` still judges. Spec FR-001, FR-004 and FR-017 (the repeated asset id);
plan IC-06 (part 1).

What is **not** here any more (2026-11-16 replan, `research/design-r3.md` §8):

- the level and org-fragment rules: they are `pack-validation`'s placement extension (S2), written in WP09 (T029);
- a descriptor rule: the descriptor version format is a precheck of the one reader (S2), written in WP05 (T030);
- the asset mime rule: also a reader precheck (WP05, T030).

## The kernel vocabulary (verify in `kernel/validation`)

- `Rule<T>.apply(T) -> List<Reason>`, with `check` returning a `Rationale`; `on(focus)`, `forEach(parts)`, `when`
  and `locatedAt` compose a rule.
- `Constraint<T>` emits a PASS reason too, so mappers read `Rationale.failures()`.
- `ReasonCode` owns its severity.
- **Shape (squad LOW fold):** a rule is a constant built from the kernel combinators over the existing pure
  functions (lambdas), not a class per check and not a convenience constructor. A `*Rules` class is the holder of its
  kind's constants.

## Subtasks

### T031: `StepRules` and `ArtifactRules`

- **Source:** `stepRules` (`:522`) and `artifactRules` (`:543`).
- **Codes:** `duplicate_step_id` and `duplicate_artifact_id`. The place follows the R2 table (C-4: validate mode used
  the repeated step, the load used the type's file).
- **Surfaces:** these are in `content()`, so every surface runs them. Where registration answers the aggregate
  breach today (R2 row 10), the R2 table's code and place are kept; the golden diff cites row 10.

### T032: `AssetRules`

- **The escape:** from `assetRules` (`:571-598`), over one sidecar fact (C-2), with the R2 table's code (`asset`, row
  13, open case 1: one finding, not two). Through a symbolic link the kept reason is the escape.
- **The repeated asset id (S2):** a consistency rule. Two sidecars that declare one id give `duplicate_artifact_id`
  at the second sidecar (row 15). It is in `content()`, so registration gains it (WP12 writes the `### Breaking`
  fragment). Open case 2's count difference closes here.
- **Not here:** the missing payload is a read fault of the one reader (WP05); the mime format is a reader precheck.

### T033: `MissionTypeRules` (no `GlossaryRules`)

- **Mission types:** slice F's `substepIdRules`, `mission_step_unlinked` and `substep_delegation_*`, from
  `PackPolicy:~409-440` and the load's equivalents. One `Reason` per fault, so the paired emitters
  (`substepIdRules` / `...Violations`) go (squad finding 5).
- **Glossary (decided in implementation, WP06 D1, under S2):** #106's glossary checks are no content rule. The schema and
  stem checks are the reader's prechecks, the repeated term id is a storage format rule, the old shape is `retired`,
  a term as an edge endpoint is a `GraphBinding` fault, and the rest are value-object invariants. No `GlossaryRules`.
- **Surfaces:** `content()`, as today.

### T034: The per-rule tests

- **One test class per rule holder,** each with at least one failing and one passing case per constant.
- **Fixtures:** prefer the planted packs, read from testFixtures through `PlantedFixtures`, which turns a planted
  directory into `PackContents` by the one reader (WP05). Use the planted pack's `expected.json` as the oracle where
  it covers the rule.
- **Equivalence:** for each rule, assert that its findings equal `PackPolicy`'s findings for that group over the
  planted packs, apart from the R2 rows the golden already lists. This proves the move before WP09 and WP12 switch.

## Definition of Done

- 4 rule holders (`StepRules`, `ArtifactRules`, `AssetRules`, `MissionTypeRules`), with tests, in `:packs`; no
  `GlossaryRules` (D1) and no asset-escape rule, which the reader's sidecar check owns (D2).
- Each rule matches `PackPolicy`'s group over the planted packs.
- `PACKS_DOMAIN_IS_PURE` is green; no rule names a `Path`, bytes, YAML or JSON-Schema type.
- `verifyAll` is green.
- A narrowed PIT run on `packs.domain.rules` is at or above 85.

## Reviewer guidance

- Does any rule read a file? None may (NFR-004).
- Does any rule hold a flag or know which surface runs it? None may: compositions choose (S1 to S3).
