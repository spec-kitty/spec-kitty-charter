---
work_package_id: WP03
title: PackPolicy's residue as packs-domain functions
dependencies:
- WP01
requirement_refs:
- FR-004
- C-001
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts were generated on slice-5-554-pack-closure-rulesets (via the mission branch kitty/mission-pack-closure-rulesets-01M7FA90). Completed changes merge back into slice-5-554-pack-closure-rulesets, then into main by one pull request (R4).
subtasks:
- T012
- T013
- T014
- T015
- T016
agent: claude
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Paths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/DescriptorChecks.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Wording.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Precedence.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphBindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PathsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/DescriptorChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/WordingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PrecedenceTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/GraphBinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Paths.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/DescriptorChecks.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Wording.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/Precedence.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/rules/package-info.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/GraphBindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PathsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/DescriptorChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/WordingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/rules/PrecedenceTest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/RelationshipShard.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/SidecarPayloads.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/api/RelationshipWire.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ArtifactReadService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/KindTaxonomyReadService.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP03 - PackPolicy's residue as packs-domain functions

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
- Start with `spec-kitty agent action implement WP03 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

`PackPolicy` cannot be deleted while about 20 classes outside the rules use its non-rule members (A-M5, M-4). Move
those members into named, plain functions in a new package, `packs.domain.rules`. `PackPolicy` keeps delegating to
them until WP09 deletes it. **The change preserves behaviour:** the golden snapshot is unchanged. Plan IC-03.

## Subtasks

### T012: `GraphBinding`

- **Purpose:** build the bound graph from a raw graph document, for both the store's shard reader and the graph rule
  (WP07).
- **Move:**
  - `PackPolicy.registeredGraph`, `build` and the `GraphBuild` result;
  - `joinsItself`, `TENSION`, `NOT_AUTHORED` and `TERM_ENDPOINT`;
  - the edge and node helpers (`:745-946` before F; re-check).
- **Keep:** the call into slice F's `RelationshipEndpointRule` / `EndpointRule` exactly as it is.
- **Findings:** the build returns them as today. WP04 retypes them.

### T013: `Paths`

- Move `bodyPathEscape` and `assetPathEscape`, which `SidecarPayloads:58` uses.
- Keep the wording byte for byte. `whole-pack-registration.feature:600-603` pins it.

### T014: `DescriptorChecks`

- **Purpose:** one function for descriptor well-formedness, from `PackPolicy.descriptorFindings`. The offering
  (`OfferingReadService:~549-558`, an out-of-map edit owned by WP08 later; repoint the call here) and the resolver
  precondition (`PackCompositionResolver.resolve`, `:280-288`) call it. So does WP06's descriptor rule.
- **Keep the detail texts exactly.** `composition.feature:304` pins one.

### T015: `Wording` and `Precedence`

- **Wording:** the detail vocabulary (`:1092-1330` before F).
- **Precedence:** `PRECEDENCE`, `group`, `comparePaths` and `comparePlaces`.
- **Their tests move whole:** `PackPolicyVocabularyTest` (49) and `PackPolicyOrderTest` (22) become `WordingTest` and
  `PrecedenceTest`. Keep every case (M-7: do not trim).
  - `git mv`, then retarget them. The old classes are owned by WP09; record the moves here as out-of-map edits.

### T016: Repoint the callers

- **The callers:** `RelationshipShard:99-101`, `RelationshipRegistrationService:119`, `ArtifactReadService:542`,
  `RelationshipWire:89,98`, `KindTaxonomyReadService:135`, `GraphShardWriter:117`, `SidecarPayloads:58`,
  `FilesystemPackStore:742` (owned by WP04), `FileSystemPackInspector:944` (owned by WP05),
  `DomainRepositoryService:772` (owned by WP05) and `OfferingReadService:555` (owned by WP08).
- **Those owned elsewhere** are a one-line out-of-map edit each, recorded in the review log.
- **`PackPolicy` keeps thin delegates,** so that nothing else moves now.
- **Confirm nothing is left:** `git grep -n 'PackPolicy\.' -- 'service/*/src/main'` lists only `PackPolicy`'s own
  rule entry points and `PackValidation`.

## Definition of Done

- The new package exists, with its tests in `:packs:test`.
- No class outside the rules names a moved member through `PackPolicy`.
- The golden snapshot is unchanged.
- `verifyAll` is green.
- A narrowed PIT run on `packs.domain.rules` is reported (`pit-targets.init.gradle`), before and after.

## Risks

- **`PACKS_DOMAIN_IS_PURE`.** The new package must name only the kernel, `java.*` and JSpecify.

## Reviewer guidance

- Was each member a pure move, with no wording drift?
- Did the order and vocabulary tests move whole?
