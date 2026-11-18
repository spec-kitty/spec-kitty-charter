---
work_package_id: WP09
title: The validation edge, PackCheck in pack-validation
dependencies:
- WP07
- WP08
requirement_refs:
- FR-005
- FR-008
- FR-010
- C-003
- NFR-003
planning_base_branch: slice-5-554-pack-closure-rulesets
merge_target_branch: slice-5-554-pack-closure-rulesets
branch_strategy: Planning artifacts for this mission were generated on slice-5-554-pack-closure-rulesets. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into slice-5-554-pack-closure-rulesets unless the human explicitly redirects the landing branch.
subtasks:
- T047
- T029
- T060
- T061
- T062
history:
- at: '2026-11-16T00:00:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
- at: '2026-11-16T12:00:00Z'
  actor: planner-priti
  action: Replanned for the S1 to S3 rulings (squad-shaping)
- at: '2026-11-16T18:00:00Z'
  actor: planner-priti
  action: Folded S6 (PackCheck reads through PackReading; Placement wraps PackReadout; no rule widened)
- at: '2026-11-16T20:00:00Z'
  actor: planner-priti
  action: Folded analysis N2, N5, N6, N7 (Precedence and PackVerdict on kernel types; PackInspector deleted here)
agent_profile: java-jenny
authoritative_surface: service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackCheck.java
create_intent:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackCheck.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/Placement.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/PlacementCodes.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/PlacementCodesTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackVerdicts.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/LevelRules.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/OrgFragmentRules.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/package-info.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackVerdictsTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/LevelRulesTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/OrgFragmentRulesTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/golden/ValidateGoldenTest.java
- service/pack-validation/src/test/resources/golden/validate.json
execution_mode: code_change
owned_files:
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackCheck.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/Placement.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/PlacementCodes.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/PlacementCodesTest.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackVerdicts.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/LevelRules.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/OrgFragmentRules.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/placement/package-info.java
- service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/PackLoad.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackValidation.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackInspector.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackCheckTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackVerdictsTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/LevelRulesTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/placement/OrgFragmentRulesTest.java
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/golden/ValidateGoldenTest.java
- service/pack-validation/src/test/resources/golden/validate.json
- service/pack-validation/src/test/java/ai/speckitty/charter/packvalidation/PackLoadTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/domain/PackValidationTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/SubstepDelegationPlantsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/OrgCharterRetirementTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/EdgeEndpointPlantsTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/AssetIdNotKebabTest.java
- service/deployable/src/main/java/ai/speckitty/charter/deployable/CharterWiring.java
- service/deployable/src/test/java/ai/speckitty/charter/deployable/CharterWiringTest.java
role: implementer
tags: []
tracker_refs:
- '#554'
---

# Work Package Prompt: WP09 - The validation edge, PackCheck in pack-validation

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
- Start with `spec-kitty agent action implement WP09 --agent claude --mission pack-closure-rulesets-01M7FA90`.
- Commits are signed (`-S`), with no Co-Authored-By trailer and no model names.
- `main` is integrated by signed rebase only.
- Gradle runs in the foreground with `--max-workers=1`, one run at a time, never `--stop`.
- An edit to a file another work package owns is an **out-of-map edit**: one purpose, recorded in the review log.

## Coordination

Out-of-map edits, each with one purpose and recorded in the review log:

- **N2, the retyping onto kernel types:** WP03's `rules/Precedence.java` and `rules/PrecedenceTest.java`, and WP05's
  `PackVerdict.java` (T060). WP13's `PackPolicy.java` and `PackPolicyTest.java` keep their staging sort and result
  (T060). The sites that declare `PackPolicy.judge`'s result as a `PackVerdict`: WP02's `ExamplePacksTest` and WP05's
  `FileSystemPackInspectorTest`.
- **N6, the `PackInspector` deletion (T047):** WP05's `FileSystemPackInspector.java` (drop `implements PackInspector`
  and `inspect(root, scope)`) and `FileSystemPackInspectorTest.java` (N7's fates); WP02's `ExamplePacksTest` and
  `ExcludedFilesAgreementTest`; WP13's `PromptSlotLoadChecksTest` and `StepEnhancementLoadChecksTest`; and the test
  classes no work package owns: `DomainRepositoryServicePromptFactsTest`, `GlossaryOldShapeRefusalTest`,
  `MissionTypeBindingTest`, `PackDocumentRoundTripTest`, `PackManifestRoundTripTest`, `PackRootSpellingTest`,
  `ProcedureEdgesTest` and `RetiredFilesTest`; `PackRoot.java`'s Javadoc link and `packvalidation/package-info.java`'s
  text. `HexagonalArchitectureTest`'s two comments that name `PackInspector` stay for WP13 (T052).
- **The golden:** WP01's `surfaces.json` and `SurfaceAnswers` (T061).

## Objective

Make validate mode **`pack-validation`'s edge** (the operator's direction of 2026-11-16, R3 as amended, S2):
`PackCheck`, moved from `packs.domain.PackValidation`, runs the one read through the domain port `PackReading` (S6),
appends the placement rules' `Rationale` to the readout's with `Rationale.and`, and `PackVerdicts` maps the
findings. Placement (the configured level and
the org fragment) is the edge's own extension. After this work package `PackRulesets` and `StorageRulesets` judge
validate mode; registration and the compose read switch in WP12. Spec FR-005, FR-008 (`PackVerdicts`), C-003 and
NFR-003; plan IC-08; squad findings A6/Pr2, P5, Pr4, Pr5.

## Subtasks

### T047: `PackCheck`

- **The move:** `git mv` `packs.domain.PackValidation` to `pack-validation`'s `packvalidation.PackCheck`, then rewrite
  it over one input type (U1, I6):

  ```java
  // packvalidation.placement
  public record Placement(PackReadout read, Optional<PackLevel> configuredLevel /* and the org facts */) {}
  // packvalidation.PackCheck, constructed with the PackReading its caller supplies (S6)
  static ModularRuleset<Placement> placement() {
      return ModularRuleset.<Placement>emptyRules()
              .must(LevelRules.RULES)                                     // Rule<Placement>
              .must(OrgFragmentRules.RULES);                              // Rule<Placement>
  }
  public PackVerdict validate(PackRoot root) {
      PackReadout read = reading.read(root);                             // one walk; its Rationale is the storage verdict
      Rationale all = read.rationale().and(placement().check(new Placement(read, root.configuredLevel())));
      return PackVerdicts.of(read.files(), all);
  }
  ```

  **The merge (S6):** a kernel `Rationale` is not a ruleset, so `must` cannot extend it. The kernel's
  `Rationale.and(Rationale)` is the union: the readout's reasons followed by placement's, neither changed; the joined
  `isPassing()` fails when either part holds a `VIOLATION`. No kernel change is needed.
  **The manifest gate** ran inside the readout's `Rationale` (WP07's `StorageRulesets.verdict`), before placement is
  checked, so placement's reasons (their codes are `PlacementCodes` constants) never gate it, and registration's
  faults stay a subset of validate mode's: the existing choice, kept by construction.
- **What it names:** `packs.domain` (`PackReading`, `PackReadout`, `PackRoot`, `PackLevel`, `PackVerdict`,
  `ClassifiedFile`) and the kernel only (C-003, S6). It names nothing in `packs.infra`. The `PackReading`
  (`FileSystemPackInspector`, WP07) is supplied by the caller: `:pack-validation` and `:deployable` tests now,
  pack-check's main in #478. `deployable` main does not construct `PackCheck` (C-003: `pack-validation` is on its test
  classpath only). `PackLoad` has no caller but its test: fold it into `PackCheck`, so nothing in main consumes the
  `PackInspector` port any more.
- **The `PackInspector` port and its `Scope` go (N6, moved here from WP13 T051).** Once `PackCheck` reads through
  `PackReading` and `PackLoad` is folded, delete `packs.domain.PackInspector` (owned here) with its `Scope`, and drop
  `implements PackInspector` and `inspect(root, scope)` from `FileSystemPackInspector` (out-of-map), which keeps only
  `PackReading.read` and the package-private `readTree`: one domain port of the read, no second port beside it.
  Repoint every test caller (lane-b today: 18 sites in `FileSystemPackInspectorTest`, and the other classes listed under
  Coordination, all in `packs.infra.storage`):
  - a site that feeds `PackPolicy.judge` or asserts the read's facts calls `readTree(root)` (same package) and reads
    `contents()`, `census()` or `readFaults()`; WP05 moved the classification to the census;
  - a site that asserts what the port returns calls `read(root)`.
- **N7, the 9 `Scope.LOAD_PATH` sites of `FileSystemPackInspectorTest`** (lane-b lines before WP05; re-find them by
  test name). The rule: a test that asserts what the read yields is repointed to `readTree(root)`, which reads the
  whole tree; a test whose only subject is the scope (one scope reads like the other) is deleted.
  - **Repoint (8 sites, 7 tests):** `aPackIsReadAsItsDescriptorItsShardsInNameOrderAndItsFragment` (:81),
    `aSourceShardNamedLikeTheRegistrationShardIsRefusedButTheStoresOwnIsRead` (:108, :115),
    `aFileThatIsNotThereIsAbsent` (:120), `aFileThatIsNotYamlOrBreaksItsSchemaIsUnreadableAndNeverThrows` (:134),
    `aRootThatIsNoDirectoryIsRefusedAsUnreadable` (:146) and `anInspectedRootThatCannotBeListedIsRefused` (:1053),
    both asserting the root fault as WP05 T026 settled it, and `aDirectoryThatEndsInGraphYamlIsNotAShard` (:1043).
  - **Delete (1 site):** `theFullScopeReadsThePackFilesOfTheLoadPathAsTheLoadPathDoes` (:248). Its subject, that the
    `FULL` read agrees with the `LOAD_PATH` read, goes with `Scope`; the shard read once is already pinned by the
    repointed tests and `OneReadTest`.
  - **The two `for (Scope scope : Scope.values())` loops** (:160, :189) assert behaviour under both scopes: drop the
    loop and assert once over `readTree(root)`. The `full(dir)` helper and the `Scope.FULL` sites become
    `readTree(root)`.
- **Confirm:** `git grep -nE '\bPackInspector\b|Scope\.(FULL|LOAD_PATH|values)' -- service
  ':!service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java'` returns
  nothing.
- **Open case 6 holds:** a file the census marks not held is warned `file_not_held` and judged no further (WP11).
- **`PackRulesets.closure()`:** add the one-line delegate to WP08's `CompositionRules` (an out-of-map edit of WP07's
  file), so the core exposes both compositions. `PackCheck` does not run it; the pack-set check is #478's.

### T029: The placement rules (`packvalidation.placement`, S2)

- **Source:** `PackPolicy.level` (`:126-160`) and `orgFragmentRules` / `isOrgPack` (`:574-600`).
- **The level rules** fire only when the root has a configured level, as today.
- **Codes (squad-shaping Q7, the default taken):** `pack_level_missing` (WARNING), `pack_level_mismatch` and
  `org_fragment_missing` are built once, in `packvalidation.placement.PlacementCodes`, the only module that emits them.
  `PackCodes` does not build them (WP04). Until this subtask, `PackPolicy` emits them through WP04's `PackFindingCode`
  view; from here, `PackCheck` emits them through `PlacementCodes` only. `PlacementCodesTest`: every token matches
  `[a-z][a-z0-9_]*`, and no token is also built in `PackCodes` or `StorageCodes`.
- **Subject:** the rules are `Rule<Placement>`, over `Placement(PackReadout read, ...)` (S6), not a `TreeRead`: they
  read the configured level and the org facts, and the descriptor through `placement.read().contents()`. Their
  composition, `PackCheck.placement()`, is an added check over the readout, merged with `Rationale.and` (T047).
- **Validate only:** placement is a deployment-config check, an edge extension (S2). Registration never runs it.
- **ArchUnit:** rules A to C name these owners (`PlacementCodes` and the placement rules) after WP13.

### T060: `PackVerdicts`

- **`PackVerdicts.of(List<ClassifiedFile> files, Rationale) -> PackVerdict`:** the readout's classified files and the
  joined `Rationale` (the read faults are inside it, S6), sorted by `Precedence.comparator(PlacementCodes.ALL)`, with
  the classification of every file as today (data-model.md, "Mappers").
- It reads `failures()` only. It hands `Precedence` the `PlacementCodes` constants as the pack-wide group, never
  compares `value()`, and names no storage code.
- **The retyping onto kernel types (N2; out-of-map on WP03's `Precedence` and WP05's `PackVerdict`).** `Precedence` is
  in `packs.domain.rules` (`:packs`), which may never name `pack-validation` (C-003):
  - `Precedence.comparator(Set<ReasonCode> firstGroup)` returns a `Comparator<Reason>`, with today's grouping logic
    (`PackPolicy.group`) and the first group injected: a code in the set ranks 4, as `PACK_WIDE` does today; then
    `comparePaths` over `location().file()`, then `comparePlaces` over the pointer's segments. `group`,
    `comparePaths` and `comparePlaces` stay public plain functions. `Precedence` names only kernel and `java` types,
    never `PlacementCodes` or `PackFinding`.
  - `PackVerdict.findings` becomes `List<Reason>`.
  - `PlacementCodes.ALL` is the set of its three constants; `PackVerdicts` supplies it.
  - `PrecedenceTest` keeps every case (M-7): the comparator cases build kernel `Reason`s and pass a fixture group set;
    `PackVerdictsTest` pins that each `PlacementCodes` constant sorts in group 4.
- **`PackPolicy` until WP13 (staging inside the single pull request, R4 b, never shipped).** `PackPolicy.judge` has
  no main caller after T047, but its tests run until WP13 deletes it. It keeps a local `Comparator<PackFinding>`
  composed from `Precedence.group`, `comparePaths` and `comparePlaces` with its own `PACK_WIDE`, and returns a
  nested staging record `PackPolicy.Judgement(List<ClassifiedFile> files, List<PackFinding> findings)` instead of
  `PackVerdict` (out-of-map on WP13's file). Callers that read `.files()` and `.findings()` compile unchanged; the
  sites that declare the result as `PackVerdict` change their declared type: `PackPolicyTest:110`,
  `ExamplePacksTest:15,36`, `FileSystemPackInspectorTest:23,769,832,909`. WP13's `git grep` pins its deletion.
- **`PackVerdict`'s consumers (lane-b, grepped):** main: `PackPolicy.judge` (`:174`, `:204`; staging above) and
  `PackValidation.validate` (`:33`; becomes `PackCheck`). No `deployable` main class names `PackVerdict`, and there
  is no deployable validate endpoint: the only `deployable` reference is the `PackValidation` bean (T062). Tests:
  `PackValidationTest:86` (becomes `PackCheckTest`), the golden harness `SurfaceAnswers:219-229` (its validate half
  becomes `ValidateGoldenTest`, T061), and the `PackPolicy.judge` sites above.
- **The order does not move (NFR-002).** `ValidateGoldenTest` reproduces `validate.json` byte for byte, its order
  included, apart from the diff list of NFR-002; it is the proof that the retyped comparator keeps today's order.

### T061: The tests and the validate golden move with their subject (Pr5)

- **`PackValidationTest`** becomes `PackCheckTest` in `:pack-validation:test`; the placement and verdict tests are new
  beside it.
- **The validate golden:** split validate mode's answers out of WP01's `surfaces.json` into
  `pack-validation/src/test/resources/golden/validate.json`, byte for byte, and move the validate half of
  `SurfaceAnswers` into `ValidateGoldenTest` (out-of-map edits of WP01's files, recorded). Every golden diff row cites
  an entry of the diff list of NFR-002; validate mode's R2 rows were applied in WP04.
- **The harness reads `Reason`s (N2):** each row keeps its columns: `code` is `code().value()` (the token
  `PackFindingCode`'s lower-cased name gave), `file` is `location().file()`, and `place` is the pointer's segments.
  `validate.json` itself does not change for the retyping.
- **The callers of `new PackValidation(`** in `:packs` tests (`SubstepDelegationPlantsTest`,
  `OrgCharterRetirementTest`, `EdgeEndpointPlantsTest`, `AssetIdNotKebabTest`, and `GlossaryPlantedRefusalsTest`
  out-of-map) call `PackCheck` through the existing `testImplementation(project(":pack-validation"))` edge, or assert
  on the rule they mean directly.

### T062: The wiring and the module rule

- **The bean goes:** `CharterWiring:87-88`'s `PackValidation` bean is removed, with `CharterWiringTest:199`'s check,
  because `deployable` keeps `pack-validation` on its test classpath only and no main code injects the bean (C-003).
- **ArchUnit is unchanged (S6, operator 2026-11-16, "Port, no rule change").** `PackCheck` names only
  `packs.domain` and the kernel, so `PACK_VALIDATION_IS_FRAMEWORK_FREE` (both halves, `OUTSIDE_THE_DOMAIN`
  included) and `ONION_ONLY_DEPLOYABLE_COMPOSES_ADAPTERS` hold as they stand. Do not edit
  `HexagonalArchitectureTest` (WP13's file) here. The transitive half walks `PackReading`, an interface, not its
  implementation; `THE_PACKS_LOAD_REACHES_NO_FRAMEWORK` walks `FileSystemPackInspector`, the storage rules it
  composes included.
- **`pack-validation/build.gradle.kts`**'s module comment says what the module now is; WP10 owns the file and re-sets
  the margin.
- **Carried from WP04 (validate mode's gains, R2 rows 24, 29, 30 and 32; row 31 is already one answer).** Once validate mode reads the one read, it
  gains the no-`pack.yaml` violation (row 24) and the manifest's membership and regularity and the
  policy-file faults (rows 29, 30 and 32) by construction. Row 24 refuses the 34 planted packs without a descriptor that core
  accepts: add a DIV entry to `docs/conformance/divergences.yaml` and the register for it, regenerate the golden, and
  record the rows in the R2 note and a `### Breaking` fragment.

### Carried from WP07's review

- `PackPolicy.notJudgedHere` is WP07's staging (validate mode takes from the verdict only what `PackPolicy.judge` does
  not judge itself). Delete it when validate mode switches to `PackCheck` over the readout's rationale. Until then it
  removes any `pack_unreadable` at `pack.yaml` when the descriptor is absent; narrow it to `AssemblyRules`' reason if
  it survives a step.
- `ManifestMembership.mismatch` is a one-line delegate to `ManifestRules.mismatch`; point callers at the rule.

## Definition of Done

- Validate mode is `PackCheck` in `pack-validation`; `packs.domain.PackValidation` is gone.
- `PackCheck` reproduces the validate golden, order included, apart from the diff list of NFR-002.
- `Precedence.comparator(Set<ReasonCode>)` returns `Comparator<Reason>` and names no `PlacementCodes`;
  `PackVerdict.findings` is `List<Reason>`; `PackVerdicts` supplies `PlacementCodes.ALL` (N2).
- The `PackInspector` port and its `Scope` are gone; T047's `git grep` returns nothing, and each of the 9
  `Scope.LOAD_PATH` sites was repointed or deleted as T047 lists (N6, N7).
- No `Surface` enum exists; placement is composed only in `pack-validation`.
- The `PackValidation` bean is gone and `CharterWiringTest` is green.
- ArchUnit is green, with every rule unchanged (S6).
- `PackCheck` names no `packs.infra` type.
- `verifyAll` is green.
- A narrowed PIT run on `packvalidation` is reported (the module now holds #554's code, NFR-003).
- Validate mode answers R2 rows 24, 29 and 30; the DIV entry for row 24 is recorded (carried from WP04). Row 32 (the
  policy file `mutability_policy` names) moves to WP12 with the other store-half faults.

## Risks

- **`pack-validation`'s floor.** The module gains main code with its tests; run the narrowed PIT before and after.
- **The golden split.** Keep `surfaces.json`'s other surfaces byte-equal while the validate half moves out.

## Reviewer guidance

- Does `PackCheck` name anything in `packs.infra`? It may not (S6).
- Is placement absent from every `:packs` composition?
- Did every validate-mode test move with its subject?
- Does any `:packs` class name `PlacementCodes`? None may (C-003).
- Did a deleted `LOAD_PATH` test assert anything but the scope?
