---
work_package_id: WP06
title: Per-pack rules, part 1 (level, descriptor, ids, assets, mission types, glossary)
dependencies:
- WP05
requirement_refs:
- FR-001
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
subtasks:
- T029
- T030
- T031
- T032
- T033
- T034
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/LevelRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/OrgFragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/DescriptorRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/StepRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ArtifactRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/AssetRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GlossaryRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/LevelRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/OrgFragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/DescriptorRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/StepRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ArtifactRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/AssetRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GlossaryRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PlantedFixtures.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/LevelRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/OrgFragmentRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/DescriptorRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/StepRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/ArtifactRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/AssetRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRules.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GlossaryRules.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/LevelRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/OrgFragmentRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/DescriptorRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/StepRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/ArtifactRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/AssetRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/MissionTypeRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GlossaryRulesTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PlantedFixtures.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP06 - Per-pack rules, part 1 (level, descriptor, ids, assets, mission types, glossary)

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
- Start with `spec-kitty agent action implement WP06 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Write the first half of the per-kind rules as kernel `Rule`s in `packs.domain.rules`. Each rule runs over the smallest
domain fact it needs. Every finding is a kernel `Reason` with a `PackCodes` code, a `Location` (file and pointer) and
the kept wording. **Nothing is wired yet:** `PackPolicy` still judges. Spec FR-001 and FR-004; plan IC-06 (part 1).

## The kernel vocabulary (verify in `kernel/validation`)

- `Rule<T>.apply(T) -> List<Reason>`, with `check` returning a `Rationale`; `on(focus)`, `forEach(parts)`, `when`
  and `locatedAt` compose a rule.
- `Constraint<T>` emits a PASS reason too, so mappers read `Rationale.failures()`.
- `ReasonCode` owns its severity.

## Subtasks

### T029: `LevelRules` and `OrgFragmentRules`

- **Source:** `PackPolicy.level` (`:126-160`) and `orgFragmentRules` / `isOrgPack` (`:574-600`).
- **The level rules** fire only when the root has a configured level, as today.
- **Codes:** `pack_level_missing` (WARNING) and `pack_level_mismatch`; `org_fragment_missing`.
- **Profile (R1):** these are VALIDATE-only. WP07's profile excludes them from REGISTER and COMPOSE_READ.
- **ArchUnit:** rules A to C name these owners after WP09. Keep the codes built only here and in `PackCodes`.

### T030: `DescriptorRules`

- **Purpose:** a `Rule<PackDescriptor>` wrapping WP03's `DescriptorChecks`. Its codes are `requires_core_invalid`,
  `pack_version_invalid` and `dependency_range_invalid`.
- **Profile:** VALIDATE-only.
- **The offering and the resolver keep calling `DescriptorChecks`,** so the texts stay the same.

### T031: `StepRules` and `ArtifactRules`

- **Source:** `stepRules` (`:522`) and `artifactRules` (`:543`).
- **Codes:** `duplicate_step_id` and `duplicate_artifact_id`. The place follows the R2 table (C-4: validate mode used
  the repeated step, the load used the type's file).
- **Profile:** R1 keeps duplicate ids off REGISTER as a new refusal. The aggregate guard's answer there is today's
  registration answer, and the R2 table decides its code.

### T032: `AssetRules`

- **Source:** `assetRules` / `mimeProblem` (`:571-598`). The escape is one fact (C-2), whatever surface reports it, with
  the R2 table's code.
- **Profile:** `asset_mime_invalid` is VALIDATE-only (R1, `assets.feature:150`).

### T033: `MissionTypeRules` and `GlossaryRules`

- **Mission types:** slice F's `substepIdRules`, `mission_step_unlinked` and `substep_delegation_*`, from
  `PackPolicy:~409-440` and the load's equivalents.
- **Glossary:** #106's glossary file rules, wherever they are today (`git grep -n glossary -- '*PackPolicy*'
  '*DomainRepositoryService*'`).
- **Profile:** all surfaces, as today.

### T034: The per-rule tests

- **One test class per rule,** each with at least one failing and one passing case.
- **Fixtures:** prefer the planted packs, read from testFixtures through `PlantedFixtures`, which turns a planted
  directory into `PackContents` by the real inspector. Use the planted pack's `expected.json` as the oracle where it
  covers the rule.
- **Equivalence:** for each rule, assert that its findings equal `PackPolicy`'s findings for that group over the
  planted packs. This proves the move before WP09 switches over.

## Definition of Done

- 8 rule classes, with tests, in `:packs`.
- Each rule matches `PackPolicy`'s group over the planted packs.
- `PACKS_DOMAIN_IS_PURE` is green.
- `verifyAll` is green.
- A narrowed PIT run on `packs.domain.rules` is at or above 85.

## Reviewer guidance

- Does any rule read a file? None may (NFR-004).
- Does any rule hold a flag instead of being chosen by a profile?
