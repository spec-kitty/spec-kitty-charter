---
work_package_id: WP13
title: The deletions and ArchUnit
dependencies:
- WP12
requirement_refs:
- FR-007
- SC-004
- NFR-001
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-closure-rulesets-01M7FA90
base_commit: 58215dcaeac36b3533cfa167204646b122949b76
created_at: '2026-11-17T18:21:59.837338+00:00'
subtasks:
- T051
- T052
- T070
history:
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Prompt generated for the S1 to S3 replan (squad-shaping)
- at: '2026-11-16T18:00:00Z'
  actor: planner-priti
  action: Folded S6 (PackReading replaces PackInspector; no rule widened)
- at: '2026-11-16T20:00:00Z'
  actor: planner-priti
  action: Folded analysis N5, N6 (PackInspector deletion moved to WP09; the diff list cites NFR-002)
agent_profile: java-jenny
authoritative_surface: service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java
create_intent:
- service/deployable/src/test/java/ai/speckitty/charter/deployable/packlevelfixture/BuildsAPackTokenTwice.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/packlevelfixture/RuleReadsAFile.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/packlevelfixture/StorageRuleNamesSpring.java
execution_mode: code_change
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFinding.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackFindingCode.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackFindingTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackPolicy*Test.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/Judged.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/StepEnhancementLoadChecksTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PromptSlotLoadChecksTest.java
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

# Work Package Prompt: WP13 - The deletions and ArchUnit

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
- Start with `spec-kitty agent action implement WP13 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Objective

Delete the glue and pin the shape. After this work package `PackPolicy`, `PackFinding`, `PackFindingCode`, the
converters and the parity-by-hand tests are gone, and ArchUnit holds the S1 to S3 placement, with the edges reaching
storage through the domain port `PackReading` (S6) and no rule widened. Spec FR-007, SC-004 and
NFR-001; plan IC-12; squad findings P6, Pr4, Pr7.

## Coordination

- This work package owns `PackFinding.java`, `PackFindingCode.java` and `PackFindingTest.java` (it deletes them);
  WP04 retyped and narrowed them out-of-map.
- `PackPolicy.java` is owned here; WP09 gave it a staging sort and result record (`PackPolicy.Judgement`) out-of-map,
  when it retyped `Precedence` and `PackVerdict` onto kernel types (N2). Both go with `PackPolicy`.
- `WholePackStore.java` and `DomainRepositoryService.java` are WP12's: T051's deletions there (`violationOf`, a
  paired emitter) are out-of-map, recorded.

## Subtasks

### T051: The deletions

- **Delete:**
  - `PackPolicy` (its residue went to WP03, its rules to WP06, WP07 and WP09's placement);
  - `PackFinding`, `PackFindingCode` and `PackFindingTest` (owned here) and `toRefusal` / `toDiagnostic`;
  - not here: the domain `PackInspector` port and its `Scope` went in WP09 (T047, N6), with their test callers
    repointed and the `LOAD_PATH` tests given their fates (N7). WP09 owns `PackInspector.java`;
  - `violationOf`, `findingOf` and `violationKind`, and the paired emitters left anywhere (squad finding 5);
  - `OverrideSourceRule` leftovers, if any;
  - `WholePackStore.violationOf` (WP12's file) and `FilesystemPackStore.violationsOf` (WP04's), once unused
    (out-of-map, recorded);
  - `Judged`.
- **Delete or fold** `StepEnhancementLoadChecksTest` and `PromptSlotLoadChecksTest`, which were parity by hand. First
  check that each case is covered by a rule or composition test; WP10 audits this.
- **The `PackPolicy*Test` classes:** delete them after confirming coverage moved (WP03, WP06, WP07 and WP09).
- **Confirm the shape:** `git grep -nE 'PackPolicy|PackFindingCode|PackFinding\b|PackValidation\b|PackInspector\b|TreeReader\b|ExcludedFile\b|enum Surface' --
  service` returns nothing. `PackInspector\b` has returned nothing outside `HexagonalArchitectureTest`'s comments since
  WP09; it stays in the pattern as a pin, and T052 rewrites those comments.

### T052: ArchUnit (`HexagonalArchitectureTest`)

- **Rules A to C (`:~1883-1925`):** re-point them from `PackPolicy` and `PackFindingCode` to `pack-validation`'s
  `placement.LevelRules` and `placement.PlacementCodes` (squad-shaping Q7), the one owner of the placement tokens. Update the bite fixtures `packlevelfixture/BuildsFindingDirectly`,
  `BuildsMissingDiagnostic` and `RefusesWithMismatch`.
- **`PACK_CODES_HAVE_ONE_OWNER`, reworded (P6):** each pack token is built exactly once, in `PackCodes`,
  `StorageCodes` or `PlacementCodes`; no other class builds a pack `ReasonCode`. Bite fixture `BuildsAPackTokenTwice`.
- **`PACK_RULES_READ_NO_FILE`:** `packs.domain.rules..` names no `java.nio.file`, `java.io.File` or `java.io` stream
  types, and no `packs.infra` type. Bite fixture `RuleReadsAFile`.
- **`STORAGE_RULES_ARE_FRAMEWORK_FREE` (new, S3):** `packs.infra.storage.rules` names no Spring, HTTP, commit store
  or lock, and no other `packs.infra.storage` class, directly or transitively. Bite fixture `StorageRuleNamesSpring`.
- **Unchanged (S6, operator 2026-11-16, "Port, no rule change"):** the onion rules
  (`ONION_APPLICATION_DEPENDS_ON_DOMAIN_ONLY`, `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` and the rest),
  `PACK_VALIDATION_IS_FRAMEWORK_FREE` (both halves, `OUTSIDE_THE_DOMAIN` included), `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK`,
  `STORAGE_RETURNS_DOMAIN` and `PORT_SIGNATURES_NAME_ONLY_DOMAIN_TYPES`: no allowlist entry is added, and no rule is
  widened. They already hold the S6 shape: `packs.application` and `pack-validation` name only `PackReading` and its
  domain types, and a class of either that named `packs.infra.storage.rules` would fail the onion rules (their
  `ReachesStorage` bite already covers it). Only their comments that name `PackInspector` change, to `PackReading`.
  The rules above are additions and re-pointings of rules whose subjects this work package deletes, not widenings.

### T070: The parity tests onto the new owners

- **Rewrite** `VocabularyEqualityTest`, `WireTokenParityTest` and `ReservedRefusalCodesTest` onto `PackCodes`,
  `StorageCodes`, `PlacementCodes` and the two mappers, keeping the same assertions. The tests may name `StorageCodes`
  (they are test code); the mappers do not (S6), so the parity runs through the `Rationale` that `PackReading`
  returns. Drop the `OFF_WIRE` allowance once nothing names
  `PACK_LEVEL_MISMATCH` off the wire.
- Never exclude a code from a parity check to make it pass (charter).

### Carried from WP06's review

- `rules/Findings.java` copies `PackFinding.reason()`'s RFC 6901 pointer encoding and `Reason` construction. When
  `PackFinding` goes, `Findings` is the one survivor; no second copy remains.

### Carried from WP09's review

- When rule B points at `PlacementCodes`, build `pack_level_mismatch` from `RefusalCode.PACK_LEVEL_MISMATCH.token()`
  instead of the literal, and delete `PackFindingCode`'s copy, so the token has one source.
- `PolicyReadouts` and `PlantedFixtures.policyReadout()` (test staging for `PackPolicy.judge`) go with `PackPolicy`.

### Carried from WP05 and WP12 (operator, 2026-11-17: "Switch it in WP13")

- **`WholePackCensus` reads the `TreeCensus`.** The round-trip gate (`PackRoundTrip`, `DomainRoundTrip`) still walks and
  classifies a tree on its own. Extend the census with what the gate's path-to-gate-kind map needs, as domain values
  with no `Path`: symlink entries, the `other:<segment>` buckets, the `retired:` and `excluded:` prefixes, companion
  owner kinds, and a pack whose descriptor cannot be read. Then point `WholePackCensus.of` at it, so one owner of
  "held" remains (data model). The baseline lists and the drift test stay unchanged.
- `StoreRead.metOnlyByTheStore` picks store-only faults by message prefix (`"Constraint breached"`, `"template "`):
  carry them typed in `PackRead` through `readKind` instead (WP12 review cycle 2, NIT-B).
- Correct the R2 note's "Cascades the store half does not reach" bullet and the display name of
  `StoreHalfFaultsTest.aRepeatedStepDoesNotHideADistinctFault`. The first fault the entity build meets stops it (in the
  plant, the renamed step's prompt path, thrown while the builder is filled); the domain guards check keys before step
  ids (WP12 review cycle 3, MINOR-1). Rewrap `AssemblyRules.sameFault`'s javadoc (`:149-150`).
- Rename the test classes still named after `LoadChecks` (`StepEnhancementLoadChecksTest`, `PromptSlotLoadChecksTest`,
  `PackFileSetBudgetTest.theLoadChecksAreWithinBudget`) after what they test now.

## Definition of Done

- The `git grep` of T051 returns nothing.
- The golden snapshot holds: every diff row cites an entry of the diff list of NFR-002.
- ArchUnit is green, with a bite test for each new or reworded rule, and the onion, `PACK_VALIDATION_IS_FRAMEWORK_FREE`,
  `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` and `STORAGE_RETURNS_DOMAIN` rules unchanged (S6).
- `./gradlew verifyAll --max-workers=1` is green, including the native build.
- `WholePackCensus` reads the `TreeCensus`; no second walk or classification of a pack tree remains (operator,
  2026-11-17).

## Risks

- **Deleting coverage.** Each deleted test's cases are first found in a rule, composition or mapper test.

## Reviewer guidance

- Does each ArchUnit rule bite?
- Is any token built twice?
- Was any existing rule widened, or an allowlist entry added? None may be (S6).
