# Implementation Plan: Charter contract skeleton and Gradle build (retroactive)

**Branch**: `ccr-9654c444-x7y94q` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/charter-contract-and-build-retro-01M57QHJ/spec.md`

Planning questions are answered: the operator's rulings of 2026-10-18 are recorded as
Decision Moments under `decisions/` and summarized in [research.md](research.md) §10–12.

## Summary

Close roadmap steps 1 and 2 under governance. The contract moves to `1.0.0-SNAPSHOT` and
gets its own contract checks (a charter-authored lint ruleset, a bundle and a
breaking-change check) inside the build, with the
operator's rulings on the three unreviewed calls carried through scenarios first. The
build gains the operator-vetted quality gates behind one opt-in convention plugin, a
documented public API with Javadoc published inside the docsite, and a nightly mutation
run. CI proves it on Java 25.

## Technical Context

**Language/Version**: Java 25 (toolchain and `--release`), Kotlin DSL for the build
**Primary Dependencies**: Gradle 9.8.1 wrapper; Spring Boot 4.1.1; openapi-generator 7.25.0; Cucumber-JVM 8; ArchUnit 1.5; JUnit 6.1; AssertJ 3.27. New, operator-vetted: Checkstyle, Spotless with palantir-java-format, Error Prone with NullAway and JSpecify annotations, JaCoCo (with report aggregation), PIT (gradle-pitest-plugin), Mockito; for the contract checks, the vacuum and oasdiff binaries, each pinned by version and SHA-256 and verified before use
**Storage**: N/A (file-based packs; the YAML adapter is unchanged apart from code spellings)
**Testing**: Test first. Contract scenarios in `contracts/charter/scenarios/` change before the service; JUnit unit tests; ArchUnit gate; Cucumber suite; open-packs round-trip; each new gate proved by a planted violation that must fail it (non-vacuity)
**Target Platform**: Linux CI (`ubuntu-24.04`), Temurin 25
**Project Type**: Multi-module Gradle service plus docsite (Starlight) and CI workflows
**Performance Goals**: `verifyAll` within 10 minutes on CI with a warm cache, excluding the nightly mutation task (NFR-002)
**Constraints**: Strict SHA-256 dependency verification for every new artifact (NFR-003); no skipped, quarantined or blanket-suppressed checks (NFR-001); generated contract sources exempt by construction (C-005); no jqwik (C-008); no OpenRewrite; no local mirror script committed (C-007)
**Scale/Scope**: About 1,380 main and 580 test lines across five hand-written modules; 2 contract operations; 8 scenarios growing to about 12

## Charter Check

| Charter rule | How this plan meets it |
|---|---|
| Content parity with the Python oracle; contract compliance (costliest failures) | Every behavior change is a scenario change first; the action-context gaps are declared `x-provisional` rather than frozen; the case mapping to core's codes is lossless and tested |
| A call made without review is logged | The three calls carry rulings (research §10); deferred findings S3/S4 are listed there |
| `verifyAll` on Java 25; strict verification | All new tools enter through the catalog and `verification-metadata.xml`; CI on Temurin 25 |
| Java style check gate | Checkstyle plus the palantir formatting check, in `verifyAll` |
| Docs checks | All eight `check_docs.py` checks, markdownlint, cspell; `links` gains a reviewed allow-prefix for `/javadoc/` |
| BDD, specification by example, test first | Scenarios first; planted-violation proofs for every gate |
| DDD | "Contract scenarios", "diagnostic" and the code spellings follow the canonical terms (research §10, D5, S6) |
| C4 | The docsite page for the Javadoc sits in the API section; no new architecture views are needed |
| Supply-chain safety (DIRECTIVE_051) | Results recorded per new dependency below; OpenRewrite and jqwik rejected on supply-chain and terms grounds |
| Adversarial squad on contract and architecture changes | Ran at the plan point (research §10); runs again before the pull request |

**Supply-chain controls for the new dependencies.** Each is added through
`gradle/libs.versions.toml`, resolved from Maven Central or the Gradle Plugin Portal over
HTTPS, pinned to an exact version at least two weeks old, and recorded with SHA-256 in
`verification-metadata.xml` by `--write-verification-metadata sha256`, then reviewed. No
dependency runs install-time scripts. Licences: Checkstyle LGPL-2.1 (build-time tool,
not shipped); Spotless and palantir-java-format Apache-2.0; Error Prone and NullAway
Apache-2.0; JSpecify Apache-2.0; JaCoCo EPL-2.0; PIT Apache-2.0; Mockito MIT. None carries
an AI-use clause (checked against jqwik's precedent).

## Project Structure

### Documentation (this mission)

```
kitty-specs/charter-contract-and-build-retro-01M57QHJ/
├── spec.md
├── research.md
├── plan.md
├── quickstart.md
├── decisions/
├── checklists/requirements.md
└── tasks/              # from /spec-kitty.tasks
```

### Source Code (repository root)

```
build-logic/src/main/kotlin/
├── charter.java-base.gradle.kts        # toolchain, compile, tests (all modules)
├── charter.hand-written.gradle.kts     # every quality gate (all modules except charter-api)
├── charter.domain-conventions / spring / service / contract-api   # existing, re-based
└── (checkstyle config + suppressions register under config/)
service/
├── charter-api/                        # generated; base plugin only
├── charter-domain/ charter-read-app/ adapter-in-rest/ adapter-out-yaml/ charter-service/
└── charter-javadoc/ (or a root-level aggregation task) # aggregated Javadoc + JaCoCo report
contracts/charter/                      # 1.0.0-SNAPSHOT; snake_case codes; citations
contracts/lint/                         # the charter's own ruleset + planted fixtures per rule
contracts/build.gradle.kts              # contract-checks project: lint, bundle, breaking check (pinned binaries)
.github/workflows/service.yml           # verify (contract checks included); workflow_call; artifacts
.github/workflows/docs-site.yml         # calls service.yml, publishes Javadoc
.github/workflows/mutation-nightly.yml  # PIT, schedule + dispatch
docs/api/javadoc.md                     # sidebar entry to the Javadoc pages
site/public/javadoc/ (CI only, ignored) # Javadoc HTML placed before astro build
scripts/docs/check_docs.py              # /javadoc/ allow-prefix (+ unit test, doc rule)
```

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|---|---|---|
| A second convention plugin (`charter.hand-written`) | Generated code must be exempt from seven gates (G1) | Subtracting each gate in `charter.contract-api` couples on strings and grows with every gate |
| A charter-owned lint ruleset and contract-checks project | The charter owns its contract conventions (operator, 2026-10-18; DM 01M57WH7P0QZWHKYGBA2R0MA3K) | Running core's tools couples the redesign to core's conventions and rough edges; the integration seam in core absorbs differences instead |
| An aggregation project for Javadoc | One searchable API site with cross-module links (FR-012) | Five per-module sites need hand-written indexes and `-linkoffline` |

## Implementation Concern Map

### IC-01 — Convention plugins and quality gates

- **Purpose**: Put every vetted gate behind one opt-in plugin so generated code is exempt by construction, and make the gates fail the build.
- **Relevant requirements**: FR-006, FR-007, FR-008, FR-009, FR-010, NFR-001, NFR-003, C-004, C-005, C-008
- **Affected surfaces**: `build-logic/**`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, every `service/*/build.gradle.kts`, a Checkstyle config and one suppressions register
- **Design**: split `charter.java-conventions` into `charter.java-base` (toolchain, compile flags, JUnit) and `charter.hand-written` (Checkstyle; Spotless with palantir on main and test; Error Prone on main and test; NullAway in JSpecify `OnlyNullMarked` mode on main only; JaCoCo; PIT configuration with targets selected by plugin presence, never in `check`); charter-api applies only the base. Mockito joins the test catalog and one test that uses it. Aggregations select projects by `plugins.hasPlugin("charter.hand-written")`.
- **Sequencing/depends-on**: none; its palantir reformat lands as one formatting-only commit before other code changes
- **Risks**: Error Prone needs `--add-exports`/`--add-opens` on JDK 25 and an Error Prone release that supports it; the plugin set must stay configuration-cache safe; each gate needs a planted violation proof that is then removed

### IC-02 — Nullness and the documented API

- **Purpose**: Make the hand-written code pass NullAway and doclint with warnings as errors, and document every public type and member.
- **Relevant requirements**: FR-007, FR-011
- **Affected surfaces**: every hand-written `package-info.java` (`@NullMarked`; add `charter-service`'s), the `orElse(null)` sites into generated DTOs, `PackDescriptorReader`, `ScalarWriter`, `CharterProperties`, public members across all five modules
- **Sequencing/depends-on**: IC-01
- **Risks**: generated DTOs are unannotated, so passing a nullable into them must be explicit at the call site, not suppressed

### IC-03 — Contract amendments, scenarios first

- **Purpose**: Carry the rulings into the charter contract so it passes the charter's own contract checks and declares what is provisional.
- **Relevant requirements**: FR-002, FR-003, FR-004 (content), FR-013
- **Affected surfaces**: `contracts/charter/**` (version `1.0.0-SNAPSHOT`; snake_case refusal and diagnostic codes; `x-provisional` on the action-context operation and on `PackSummary.diagnostics`; `via` wording; three descriptions; `x-source`/`x-derived` citations; CHANGELOG with Changed and Provisional sections), `contracts/charter/scenarios/**` (`@contract` and `@provisional` tags; sorted order; exact body comparison kept, `detail` included (Call 1 ratified as is); new scenarios for the real `DIRECTIVE_042` duplicate, a context load error and an undeclared action retitled; README as "Contract scenarios" with the inline-fixture rule), `charter-domain` `RefusalCode` and `Diagnostic` tokens, `adapter-in-rest` mapping through tokens, `ActionContextQuery` sort and path comment, an enum-parity test (Java enums against contract enums), `OpenPacksRoundTripTest` per-pack diagnostics, `charter-service/build.gradle.kts` (`include("scenarios/**")`) and `CharterContractSuite` tag selection
- **Sequencing/depends-on**: IC-01's formatting commit (to avoid churn)
- **Risks**: the case mapping must be tested both ways; `PACK_LEVEL_MISSING` behavior stays exactly as ratified (strict)

### IC-04 — The charter's own contract checks

- **Purpose**: Check the contract with rules the charter owns, inside `verifyAll`, and produce the bundle core vendors.
- **Relevant requirements**: FR-004, FR-005
- **Affected surfaces**: `contracts/lint/` (the charter's ruleset in Spectral format: operation ids and tags, problem responses, described properties, enum casing with the canonical tier spelling `built-in`, provenance citations, `x-provisional` shape; one planted fixture per rule); a recursive, unit-tested Java checker in `contracts/src/` that proves every response schema closed (`additionalProperties: false`) and fails closed on keywords it does not know (DM 01M584Q3NNK0B78H9NKHV7VJDK: JSONPath could not classify composed schemas soundly); `contracts/build.gradle.kts` (a Gradle project included from `settings.gradle.kts`: tasks that download vacuum and oasdiff at pinned versions, verify SHA-256, then `lintContract`, `bundleContract`, `checkContractCompatibility`; all part of `check`); the CI artifact upload of the bundle
- **Sequencing/depends-on**: IC-03
- **Risks**: platform-specific binaries (pin Linux x86-64 for CI plus the developer platforms you support, each with its checksum); the breaking-change check has no baseline until the first `contract-charter-v*` tag (passes, and says so); the bundler must not change semantics (check the bundle validates with the generator)

### IC-05 — Workflows: artifacts, docsite publishing, nightly mutation

- **Purpose**: Package Javadoc and coverage in the main build workflow, publish the Javadoc through the docsite, and run mutation testing nightly only.
- **Relevant requirements**: FR-005, FR-009, FR-011, FR-012
- **Affected surfaces**: `service.yml` (`workflow_call`, `ubuntu-24.04`, aggregated Javadoc and JaCoCo artifacts), `docs-site.yml` (calls `service.yml`, downloads the Javadoc into `site/public/javadoc` before `astro build`; trigger paths widened), `site/.gitignore`, a JDK 25 `--add-stylesheet` override mapped to the doc-toolkit tokens with a dark block, a relative "back to docs" link, `mutation-nightly.yml` (schedule plus `workflow_dispatch`)
- **Sequencing/depends-on**: IC-01, IC-02
- **Risks**: `check:built-links` walks the Javadoc HTML; the called workflow's concurrency applies inside the caller

### IC-06 — Docs, plans and records

- **Purpose**: Keep every doc truthful about the build, the rulings and the scenario homes, and record the mission.
- **Relevant requirements**: FR-001, FR-013, FR-014, FR-015, NFR-004
- **Affected surfaces**: `docs/api/javadoc.md` and `docs/api/README.md`; `scripts/docs/check_docs.py` allow-prefix with a unit test and `docs/_meta/doc-rules.yaml`; `docs/guides/service-build.md`, `contributing.md`, `docsite.md`; `README.md`; `docs/plans/roadmap.md` (steps 1–2 done only after FR-004 and FR-005), `open-questions.md` (rulings, BDD layout with all three homes, pack defects with owners); `docs/architecture/behavior-and-scenarios.md`, `contract-and-dtos.md`, the four docs that call `PACK_LEVEL_MISSING` a refusal (link to the contract instead), `docs/api/service-contract.md` (real files; closes #7's last criterion); `.kittify/charter/charter.md` gates; a changelog entry
- **Sequencing/depends-on**: IC-03 to IC-05 (it describes them)
- **Risks**: the terms guard; cspell additions for tool names

## Out of scope (recorded)

Roadmap step 3 and the corpus semantics (#9); the ADR-0006 slice restructure; the
domain-policy ownership move for the pack-level rule (S3/S4, follow-up); binding #43's
suites (they move and bind together in a later change).
