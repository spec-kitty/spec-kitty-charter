---
work_package_id: WP01
title: Domain finding model and the pack-level policy (#48)
dependencies: []
requirement_refs:
- C-003
- C-005
- C-007
- FR-001
- FR-002
- FR-003
- SC-003
- SC-004
planning_base_branch: feat/pack-schemas-and-level-policy
merge_target_branch: feat/pack-schemas-and-level-policy
branch_strategy: Planning artifacts for this mission were generated on feat/pack-schemas-and-level-policy. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into feat/pack-schemas-and-level-policy unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-pack-schemas-and-level-policy-01M5AFMA
base_commit: 8b9e38308e7165cc4cc974b14ed36b316a18806c
created_at: '2026-10-19T18:18:25.660766+00:00'
subtasks:
- T001
- T002
- T003
- T004
- T005
- T006
history:
- at: '2026-10-19T18:10:00Z'
  event: created
  by: /spec-kitty.tasks
- at: '2026-10-19T19:05:00Z'
  event: revised
  by: post-tasks squad (renata, priti, ivan)
agent_profile: java-jenny
authoritative_surface: service/charter-domain/
create_intent:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackRoot.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackContents.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFinding.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackVerdict.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackInspector.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackFindingTest.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackDescriptorReaderTest.java
execution_mode: code_change
model: sonnet
owned_files:
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackDescriptor.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterPack.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/CharterPackRepository.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackRoot.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackContents.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFinding.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackFindingCode.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackVerdict.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackInspector.java
- service/charter-domain/src/main/java/ai/speckitty/charter/domain/PackPolicy.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackPolicyTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/PackFindingTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/CharterOfferingTest.java
- service/charter-domain/src/test/java/ai/speckitty/charter/domain/GovernanceFingerprintTest.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/PackListingQueryTest.java
- service/charter-read-app/src/test/java/ai/speckitty/charter/read/ActionContextQueryTest.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/PackDescriptorReader.java
- service/adapter-out-yaml/src/main/java/ai/speckitty/charter/yaml/FileSystemCharterPackRepository.java
- service/adapter-out-yaml/src/test/java/ai/speckitty/charter/yaml/PackDescriptorReaderTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/HexagonalArchitectureTest.java
- service/charter-service/src/test/java/ai/speckitty/charter/service/packlevelfixture/**
- service/charter-service/src/test/java/ai/speckitty/charter/service/networkntfixture/**
- service/charter-service/src/test/java/com/networknt/fixture/**
role: implementer
tags: []
tracker_refs: []
---

# WP01 — Domain finding model and the pack-level policy (#48)

## ⚡ Do This First: Load Agent Profile

Before reading anything else, load your assigned agent profile with the `/spk-charter-profile-load`
skill (profile: `java-jenny`). Then load governance context for this action:
`spec-kitty charter context --action implement`. The charter (`.kittify/charter/charter.md`) wins over
this prompt. Read the **mission-wide rules** in [tasks.md](../tasks.md) (red-first, style gates,
Gradle invocation) before you start.

## Objective

Issue #48 asks for the pack-level policy to be decided in the domain only. Today it is split:

- the YAML adapter builds the `pack_level_missing` diagnostic and its text in two places:
  `PackDescriptorReader.java:37-41` and `FileSystemCharterPackRepository.java:95-100`;
- the domain builds the `pack_level_mismatch` refusal inside `CharterPack`'s canonical
  constructor (`CharterPack.java:40-47`).

This WP does three things:

1. It moves both outcomes, and their detail text, into a new pure domain service, `PackPolicy`.
2. It introduces the **finding model** that every later WP builds on: `PackFinding`,
   `PackFindingCode`, `PackRoot`, `PackContents`, `PackVerdict` and the `PackInspector` port.
3. It adds ArchUnit rules that keep the constants confined.

There is no contract change, and every pinned text stays as it is (spec FR-001, FR-002, FR-003;
R4).

Read first:
- [spec.md](../spec.md): US1, FR-001 to FR-003, R4;
- [plan.md](../plan.md): "One owner", "Load is a projection of judge", "Precedence" and
  "Pack-level codes confined";
- [data-model.md](../data-model.md), all of it;
- [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md).

## Branch Strategy and environment

- **Branches.** The planning base and the final merge target are both
  `feat/pack-schemas-and-level-policy`. You execute in the lane worktree from `lanes.json`.
- **Implement command:**
  `spec-kitty agent action implement WP01 --agent claude --mission pack-schemas-and-level-policy-01M5AFMA`.
- **Open-packs checkout.** This WP runs in parallel with WP03, which moves the open-packs layout.
  Run every open-packs task here against the **old-layout** checkout at `7f063ae`:
  `-PopenPacksDir=/home/user/spec-kitty/spec-kitty-open-packs`. That clone is detached at
  `7f063ae…`, clean, with `built-in/` at its root. Always pass it as an absolute path: the default
  `../spec-kitty-open-packs` resolves wrongly inside `.worktrees/<lane>/`.

## Subtasks

### T001 — Pin the pack-level texts red-first

**Purpose**: The refactor must be shown to change nothing visible, so the texts are pinned at the
domain level before anything moves.

**Steps**:

1. Create a `PackPolicy` **stub** (T003's methods, each throwing `UnsupportedOperationException`)
   so the tests compile.
2. Write `PackPolicyTest` with four cases:
   - **Absent descriptor** (no `pack.yaml`), configured ORG, name `qa`: one `pack_level_missing`
     warning with detail `org pack qa has no pack.yaml; the configured level org is used`
     (`packs.feature:43`).
   - **Descriptor without `pack_level`**, name `internal`, configured ORG: detail
     `pack.yaml of internal has no pack_level; the configured level org is used`
     (`packs.feature:62`).
   - **Declared BUILT_IN, configured ORG**, name `rogue`: a blocking `pack_level_mismatch` with
     detail `Pack 'rogue' is configured as org but declares pack_level built-in`
     (`packs.feature:80`).
   - **Declared equals configured**: no finding.
3. Run the test. Red means the assertions fail on the stub's exception, not a compile error.
   Record the output.
4. `PackDescriptorReaderTest` (adapter) pins today's texts as **ratchets**. They must pass on
   today's code:
   - `pack.yaml of <n> declares an unknown pack_level '<t>'` (`PackDescriptorReader.java:43-46`;
     no test pins it today);
   - `pack.yaml of <n> has no <key>`;
   - `pack.yaml of <n>: <key> must be a string`;
   - `pack.yaml of <n> is not a mapping`.

### T002 — Finding model types and the `PackInspector` port

**Purpose**: A stable model for every later WP (plan: "One owner").

**Steps**: create these types in `ai.speckitty.charter.domain`. They are plain Java with Javadoc
on every public element and `@param` for every record component (doclint). Use no annotations
except jspecify `@Nullable`. Nested records in an interface take **no** `public`/`static`
modifiers (Checkstyle `RedundantModifier`).

- **`PackRoot`**: `record PackRoot(String name, Optional<PackLevel> configuredLevel, String locator)`.
- **`PackContents`**: the full record from data-model.md, with its nested types:
  - `sealed interface FileRead<T>` with `Absent(String file)`,
    `Unreadable(String file, ParseFailure parse, List<SchemaViolation> violations)` and
    `Read(String file, T value)`;
  - `SchemaViolation`, `ParseFailure` and `RawGraph` (with `enum Shape { SHARD, FRAGMENT }`);
  - `RawNode`, `RawEdge`, `ClassifiedFile`, `StepContractFacts`, `ArtifactFacts` and
    `AssetFacts`.

  Lists are copied with `List.copyOf`.
- **`PackFindingCode`**: the catalogue in
  [contracts/pack-finding-codes.md](../contracts/pack-finding-codes.md), **except
  `PACK_UNKNOWN_KEY`**, which WP06 adds. Its wire projection goes in with the contract value in
  WP07: adding a `Diagnostic.Code` constant now would turn `adapter-in-rest`'s
  `WireTokenParityTest` red until the contract has the value.
  - Each code has a fixed severity.
  - `refusal()` returns the `RefusalCode` for a load-path blocking code.
  - `diagnostic()` returns the `Diagnostic.Code` for a load-path warning code.
  - Both throw `IllegalStateException` for a validate-only code.
  - Write the projections as an explicit switch expression. The ArchUnit rule exemption covers
    the synthetic `PackFindingCode$1` class (T005).
- **`PackFinding`**: `record PackFinding(PackFindingCode code, String file, List<String> place, String detail)`,
  with `severity()`, `toRefusal()` and `toDiagnostic()`.
- **`PackVerdict`**: `record PackVerdict(List<ClassifiedFile> files, List<PackFinding> findings)`.
- **`PackInspector`**: the port, with `enum Scope { LOAD_PATH, FULL }` and
  `PackContents inspect(PackRoot root, Scope scope)`. Its Javadoc says it never throws on file
  content, only on I/O.

Write `PackFindingTest` first, against stubs. It covers severity per code, the projections, and
`IllegalStateException` for validate-only codes.

### T003 — `PackPolicy` with the level rules

**Purpose**: The single owner (data-model "Domain service").

**Steps**:

1. `PackPolicy` is a `final` utility class: static methods only, with a private constructor
   (Checkstyle `HideUtilityClassConstructor` and `FinalClass`). Every later WP calls it statically.
   It provides:
   - **`static List<PackFinding> level(String name, Optional<PackLevel> configured, FileRead<PackDescriptor> descriptor)`**.
     It fires only on `Absent` or `Read`, never on `Unreadable`.
     - `Absent` with a configured level gives the "has no pack.yaml" warning.
     - `Read` with no declared level and a configured level gives the "has no pack_level" warning.
     - A declared level that differs from the configured one gives the mismatch blocking finding.
     - **No configured level** (bare path) gives no finding. Core's `validate_pack` without
       `expected_level` skips the check.
     - The findings are attached to `pack.yaml` with an empty place.
   - **`static PackVerdict judge(PackContents contents)`**. For now it aggregates `level(...)`.
     It orders the findings with the plan's precedence comparator, which lives here; WP05 refines
     it.
   - **`static List<String> loadPath(PackContents)`**: the one shared restriction (`pack.yaml`,
     the root shards and `drg/fragment.yaml`).
   - **`static CharterPack load(PackContents contents, DoctrineGraph graph)`**, the WP01 shape.
     - Throw the first blocking finding of `judge`, restricted to `loadPath`, with `toRefusal()`.
     - Otherwise build the `CharterPack` with the restricted warnings as `Diagnostic`s.
     - An empty `configuredLevel` throws `IllegalArgumentException("load needs a configured level")`.
       A bare path is validate-only.
     - **WP05 adds** the overload `load(PackContents)`, which builds the graph from `RawGraph`s, and
       keeps this one. **WP07 deletes** this overload when the adapter switches.
2. **`PackDescriptor`**:
   - replace `PackLevel level` with `Optional<PackLevel> declaredLevel`;
   - add `Optional<String> requiresCore` and `List<String> undeclaredKeys`. Until WP07 the
     adapter fills these with `Optional.empty()` and `List.of()`.
3. **`CharterPack`**. Keep the record shape. Replace the constructor's `CharterRefusal` with an
   `IllegalArgumentException` guard: a present declared level that differs from the configured
   `level` is a programming-error invariant. Update the Javadoc.
4. **`CharterOfferingTest:46-53`**. Its construction-time mismatch assertion moves to
   `PackPolicyTest`, with code and detail. In `CharterOfferingTest`, assert the
   `IllegalArgumentException` guard instead. Say in the commit message that this is a behavior
   move.

### T004 — The adapter reports the declared level as a fact

**Steps**:

1. **`PackDescriptorReader.read`**:
   - Remove the `PACK_LEVEL_MISSING` construction and the defaulting to the configured level.
   - Return a `PackDescriptor` with an empty `declaredLevel` when the key is absent.
   - Keep today's refusals (the T001 ratchets). WP07 replaces them with schema facts.
2. **`FileSystemCharterPackRepository.loadPack`**:
   - Build `PackContents` as follows:
     - `descriptor`: `FileRead.Read(...)`, or `FileRead.Absent("pack.yaml")` when there is no
       file;
     - `shards`: one `FileRead.Read(file, RawGraph(file, SHARD, List.of(), List.of()))` per root
       shard file;
     - `fragment`: `Read(…, RawGraph(…, FRAGMENT, List.of(), List.of()))` when
       `drg/fragment.yaml` exists, `Absent("drg/fragment.yaml")` otherwise. The empty `RawGraph`s
       are placeholders: the real graph still comes from today's `GraphDocumentReader`. The
       file's presence is still reported truthfully, which WP06 and WP07's org-fragment rule
       relies on.
     - `PackRoot(name, Optional.of(level), path)`.
   - Read the graphs as today, then call `PackPolicy.load(contents, graph)`.
   - Delete the `Diagnostic` construction at `:95-100`.
3. **Precedence.** Shard read errors are still thrown by `GraphDocumentReader` before `PackPolicy`
   runs, so a pack with a bad shard and a level mismatch still refuses with the shard's
   `drg_schema_error`, which is today's behavior. WP07 (T034) owns the two-fault test.

**Validation** (`-PopenPacksDir` set to the old-layout clone, as above):
- `:adapter-out-yaml:check`, which includes the round-trip with the `DIAGNOSTICS` table unchanged;
- `:charter-service:test`, which includes `packs.feature` scenarios 1-3, unchanged.

### T005 — ArchUnit: the constants confined, networknt confined

**Steps** (in `HexagonalArchitectureTest`, modelled on the `internal_error` rule at `:95-117` and
its `internalerrorfixture/`):

1. **Rule A.** No class accesses the field `Diagnostic.Code.PACK_LEVEL_MISSING`, except these
   exemptions:
   - classes whose name **starts with** `ai.speckitty.charter.domain.PackPolicy`,
     `ai.speckitty.charter.domain.PackFindingCode` or `ai.speckitty.charter.domain.Diagnostic`.
     Use a `DescribedPredicate` on `getName().startsWith(...)`, so synthetic `$1` and `$SwitchMap`
     classes are covered.
2. **Rule B.** No class accesses `RefusalCode.PACK_LEVEL_MISMATCH`, except the same prefixes
   (plus `RefusalCode`) and `ai.speckitty.charter.rest..`. `RefusalHandler$1` reads it with
   `getstatic`.
3. **Rule C.** No class accesses `PackFindingCode.PACK_LEVEL_MISSING` or
   `PackFindingCode.PACK_LEVEL_MISMATCH`, except the `PackPolicy` and `PackFindingCode` prefixes.
   This closes the `new PackFinding(...).toDiagnostic()` bypass.
4. **Negative fixtures** go in `packlevelfixture/`, one class per route (A, B and C). Each
   negative test asserts that `AssertionError` names the fixture class.
5. **Rule D.** No class outside `ai.speckitty.charter.yaml..` depends on `com.networknt..`. Its
   fixture needs no dependency:
   - a stand-in `com.networknt.fixture.Stand` under `charter-service/src/test/java/com/networknt/fixture/`;
   - a user class in `networkntfixture/`.

   Comment that the stand-in exists only to prove the rule bites.
6. **Document the gap** in a comment: the rules check field access. `Enum.valueOf("PACK_LEVEL_MISSING")`
   or `values()` would get around them. No production code does that today.
7. **Red.** Before T004, rule A fails, naming `PackDescriptorReader` and
   `FileSystemCharterPackRepository`. Record that output.

### T006 — Test-shape fixes, Javadoc, gate run

**Steps**:

1. Update the constructor shapes in:
   - `GovernanceFingerprintTest:21`;
   - `PackListingQueryTest:46,49,54-55`. Keep its test-only `PACK_LEVEL_MISSING` construction:
     ArchUnit excludes tests.
   - `ActionContextQueryTest:32`.

   These are shape-only edits. The read-app is runner A's, so change no assertion (C-005).
2. Add a paragraph to the `CharterPackRepository` Javadoc: the file-system implementation composes
   the domain's `PackPolicy`, and #115 moves that composition into an application-ring use case.
3. Run the gate:
   `./gradlew :charter-domain:check :charter-read-app:check :adapter-in-rest:check :adapter-out-yaml:check :charter-service:test -PopenPacksDir=/home/user/spec-kitty/spec-kitty-open-packs --max-workers=2`.

## Definition of Done

- [ ] No production class outside `PackPolicy` constructs a `pack_level_missing` diagnostic or a
      `pack_level_mismatch` refusal. Rules A to C are green, and each negative fixture proves its
      rule bites.
- [ ] `PackPolicyTest` covers equal, differs, absent and bare path, with code and detail asserted.
      Its red was an assertion failure on stubs.
- [ ] `packs.feature:43/62/80`, `OpenPacksRoundTripTest` (old-layout clone) and
      `ProductionWiringTest:113-117` pass unchanged. `WireTokenParityTest` is still green, because
      no new `Diagnostic.Code` was added.
- [ ] Rule D is in place with its stand-in fixture.
- [ ] Commits carry the trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Reviewer guidance (opus, reviewer-renata)

- Grep production code for `PACK_LEVEL_` outside `charter-domain` and `rest`.
- Check the `CharterPack` guard is an `IllegalArgumentException`.
- Check the bare-path case (no configured level) matches core's `validate_pack(path)`.
- Check that each negative fixture fails its rule.
