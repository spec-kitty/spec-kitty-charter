# Implementation Plan: Charter example packs replace the open-packs corpus

**Branch**: `claude/adr-open-packs-456-458-adf2fo` (PR #474) | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)
**Input**: `kitty-specs/charter-example-packs-01M74GCF/spec.md`; ADR-0070; `docs/plans/charter-example-packs.md`

## Summary

Replace the one seam every corpus test reads, `OpenPacksCorpus` (`root()`, `packsDirectory()`, `packs()`,
`assertPinned()`), with an `ExamplePacks` locator over charter-owned example packs in a `java-test-fixtures` source set
of `:packs` (decision DM-01M74GFCT9ZC6V4HEAQZM6MA3C). Rewrite each test's content-specific assertions against examples
designed for them, add a coverage check, drop the open-packs inputs from the required tasks and CI, add a nightly drift
watch, and amend the governance texts.

## Technical Context

**Language/Version**: Java 25 (Gradle 9.8.1, Kotlin DSL build logic)
**Primary Dependencies**: JUnit 5, AssertJ, networknt json-schema-validator (all already in the build); Gradle's core
`java-test-fixtures` plugin (no new artifact)
**Storage**: pack files on the file system (YAML, Markdown, binary assets)
**Testing**: `:packs:test`, `:deployable:test`, `:packs:wholePackRoundTrip`, `verifyAll`
**Target Platform**: Linux CI (GitHub Actions)
**Project Type**: multi-module Gradle service (single repository)
**Performance Goals**: required checks no slower than today (the corpus is larger than the examples)
**Constraints**: canonical shapes only (C-002); no production code change (C-003); lands on the PR #474 branch (C-001)
**Scale/Scope**: about 28 test classes (~8,000 lines) re-pointed; 3 example packs, at most 150 files (NFR-002)

**Supply chain**: no dependency is added, upgraded or removed. `java-test-fixtures` ships with Gradle; the
dependency-verification metadata does not change. DIRECTIVE_051 controls: not triggered.

## Charter Check

- **Canonical model, canonical-only tests** (amended 2026-11-10): the example packs carry only the canonical shape;
  invalid shapes stay in `planted/`. Pass.
- **Gates before merge**: the whole-pack round trip stays required, over the example packs; amended text lands in this
  mission (FR-008). Pass with the amendment.
- **PROJECT_002 (open-packs round trip)**: amended as ADR-0070 decides. Pass with the amendment.
- **BDD / test first**: each moved test is red against the empty `ExamplePacks` before the example lands. Pass.
- **Adversarial squad at the plan point**: done for ADR-0070's decision in this session; a squad review before merge is
  kept (end of mission).

## Project Structure

### Documentation (this mission)

```
kitty-specs/charter-example-packs-01M74GCF/
├── plan.md, research.md, data-model.md, quickstart.md
└── tasks/
```

### Source Code (repository root)

```
service/packs/src/testFixtures/
├── java/ai/speckitty/charter/packs/examples/ExamplePacks.java     # the locator (replaces OpenPacksCorpus)
└── resources/example-packs/{built-in,org-base,project}/           # the example packs
service/packs/src/test/...           # corpus tests re-pointed; OpenPacksCorpus removed
service/deployable/src/test/...      # profile tests re-pointed (testFixtures(project(":packs")))
service/packs/build.gradle.kts, service/deployable/build.gradle.kts   # tasks and inputs
.github/workflows/whole-pack.yml, service.yml, + open-packs-drift.yml (nightly)
.kittify/charter/charter.md, charter.yaml (regenerated)
```

## Complexity Tracking

None: no new module, no new dependency.

## Implementation Concern Map

### IC-01 — Example packs and locator

- **Purpose**: the three canonical example packs and `ExamplePacks`, exposed as test fixtures to `:packs` and
  `:deployable`.
- **Relevant requirements**: FR-001, NFR-002, C-002
- **Affected surfaces**: `service/packs/build.gradle.kts` (`java-test-fixtures`), `service/packs/src/testFixtures/`,
  `service/deployable/build.gradle.kts`
- **Sequencing/depends-on**: none
- **Risks**: under-covering a kind; mitigated by IC-03.

### IC-02 — Round trip over the examples

- **Purpose**: `WholePackRoundTripTest`, `WholePackOpenPacksRoundTripTest` and the expectation lists run over the
  example packs; expected-red lists empty.
- **Relevant requirements**: FR-002
- **Affected surfaces**: `whole-pack/*.yaml` expectations, the two round-trip tests, `WholePackReport`
- **Sequencing/depends-on**: IC-01
- **Risks**: an example that does not round-trip reveals a real defect; fix the defect, never the expectation.

### IC-03 — Coverage check

- **Purpose**: fail when a pack schema property, an artifact kind or a raised refusal code has no example.
- **Relevant requirements**: FR-003, SC-003
- **Affected surfaces**: a new test in `:packs` (schemas, kinds) and one in `:deployable` (refusal codes, scenarios)
- **Sequencing/depends-on**: IC-01
- **Risks**: refusal codes are proven by scenarios, not by packs; the check counts a code covered by a contract scenario
  or an example.

### IC-04 — Corpus tests re-pointed

- **Purpose**: each `open-packs`-tagged test reads `ExamplePacks` and asserts the same property over examples built
  for it; `OpenPacksCorpus` removed.
- **Relevant requirements**: FR-004
- **Affected surfaces**: the groups of the plan doc: artifact round trip, mission types and steps, composition, files
  and digests, profiles (`:packs` and `:deployable` tests)
- **Sequencing/depends-on**: IC-01
- **Risks**: values pinned from real content (digests, counts, ids) are re-derived from the examples, documented
  where they are pinned.

### IC-05 — Build and CI without open-packs

- **Purpose**: remove `openPacksRoundTrip`, `openPacksProfiles`, the `openPacksDir` test inputs and the open-packs
  checkout from required jobs; keep `syncPacks`/`openPacksRef`; add the nightly drift workflow.
- **Relevant requirements**: FR-005, FR-006, FR-007, NFR-001
- **Affected surfaces**: `service/packs/build.gradle.kts`, `service/deployable/build.gradle.kts`, `build.gradle.kts`,
  `.github/workflows/{whole-pack,service,ci}.yml`, new `.github/workflows/open-packs-drift.yml`
- **Sequencing/depends-on**: IC-02, IC-04
- **Risks**: workflow guard tests in `build-logic` pin the workflow shape; update them with the change.

### IC-06 — Governance

- **Purpose**: amend the charter companion's gates and PROJECT_002; regenerate `charter.yaml`; file the open-packs
  validation-job issue.
- **Relevant requirements**: FR-008
- **Affected surfaces**: `.kittify/charter/charter.md`, `.kittify/charter/charter.yaml`, the PROJECT_002 library
  entry
- **Sequencing/depends-on**: none
- **Risks**: `charter generate` may rewrite more than the two clauses; review its diff.

## Test inventory (exact; WP ownership in tasks.md)

**A. Read the pinned checkout** (`OpenPacksCorpus`, `charter.openPacksDir`, tags `open-packs` / `whole-pack`):
`WholePackRoundTripTest`, `WholePackOpenPacksRoundTripTest`, `WholePackExpectation`, `WholePackExpectationTest`,
`WholePackReport` (WP02); `PackDoctrineCorpusRoundTripTest`, `MarkdownCorpusTest`, `ProcedureAndGuideCorpusTest`,
`GlossaryPackAndSkillCorpusTest`, `GlossaryPackAndSkillSchemaTest`, `MissionTypeCorpusTest`,
`MissionTypeHoldingCorpusTest`, `MissionStepContractCorpusTest`, `PromptSlotLoadChecksTest`,
`PromptSlotsCanonicalCorpusTest` (WP04); `OpenPacksComposeTest`, `CompanionFilesTest`, `CompanionDigestTest`,
`BinaryPayloadRevisionTest`, `PackDigestBaselineTest`, `PackManifestParityTest`, `PackFileKindTest`,
`FileSystemPackInspectorTest`, `PackLevelSchemasTest`, `ProfileGovernanceCorpusTest` (WP05); `ProfileCorpusTest`,
`ProfileExamplesEdgeTest`, `PackGraphEdges` (WP06); `OpenPacksCorpus` (WP07, deleted).

**B. Read the synced `packs/qa` / `packs/internal` trees**: `ArtifactRemovalServiceTest`,
`RelationshipRemovalServiceTest`, `ArtifactControllerTest`, `PackRemovalControllerTest`, `HypermediaTest`,
`PackFileSetTest`, `PolicyRoundTripTest`, `StorageRootLocationsTest`, `GlossaryPackYamlTest`,
`FileSystemPackSourcesTest`, `RetiredFilesTest` (WP09); `ConfigRootWiringTest`, `CharterWiringTest`,
`ActivationListingFaultTest`, `RegularArtefactWiringTest`, `ProblemResponsesTest`, `bdd/OperationCoverageTest`,
`bdd/LinkChecksTest`, `bdd/ContractConformanceTest` (WP10).

Done when `grep -rE "OpenPacksCorpus|charter\.openPacksDir|packs/(qa|internal)" service/*/src/test` finds nothing.

## Review folds (adversarial squad, 2026-11-11)

- `ExamplePacks` copies the tree once per JVM to a temp directory (opening the jar's file system when the fixtures are
  a jar), so every test gets a real directory; tests that mutate copy again into `@TempDir`.
- `syncPacks` keeps the pinned checkout in required jobs (copy and digest check only); test tasks lose
  `-PopenPacksDir` and the `charter.openPacksDir`/`openPacksRef` wiring, the default `test` task included.
- The drift watch runs from its own `driftTest` source set, excluded from `check` and `verifyAll`, in a workflow with
  `issues: write` and label-based de-duplication.
- Pinned digests are recomputed over the example packs, with the regeneration command documented beside the pins.
- WP08 edits the authored sources (`.kittify/charter/interview/answers.yaml`,
  `.kittify/charter-packs/directive/002-...`), regenerates, and rejects any diff outside the two clauses.
