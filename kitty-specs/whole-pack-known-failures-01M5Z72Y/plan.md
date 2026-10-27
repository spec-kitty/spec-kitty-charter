# Implementation Plan: Whole-pack gate known-failure list

**Branch**: `feat/whole-pack-known-failures` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/whole-pack-known-failures-01M5Z72Y/spec.md`

Planning questions answered: decision `01M5Z73CH2336MGVKF64EJ444G` (entry shape: exact
`spec-kitty/spec-kitty-open-packs#N`, one entry per file, `at` pinned) and decision `01M5Z7T5QET0XX0GDP5GE1ZNSH`
(the new status and its report column are appended last). Design input: the pre-spec squad (architect, reviewer),
dispositioned in [research.md](research.md).

## Summary

Add a third checked-in list to the whole-pack gate, `whole-pack/known-failures.yaml`, beside `expected-red.yaml` and
`exclusions.yaml`. The gate's expectation loads it, judges a listed red file at its pinned location as a new status
`KNOWN_FAILURE` (no problem), fails a listed file that round-trips (`UNEXPECTEDLY_GREEN`), a moved difference
(`CHANGED_RED`) and misplaced entries, and refuses at load an entry whose issue is not an open-packs reference. The
report counts the new status in its totals line and in a last per-kind column. The list ships empty.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; `-Pcharter.javaVersion=21` only for a local run without a 25 JDK)
**Primary Dependencies**: none added; JUnit 5 and AssertJ (existing test dependencies), the module's own `Yaml` loader
**Storage**: test resources under `service/packs/src/test/resources/whole-pack/`
**Testing**: JUnit 5 in `:packs:test` (untagged, part of `verifyAll`) for the expectation, loader and report; the tagged corpus gate `:packs:wholePackRoundTrip` for the real pack at `openPacksRef`
**Target Platform**: the Gradle build and the non-required `whole-pack.yml` workflow
**Project Type**: single (Gradle multi-module service; test-scope change in `service/packs`)
**Performance Goals**: no measurable change to the gate's run time (one more small YAML file loaded)
**Constraints**: additive only (C-001); no production code (C-002); report stays deterministic (NFR-003); Error Prone, NullAway, Checkstyle and palantir-java-format pass
**Scale/Scope**: 3 source files changed (`WholePackExpectation`, `WholePackReport`, `WholePackExpectationTest`), 1 resource added, 2 docs touched

Supply chain (DIRECTIVE_051): no dependency is added, upgraded or removed, so no control applies; verification metadata is unchanged.

## Charter Check

| Gate | Result |
|------|--------|
| Non-trivial work runs as a mission and lands by PR | Pass: this mission, draft PR to `main` |
| Adversarial squad at plan point and before merge (contract, architecture or domain change) | Pass: a pre-spec squad ran (research.md); a pre-PR squad is planned. This is a test-harness change, not a contract, architecture or domain change |
| `./gradlew verifyAll` on Java 25 with strict dependency verification | Planned: no dependency change; run before PR |
| Java style check (Spotless, Checkstyle, Error Prone, NullAway, doclint) | Planned: part of `verifyAll` |
| Docs checks (`check_docs.py`, markdownlint-cli2, cspell) | Planned: run on the touched docs |
| Test first, no fake green, outcome assertions | Planned: NFR-001, NFR-002; tests written before the code in each concern |
| Open-packs round-trips without loss of meaning (ADR-0036) | Pass: the list is test bookkeeping, ruling 3 stands (C-002) |
| HTTP contract unchanged | Pass: no contract file touched; `meta.json` records `contracts: none` |

Post-design re-check: unchanged; no violations, so Complexity Tracking is empty.

## Project Structure

### Documentation (this mission)

```
kitty-specs/whole-pack-known-failures-01M5Z72Y/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md            # /spec-kitty.tasks
```

### Source Code (repository root)

```
service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/
├── WholePackExpectation.java       # KnownFailure record, 3-arg load, KNOWN_FAILURE status, new problems
├── WholePackReport.java            # known-failure label and last per-kind column
└── WholePackExpectationTest.java   # nested KnownFailures tests, report assertions, real-resource load
service/packs/src/test/resources/whole-pack/
└── known-failures.yaml             # new, ships empty
docs/guides/service-build.md        # gate section: the known-failure workflow
docs/changelog/                     # new entry + README index line
```

**Structure Decision**: everything stays in the existing gate package and resource directory; no new module, class
file or production source.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Known-failure list and judgement

- **Purpose**: Load the new list and judge each file against it, so a tracked pack inconsistency passes, a fixed one expires and a misplaced entry fails.
- **Relevant requirements**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, NFR-001, NFR-002, C-001, C-002
- **Affected surfaces**: `WholePackExpectation.java`, `known-failures.yaml`, `WholePackExpectationTest.java`
- **Sequencing/depends-on**: none
- **Risks**: Sibling missions edit `expected-red.yaml` and may add tests to `WholePackExpectationTest`; keep every change additive (a new nested test class, a delegating 2-arg `load`). The refused-file match must compare the whole message for known failures only, leaving the `files:` behaviour unchanged.

### IC-02 — Report column and count

- **Purpose**: Show known failures on their own in the totals line and the per-kind table.
- **Relevant requirements**: FR-008, NFR-001, NFR-003
- **Affected surfaces**: `WholePackReport.java`, `WholePackExpectationTest.java`
- **Sequencing/depends-on**: IC-01 (the status value)
- **Risks**: Appending last keeps existing substring assertions valid; the workflow summary prints the report header unchanged in shape.

### IC-03 — Workflow docs and changelog

- **Purpose**: Tell contributors when and how to use the list, and announce it.
- **Relevant requirements**: FR-009
- **Affected surfaces**: `docs/guides/service-build.md`, `docs/changelog/`
- **Sequencing/depends-on**: IC-01, IC-02 (problem codes and labels named in the docs)
- **Risks**: Docs checks (cspell, markdownlint, `check_docs.py` frontmatter) must pass.
