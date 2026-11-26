---
title: "Codebase health, 2026-11-25"
description: The one-page verdict of the 2026-11-25 codebase assessment delta, its scorecard against 2026-11-20, top hotspots, re-ranked actions and operator questions.
doc_status: draft
updated: 2026-11-26T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-delta-2026-11-25
  - plans/codebase-health-2026-11-20
audience:
  - profile: governance-lead
    guidance_text: Read this for the verdict, the scorecard and the re-ranked actions to schedule; the evidence is in the delta note.
  - profile: charter-service-contributor
    guidance_text: Read this to see which defect, hotspot or gate an action targets before you pick it up.
---

# Codebase health, 2026-11-25

This page summarizes the [codebase assessment delta of 2026-11-25](/plans/engineering-notes/codebase-assessment-delta-2026-11-25/),
pinned at `4b95b915`, 109 commits after the [summary of 2026-11-20](/plans/codebase-health-2026-11-20/).
Every claim here has its evidence in the delta note or in the
[2026-11-20 assessment](/plans/engineering-notes/codebase-assessment-2026-11-20/). Nothing here is a
decision. One issue was filed, #654.

## Verdict

The five days since 11-20 went into the closure checks and the new `pack-doctor` module, and almost
none into the ten actions of 11-20. Of those actions, one moved and turned red, one is in progress,
one has started, and seven are untouched. The weight kept gathering where it was:

- `packs` complexity rose another 5.3%;
- `packs.infra.storage` now holds 51.4% of the hotspot score;
- the seven classes over 1,000 lines grew from 9,462 to 9,744 lines.

The checks around the code got worse:

- the weekly mutation run is now red, with `routing` under its floor on its first measurement and
  the `packs-core` shard out of time;
- the CPU-time budget nightly is still cancelled on most nights;
- the SonarCloud gate still fails without naming why.

A new contract defect appeared (#654): a refused per-file write drops the in-file pointer that the
contract promises, and a unit test pins the wrong output. One more open defect sits in the window,
issue #648, filed on 2026-11-24 by the review of #647, where collecting a file's schema violations is
quadratic in their number, a denial-of-service risk within the size cap.

## Scorecard

| Lens | 2026-11-20 | 2026-11-25 | Strongest evidence | Weakest evidence |
|---|---|---|---|---|
| A. Inventory and structure | 4 / 5 | **4 / 5** | Not re-measured; no test module shrank, and `pack-doctor` arrived with 14 test files | `routing-edge`, `missions` and `kernel` still have the thinnest tests |
| B. Effectiveness | 3 / 5 | **3 / 5** | Two fixed delays became signals (`d04a8573`, `c3c59f00`) | The five planted-break gaps and the timing items are unchanged; a unit test pins the defect of #654 |
| C. CI and quality gates | 3 / 5 | **2 / 5** | `service.yml` green on every run since 11-19; `verifyAll` 12 min 57 s | The mutation run is red (#644): `routing` −6.5 points, two `packs` groups unreported; the timing nightly cancelled on 5 of 6 runs; the Sonar gate still unnamed; `pack-doctor` outside the Sonar scope |
| D. Production code | 3 / 5 | **3 / 5** | Typed fault identity in progress (#647 `Origin`, one `ClosureFault`); the closure checks are behind ports (#556) | A new contract defect (#654); an open denial-of-service risk in schema-violation collection (#648); the seven largest classes +282 lines; the `rules` cycle has three more importers; `PackElementViolation` references 115 → 141 |
| E. Hotspots and churn | 2 / 5 | **2 / 5** | 6 of the top 25 cooled, among them `EffectiveReadService` and `RegistrationJobs` | `packs` is 81.0% of the score with 64% of main code lines; `packs.infra.storage` is 51.4% with 23%; 8 of the top 25 heat |

- **C drops** because a gate that was green on 11-20, the weekly mutation run, is now red, and
  the two monitors that were already failing are unchanged.
- **D holds** because #654 is the only new defect, and the typed fault identity has started.
  Against that, class size, the cycle and the lossy violation type grew.
- **E holds:** fewer hotspots heat than on 11-20, but the concentration in one package rose.

Not compared, as on 11-20: aggregate coverage and the SonarCloud ratings. Both need artifacts or an
export that this session could not reach.

## Top hotspots

Score = revisions × total cyclomatic complexity, bookkeeping commits left out.

| # | File | Revisions | Total CCN | Score | Rank on 11-20 | Trend |
|---|---|---:|---:|---:|---:|---|
| 1 | packs `infra/storage/FileSystemPackInspector` | 65 | 261 | 16,965 | 1 | heating |
| 2 | packs `infra/storage/FilesystemPackStore` | 63 | 221 | 13,923 | 2 | heating |
| 3 | packs `domain/PackCompositionResolver` | 42 | 195 | 8,190 | 3 | stable |
| 4 | packs `infra/storage/WholePackStore` | 55 | 111 | 6,105 | 5 | stable |
| 5 | packs `infra/storage/DomainRoundTrip` | 34 | 177 | 6,018 | 7 | heating |
| 6 | packs `infra/storage/DomainRepositoryService` | 87 | 68 | 5,916 | 4 | stable |
| 7 | packs `infra/storage/PackCommitStore` | 41 | 128 | 5,248 | 6 | stable |
| 8 | packs `infra/storage/PackFileSet` | 37 | 103 | 3,811 | 9 | heating |
| 9 | activations `application/EffectiveReadService` | 31 | 101 | 3,131 | 8 | cooling |
| 10 | packs `application/RegistrationJobs` | 17 | 151 | 2,567 | 10 | cooling |

## Top actions, re-ranked

| # | Action | Issue | Size | Risk if left |
|---|---|---|---|---|
| 1 | Put the in-file pointer on a refused per-file write; fix the test that pins the bare file; add a scenario | #654 | S | MEDIUM: a contract promise broken on the wire, with green gates |
| 2 | Turn the mutation run green: bring `routing` to its floor or rule a lower first floor in ADR-0062; get `packs-core` inside its 110 minutes | #644, #530, #531 | S–M | MEDIUM: two `packs` groups go unmeasured every week |
| 3 | Revive the CPU-time budget nightly: fix the two fixtures, add a step timeout, report when the step does not succeed | new (adjacent: #494) | S | HIGH: a cost regression lands unseen |
| 4 | Make the SonarCloud gate name its condition and turn green; add `pack-doctor` and the packs test fixtures to the Sonar scope | #463, #494 | S | MEDIUM: a real regression hides behind a known-red gate |
| 5 | Guard the five open planted-break rules, template path first | new | S | MEDIUM: a containment rule can be deleted with every gate green |
| 6 | Finish the typed fault identity: delete the text matching and `PackElementViolation` | new (ADR-0075 AA2; #614) | M | MEDIUM: hotspots 1, 2 and 7 break on a wording edit |
| 7 | One parse per file and one companion resolution | new (related: #416) | L | MEDIUM: the hottest file stays the costliest to change |
| 8 | Replace the sleeps with state waits; one shared await helper | #659 (delivered by its pull request) | S–M | MEDIUM: vacuous passes on a loaded runner |
| 9 | Take the static counters, `JobGate` and the test-only constructors out of main code | #659 (ADR-0075 AA5; delivered by its pull request) | S | MEDIUM: global state across parallel tests |
| 10 | Bound class size and parameter count, forbid package cycles, refresh `hexagonal-service.md`, then split the largest classes | new (#205 covers the diagrams only) | S, then L | MEDIUM: the largest classes grew 3% in 5.4 days with no gate |

The per-kind read path (11-20 action 9) and the smaller actions of 11-20 stand as they were listed
there.

## Questions for the operator

The four questions of 2026-11-20 are still open, and one is new:

1. **The model rulings:** the activation store's version 1 reader, the unversioned pack descriptor
   and the "Doctrine" type names. Rule on each, or record that they wait for the RC.
2. **SonarCloud:** export the gate status, ratings, issues by rule, hotspots and duplication for
   `spec-kitty_spec-kitty-charter`. The calls are listed on the 2026-11-20
   [static analysis page](/plans/engineering-notes/codebase-assessment-2026-11-20/static-analysis/).
3. **Class-size gate:** bound file length and parameter count at today's maximum now, or after the splits?
4. **A new module's first mutation run:** make it part of the module's landing? `routing` is the case
   in point: it landed with a floor of 90 and first scored 83.5% four days later (landed 11-19 in `aa1ae67b`, scored 11-23).
5. **`routing`'s floor (new):** raise the tests to meet 90, or rule a lower first floor in an ADR-0062
   amendment?
