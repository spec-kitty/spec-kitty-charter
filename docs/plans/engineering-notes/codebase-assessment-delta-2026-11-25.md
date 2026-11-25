---
title: "Codebase assessment delta, 2026-11-25"
description: What moved in the code, the nightly checks and the ten ranked actions in the 109 commits after the 2026-11-20 codebase assessment, with one new contract defect.
doc_status: draft
updated: 2026-11-25T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/codebase-health-2026-11-20
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new defect, the hotspots that grew, and which of the ten actions moved.
  - profile: governance-lead
    guidance_text: Read this for the action progress table and the re-ranked actions.
---

# Codebase assessment delta, 2026-11-25

**Status: assessment, not a decision.** This note re-measures the
[codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/) on
current `main`. It reports only what moved; the method, the definitions and the full findings are
in that note. It changes no code and files one issue: the defect below, #654.

## Baseline

- **Pin:** `4b95b9151f0fb42c9d184f1b831e01f843a36002` (`main`, 2026-11-25). **Previous pin:**
  `25e24c372bae838d95a9328be57ee2063f6c05d1` (2026-11-19). Between them: 109 commits, 99 of them
  not bookkeeping, over 5.4 days.
- **Main code changed:** 166 files, +11,088 / −3,013 lines. `packs` took 128 files, the new
  `pack-doctor` module (formerly `pack-check`) 12, `pack-validation` 12 and `kernel` 11. `activations`,
  `deployable`, `profiles`, `routing` and every `-edge` module did not change.
- **What ran:**
  - the hotspot scripts with the two pins as parameters;
  - a code re-check of every finding of 11-20 at the new pin;
  - the GitHub Actions runs and the issue states since 2026-11-19.

  PIT and `verifyAll` were not re-run.

## Short answer

**The work since 11-20 went to the closure checks and `pack-doctor`. Of the ten ranked actions,
one moved (and went red), one is in progress, one has started and seven are untouched. Two nightly checks got
worse, and one new contract defect appeared.**

- **A new contract defect** (MEDIUM). A per-file write refused because its pack would not
  revalidate drops the place inside the file from its violation pointer. The contract promises
  that place since 2026-11-23.
- **The weekly mutation run is now red** (run 45101937079, 2026-11-23, alert #644):
  - `routing` was measured for the first time and is 6.5 points under its floor;
  - the `packs-core` shard ran into its 110-minute limit, so two `packs` groups have no report.
- **The CPU-time budget nightly is still dead.** Five of the six runs from 11-20 to 11-25 were cancelled at the
  job timeout. The one that finished hid two failed budget tests, because its budget step may fail
  without failing the job. No commit touched the workflow or the two fixtures.
- **The hotspots kept growing.**
  - Complexity in `packs` rose another 5.3% (7,997 to 8,423).
  - `packs.infra.storage` went from 49.4% to 51.4% of the hotspot score.
  - The seven classes over 1,000 lines grew from 9,462 to 9,744 lines. `FilesystemPackStore` alone
    gained 146 lines.
- **Of the 69 findings re-checked:** 4 fixed, 2 partly fixed, 4 worse and 59 open.

## New defect: the per-file write refusal loses its in-file pointer

**Severity: MEDIUM. Lens: L2 re-check. Size: S.**

- **The contract.** The change note `contracts/charter/changes/2026-11-23-violation-in-file-pointer.md`
  (`516ec2bd`) and `contracts/_shared/schemas/RefusalViolation.yaml:10-31` say a per-file write or
  removal refused because the pack would not revalidate names the file, then `#` and the JSON
  pointer of the place, for example `drg/directive.graph.yaml#/edges/3`.
- **Registration keeps that promise.** It composes the pointer with `PackRefusalMapping.pointer(Location)`
  (`packs/domain/PackRefusalMapping.java:108-110`, `ff46ac41`).
- **The per-file path does not.** `PackCommitStore.requireNoNewViolation` builds each
  `RefusalViolation` with `reason.location().file()` alone (`packs/infra/storage/PackCommitStore.java:436-440`).
  These lines are unchanged since the previous pin. The `Reason` already carries the in-file pointer.
- **A test pins the wrong pointer.** `PackCommitStoreTest.aHeldFaultDoesNotBlockAWriteAndANewFaultIsRefused`
  (`PackCommitStoreTest.java:258`) asserts the bare `RelationshipShard.SHARD_PATH` for a
  `drg_schema_error` whose `Reason` carries `/edges/0`, so the gates stay green on the defect. No
  scenario covers the pointer on this refusal.
- **Fix:** build the violation through `PackRefusalMapping`'s one mapping, by making
  `violation(Reason)` (or `pointer(Location)`) public, since `PackCommitStore` is outside
  `packs.domain`. Update `PackCommitStoreTest.java:258` to `registered.graph.yaml#/edges/0`, and add a
  scenario that pins `file#/edges/<n>` on a refused per-file write. Tracked in #654, filed on 2026-11-25 at the orchestrator's request.

## Hotspots now

Score = revisions × total cyclomatic complexity (CCN), bookkeeping commits left out, as on 11-20.
Trend compares each file's share of the 99 commits since 11-19 with its share in the 30 days before.

| # | File | Revisions | Total CCN | Score | Rank on 11-20 | Trend |
|---|---|---:|---:|---:|---:|---|
| 1 | packs `infra/storage/FileSystemPackInspector` | 65 | 261 | 16,965 | 1 | heating |
| 2 | packs `infra/storage/FilesystemPackStore` | 63 | 221 | 13,923 | 2 | heating (+81 code lines) |
| 3 | packs `domain/PackCompositionResolver` | 42 | 195 | 8,190 | 3 | stable |
| 4 | packs `infra/storage/WholePackStore` | 55 | 111 | 6,105 | 5 | stable |
| 5 | packs `infra/storage/DomainRoundTrip` | 34 | 177 | 6,018 | 7 | heating (CCN +17) |
| 6 | packs `infra/storage/DomainRepositoryService` | 87 | 68 | 5,916 | 4 | stable |
| 7 | packs `infra/storage/PackCommitStore` | 41 | 128 | 5,248 | 6 | stable (CCN −8) |
| 8 | packs `infra/storage/PackFileSet` | 37 | 103 | 3,811 | 9 | heating (CCN +15) |
| 9 | activations `application/EffectiveReadService` | 31 | 101 | 3,131 | 8 | cooling (no change) |
| 10 | packs `application/RegistrationJobs` | 17 | 151 | 2,567 | 10 | cooling (no change) |

- **Heating outside the top 10:** `CompositionFactsBuilder` (8 commits in 5.4 days, against 7 in
  the 30 days before), `StorageRootLocations`, `CommitStorePackWriteStore` and `WholePackExpectation`.
- **By module:** `packs` is 81.0% of the score (80.4% on 11-20). The new `pack-doctor` module
  enters with 1,592 code lines and a total CCN of 377.
- **The squash filter still holds.** 18 of the top 20 stay when only commits of 30 files or fewer
  count. `29f0b4ed` (279 files) is the largest new commit.

## The ten actions of 11-20

| # | Action | Status | Evidence |
|---|---|---|---|
| 1 | Revive the CPU-time budget nightly | **Untouched; still failing** | Runs on 11-21, 11-23, 11-24 and 11-25 were cancelled. Run 44984587709 (11-22) shows success, but 2 of the 6 `:packs` budget tests failed on the same two fixtures (`ResolverCostBoundTest:139-140`, `ReachabilityBudgetTest:95-104`). The budgets it did measure were within their limits. `timing-nightly.yml` is unchanged |
| 2 | Make the SonarCloud gate legible and green | **Untouched** | Run 45547043477 still logs "Quality Gate has FAILED" with no condition named. `pack-doctor` (2,513 lines) is still outside `sonar.yml:160-161`; the test-fixture gap is still open. #463 and #494 have had no activity |
| 3 | Guard the five open planted-break rules | **Untouched** | Gaps 1, 2, 3, 5 and 6 are open. The `KindBindingsTest` friction bit as predicted: `29f0b4ed` had to change `hasSize(15)` to `hasSize(16)` for `ANTI_PATTERN` (`:75-77`) |
| 4 | Measure what the floors claim | **Moved, and now red** | Run 45101937079 measured `routing` (83.5% against 90, −6 mutants) and `routing-edge` (92.4% against 85). `packs-core` ran its mutation step for 1h50m against a 110-minute limit (44 min on 11-16), so packs domain and application, and packs `infra.fetch`, have no report. `:profiles` is still 3 mutants over. #461 was closed on 11-21; #644 is the new alert. ADR-0062 has no amendment for routing |
| 5 | Typed fault identity | **In progress** | #647 added `Origin` to `Reason` and one `ClosureFault`, and `ff46ac41` gave `PackElementViolation` its in-file pointer. The text matching is unchanged (`AssemblyRules.java:194`, `:206-215`, `:235`; `FilesystemPackStore.reasonOf :1675-1683`). Main-code references to `PackElementViolation` grew from 115 to 141. #614 remains open for `ClosureVerdict` |
| 6 | One parse per file | **Untouched** | Two parses (`FileSystemPackInspector :362`, `:936`, `:1183`); companions resolved twice (`CensusEntries :104`, `PackFileSet :218`); salvage reader at `WholePackStore :424`. #416 has had no activity |
| 7 | State waits instead of sleeps; one await helper | **Started** | `d04a8573` and `c3c59f00` replaced two fixed delays with signals. The 11-12 timing items and the six poll copies are unchanged, and no shared helper exists |
| 8 | Test hooks out of main code | **Untouched** | Still six static read counters, the `JobGate` bean (`RegistrationWiring.java:83-84`), and the test-only constructors |
| 9 | One read path for every kind | **Untouched** | Six per-kind read services; `ArtifactReadService` still takes 12 parameters |
| 10 | Bound class size, forbid cycles, refresh the docs | **Untouched; worse** | No `FileLength`, `ParameterNumber` or cycle rule. The `packs.domain ⇄ packs.domain.rules` cycle widened: three more `rules` classes import `packs.domain` (`CaseOnlyRules` and `PresetItemRules`, `29f0b4ed`; `Findings`, `ff46ac41`); the `PackCompositionResolver` → `rules.DescriptorChecks` back edge is unchanged. The seven largest classes grew by 282 lines |

Issue #556 (slice 7, the closure checks behind ports) closed on 11-21 by PR #618. The 11-20 review had
remapped actions 5, 6 and 8 away from it, and slice 7 did not change their findings. None of the
"new" actions has been filed as an issue, and no commit cites actions 1 to 10.

## Findings that moved

- **Fixed:**
  - `MixedReadConcurrencyTest` no longer releases its swap after a fixed 15 ms (`d04a8573`).
  - The closure checks are behind ports (#556).
  - Before the range: the `RegistrationJobsTest` 30 s gate and planted-break gap 4.
- **Partly fixed:** `PackElementViolation` now carries the in-file pointer, but `reasonOf` still
  strips a text prefix, and the type has no severity.
- **Worse:**
  - The seven classes over 1,000 lines now total 9,744 lines (9,462 before):
    - `FilesystemPackStore`: 1,627 → 1,773 lines;
    - `PackCompositionResolver`: 1,480 → 1,542;
    - `FileSystemPackInspector`: 1,438 → 1,469;
    - `ClosureView`: 1,171 → 1,225.
  - The `packs.domain ⇄ packs.domain.rules` cycle has three more `rules` → `packs.domain` importers.
  - The retired "Doctrine" type names appear on one more line (`DomainRoundTrip`).
  - `pack-doctor` is a new module outside the Sonar scope.
- **Open and unchanged:** the other 59, among them the text-matched fault identity, the double
  parse, the static counters, the per-kind read path, the legacy readers without a ruling, the
  hexagonal-document drift, the knowledge duplicates and the dependency hygiene. The code re-check
  lists each one with its line at the new pin; the 11-20 lens pages give the reasoning.

## Mutation margins, run 45101937079

| Group | Score | Floor | Margin | On 11-16 |
|---|--:|--:|---|---|
| **routing** | 83.5% | 90 | **−6.5 points, 6 mutants short** | never run |
| routing-edge | 92.4% | 85 | +7.4 points, 3 mutants | never run |
| **profiles** | 91.1% | 90 | **+1.1 points, 3 mutants** | the same |
| packs `infra.archive` | 82.9% | 80 | +2.9 points, 12 mutants | the same |
| pack-validation | 90.1% | 85 | +5.1 points, 23 mutants | +3.0, 10 |
| packs `infra.storage` | 90.5% | 85 | +5.5 points, 199 mutants | +5.4, 175 |
| pack-check | 96.3% | 90 | +6.3 points, 12 mutants | — |
| packs domain and application; packs `infra.fetch` | no report | 85 / 70 | — | +5.3, 219 / +8.2, 56 |

The other groups (activations, artifacts-edge, kernel, missions, missions-edge, profiles-edge)
are within a point of their 11-16 margins.

## Ranked actions, updated

The ranking of 11-20 stands, with three changes:

1. **New, first:** fix the pointer of the per-file write refusal and pin it with a scenario
   (above). Size S. #654.
2. **Action 4 rises to second.** The run is red: bring `routing` over its floor or amend ADR-0062
   with a ruled floor, and get `packs-core` inside its time limit (#644, #530, #531). The
   `ExampleVocabularyReadTest` cost from 11-20 is a likely part of the shard's time.
3. **Action 2 gains a step:** add `pack-doctor` to `sonar.yml`.

Then actions 1, 3, 5, 6, 7, 8, 9 and 10, as on 11-20. Action 10 is more urgent than it was, since
the largest classes grew 3% in 5.4 days with no gate on them.

## Questions for the operator

1. The four questions of 11-20 are still open.
2. **`routing`'s floor.** Raise the tests to meet 90, or rule a lower first floor in an ADR-0062
   amendment? No amendment exists for either `routing` module.

## Reproduce

```bash
git checkout 4b95b9151f0fb42c9d184f1b831e01f843a36002
ANALYSIS_REV=4b95b9151f0fb42c9d184f1b831e01f843a36002 \
ANALYSIS_BASE=25e24c372bae838d95a9328be57ee2063f6c05d1 \
ANALYSIS_OUT=build/analysis/hotspots-4b95b915 \
  bash scripts/analysis/hotspots/run-all.sh
git diff --stat 25e24c37 4b95b915 -- 'service/*/src/main/**'
git log --format='%h %cs %s' 25e24c37..4b95b915
```

- **The scripts still reproduce the 11-20 tables.** With no variables set, `run-all.sh` still
  reproduces them; only the trend heading and its label sentence changed.
- **CI runs read:**
  - timing-nightly: 44984587709 and 45546253555;
  - sonar: 45547043477;
  - mutation-testing: 45101937079;
  - service: 45484928123 (`verifyAll` took 12 min 57 s).
- **Not read:** the `packs-core` shard log; its timeout is inferred from its timestamps.
