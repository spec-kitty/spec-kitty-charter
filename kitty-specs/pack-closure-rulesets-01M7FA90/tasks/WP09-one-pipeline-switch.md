---
work_package_id: WP09
title: One pipeline, the switch, the deletions and ArchUnit (atomic)
dependencies:
- WP07
- WP08
requirement_refs:
- FR-005
- FR-007
- SC-001
- SC-004
- NFR-001
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts were generated on slice-5-554-pack-closure-rulesets (via the mission branch kitty/mission-pack-closure-rulesets-01M7FA90). Completed changes merge back into slice-5-554-pack-closure-rulesets, then into main by one pull request (R4).
subtasks:
- T047
- T048
- T049
- T050
- T051
- T052
- T053
agent: claude
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
create_intent:
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SurfaceSubsetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/FailClosedGuardTest.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackValidation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackRegistrationService.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/PackSourceStagers.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicy*Test.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackValidationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/Judged.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementLoadChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptSlotLoadChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/PackRegistrationServiceTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/SurfaceSubsetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/FailClosedGuardTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/VocabularyEqualityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/api/WireTokenParityTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/ReservedRefusalCodesTest.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/packlevelfixture/**
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP09 - One pipeline, the switch, the deletions and ArchUnit (atomic)

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
- Start with `spec-kitty agent action implement WP09 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.

## Objective

Switch every surface to `PACK`, and delete the glue. This work package is **atomic**: it lands as one green change,
with commits inside it squashed if a step does not compile on its own. After it, `PackRulesets` judges validate mode,
registration and the compose read. `PackPolicy`, `PackFinding`, `PackFindingCode` and the converters are gone. Spec
FR-005, FR-006 and FR-007, SC-001 and SC-004; plan IC-08; P1, R1, R2 and R3.

## Subtasks

### T047: Validate mode

- **`PackValidation.validate(root)`:** becomes `PackVerdicts.of(PackRulesets.pack(VALIDATE).check(inspector.inspect(root, FULL)))`.
- **The wiring** at `CharterWiring:87` is unchanged.

### T048: Registration (P1, A-M2)

- **`PackRegistrationService.register` (`:265`)** takes a `PackInspector` bean. Wire it in `CharterWiring`, an
  out-of-map edit of one bean.
- **Order of work:**
  1. Stage, as today.
  2. Inspect the staged tree as `PackRoot(packId, empty)` (C-11).
  3. Run `PACK(REGISTER)`.
  4. If it fails, throw `CharterRefusal.withViolations(VALIDATION_FAILED, PackRefusals...)`, with today's problem
     detail.
  5. Call `loader.loadStaged`.
  6. **The fail-closed guard (A-D3):** if the reader returned any fault, or a build breach, while `PACK` passed, refuse
     as `validation_failed` in today's aggregate shape: graph faults, then `{pack, <token>, message}`, with the detail
     `"The pack cannot be registered: " + message`.
- **The guard runs before** the whole-pack gate and the registration impact check, which both read the reader's
  contents (F6, F10).
- **The diagnostics** come from `PackRefusals`. The reader's warnings are dropped.
- **`PackSourceStagers` (`:73`)** calls through `register`, so there is one registration judgement.

### T049: `loadStaged` reads only

- **`WholePackStore.loadStaged`** (owned by WP02; an out-of-map edit, recorded) no longer refuses. It returns
  `Contents` plus the reader's faults.
- **Remove `graphViolations` and the `loadStaged` sequence.** Its order now lives in `PackRefusals`.

### T050: The compose read (A-M3)

- **`DomainRepositoryService.readable`** (owned by WP05; out-of-map) runs `PACK(COMPOSE_READ)` on its own read, through
  `PackAssembly`'s facts.
- **The answer:** the first failure, in reader order, as `pack_unreadable`, with the endpoint prefix kept.
- **The `judged()` withholding stays,** in `PackAssembly`.
- **Pin it:** every compose-read 500 detail in the golden snapshot is unchanged.

### T051: The deletions

- **Delete:**
  - `PackPolicy` (its residue went to WP03 and its rules to WP06 and WP07);
  - `PackFinding`, `PackFindingCode` and `toRefusal` / `toDiagnostic`;
  - `violationOf`, `findingOf` and `violationKind`;
  - `OverrideSourceRule` leftovers, if any;
  - `WholePackStore.violationOf` and `FilesystemPackStore.violationsOf`, once unused;
  - `Judged`.
- **Rewrite** `VocabularyEqualityTest`, `WireTokenParityTest` and `ReservedRefusalCodesTest` onto `PackCodes` and the
  mappers, keeping the same assertions. Drop the `OFF_WIRE` allowance once nothing names `PACK_LEVEL_MISMATCH` off the
  wire.
- **Delete or fold** `StepEnhancementLoadChecksTest` and `PromptSlotLoadChecksTest`, which were parity by hand.
  First check that each case is covered by a rule or composition test; WP10 audits this.
- **The `PackPolicy*Test` classes:** delete them after confirming coverage moved (WP03, WP06 and WP07).

### T052: ArchUnit (`HexagonalArchitectureTest`)

- **Rules A to C (`:~1886-1925`):** re-point them from `PackPolicy` and `PackFindingCode` to `LevelRules` and
  `PackCodes`. Update the bite fixtures `packlevelfixture/BuildsFindingDirectly`, `BuildsMissingDiagnostic` and
  `RefusesWithMismatch`.
- **New `PACK_CODES_HAVE_ONE_OWNER`:** only `PackCodes` builds a pack `ReasonCode`. It needs a bite fixture.
- **New `PACK_RULES_READ_NO_FILE`:** `packs.domain.rules..` names no `java.nio.file`, `java.io.File` or `java.io`
  stream types. It needs a bite fixture.
- **Unchanged:** `PACK_VALIDATION_IS_FRAMEWORK_FREE`, `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK`, `OUTSIDE_THE_DOMAIN` and
  the onion rules.

### T053: The subset tests and the guard test

- **`SurfaceSubsetTest`**, over every planted, example and golden pack:
  - **forward:** registration's faults ⊆ validate mode's faults, with equal code and wording (SC-001);
  - **reverse:** registration's faults equal today's golden registration faults (R1, F4).
- **`FailClosedGuardTest`:** a fake reader fault with `PACK` passing gives today's aggregate shape. One real
  A-M1 fixture row is reachable through the guard, if any is.

## Definition of Done

- `git grep -nE 'PackPolicy|PackFindingCode|PackFinding\b' -- service` returns nothing.
- The golden snapshot holds: the diff list is only Q1 and R2 rows.
- The subset tests and the guard test pass.
- ArchUnit is green, with bite tests.
- `./gradlew verifyAll --max-workers=1` is green, including the native build.

## Risks

- **Size.** About 80 test files are touched. Keep the commits ordered, and squash any that do not compile.
- **The 63 `new WholePackStore(` test sites** that assert refusals now get a store that does not refuse. Move their
  assertions to `PackRegistrationService` tests or rule tests. Do not add a judge back into the store.

## Reviewer guidance

- Is there exactly one judgement per surface?
- Is any judge left in the store?
- Does the guard answer exactly today's aggregate shape?
