---
work_package_id: WP05
title: Inspector facts, the Spring-free assembly and composition facts
dependencies:
- WP04
requirement_refs:
- FR-006
- FR-015
- NFR-004
- C-003
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
subtasks:
- T023
- T024
- T025
- T026
- T027
- T028
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackAssembly.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDerivation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackAssemblyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/InspectorReaderAgreementTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackAssembly.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackManifestDerivation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilder.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/ManifestMembership.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackContents.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackAssemblyTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/CompositionFactsBuilderTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/InspectorReaderAgreementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspectorTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/ManifestMembershipTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP05 - Inspector facts, the Spring-free assembly and composition facts

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
- Start with `spec-kitty agent action implement WP05 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Make `PackContents`, the inspector's read, hold every fact a rule needs, so that registration can judge the staged
tree without the store's reader (P1). Build composition facts without Spring, so that `CLOSURE` can run in pack-check
(F5). **The change preserves behaviour:** the golden snapshot is unchanged. Plan IC-05; squad findings A-B3, A-M1, F2,
F4 and F5.

## Subtasks

### T023: `PackAssembly`

- **Purpose:** a Spring-free class, not a bean, in `infra.storage` (F2), carved out of `DomainRepositoryService`
  (a `@Service`, `:112`).
- **What it takes:** the DSO-to-entity build of `assembly` (`:955` before F; re-check) and the `judged()` withholding
  (`:960-985`), which keeps `Pack.guardKind` from breaching.
- **Callers:** `DomainRepositoryService` delegates to it, and `FileSystemPackInspector` calls it to learn the
  value-object breaches (`domain`) and the reference faults.
- **Acceptance:** `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` (`HexagonalArchitectureTest:~389`) stays green. Run
  `:deployable:test --tests '*HexagonalArchitecture*'`.

### T024: `PackManifestDerivation`

- **Purpose:** move `ManifestMembership.derived` and `derivedAt` off `WholePackStore` (A-B3: `derivedAt` names the
  `@Service`).
- **Who calls it:** the inspector's fact, the membership check and the manifest stamp (`PackCommitStore.java:396`)
  all call this one function, so the two derivations never split (A-D1).

### T025: The inspector facts (the A-M1 checklist)

`PackContents` gains, and `FileSystemPackInspector` fills, each of these. Each needs a fixture.

1. value-object `domain` breaches: file, kind and the breach's `Reason`;
2. `reference` faults (unclosed references);
3. the aggregate `build()` breach (`Pack.guardKind`);
4. descriptor and not-a-pack-directory faults;
5. the named mutability-policy file being unreadable;
6. a misplaced preset entry;
7. the store's graph read: `drg_schema_error`, and the endpoint rule's input;
8. the companion-to-`prompt_fragment_invalid` remap;
9. the files that are not held (unmodelled and excluded), with their reasons (A-D2);
10. the manifest: present, regular, and listed against derived;
11. the reserved-shard stamp, as a fact and not as an unreadable graph (C-4).

### T026: The registration path's root and links (F4)

- **The root:** inspecting a staged root that cannot be read, or that is not a directory, must yield a fact that
  registration answers as today, 422 `validation_failed` at `pack`. It must not throw a bare `pack_unreadable`.
- **Symlinks:** the inspector follows no symlinks under `Scope.FULL` on a staged tree, as the store does not.
- **Tests:** add one for each case.

### T027: `CompositionFactsBuilder` (F5)

- **What it builds:** `PackUnderResolution` from `PackContents`: the descriptor, the level, the defined and changed
  artifacts (from the `overrides` edges) and the held terms. It is Spring-free.
- **`CompositionFactsBuilderTest`:** over every example pack, it equals what `DomainRepositoryService` builds today.

### T028: The agreement tests

- **`InspectorReaderAgreementTest`:** over every planted and example pack, every fault the store's reader records has
  a matching inspector fact (forward). This is the precondition for WP09's subset tests.
- **Test placement:** keep the inspector's fact tests in `FileSystemPackInspectorTest`, and split by fact if it grows
  past about 1,500 lines.

## Definition of Done

- Every A-M1 row has a fact and a fixture.
- `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and `PACK_VALIDATION_IS_FRAMEWORK_FREE` are green.
- The parity test passes.
- The golden snapshot is unchanged.
- `verifyAll` is green.
- A narrowed PIT run on `infra.storage` is reported.

## Risks

- **Two reads of a staged tree.** That is accepted (P1).
- **The inspector growing a second assembly.** It must call `PackAssembly`, never copy it.

## Reviewer guidance

- Is there one assembly?
- Is there one manifest derivation?
- Does each fact come from the shared function?
