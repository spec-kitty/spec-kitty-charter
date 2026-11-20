---
title: "Codebase health, 2026-11-20"
description: The one-page verdict of the 2026-11-20 codebase assessment, its scorecard against 2026-11-12, top hotspots, ten ranked actions and operator questions.
doc_status: draft
updated: 2026-11-20T00:00:00Z
type: Plan
kind: Planning
related:
  - plans/engineering-notes/codebase-assessment-2026-11-20
  - plans/engineering-notes/code-and-test-quality-assessment-2026-11-12
audience:
  - profile: governance-lead
    guidance_text: Read this for the verdict, the scorecard and the ten actions to schedule; the evidence is in the assessment note.
  - profile: charter-service-contributor
    guidance_text: Read this to see which hotspot or test gap an action targets before you pick it up.
---

# Codebase health, 2026-11-20

This page summarizes the [codebase assessment of 2026-11-20](/plans/engineering-notes/codebase-assessment-2026-11-20/),
pinned at `25e24c37`. Each claim here has its evidence (file and line, numbers, commands) in that
note and its lens pages. Nothing here is a decision, and no issue was filed.

## Verdict

The charter's code is in good shape method by method, and its weight is gathering in one package.
The complexity gate is on, `PackPolicy` is gone, and no method is over the limit. No lens found a
correctness, data or security defect in production code. But `packs.infra.storage` holds half of
the hotspot score. Complexity in `packs` grew 24% in 7.6 days. Seven classes are now over 1,000
lines (six on 2026-11-12, `PackPolicy` among them), and no gate measures class size. The hottest files decide fault identity by matching text,
and they read each file twice: these are known limits that ADR-0075 defers to its single-parse unification, which has no issue yet
(#556 is slice 7, the closure checks). Around the code, the checks are strong where they run, but some fail without saying so:

- the CPU-time budget nightly has been cancelled at its 20-minute job timeout for two nights; its green runs
  already took 19.5 minutes, and since 2026-11-19 two budget fixtures are also broken;
- the SonarCloud gate fails without naming its condition;
- two modules have mutation floors and no score.

Five of the six test gaps that the 2026-11-12 assessment proved are still open.

## Scorecard

| Lens | 2026-11-12 | 2026-11-20 | Strongest evidence | Weakest evidence |
|---|---|---|---|---|
| A. Inventory and structure | 4 / 5 | **4 / 5** | 1,001 contract scenarios (653 before); 5,993 test methods (about 4,652 before); test-to-main ratio 1.46 by lines | `routing-edge` (0.51), `missions` (0.61) and `kernel` (0.53) have the thinnest tests |
| B. Effectiveness | 4 / 5 | **3 / 5** | `DrgEdge` gap closed (as a side effect of #537); ADR-0059 D1 switch gate enforced with controls; #593 sets a good timing pattern | 5 of 6 proved gaps open; all 8 timing items open; the type-only pattern spreads into new `:missions` tests; the duplicated poll grew from 4 copies to 6 |
| C. CI and quality gates | 3 / 5 | **3 / 5** | Mutation run 43794141037 green with every floor met; the complexity gate is on (#371); CPD duplication 0.17% | The timing nightly is dead and silent; the Sonar gate fails unnamed; `routing` and `routing-edge` have never been measured; no dependency update bot |
| D. Production code | 3 / 5 | **3 / 5** | `PackPolicy` deleted (#554); no method over CCN 15; every service package `@NullMarked`; 7 suppressions in main code | Seven classes over 1,000 lines (six on 11-12); text-matched fault identity; six static counters (two before); four legacy items without a ruling; 9 copies of code-point ordering (5 before) |
| E. Hotspots and churn (new) | — | **2 / 5** | The edge modules are not change-coupled to their domains (1–12%) | `packs` is 80.4% of the score with 65% of main code lines, but `packs.infra.storage` is 49.4% with 23%; 13 of the top 25 hotspots are heating |

B drops because no gap was closed on purpose in eight days and the type-only pattern spread into new
`:missions` tests; the open counts alone are no worse than on 11-12. E grades how concentrated and how
fast-moving the hotspots are: 5 is spread and cooling, 1 is one package that holds most of the score and
heats. Here one package holds half the score from a quarter of the code, and 13 of the top 25 heat.

Not compared: aggregate coverage and the SonarCloud ratings. Both need artifacts or an export that
the assessment session could not reach (question 2).

## Top hotspots

Score = revisions × total cyclomatic complexity, bookkeeping commits left out.

| # | File | Revisions | Total CCN | Score | Trend since 11-12 |
|---|---|---:|---:|---:|---|
| 1 | packs `infra/storage/FileSystemPackInspector` | 56 | 260 | 14,560 | heating |
| 2 | packs `infra/storage/FilesystemPackStore` | 52 | 212 | 11,024 | stable |
| 3 | packs `domain/PackCompositionResolver` | 40 | 197 | 7,880 | heating |
| 4 | packs `infra/storage/DomainRepositoryService` | 82 | 69 | 5,658 | stable, shrinking |
| 5 | packs `infra/storage/WholePackStore` | 51 | 107 | 5,457 | heating, shrinking |
| 6 | packs `infra/storage/PackCommitStore` | 38 | 136 | 5,168 | heating |
| 7 | packs `infra/storage/DomainRoundTrip` | 31 | 160 | 4,960 | heating |
| 8 | activations `application/EffectiveReadService` | 31 | 101 | 3,131 | heating |
| 9 | packs `infra/storage/PackFileSet` | 32 | 88 | 2,816 | cooling |
| 10 | packs `application/RegistrationJobs` | 17 | 151 | 2,567 | stable |

## Top 10 actions

| # | Action | Issue | Size | Risk if left |
|---|---|---|---|---|
| 1 | Revive the CPU-time budget nightly: fix the two fixtures, add a step timeout, and report when the step does not succeed | new (adjacent: #494 covers the mutation and Sonar nightly runs) | S | HIGH |
| 2 | Make the SonarCloud gate legible and green: name the condition, mark the five false positives, fix the scope | #463, #494 | S | MEDIUM |
| 3 | Guard the five open planted-break rules, template path first; stop type-only refusal tests in `:missions` | new | S | MEDIUM |
| 4 | Dispatch a mutation run so `routing` and `routing-edge` get a first score; amend ADR-0062; strengthen `:profiles`; close #461 | #461, #530, #531 | S | MEDIUM |
| 5 | Give faults a typed identity from read to refusal; delete the text matching and `PackElementViolation` | new (ADR-0075 AA2; #614 for `CompositionFault`) | M | MEDIUM |
| 6 | Finish the one reader: one parse per file, one companion resolution, one read per write | new (ADR-0075 single-parse unification; related: #416) | L | MEDIUM |
| 7 | Replace the sleeps with state waits on the #593 pattern; one shared registration await helper | new | S–M | MEDIUM |
| 8 | Take the static counters, `JobGate` and the test-only constructors out of main code | new (ADR-0075 AA5) | S | MEDIUM |
| 9 | Collapse the per-kind read path onto the kind-keyed one | new | M | MEDIUM |
| 10 | Gate class size and parameter count at today's maximum, forbid package cycles, refresh `hexagonal-service.md`, then split the largest classes | new (#205 covers the PlantUML diagrams only) | S, then L | MEDIUM |

Further actions, tied to #51, #49, #50, #596 and #397, are listed in the
[note's ranked actions](/plans/engineering-notes/codebase-assessment-2026-11-20/#ranked-actions).

## Questions for the operator

1. The three model rulings asked on 2026-11-12 are still open: the activation store's version 1
   reader, the unversioned pack descriptor and the "Doctrine" type names. Rule now, or after the RC?
2. Export the SonarCloud gate status, ratings, issues by rule, hotspots and duplication for
   `spec-kitty_spec-kitty-charter`. The calls are listed on the [static analysis page](/plans/engineering-notes/codebase-assessment-2026-11-20/static-analysis/).
3. Should Checkstyle bound file length and parameter count at today's maximum now, or after the
   splits of action 10?
4. Should a new module's first mutation run be part of its landing, given that the workflow runs only weekly?
