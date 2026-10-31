# #194 PR-B (mission_type, mission_step): gates, ledgers, migration path, churn, sibling conflicts

Profile: analyst-annie (read-only research). Repo HEAD `21262a5` on `issue-194-mission-type-consolidation`
(main tip `feeff0a`). Pinned open packs: `/home/user/spec-kitty-open-packs` @ `5df92a5d` (detached, clean).
No `.codegraph/` index, so this was researched with grep and reads.

Java: `JAVA_HOME=/usr/lib/jvm/temurin-25`. Open-packs checkout: by default `../spec-kitty-open-packs`, or `-PopenPacksDir=`.

---

## 1. Every gate and ledger on this surface

### 1.1 Whole-pack gate: `:packs:wholePackRoundTrip` (NOT part of `check`)

- **Build**: `service/packs/build.gradle.kts:134-155`. It is a `Test` task with `includeTags("whole-pack")`.
  - Inputs: `openPacksDir` (dir, RELATIVE), the `openPacksRef` property, and the classpath.
  - Output: `build/reports/whole-pack/report.md`.
  - `doFirst` checks that the pin is 40 lowercase hex characters.
- **CI**: `.github/workflows/whole-pack.yml`. It is non-required (not called by `ci.yml`, so it never feeds
  `CI gate / gate`). It triggers on `contracts/packs/**`, `service/**` and `gradle.properties`. It uploads `whole-pack-report`.
- **Test**: `WholePackRoundTripTest#theBuiltInPackRoundTripsAsExpected` (built-in pack ONLY). Steps:
  `OpenPacksCorpus.assertPinned()` → `WholePackCensus.of(pack)` → `DomainRoundTrip.observe` →
  `WholePackExpectation.fromClasspath().evaluate(census, modelledKinds, observed)` → asserts `problems().isEmpty()`.
  - `modelledKinds` must equal {descriptor, graph-shard, asset-payload, template, preset, pack-manifest} plus every
    `KindBindings.all()` schema name, and must contain `REQUIRED_KINDS`.
    **Add `mission-type` and `mission-step` to `REQUIRED_KINDS`.** That strengthens the check honestly.
  - The second test, `missionTemplatesAreNotTemplates` (line ~157), asserts that every `missions/*/templates/**` file
    censuses as `other:missions`. It stays valid only while mission templates stay unmodelled.
- **Gate kind of a file** (`WholePackCensus.kindOf`):
  - A companion takes its owner's schema name.
  - Otherwise a `file-kinds.yaml` **Mapped** rule gives its schema name, hyphenated (e.g. `mission-step-contract`).
  - Otherwise the kind is `other:<first path segment>`.
  - **A file that `file-kinds.yaml` *excludes* is NOT Mapped, so it censuses as `other:missions`.** The file-kinds
    exclusions and the gate's own `whole-pack/exclusions.yaml` are separate lists.
- **Ledgers** (`service/packs/src/test/resources/whole-pack/`):
  - `expected-red.yaml` (34 lines):
    - `kinds:` has `other:missions` (#194) and `other:pack.md` (#260).
    - `files:` has `anti_pattern.graph.yaml` (#236, tags) and `directive.graph.yaml` (#235, tension edges), each with
      an exact `at`.
    - `empty_kinds:` has `skill` (#192).
  - `known-failures.yaml`: 6 asset sidecars (open-packs#75), `presets/default.yaml` and `presets/minimal.yaml`
    (`#TBD-preset-shape`), and `pack-manifest.yaml` (`#TBD-manifest-shape`).
    - The `issue` field must be `spec-kitty/spec-kitty-open-packs#<n>`. Only the two `PENDING_DRAFTS` TBD keys are admitted.
    - The list is self-expiring.
  - `exclusions.yaml`: `**/README.md`, `**/.gitkeep`, `**/__init__.py`. It is append-only.
- **Failure modes** (`WholePackExpectation.evaluate`, lines 254-390):

  | Problem | When it fires |
  |---|---|
  | `UNEXPECTED_RED` | An unlisted red file |
  | `CHANGED_RED` | A listed file whose first difference (`at`) moved |
  | `UNEXPECTEDLY_GREEN` (file or kind) | A listed file or kind now round-trips. Remove the entry. |
  | `STALE_BASELINE` | A listed path is gone or excluded, or a listed kind no longer has any file |
  | `STALE_KNOWN_FAILURE` | A known failure's path is gone or excluded |
  | `KNOWN_FAILURE_UNMODELLED` | A known failure of a kind no adapter models |
  | `AMBIGUOUS_ENTRY` | A path is in both lists |
  | `BASELINED_MODELLED_KIND` | A kind entry for a modelled kind. A modelled kind is never red as a whole; list its files one by one. |
  | `STALE_EXCLUSION` | An exclusion matches no file |
  | `EXCLUDED_MODELLED` | A gate exclusion over a modelled file |
  | `EMPTY_MODELLED_KIND` | A modelled kind with no file, not listed in `empty_kinds` |

  The written tree is also checked: any written file that no source pairs with is a difference ("unpaired").
- **Command**: `./gradlew :packs:wholePackRoundTrip`. Then read `service/packs/build/reports/whole-pack/report.md`.
- **Keeping it honest**:
  - Once `mission-type` and `mission-step` are bound, the gate never lets them be red as a whole kind. Each pinned
    file must be green, or listed singly:
    - in `expected-red.files`, for a service defect with an exact `at`;
    - in `known-failures`, for a pack inconsistency with a real open-packs issue.
  - Remove `other:missions` only if no unmodelled `missions/**` file remains. Remaining unmodelled files include:
    mission `templates/**`, `plan-field-declaration.yaml`, and any retired file still on disk.
    If you keep the entry, narrow its `reason`; never widen it.
  - Never add a gate exclusion for a modelled file.
  - Edit `exclusions.yaml` only by appending.

### 1.2 `:packs:openPacksRoundTrip`: IS part of `check` and `verifyAll` (`tasks.check { dependsOn(openPacksRoundTrip) }`)

`includeTags("open-packs")`, `excludeTags("whole-pack")`. Inputs: the open-packs dir, `packs/` (vendored) and
`openPacksRef`. The tests that bite PR-B:

- **`WholePackOpenPacksRoundTripTest`** loads EVERY open-packs pack (built-in, cave-kitty, event-driven, internal, qa)
  and the repo's `packs/` through `WholePackStore`.
  - For built-in, the violations must equal the `known-failures.yaml` pins exactly. Every other pack must have
    **zero** violations, and its clean packs must round-trip whole.
  - Hard counts that will move:

    | Count | Location |
    |---|---|
    | 9 pinned refusals | line 408 |
    | 272 manifest constituents | line 446 |
    | `Counts(6,7,0,2)` | |
    | 26 profiles | |

  - **cave-kitty and qa carry mission files**, so a refusal there reds `check`. They are listed below.
    - cave-kitty: `mission_types/cave-dev.yaml`, `mission_types/cave-dev/governance-profile.yaml`,
      `missions/cave-dev/{mission,mission-runtime,expected-artifacts}.yaml`, `command-templates/*.md`.
    - qa: `mission_types/qa.yaml`, `mission_types/qa/governance-profile.yaml`, `mission-steps/qa/*/step.yaml` (7),
      `missions/qa/{mission,mission-runtime,expected-artifacts}.yaml`, `templates/**`.
- **`FileSystemPackInspectorTest#validateModeOverTheBuiltInPackFindsWhatMainFoundAndListsTheConfigPayload`**
  (`@Tag open-packs`, line ~485) asserts that the built-in validate-mode findings `containsOnly` the manifest and the
  two presets. A schema-mapped mission file that fails its schema adds an `ARTIFACT_SCHEMA_ERROR`, and the test goes red.
- **`VendoredPacksTest`**: `packs/internal` and `packs/qa` must be byte-equal to the pin, except `PROVENANCE.md`,
  `README.md` and `LOCAL_DELTA` (currently `internal/assets/validate-pr-body.py`, open-packs#92).
  `packs/qa` has the same mission files as above, so it must be re-vendored after the re-pin.
- **`MissionStepContractCorpusTest`** pins 28 step contracts (21 built-in + 7 qa).
  **`ProcedureAndGuideCorpusTest`** holds per-pack counts and `theMigrationHarnessIsGone`. That test asserts that the
  package `ai/speckitty/charter/packs/migration` is **absent from the test classpath**, so a PR-B harness in that
  package reds `check` until it is deleted. This is the guard PR-A left; do not weaken it.
- **Pin check**: `OpenPacksCorpus.assertPinned()`. The checkout HEAD must equal `openPacksRef` and
  `git status --porcelain --untracked-files=all` must be empty. Both are asserted again after the gate run.
  - Pin source: `gradle.properties` `openPacksRef=` (keep the explanatory comment current).
  - CI reads the same pin (`service.yml`, `whole-pack.yml`).
- `OpenPacksCorpus.YAML_FILE_COUNT = 516` is defined but appears unused (worth a re-check). It moves if the retired
  files are deleted upstream.
- **Command**: `./gradlew :packs:openPacksRoundTrip`, or for one class:
  `./gradlew :packs:openPacksRoundTrip --tests '*WholePackOpenPacksRoundTripTest'`.

### 1.3 `:packs:test` (unit; tags open-packs and whole-pack excluded)

- **`PackFileKindTest`**:
  - `MAPPED` must hold exactly one sample per `files` rule (`containsExactlyInAnyOrder` over the rule schemas).
    Add samples for the new rules.
  - `EXCLUDED` sample classes must equal the exclusion rules exactly (`everyExclusionClassMatchesItsSamplePath`).
    Removing a rule means removing its sample, e.g. `core/actions/plan/index.yaml`,
    `.../mission-runtime.yaml`, `.../expected-artifacts.yaml`, `.../governance-profile.yaml`,
    `core/mission_types/software-dev.yaml`, `.../mission.yaml` and the `step.yaml` sample.
  - `aHiddenFileUnderAssetsOrTemplates…` asserts that `built-in/missions/plan/templates/.gitkeep` is Unclassified.
  - `noSamplePathIsMatchedByTwoPayloadRules`: new samples must not hit `*/templates/**/*`. Mission templates live
    under `missions/<t>/templates`, which that glob does not match.
- **`VocabularyEqualityTest`**:
  - `node_kinds` must equal `NodeKind` in order, and must also equal the graph-shard `DRGNode.kind` enum, the URN
    alternation, the edge source/target alternations and one `allOf` URN-prefix branch per kind.
  - `fragment_kinds` must equal the org-fragment `_OrgDRGNode.kind` enum and `NodeKind.fromPlural`.
  - `artifact_kinds` must equal `ArtifactKind` in order.
  - `theInspectorsArtifactSchemaKindsAreTheMappedSchemasThatDeclareAnId`: every mapped schema with an `id` property
    must be in `FileSystemPackInspector.artifactKinds()`. A **hand-written list of 10** must then be extended, or the
    schema exempted the way `preset` is.
  - `mission_type` and `action` are already node kinds and fragment plurals. A **new `mission_step` node kind** would
    add a 16th kind everywhere listed above.
- **`PackSchemasTest`**: every `contracts/packs/schemas/*.schema.json` is bundled and named by a file-kinds rule.
  There are 19 files today; the README says "18 in all", which is already stale.
- `WholePackExpectationTest$…#filesTakeTheirSchemaKindOrWhereTheyLive` (line ~849) uses the bundled table and expects:
  - `missions/m/mission.yaml` → `other:missions`. This changes when `**/missions/*/mission.yaml` is mapped.
  - `pack.md` → `other:pack.md`. #260 changes this; see §4.
- `FileSystemPackInspectorTest#everyYamlFileIsClassifiedInPathOrderAndAnUnclassifiedOneIsLeftOut` (line ~218) uses
  `excluded("missions/m/mission.yaml", "**/missions/*/mission.yaml")`. It changes when that exclusion goes.
- `PlantedFilesTest` and `DivergenceRegisterTest` read `docs/conformance/divergences.yaml`, `divergence-register.md`
  and `docs/conformance/`.
- **Command**: `./gradlew :packs:test`

### 1.4 Code-quality gates (`build-logic/src/main/kotlin/charter.hand-written.gradle.kts`; all in `check`)

| Gate | What it enforces | Command |
|---|---|---|
| javac | `-Xlint:all -Werror` | |
| Error Prone | main and test | |
| NullAway | ERROR, main only, `OnlyNullMarked` and `JSpecifyMode`. New packages need `@NullMarked` (package-info) to be covered. | `./gradlew :packs:compileJava :packs:compileTestJava` |
| Checkstyle | `config/checkstyle/checkstyle.xml`; naming, imports, coding rules; `maxWarnings=0`; **no complexity module**. `suppressions.xml` is the single exception register (rule, file, reason, sunset), currently with **0 entries**. | `./gradlew :packs:checkstyleMain :packs:checkstyleTest` |
| Spotless | palantir-java-format, main and test | `./gradlew :packs:spotlessCheck` (fix with `spotlessApply`) |
| Javadoc | `-Xdoclint:all -Werror` on main: every public type and member is documented | `./gradlew :packs:javadoc` |
| Jacoco | report only, no threshold | |

- **Complexity ≤ 15** is a mission NFR convention (spec, plan, WP text), **not a tool gate**. Earlier missions record
  it as "holds by inspection". Keep each method small. Do not add a suppression.
- **ArchUnit**:
  - `service/deployable/.../HexagonalArchitectureTest`: onion rules, pure kernel and packs domain, no framework
    annotations in domain, ports in domain, storage returns domain types.
  - `GeneratedConstraintTypesTest` and `ReservedRefusalCodesTest`.
  - `service/packs/.../NetworkCodeBoundaryTest`.
  - New `MissionType`/`MissionStep` domain types must sit in `packs.domain` (or the kernel) with no infra imports.
    The YAML codec/DSO goes in `infra.storage`.
  - Command: `./gradlew :deployable:test --tests '*HexagonalArchitectureTest'`.
- **Whole build**: `./gradlew verifyAll`. This runs every module's `check`, which includes `openPacksRoundTrip`,
  plus build-logic's check. It does not include the whole-pack gate. CI (`service.yml`) runs
  `./gradlew verifyAll -PopenPacksDir=…`.

### 1.5 Docs gates (`.github/workflows/docs-cleanliness.yml`)

- `python3 scripts/docs/check_docs.py all` runs frontmatter, related, freshness, adr, changelog, terms, orphans and
  links.
  - A changelog entry must be named `YYYY-MM-DD-kebab.md`, be listed in `docs/changelog/README.md` (index newest
    first) and stay under the size cap.
  - `adr` checks the index in `docs/adr/README.md`.
- `python -m pytest scripts/docs/tests -q`
- markdownlint-cli2, cspell (`cspell.json`), lychee (`lychee.toml`).
- **Terminology trap**: `docs/_meta/terminology.yaml` `avoid` → `mission-steps-fragment-kind` (pattern
  `\bmission_steps\b`, prefer `mission_step_contracts`, R14).
  - It is allowed only in `divergence-register.md`.
  - `docs/context/domain.md:108-111` says the service *refuses* `mission_steps` and the singular `mission_type` as a
    fragment kind.
  - DIV-011 and DIV-012 (`docs/conformance/divergences.yaml:190-215`) state "15 plurals" and "15 node kinds".
  - A `mission_step` kind with the plural `mission_steps` collides with ruling R14. It needs an operator ruling, then
    an edit to `terminology.yaml`, `domain.md` and DIV-011/012. Neither the docs nor the code can simply ignore it.

---

## 2. Excluded mission files today, and the getting-green path

### Today

`file-kinds.yaml` excludes the following:
- `**/mission-steps/**/step.yaml`
- `**/actions/*/index.yaml`
- `**/missions/*/mission.yaml`
- `**/missions/*/mission-runtime.yaml`
- `**/missions/*/expected-artifacts.yaml`
- `**/governance-profile.yaml`
- `**/mission_types/*.yaml`
- `built-in.source.yaml`
- `**/templates/plan-field-declaration.yaml`

Consequences:
- The store and inspector skip excluded files.
- The gate still censuses them as `other:missions`, which is not Mapped. They are therefore all EXPECTED_RED under
  the single kind entry `other:missions` (#194), detail "no adapter models kind other:missions".
- `prompt.md`, `guidelines.md` and mission `templates/**` are Unclassified and also `other:missions`.

The built-in pin holds:

| Files | Count |
|---|---|
| `missions/mission_types/*.yaml` | 4 |
| `missions/<t>/mission.yaml` | 4 |
| `mission-runtime.yaml` | 4 |
| `expected-artifacts.yaml` | 4 |
| `governance-profile.yaml` | 4 |
| `missions/<t>/actions/*/index.yaml` | 24 |
| `missions/mission-steps/<t>/<step>/step.yaml` | 30 |
| `prompt.md` | 30 |
| `guidelines.md` | 17 |
| `README.md` (gate-excluded) | 12 |
| mission templates | several |
| `built_in_step_contracts/*.step-contract.yaml` | 21 (already green) |

### After the exclusions are removed and new `files` rules plus bindings land, at the OLD pin

- Every mission-type and step file that does not match the new sanitized schema is **refused**. The results:
  - Gate: `UNEXPECTED_RED` per file. A kind-level entry is forbidden (`BASELINED_MODELLED_KIND`).
  - `check`: red. `WholePackOpenPacksRoundTripTest` sees unpinned violations in built-in, cave-kitty and qa, and
    `FileSystemPackInspectorTest` sees new schema findings.
  - Companions of a refused owner (`prompt.md`, `guidelines.md`) would be unowned.
- The known-failure list is not a valid way out. It accepts only open-packs issues for *pack inconsistencies*, and
  ADR-0043 decision 6 / D7 rule that a deliberate shape change is migrated, not listed. It also covers built-in only:
  cave-kitty and qa may have no refusals at all.
- The retired files (`mission-runtime`, `actions/*/index`, `governance-profile`, `expected-artifacts`) become
  **Unclassified**, not refused, once their exclusion is removed. They are silently skipped by the load and still
  census as `other:missions`, so they can hide under that kind entry.
  - **Recommendation**: the migration deletes them upstream (folding their content into the mission-type file).
  - Add a guard test, e.g. tagged `open-packs`, asserting that no pinned file matches a retired glob. This is a
    strengthening, not a loosening.

### Getting-green path: PR-A precedent

Sources: research.md D7; plan IC-06; tasks WP09/WP10; ADR-0043 decision 6; `docs/guides/service-build.md`
"When a shape change refuses the pinned packs".

1. Change the schemas, `file-kinds.yaml` rules and bindings first, proven on fixtures. From here until the re-pin,
   the corpus tests and the gate are red **by design**. Keep that window to one PR, which is opened only after the re-pin.
2. **Throwaway harness (WP09)**: test source set, package `ai.speckitty.charter.packs.migration`, files
   `PackMigrationHarness`, `Legacy*Reader`, `GuideRestructurer`, `FieldMappingVerifier`.
   - It runs under a `migration` JUnit tag, excluded from `test`, `openPacksRoundTrip` and `wholePackRoundTrip`.
   - A dedicated Gradle task runs it: `./gradlew :packs:packMigration -PmigrationTarget=<absent or empty dir>`.
   - The legacy readers read the old shapes as plain YAML maps and fail on any unmapped key. Old-shape knowledge
     lives only in `src/test`.
   - It converts to the new domain types and writes them with the **production writer** (and `MarkdownCodec` for
     companions).
   - It is guarded against writing into the pinned checkout.
   - `FieldMappingVerifier` is independent of the conversion:
     - Every source key path maps to exactly one target path, or to a listed and justified drop.
     - Order within each section is checked.
     - Counts in equal counts out (PR-A: 39 procedures, 24 toolguides, 24 guides).
     - A second run is a no-op (idempotence).
   - Authored YAML comments become `manual-decisions` entries. Non-mechanical cases are listed in
     `manual-decisions.md` for human review in the open-packs PR.
3. Run it for built-in, internal and qa into a copy of the open-packs tree. Produce per-class diffs and the verifier
   report, and round-trip the output through the new store.
   - Open one open-packs issue per class of change.
   - Commit on a branch of `spec-kitty-open-packs` (never its `main`). PR-A's PR was open-packs#91, generated on
     open-packs `main` @ `72cb7dbc`.
   - The merge is an operator decision. It merged with core's validate check red, by waiver (ADR-0043 decision 7).
4. **Open-packs follow-ups that PR-A needed after the migration commit** (open-packs `b57aecc..5df92a5`):
   - re-seed `packs/built-in.source.yaml` `tree_digest` (`scripts/ci/check-built-in.sh` enforces it);
   - recompute the `pack-manifest.yaml` `content_hash` values (core's hashing);
   - point stale references at renamed files;
   - regenerate the pack reference pages;
   - write the changelog and authoring docs.

   Mission-file edits to built-in will at least need the `tree_digest` re-seed. The manifest lists only constituents,
   so check whether the mission files are among them.
5. **Re-pin (WP10, one commit `a0dfb51`)**:
   - Check out the merged SHA and set `openPacksRef`, with the comment naming the PR.
   - Re-vendor `packs/internal` and `packs/qa` exactly and recompute `LOCAL_DELTA`.
   - Remove the now-green entries from `expected-red.yaml` (PR-A removed `procedure` and `other:toolguides`) and keep
     the others byte-intact.
   - Add `known-failures` entries only for residuals, with real issue numbers.
   - Add corpus tests with counts.
   - **Delete the harness and the task.** `theMigrationHarnessIsGone` enforces this. The harness never appears in
     main's history.
   - Prove green: `./gradlew :packs:test :packs:openPacksRoundTrip :packs:wholePackRoundTrip :deployable:test` and
     `verifyAll`, plus the docs checks.
   - Positive controls: temporarily break the writer locally, show the gate goes red, and do not commit the break.
6. Core still reads `mission-runtime.yaml`, `governance-profile.yaml` and `actions/*/index.yaml`, and open-packs
   `validate-packs.yml` validates against a pinned core commit. Retiring those files upstream **breaks core's runtime
   and validate check far harder than the procedure rename did**. Under ADR-0043 decision 7 ("operate as if core does
   not exist") it needs an explicit operator waiver and a DIV-4xx entry. Flag this to the operator early.

---

## 3. 90-day churn (since 2026-08-01)

- 211 commits touch `contracts/packs`, `service/packs` and `docs/adr`.
- Hot files, by commit count:

  | Commits | File |
  |---|---|
  | 55 | `docs/adr/README.md` |
  | 21 | `FilesystemPackStore.java` |
  | 17 | `PackFileSet.java` |
  | 16 | `DomainRepositoryService.java` |
  | 14 | ADR-0037 |
  | 13 | `YamlSerializationStrategy.java` |
  | 13 | `Pack.java` |
  | 12 | `PackFileSetExactnessTest` |
  | 11 | `WholePackStore.java` |
  | 11 | `GeneratedPackTree` |
  | 10 | `WholePackRoundTripTest` |
  | 10 | `WholePackOpenPacksRoundTripTest` |
  | 10 | `KindBindingsTest` |
  | 10 | `FileSystemPackInspector.java` |
  | 9 | `KindBindings.java` |
  | 9 | `expected-red.yaml` |
  | 9 | `DomainRoundTrip` |
  | 9 | `WholePackStoreTest` |
  | 8 | `file-kinds.yaml` |
  | 8 | `WholePackExpectation(Test)` |
  | 8 | `PackFileKindTest` |
  | 7 | `contracts/packs/README.md` |

- `expected-red.yaml` was edited by #187, the squad, #190, #191, #192, #193, #195 (twice) and #194 PR-A, all within
  2026-10-27..29. Every kind mission edits it. It is the single highest-contention ledger.

---

## 4. Sibling runs: #260 (pack.md) and #229 (hosted config)

- **Remote branches**: none is in flight for #260 or #229. The `feat/whole-pack-round-trip-gate`,
  `feat/whole-pack-known-failures`, `feat/195-…`, `feat/asset-template-…` and `feat/mission-step-contract-…` branches
  are stale lanes, already squash-merged (74-122 commits behind main). Only `feat/asset-template-round-trip` diverges
  on the ledgers, and it is merged content. The sibling work lives in parallel sessions that have not pushed yet.
- **#229** (`application-container.yaml`, secret import, token message) by its scope fence lives in `deployable`
  config. Expected overlap is small:
  - `docs/changelog/README.md` (top line);
  - possibly `gradle.properties` / `.github`;
  - the ADR number, if it writes one.
  If it really edits the gate, the same strategy applies.
- **#260** collides directly. Its likely edits:
  - `file-kinds.yaml`: a new `files` rule `*/pack.md` (markdown) at the end of `files:`;
  - `expected-red.yaml`: remove the `other:pack.md` kind entry;
  - `PackFileKindTest` `MAPPED`;
  - `WholePackExpectationTest#filesTakeTheirSchemaKindOrWhereTheyLive`: the same `Map.of(...)` that holds our
    `missions/m/mission.yaml` line;
  - `VocabularyEqualityTest` (if `pack.md` has an id);
  - `WholePackRoundTripTest` `REQUIRED_KINDS` / bound set;
  - `DomainRoundTrip`, `WholePackStore`;
  - `contracts/packs/README.md` (schema count line, "No rule declares markdown yet");
  - ADR index and ADR number (next free is **0044**, so whoever lands second renumbers, as #195 did 0043→0042);
  - `docs/changelog/README.md` top entry;
  - `docs/development/epic-186-sanitized-shape-follow-ups.md`.
  - #260 may also re-pin `openPacksRef` if pack.md needs migration. **Two re-pins race.** Whoever lands second must
    rebase onto the other's open-packs commit and re-vendor again.
- **Strategy to localize**:
  1. In `file-kinds.yaml`:
     - Put the new `files` rules in one contiguous block placed directly after the `**/*.step-contract.yaml` rule,
       or at the end of `files:` behind a `# #194 PR-B` comment, so it does not sit next to #260's `pack.md` rule.
     - Delete the exclusions as one contiguous hunk (they are adjacent, lines ~162-181).
     - Only touch `node_kinds`, `artifact_kinds` and `fragment_kinds` if `mission_step` becomes a node kind.
       Append it at the end in `NodeKind` order.
  2. `expected-red.yaml`: edit only the `other:missions` entry (narrow or delete it). Leave the `other:pack.md` lines
     and the `files:` block byte-identical.
  3. `WholePackExpectationTest` census sample: change only the `missions/m/mission.yaml` line(s). Prefer a new,
     dedicated census test for mission files over growing the shared `Map.of`.
  4. Put mission-specific assertions in new test classes (e.g. `MissionTypeCorpusTest`, `MissionStepCorpusTest`)
     rather than extending `WholePackOpenPacksRoundTripTest`, except for the counts that must move.
  5. Use new docs files (a new ADR and a new changelog entry). Touch shared indexes with one appended line each. Agree
     the ADR number with the #260 run up front.
  6. Sequence the re-pin: land open-packs PRs serially (one open-packs `main` commit per sibling), then rebase.
     Coordinate through the parent session.

---

## 5. Docs that must change when the kinds are added

- `contracts/packs/README.md`:
  - "the eleven artifact kinds" and "18 in all" (already 19);
  - the `Validate a file` text "Files under `exclusions` have no schema";
  - the stale "No rule declares `markdown` yet".
- `docs/architecture/descriptors.md`:
  - §"Mission type — `missions/mission_types/<id>.yaml`" (lines ~325-329: "`mission-type` is an operator token, not
    an ArtifactKind");
  - lines ~106 and ~136-137: "`mission_steps` and `mission_type` are refused (DIV-011)";
  - the mission-step section.
- `docs/architecture/descriptor-schemas.md` (schema diagrams, `descriptor-kinds-overview.puml`) and
  `docs/architecture/logical-model.md`.
- `docs/context/domain.md:104-111` (fragment plural rules, Mission type / Mission step contract rows),
  `docs/_meta/terminology.yaml` (`mission_steps` avoid rule), and `site/.contextive/definitions.yaml`
  ("Mission step", "Mission type", "Mission-Type Profile", "recipe ingredients").
- `docs/conformance/divergences.yaml` and `divergence-register.md`:
  - DIV-011 and DIV-012 counts and plurals;
  - a new DIV-4xx for shapes core does not read (retired runtime files).
- `docs/development/epic-186-sanitized-shape-follow-ups.md`:
  - item 9 (step-contract ids);
  - item 13 ("core's mission step (`prompt_template`, `template_file`) … the mission-type kind, when it is built":
    resolved by PR-B);
  - item 14 (`built_in_step_contracts/__init__.py`, open-packs#94);
  - add a "Residuals of #194 PR-B" section.
- `docs/guides/service-build.md` (gate section, migration recipe; the harness command is marked historical).
- A new ADR, plus a one-line append in `docs/adr/README.md` index and status amendments on ADR-0036, 0037, 0039,
  0040 and 0043 rows. There is no root `CHANGELOG.md`; the changelog is
  `docs/changelog/YYYY-MM-DD-<slug>.md` plus a line in `docs/changelog/README.md`, newest first, under the size cap.
- `gradle.properties` comment on the pin, and `packs/*/PROVENANCE.md` on re-vendor.
