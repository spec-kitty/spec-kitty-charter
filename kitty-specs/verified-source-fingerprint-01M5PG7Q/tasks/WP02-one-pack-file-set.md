---
work_package_id: WP02
title: One pack file set for the load and the fingerprint
dependencies: []
requirement_refs:
- FR-006
- FR-007
- FR-008
- FR-009
- FR-012
- NFR-001
- C-003
- C-004
- C-006
- SC-002
- SC-003
planning_base_branch: fix/verified-source-fingerprint
merge_target_branch: fix/verified-source-fingerprint
branch_strategy: Planning artifacts for this mission were generated on fix/verified-source-fingerprint. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into fix/verified-source-fingerprint unless the human explicitly redirects the landing branch.
base_branch: kitty/mission-verified-source-fingerprint-01M5PG7Q
base_commit: fa9c3fc10bf008a75e5b72e6b5efe70b4caa519d
created_at: '2026-10-24T08:55:40.548608+00:00'
subtasks:
- T006
- T007
- T008
- T009
- T010
phase: Phase 2 - Foundational
history:
- at: '2026-10-24T09:30:00Z'
  actor: system
  action: Prompt generated via /spec-kitty.tasks
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
execution_mode: code_change
model: sonnet
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/PackFileSet.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FilesystemPackStore.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSources.java
- service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java
- service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackSources.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/PackFileSetExactnessTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/GeneratedPackTree.java
- service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackSourcesTest.java
- contracts/charter/scenarios/service.feature
role: implementer
tags: []
task_type: implement
tracker_refs: []
---

# Work Package Prompt: WP02 — One pack file set for the load and the fingerprint

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

---

## Objectives & Success Criteria

Close issue #120: the source fingerprint digests exactly `pack.yaml`, every file the pack load
reads (tactic, directive, styleguide, paradigm files) and the declared graph inputs (root
`*.graph.yaml` shards, `drg/fragment.yaml`), and nothing else, and the load and the fingerprint
take their file list from **one** place.

Read first: [spec.md](../spec.md) (US3, Edge Cases, FR-006..FR-009, FR-012, NFR-001, C-003),
[plan.md](../plan.md) ("PackFileSet", "Testing design"), [data-model.md](../data-model.md)
("LoadedKind", "Digested file list"), [research.md](../research.md) (R2, R4, R5, R7). Then the code:
`FilesystemPackStore.java` (especially `read`, `readTactics`, `readKind`, `locate`, `kindFiles`),
`FileSystemPackSources.java`, `FileSystemPackInspector.java` (`graphShards`, `unlistable`,
`LOAD_PATH`), `PackFileKind.java`, `contracts/packs/file-kinds.yaml`,
`DomainRepositoryService.java` (`resolve`, `over`), and the tests next to them
(`DomainRepositoryServiceTest`, `DoctrineRoundTripTest`, `FileSystemPackSourcesTest`,
`PackFileKindTest`) and `service/packs/src/test/resources/roundtrip-fixtures/`.

Success:

- `PackFileSetExactnessTest` is **red on the unchanged code** (recorded) and green after.
- A mutant that drops any one loaded kind from the digest makes it fail, naming a planted file.
- The contract scenario "The source fingerprint follows a tactic file" is red, then green.
- `./gradlew verifyAll -PopenPacksDir=<open-packs>` is green (Java 25).

## Context & Constraints

- **Rings** (ADR-0006): everything here stays in `infra.storage`, package-private where it can
  be. `PackFileSet` must not move to `domain` (`DOMAIN_IS_PURE`).
- **No new dependency** (C-003, jqwik excluded). Plain JUnit 6 + AssertJ.
- **Behavior to keep**: the store's walk semantics exactly as today: `Files.walk` (enters hidden
  directories, does not follow directory symlinks), `Files.isRegularFile` (follows file symlinks),
  names ending `.yaml` (case-sensitive), classification by
  `PackFileKind.bundled().classify(<directory name>/<relative path>)`, `String` sort. The digest
  order for a pack with no loaded-kind files stays `pack.yaml`, root shards by name,
  `drg/fragment.yaml`, with the same labels, so `/info`'s value does not change for such inputs.
- **Normalization**: every `PackFileSet` entry point does `toAbsolutePath().normalize()` on the
  directory before classifying, because `FileSystemPackSources` receives the configured root raw
  while the store's directory is normalized (`DomainRepositoryService.over`).
- **Style**: palantir-java-format (`./gradlew spotlessApply`), Checkstyle, Error Prone, NullAway,
  `@NullMarked` packages, Javadoc on non-private members (doclint), test names in the module's
  style (see `code-style-and-conventions.md`). Complexity ≤ 15 per method.
- **Commits**: each commit compiles and passes `verifyAll`. The red-first proof is a **recorded
  run**, not a red commit (plan.md "Red-first capture").

## Branch Strategy

- Planning/base branch: `fix/verified-source-fingerprint`; final merge target:
  `fix/verified-source-fingerprint`.
- Execution worktrees are allocated per computed lane from `lanes.json`. Run
  `spec-kitty agent action implement WP02 --agent claude --mission verified-source-fingerprint-01M5PG7Q`
  and work only in the worktree it gives you.
- Java: `export JAVA_HOME=/usr/lib/jvm/temurin-25 PATH=/usr/lib/jvm/temurin-25/bin:$PATH`.
  Open-packs checkout: `/home/user/spec-kitty/spec-kitty-open-packs` (at the pinned ref); pass
  `-PopenPacksDir=/home/user/spec-kitty/spec-kitty-open-packs`. On a 429 from Maven Central, wait
  a minute and retry with `--max-workers=2`; never touch `gradle/verification-metadata.xml`.

## Subtasks

### T006 — Red first: the exactness test and the contract scenario

**Purpose**: Prove the gap on the unchanged code with tests whose expected sets do not come from
the code under test.

**Steps**:

1. Create `GeneratedPackTree` (test source, `infra.storage` package): a seeded generator that
   writes 1-3 pack directories under a `@TempDir` and returns, per pack, the configured
   `PackLocation` (name, level, root) and three expected sets of relative paths:
   `loaded` (planted kind files), `graphInputs` (root shards, fragment), `decoys`. The verdict of
   every planted path comes from an **explicit name-shape table in the generator**, never from
   `PackFileKind` or `PackFileSet`. Table (each row: shape → verdict):

   | Shape | Verdict |
   |---|---|
   | `<stem>.<kind>.yaml` at depth 0-3, kind in tactic/directive/styleguide/paradigm | loaded |
   | same with a hidden stem `.<stem>.<kind>.yaml` | loaded |
   | same under a hidden directory `.h/<stem>.<kind>.yaml` | loaded |
   | `presets/<stem>.tactic.yaml` (files rule wins over the exclusion) | loaded |
   | `<stem>.config.tactic.yaml` | loaded (check against `file-kinds.yaml`; if the table says otherwise, the test must fail, not the generator be "fixed") |
   | file symlink inside the pack to an external valid tactic | loaded (skip with `assumeTrue` where symlinks are unsupported) |
   | root `a.graph.yaml`, hidden root `.b.graph.yaml` | graph input |
   | `drg/fragment.yaml` | graph input |
   | `x.tactic.yml`, `x.tactic.YAML`, `x.TACTIC.yaml`, `x.tactic.yaml.bak`, `x.tactic.config.yaml` | decoy |
   | `notes.yaml`, `sub/r.yaml` | decoy |
   | `*.toolguide.yaml`, `*.procedure.yaml`, `*.agent.yaml`, `*.asset.yaml`, a step-contract file | decoy |
   | root `org-charter.yaml`, `sub/charter.yaml`, nested `sub/pack.yaml` | decoy |
   | nested `sub/y.graph.yaml`, nested `sub/drg/fragment.yaml` | decoy |
   | a directory named `d.tactic.yaml` | decoy (not a file) |
   | a directory symlink to an external directory holding a valid tactic | decoy (skip where unsupported) |

   Check every glob against `contracts/packs/file-kinds.yaml` while writing the table (for the
   step-contract and agent-profile names, use exactly what the table classifies). Keep directory
   names that are both a files rule and an exclusion (`mission_types/`, `missions/*/`,
   `actions/*/`) out of the segment alphabet. Every pack gets at least one file of each loaded kind
   and a `pack.yaml` copied from `roundtrip-fixtures/pack-a/pack.yaml`. Some trees configure a pack
   name different from the directory name, and at least one tree configures a non-normalized
   root (`<dir>/.`).

   Content: valid YAML with a planted unique id per file. These are the minimal shapes the
   store's readers accept (`DoctrineRoundTripTest.java:139-146`, `roundtrip-fixtures/pack-a/`);
   every kind needs `schema_version`:
   - tactic: `id: t7\nschema_version: '1.0'\nname: N\nsteps:\n  - title: S\n`
   - directive: `id: D7\nschema_version: '1.0'\ntitle: T\nintent: I\nenforcement: advisory\n`
   - styleguide: `id: s7\nschema_version: '1.0'\ntitle: T\nscope: code\nprinciples: [p]\n`
   - paradigm: `id: p7\nschema_version: '1.0'\nname: N\nsummary: S\n`
   Check id patterns against the readers/rulesets before generating (ids must be unique per
   kind within a pack).
   About one loaded file in five gets malformed content (`id: [\n`); it must appear as a
   `PackElementViolation` with its relative path.

2. Create `PackFileSetExactnessTest` with:
   - base seed constant, overridable by system property `packFileSet.seed`; tree *i* uses seed +
     *i*; 200 trees; `@Timeout(10)`; every assertion `.as("seed=%d tree=%d file=%s", ...)`;
   - **load side**: for each pack, `new FilesystemPackStore(parent, <the strategy the module
     uses>).read(new PackIdentifier(dirName))`; the observed read set is `pack.yaml` plus the
     planted ids found in `tactics()`, `directives()`, `styleguides()`, `paradigms()` mapped back
     to their planted paths, plus `violations()` file paths. Assert it equals `{pack.yaml} ∪
     loaded`. Also assert `DomainRepositoryService.tacticsOf` ids equal the **valid** planted tactic ids.
     **Non-vacuity**: per tree, assert at least one valid file of each kind was observed through
     its typed list (`tactics()`, `directives()`, `styleguides()`, `paradigms()`), not only as a
     violation.
   - **digest side**: baseline `new FileSystemPackSources(() -> locations).fingerprint()`, stable
     on a second call; for every regular file in the trees (decoys and symlink targets
     included) append `"\n# m\n"`, digest, restore the exact bytes; the fingerprint must change
     **if and only if** the path is in `{pack.yaml} ∪ loaded ∪ graphInputs` of its pack. Add one
     new random file per tree (loaded shape or decoy) and check the same rule. Assert the baseline
     returns at the end.
   - **Never** import or call `PackFileKind` or `PackFileSet` to compute an expected set.
     Structure the checks as a reusable oracle
     `void assertExact(GeneratedPackTree tree, Function<List<PackLocation>, String> fingerprintOf)`
     so T010 can run mutants through it.

3. Add to `contracts/charter/scenarios/service.feature` (Background already writes a built-in
   graph shard) the scenario below; it uses existing steps only (check them in `CharterSteps`):

   ```gherkin
   Scenario: The source fingerprint follows a tactic file
     A tactic file is loaded by the tactic reads, so editing it changes the fingerprint; editing a
     file the service does not load does not.
     Given the org pack "qa" with the file "rehearse.tactic.yaml":
       """
       id: rehearse
       schema_version: '1.0'
       name: Rehearse
       steps:
         - title: Only step
       """
     When I GET "/info"
     And I remember the field "sourceFingerprint" of the response body
     Given the org pack "qa" with the file "notes.yml":
       """
       a: 1
       """
     When I GET "/info"
     Then the field "sourceFingerprint" of the response body is the remembered one
     Given the org pack "qa" with the file "rehearse.tactic.yaml":
       """
       id: rehearse
       schema_version: '1.0'
       name: Rehearse again
       steps:
         - title: Only step
       """
     When I GET "/info"
     Then the field "sourceFingerprint" of the response body differs from the remembered one
   ```

   If a step phrase differs in the glue, use the glue's phrase; do not add steps in this WP.

4. **Run red and record**: `./gradlew :packs:test --tests '*PackFileSetExactnessTest*'` and
   `./gradlew :deployable:test --tests '*CharterContractSuite*'`. Expected: the exactness test
   fails because a planted tactic (or directive, styleguide, paradigm) file does not change the
   fingerprint; the scenario fails on "differs from the remembered one". Save the failing test
   names and first assertion messages to the WP's review evidence (paste them into your hand-off
   notes / the review request). Do not commit a red state: commit the tests together with T007-T009.

### T007 — `PackFileSet`

**Purpose**: The one authority (plan.md "PackFileSet").

**Steps**:
1. Create `infra/storage/PackFileSet.java`, `final class`, package-private, private constructor:
   - `static final String PACK_YAML = "pack.yaml";`
   - `enum LoadedKind { TACTIC("tactic"), DIRECTIVE("directive"), STYLEGUIDE("styleguide"), PARADIGM("paradigm"); String token(); }`
   - `static List<String> files(Path packDir, LoadedKind kind)` — today's `kindFiles` behavior,
     moved verbatim apart from the normalization and the typed kind.
   - `static List<String> digested(Path packDir)` → `digested(packDir, EnumSet.allOf(LoadedKind.class))`.
   - `static List<String> digested(Path packDir, Set<LoadedKind> kinds)` — package-private: the
     seam the planted-omission mutants use. Order: `pack.yaml` if a regular file; then, for each
     kind in declaration order that is in `kinds`, its files; then root `*.graph.yaml` regular
     files by name (reuse the inspector's `graphShards` logic; move it here and let the inspector
     call it, or call the inspector's static; one implementation only); then `drg/fragment.yaml`
     if a regular file. One `Files.walk` per call, shared by all kinds (collect, classify once,
     group by kind).
   - Listing failures throw what they throw today (`FileSystemPackInspector.unlistable(...)`,
     `PACK_UNREADABLE`).
2. Javadoc: the class is the single declaration of which pack files the service loads and
   digests; adding a kind is adding a `LoadedKind`, which the digest then covers.

### T008 — The store reads only declared kinds

**Steps**:
1. In `FilesystemPackStore`, delete `PACK_YAML` and the four `*_KIND` constants; use
   `PackFileSet.PACK_YAML` and `PackFileSet.LoadedKind`. `kindFiles(directory, String)` goes;
   callers use `PackFileSet.files(directory, LoadedKind.X)`. `locate`, `readKind`, `readOne`,
   `newFile` and the write methods take `LoadedKind` (use `kind.token()` where a string is needed,
   for example `<id>.<token>.yaml`).
2. Behavior is unchanged: existing storage tests stay green without edits. If one needs an edit,
   it is a signal you changed behavior; stop and check.

### T009 — The fingerprint digests through `PackFileSet`

**Steps**:
1. `FileSystemPackSources.fingerprint()` becomes `return digest(locations.get(), PackFileSet::digested);`
   with a package-private
   `static String digest(List<PackLocation> locations, Function<Path, List<String>> filesOf)` that
   keeps today's loop: non-directory root → `PACK_UNREADABLE` "pack <name> is not a directory";
   per file, label `<location.name()>/<relative path with '/'>` then bytes; read failures as
   today. This is the production method's own decomposition, used by the mutants.
2. `filesOf` returns paths relative to the (normalized) pack directory; resolve them against the
   normalized root when reading bytes.
3. If you moved `graphShards`, make `FileSystemPackInspector` call `PackFileSet`'s version; the
   inspector's `LOAD_PATH` scope and its results are unchanged (its tests stay green).
4. Update `FileSystemPackSourcesTest` only by **adding** cases: a tactic, directive, styleguide
   and paradigm file each change the digest; a decoy does not; a non-normalized root digests the
   same as the normalized one; an unreadable nested loaded file (or an unlistable nested
   directory) makes `fingerprint()` refuse with `PACK_UNREADABLE` (the deep-walk edge case of the
   spec). The container and CI may run as root, where `chmod 000` does not bite: use
   `assumeFalse("root".equals(System.getProperty("user.name")))` or a substitute that fails
   for root too (for example a dangling file symlink with a loaded-kind name, if the walk treats
   it as unreadable; verify).

### T010 — Planted omission, unit tests, javadoc

**Steps**:
1. In `PackFileSetExactnessTest`, add `@ParameterizedTest @EnumSource(PackFileSet.LoadedKind.class)`
   mutants: run `assertExact` with
   `locs -> FileSystemPackSources.digest(locs, dir -> PackFileSet.digested(dir, EnumSet.complementOf(EnumSet.of(kind))))`
   and assert it throws `AssertionError` whose message contains a planted file of that kind.
   (This test may reference `PackFileSet` because it builds the mutant, not an expected set.)
   Add two more mutants: graph inputs dropped (a `filesOf` that filters them out) must fail;
   a nested shard added (a `filesOf` that appends `sub/y.graph.yaml` when present) must fail.
2. `PackFileSetTest`: focused unit tests for `files` (each verdict class of the table on a fixed
   small tree), `digested` order, normalization (`<dir>/.`), `PACK_YAML` absent.
3. Javadoc: `domain/PackSources.java` port says "every file the offering is loaded from ... in
   load order"; amend to "every pack file the service loads, plus the declared graph inputs, and
   any other configured input the adapter digests, in a fixed order" (keep it storage-agnostic).
   `FileSystemPackSources` class javadoc: name `PackFileSet` as the source of the list.
4. Run `./gradlew spotlessApply`, then `./gradlew verifyAll -PopenPacksDir=...`. Green.

## Definition of Done

- [ ] Red runs recorded (exactness test and scenario fail on unchanged code for the stated reason).
- [ ] `PackFileSet` is the only place that lists a pack's loaded and digested files; the store has no kind string constants.
- [ ] The exactness test never derives an expected set from `PackFileKind`/`PackFileSet`; 200 seeded trees in under 10 s.
- [ ] Each `LoadedKind` mutant, the graph-inputs mutant and the nested-shard mutant fail the oracle.
- [ ] `/info`'s value for packs without loaded-kind files is unchanged (existing tests untouched and green).
- [ ] `verifyAll` green on Java 25; spotless clean.

## Risks

- **Circular oracle**: the most likely way to fake this WP. Reviewers will grep the test for
  `PackFileKind`/`PackFileSet` in expected-set code.
- **Glob surprises**: if the table and `file-kinds.yaml` disagree, report it in the hand-off; do
  not silently adjust the table to make the test pass.
- **Runtime**: 200 trees × every file × one digest can be slow; keep trees small (≤ ~25 files per
  pack) and measure.
- **Symlinks** on the CI runner (Linux) work; use `assumeTrue` only for unsupported file systems.

## Reviewer Guidance

- Confirm the red evidence names a loaded file that did not change the digest.
- Confirm `FilesystemPackStore` cannot enumerate an undeclared kind (no string kinds left).
- Confirm the digest order and labels for a shard-only pack are byte-identical to before.
- Confirm the scenario edits a `*.tactic.yaml` and the control edits an unloaded file.
