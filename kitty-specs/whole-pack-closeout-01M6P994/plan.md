# Implementation Plan: Whole-pack round trip closeout (epic #186)

**Branch**: `epic-186-closeout` (PR to `main`) | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)
**Input**: Mission specification from `kitty-specs/whole-pack-closeout-01M6P994/spec.md`

## Summary

Three small steps, in order, one commit each, in one pull request:

1. **#299** — move the Markdown form's line rules (`MarkdownScanner` and the heading, block and opening checks) from
   `packs.domain.markdown` into a new kernel package `ai.speckitty.charter.kernel.markdown`. `MarkdownDocument` keeps
   its structural role in `packs` and delegates. `StepDocument` and `StepSection` (`:missions`) call the kernel rules
   in their constructors and refuse with `ConstraintBreachException`, as `PackDocument` does.
2. **#319** — validate mode normalizes the pack root once, where it is resolved:
   `FileSystemPackInspector.inspect` takes `Path.of(root.locator()).toAbsolutePath().normalize()`. Red first: a
   validate-mode test roots a mission-type pack with a named template at `<dir>/x/../mission-type`.
3. **Closeout** — `whole-pack.yml` becomes callable (`workflow_call`), loses its direct `pull_request` trigger, and
   `ci.yml` gets a `wholepack` change group and job that the `gate` job needs and checks. Docs, plans, roadmap,
   ADR-0023 (dated note), a changelog entry and the charter's "Gates before merge" follow.

## Technical Context

**Language/Version**: Java 25 (Gradle toolchain; `-Pcharter.javaVersion=21` never in CI), GitHub Actions YAML, Markdown docs
**Primary Dependencies**: none added; kernel stays dependency-free (`charter.domain-conventions`), JSpecify compile-only
**Storage**: pack files on the file system (validate mode reads a pack root directory)
**Testing**: JUnit 5 + AssertJ unit tests in `:kernel`, `:missions`, `:packs`; ArchUnit gate in `:deployable`; the
whole-pack round trip `:packs:wholePackRoundTrip`; docs checks (`check_docs.py all`, markdownlint-cli2, cspell)
**Target Platform**: Linux CI (`ubuntu-24.04`), the charter service JVM
**Project Type**: multi-module Gradle service (`service/kernel`, `service/missions`, `service/packs`, …)
**Performance Goals**: no change; the whole-pack job keeps its 15-minute timeout
**Constraints**: `KERNEL_IS_PURE`, `MISSIONS_DOMAIN_IS_PURE`; `Spellings` unchanged; root normalized, not real-pathed
**Scale/Scope**: about 10 production Java files, 2 workflow files, about 8 docs

## Charter Check

| Gate (charter.md) | Status |
|---|---|
| Divergence from the charter's own model | Pass: no model change; step texts get the pack form's existing rules. |
| OpenAPI contract unbroken | Pass: no contract change (`contracts: none`). |
| Round trip keeps meaning (ADR-0036) | Pass: the whole-pack gate becomes required; reads are unchanged (a read builds from an already valid document). |
| No unverified dependency | Pass: no dependency added. |
| Adversarial squad at plan and before merge (domain/architecture change: kernel) | Plan squad run on this plan; pre-PR squad before the draft PR. |
| `verifyAll` on Java 25, style checks, docs checks | Run locally before push; CI runs them under `CI gate / gate`. |
| Designing during the rewrite (no core compatibility) | Pass: nothing here depends on core. |
| ADR numbers | No new ADR. ADR-0040 gets a dated note (line rules in `kernel.markdown`, model in `packs.domain.markdown`, YAML-guard exception); ADR-0044 a one-line note on decision 7; ADR-0023 a dated note (a fourth group). |

## Project Structure

### Documentation (this mission)

```
kitty-specs/whole-pack-closeout-01M6P994/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
├── decisions/
└── tasks.md            # /spec-kitty.tasks
```

### Source Code (repository root)

```
service/kernel/src/main/java/ai/speckitty/charter/kernel/markdown/
├── MarkdownScanner.java         # moved; scan/Line/Kind/Refused public for the packs codec; FenceTracker stays package-private
├── MarkdownLines.java           # new: headingProblem, blockProblem, openingProblem
└── package-info.java
service/kernel/src/test/java/ai/speckitty/charter/kernel/markdown/   # moved scanner test + rule tests
service/packs/src/main/java/ai/speckitty/charter/packs/domain/markdown/
├── MarkdownDocument.java        # statics delegate to the kernel (call sites unchanged)
└── package-info.java            # names the kernel as the rules' home
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/markdown/MarkdownCodec.java  # imports
service/missions/src/main/java/ai/speckitty/charter/missions/domain/{StepDocument,StepSection}.java
service/missions/src/test/java/ai/speckitty/charter/missions/domain/MissionValuesTest.java (or a new StepDocumentTest)
service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/FileSystemPackInspector.java  # normalize once
service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/MissionTypeBindingTest.java    # red-first #319
.github/workflows/whole-pack.yml, .github/workflows/ci.yml
service/packs/build.gradle.kts (comment only)
docs/guides/service-build.md, docs/guides/contributing.md, docs/adr/0023-ci-gate-required-check.md,
docs/adr/0044-…md, docs/plans/roadmap.md, docs/plans/milestones.md, docs/plans/triage-2026-11-02.md,
docs/plans/engineering-notes/test-suite-assessment-2026-11-03.md, docs/changelog/2026-11-05-whole-pack-required.md,
.kittify/charter/charter.md ("Gates before merge")
```

## Implementation Concern Map

| ID | Concern | Files | Depends on |
|---|---|---|---|
| IC-01 | Kernel line rules; step texts refuse at build (#299) | kernel/markdown, packs markdown + codec, missions StepDocument/StepSection, tests, ADR-0044 note | — |
| IC-02 | Validate mode normalizes the pack root (#319) | FileSystemPackInspector, MissionTypeBindingTest | — |
| IC-03 | Whole-pack under the required gate; docs; changelog (#186) | workflows, build.gradle.kts comment, docs, charter.md | IC-01, IC-02 (the epic closes only after both) |

## Design decisions

- **D1 (#299) Only the line rules move.** The structural `MarkdownDocument` model stays in `packs` (only packs uses
  it). Moving the whole package touches about 27 files for no gain. See research R1.
- **D2 (#299) Exceptions keep their split.** The kernel rule functions return `Optional<String>` (the problem), as today;
  `MarkdownDocument` throws `IllegalArgumentException` as before, `StepDocument`/`StepSection` throw
  `ConstraintBreachException` as `PackDocument` does.
- **D3 (#299) StepDocument's opening rule.** It applies only when the step has neither title nor description, since a
  description is always written as frontmatter.
- **D4 (#319) `normalize`, not `toRealPath`.** Keeps a linked root's configured name (#284), keeps the "is not a
  directory" refusal, and matches the ten places the store already normalizes. Containment against links is checked
  separately with real paths and is unchanged.
- **D5 (closeout) Own change group.** `ci.yml` gets a `wholepack` output whose regex mirrors `whole-pack.yml`'s
  paths, corrected: `contracts/` (`:packs` reads `contracts/charter` and `contracts/offering`, not only
  `contracts/packs`) and `gradlew`, plus `whole-pack.yml` and `ci.yml` (fail-safe); `whole-pack.yml`'s `push` paths
  widen to match. Job id `wholepack`, display name `Whole-pack round trip`. The `gate` job's `needs`, env, echo and loop all gain
  `wholepack`. `whole-pack.yml` keeps `push` to `main` and `workflow_dispatch`, adds `workflow_call`, drops
  `pull_request`, and takes `service.yml`'s concurrency form so a called run never cancels its own main run.
- **D6 (squad) Tests.** #319: one parameterized validate-mode test over the spellings `host/mission-type`,
  `host/x/../mission-type` (with `x` created), `host/mission-type/.` and `host/mission-type/missions/..`; template
  present → no unreadable file and the plain run's files; template missing → exactly one, ending `: missing`; load
  parity; plus a linked-root variant under `SymbolicLinks`. Red-first evidence: `... (named by step '...'): leaves the
  pack`. #299: kernel rule tests take `MarkdownDocumentTest`'s tables; a `:packs` agreement test drives the same
  tables through `StepDocument`/`StepSection` (each field alone) and `MarkdownDocument`, with the opening-rule
  exemption controls (title present, description present), and writes and reads back every accepted text.
- **D7 (squad) Docs beyond R3.** `WholePackRoundTripTest` javadoc, `ci.yml` header (L8) and mirror comment (L86),
  `docs/architecture/hexagonal-service.md` kernel row, the `StepDocument` javadoc, both markdown `package-info` files,
  a precondition line on `FilesystemPackStore.missingNamedFile`; `service-build.md` L87 keeps "not in `verifyAll`".
