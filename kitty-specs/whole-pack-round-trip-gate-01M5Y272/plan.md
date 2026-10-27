# Implementation Plan: Whole-pack round-trip gate

**Branch**: `feat/whole-pack-round-trip-gate` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/whole-pack-round-trip-gate-01M5Y272/spec.md`

## Summary

A test harness in the `:packs` test source set loads the pinned built-in pack into the **domain** through
`DomainRepositoryService` (read → domain), writes every modelled artefact and the descriptor to a fresh directory
through a second `DomainRepositoryService` rooted there (domain → write), runs DRG shards through
`GraphDocumentReader` → `PackPolicy.graph` → `GraphShardWriter`, and reads the written pack back. Each written file
is compared with its **source file** by meaning (parsed trees, not bytes, never DSO-against-DSO). A deterministic
per-kind / per-file report is checked against a checked-in expectation (expected-red baseline + owned exclusion
list) with a two-way ratchet. It runs as its own Gradle task, `:packs:wholePackRoundTrip`, outside `check`.

## Technical Context

**Language/Version**: Java 25 (toolchain; `-Pcharter.javaVersion=21` for local fallback only)
**Primary Dependencies**: existing only — JUnit (platform 6.x), AssertJ, snakeyaml-engine (via the package's `Yaml`), the `:packs` storage classes. No new dependency.
**Storage**: files — the pinned `spec-kitty-open-packs` checkout (read-only) and a per-run `@TempDir`
**Testing**: unit tests for comparator, expectation/ratchet and the round-trip adapter over small fixture packs (in `test`); the corpus gate under tag `whole-pack` (own task)
**Target Platform**: Gradle build on Linux CI and developer machines
**Project Type**: single (multi-module Gradle; work confined to `service/packs` tests + build script)
**Performance Goals**: full built-in pack in < 60 s (NFR-001)
**Constraints**: no edits to `Pack.java` / `FilesystemPackStore` (C-001); not part of `check` (C-002); no per-file special cases in the comparator (C-003)
**Scale/Scope**: 505 files in the built-in pack at `01edb4e`; 231 in modelled gate kinds (216 artefacts of the five kinds + `pack.yaml` + 14 graph shards); census in research R-1

## Charter Check

| Charter rule | How the plan satisfies it |
|---|---|
| Open-packs YAML round-trips (charter "What must never go wrong") | The gate restores a corpus round-trip, by meaning, for the built-in pack. The charter's wording ("exactly", "in verifyAll") is stale since 2bb3f76; raised as question 4 on #187, not edited here. |
| Test first; no fake green; outcome assertions | Comparator and ratchet unit tests come first; positive controls (lossy writer, changed edge, unexpected green) prove the gate can fail. Source-vs-written comparison avoids the DSO-vs-DSO blind spot. |
| Adversarial squad at plan point and before merge | Pre-spec squad done (findings on #187); post-plan check folded below; pre-PR squad at closeout. |
| A call made without review is logged | Every provisional call (policy rules, kind naming, task placement) is listed in research.md as an open review item. |
| `verifyAll` on Java 25, style gates | New test code follows palantir-java-format/Checkstyle/Error Prone/NullAway; the new task is outside `verifyAll`, so `verifyAll` must stay green and unchanged in duration. |
| Supply-chain safety | No dependency added (DIRECTIVE_051 not triggered). |

No violations; no complexity exceptions.

## Project Structure

### Documentation (this mission)

```
kitty-specs/whole-pack-round-trip-gate-01M5Y272/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks/
```

### Source Code (repository root)

```
service/packs/
├── build.gradle.kts                         # + wholePackRoundTrip task; tag "whole-pack" excluded from test/openPacksRoundTrip
└── src/test/
    ├── java/ai/speckitty/charter/packs/infra/storage/
    │   ├── WholePackRoundTrip.java          # the seam (#189 replaces the adapter behind it)
    │   ├── DomainRoundTrip.java             # today's adapter: DomainRepositoryService ×2 + DRG reader/policy/writer
    │   ├── MeaningComparator.java           # source tree vs written tree; provisional policy (#188)
    │   ├── WholePackCensus.java             # classifies every built-in file into a gate kind
    │   ├── WholePackExpectation.java        # expected-red baseline + exclusions; ratchet evaluation
    │   ├── WholePackReport.java             # per-kind / per-file results; deterministic rendering
    │   ├── WholePackRoundTripTest.java      # @Tag("whole-pack") corpus gate
    │   ├── MeaningComparatorTest.java       # unit (test task)
    │   ├── WholePackExpectationTest.java    # unit (test task)
    │   └── DomainRoundTripTest.java         # unit over fixture packs, incl. lossy-writer + unknown-field controls
    └── resources/whole-pack/
        ├── expected-red.yaml                # baseline: kinds and files, reason + owning issue
        └── exclusions.yaml                  # owned, append-only; match + reason
```

**Structure Decision**: same package as `OpenPacksCorpus` / `PackFileKind` / `Yaml` (package-private reuse, no
main-code change). Test source set, not main: ArchUnit scans exclude tests, and nothing in production needs it.

## Complexity Tracking

None.

## Implementation Concern Map

### IC-01 — Meaning comparator

Parse both texts with the package's `Yaml` loader (same null forms as the readers). Maps compare by key set then
per key; lists in order; scalars by value and type. For kind `graph-shard`: `edges` compare as a sorted, de-duplicated
set of canonical edge maps over every field; `nodes` as a map keyed by `urn`. Result: equal, or the first difference
with a path (`edges[source=…]`, `steps[2].title`) and both values. The rules sit behind one policy object so #188 can
replace them. Duplicate edges in the source are returned as information, not a difference.

### IC-02 — Round-trip seam and adapter

`WholePackRoundTrip` maps a source pack directory and an empty target directory to per-source-file outcomes
(`Written(targetPath)` or `Refused(reason)`) for the gate kinds it models. `DomainRoundTrip` implements it with two
`DomainRepositoryService` instances (source / target locations) over `YamlSerializationStrategy` (injectable, for
the lossy-writer control), pairs source and written files by `(kind, id)`, writes graph shards via the DRG reader →
`PackPolicy.graph` → `GraphShardWriter` (keeping the source's `generated_by`), then reloads the target pack and turns
read-back violations into `Refused`.

### IC-03 — Census, expectation and ratchet

`WholePackCensus` lists every regular file under the pack and assigns a gate kind: the schema name when
`file-kinds.yaml` maps it, otherwise `other:<top-level directory or root file name>`. `WholePackExpectation` loads
`expected-red.yaml` (entries by `kind` or by `path`, each with `reason` + `issue`) and `exclusions.yaml` (`match`
glob + `reason`) and evaluates a report: unexpected red, unexpected green (baselined but passing), stale exclusion,
exclusion over a modelled file, stale baseline path, file accounted twice.

### IC-04 — Report and gate wiring

`WholePackReport` renders a deterministic Markdown report (kind table, then files sorted by path) to
`build/reports/whole-pack/report.md` and prints the kind table. `WholePackRoundTripTest` asserts the pin, runs the
round trip, writes the report, asserts per-modelled-kind counts > 0, total accounted = census total, and the ratchet
has no problems. Gradle: `wholePackRoundTrip` task modelled on `openPacksRoundTrip`; tag excluded from `test` and
`openPacksRoundTrip`; not wired into `check`. The first real run seeds `expected-red.yaml` (every red triaged with a
reason and an owning issue). The #189 seam note goes on #189.
