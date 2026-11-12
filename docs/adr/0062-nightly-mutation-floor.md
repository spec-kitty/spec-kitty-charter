---
title: "ADR-0062: Nightly mutation floor"
description: "The nightly PIT run stops being an experiment: each mutated module and packs group has a fixed floor that turns the nightly red, never a pull request."
doc_status: active
updated: 2026-11-11T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - guides/service-build
  - adr/0023-ci-gate-required-check
  - adr/0057-performance-budgets-never-block-a-pull-request
  - adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr
  - plans/engineering-notes/test-suite-assessment-2026-11-03
audience:
  - profile: charter-service-contributor
    guidance_text: Read this when the mutation nightly is red, before you change a floor, or when you triage a surviving mutant.
  - profile: governance-lead
    guidance_text: Read this for why mutation testing gates the nightly but never a pull request, and how a floor moves.
---

# ADR-0062: Nightly mutation floor

## Status

**Accepted**, operator rulings of 2026-11-03, 2026-11-04 and 2026-11-05. Decider: Stijn Dejongh (operator). On
2026-11-03 the operator adopted [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) as designed (R1 and
W1 of the [test suite assessment](/plans/engineering-notes/test-suite-assessment-2026-11-03/)). On 2026-11-04 and
2026-11-05 the operator ruled the floors and put `packs.infra.storage`, then `packs.infra.fetch`, in scope with floors
of their own. The implementation is the mission `test-mutation-floors-01M6NSDR`, in the same pull request as this ADR.

This ADR ends the "experiment" status of mutation testing, decided as `DM-01M57RMMBAS7275PXGNSYJZBQY`
("Nightly, as an experiment; never on every pull request and never a merge gate"). It supersedes the "experiment"
wording of the PIT-cadence decision and of User Story 5 in the
[contract-and-build mission's spec](https://github.com/spec-kitty/spec-kitty-charter/blob/main/kitty-specs/charter-contract-and-build-retro-01M57QHJ/spec.md),
which FR-009 implements. The rest of that decision stands: mutation testing never runs on a pull request and is never
a merge gate.

It also amends [ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/), whose Context and
References describe `mutation-nightly.yml` as gating nothing and as the report-only shape the SonarCloud nightly
follows. From this ADR on, the mutation nightly turns red when a floor is missed; it still gates no pull request and is
no part of the required check. The SonarCloud nightly stays report-only, as ADR-0058 decides.

## Context

Until this change, `.github/workflows/mutation-nightly.yml` ran `./gradlew pitest` every night and was always green:

- **No threshold.** No module set a floor, so a falling score failed nothing.
- **Not where the logic is.** PIT reached kernel, missions and profiles only. `packs`, which holds about 90 % of the
  code, cannot apply `charter.domain-conventions` (it refuses external dependencies), so it was never mutated.
- **Barely visible.** There was no job summary and no issue on a red run, and only kernel's report was uploaded.

First-run scores, measured on main's tests (JDK 25):

| Module / group | Mutants | Score |
|---|--:|--:|
| kernel | 378 | 72.4 % (76 % with this pull request's kernel triage) |
| missions | 144 | 75.0 % |
| profiles | 66 | 53.0 % |
| packs `domain+application` | 1638 | 92.7 % |
| packs `infra.storage` | 2092 | 90.7 % |
| packs `infra.fetch` | 269 | 77.3 % |

After [#384](https://github.com/spec-kitty/spec-kitty-charter/pull/384) merged, the scores are:

| Module / group | Detected / mutants | Score | Margin over floor |
|---|--:|--:|--:|
| kernel | 312 / 379 | 82.3 % | 27 (at the raised floor of 75) |
| missions | 108 / 144 | 75.0 % | 7 |
| profiles | 64 / 66 | 96.9 % | 4 (at the raised floor of 90) |
| packs `domain+application` | 1519 / 1632 | 93.0 % | 131 |
| packs `infra.storage` | 1900 / 2092 | 90.8 % | 121 |
| packs `infra.fetch` | 208 / 269 | 77.3 % | 19 |

A full `:packs:pitest` took 45m54s on an idle 4-core machine without `infra.fetch`, which adds about 2 minutes: its
narrowed run (the `infra.fetch` classes against every packs target test) took 116 s in the `pitest` task, 311 s for the
whole Gradle run. Kernel, missions and profiles together take about 3 minutes. PIT's `mutations.xml` holds raw `<`
characters in mutator descriptions, so it is not well-formed XML.

## Decision

1. **One convention.** The `charter.mutation-conventions` plugin in `build-logic` holds PIT's versions, the HTML and
   XML reports, `timestampedReports = false`, PIT's default mutators and at most four threads.
   `charter.domain-conventions` applies it for kernel, missions and profiles; `service/packs` applies it directly.
2. **The score.** A group's score is PIT's mutation score: the detected mutants over **all** mutants, uncovered ones
   included. Detected means PIT's detected statuses: KILLED, TIMED_OUT, NON_VIABLE, MEMORY_ERROR, RUN_ERROR and
   EQUIVALENT.
   - **The pass rule is exact.** A group passes when `detected × 100 ≥ floor × mutants`, in integers, not PIT's
     rounded percentage. Printed scores are truncated to one decimal, never rounded up, so a failing score never
     reads as its floor.
   - **Test strength is reported, not gated.** It is detected over covered mutants (all minus NO_COVERAGE). Coverage
     stays JaCoCo's to report.
3. **Fixed floors per module, declared in its build file** (`mutationFloors { group(...) { ... } }`):

   | Module | Group | Packages | Floor | Margin |
   |---|---|---|--:|--:|
   | kernel | `kernel` | `ai.speckitty.charter.kernel` | 75 | 27 |
   | missions | `missions` | `ai.speckitty.charter.missions` | 70 | 7 |
   | profiles | `profiles` | `ai.speckitty.charter.profiles` | 90 | 4 |
   | activations | `activations` | `ai.speckitty.charter.activations` | 85 | 37 |
   | profiles-edge | `profiles-edge` | `profiles.application`, `profiles.infra`, without the four `*.config` packages | 85 | 18 |
   | missions-edge | `missions-edge` | `missions.application`, `missions.infra`, without the four `*.config` packages | 85 | not yet measured |
   | packs | `domain+application` | `packs.domain`, `packs.application`, without `application.config` | 85 | 126 |
   | packs | `infra.storage` | `packs.infra.storage` | 85 | 120 |
   | packs | `infra.fetch` | `packs.infra.fetch`, without `infra.fetch.config` | 70 | 19 |

   The packs floors follow `floor5(score − 3)` of the first measured run (92.7 % and 90.7 % give 85 each, 77.3 % gives
   70). Missions takes the floor the operator ruled. Kernel and profiles were ruled 65 and 50, then raised to 75 and 90
   by the raise rule once #384's tests landed (see the amendment below).

   - **The margin** is how many detected mutants a group can lose and still pass, in the run that set its floor:
     `detected − ⌈floor × mutants / 100⌉` (kernel 289 of 378 with this pull request's triage, missions 108 of 144,
     profiles 35 of 66, `domain+application` 1519 of 1638, `infra.storage` 1899 of 2092, `infra.fetch` 208 of 269).
   - **What a floor catches.** A floor backstops a large regression: a deleted test class, a module's tests no longer
     running, a large piece of code arriving untested. It does not catch a single weakened test, which costs a few
     mutants, well inside every packs and kernel margin.
   - **Missions is near the trigger.** A margin of 7 mutants means a small change there, even moving a few lines of
     covered code, can turn the nightly red. Profiles had a margin of 2 in the first run; after #384 and the raise to 90 it has 4, so it is near the trigger too.
4. **The packs scope.** PIT mutates `packs.domain.*`, `packs.application.*`, `packs.infra.storage.*`
   (`infra.storage.config` included, operator ruling) and `packs.infra.fetch.*` (operator ruling), and never the
   Spring wiring (`application.config.*`, `infra.fetch.config.*`) or `infra.api.*` (the REST adapter). It runs only
   the unit tests of those packages: no `*WiredTest`, and no test tagged `open-packs`, `whole-pack` or `timing`. The
   three groups are scored from the one `:packs:pitest` run.
5. **The floor check.** `pitest` is finalized by `checkMutationFloors`, which reads `build/reports/pitest/mutations.xml`
   with a lenient scan, never a strict XML parser, and writes `build/reports/mutation-floors/floors.json`. A group
   below its floor fails the task with `<path> <group>: mutation score S % is below its floor F % (#239, ADR-0062)`.
   A group without mutants, a missing report and `<mutation>` elements that cannot be read each fail too, so a broken
   run is never scored as a pass.
6. **Moving a floor.**
   - **Raise** a floor by pull request when the nightly score is 5 or more points above it.
   - **Lower** a floor only by an amendment to this ADR.
   - [#384](https://github.com/spec-kitty/spec-kitty-charter/pull/384) has merged, so kernel and profiles qualified
     under the raise rule; they are raised to 75 and 90 (see the amendment below).
7. **Triage of a surviving mutant**, in this order:
   1. kill it by strengthening an **existing** test's assertion;
   2. mark it equivalent, with a one-line reason;
   3. accept it, naming an owner.

   A new test whose only purpose is killing a mutant is never an allowed outcome (DIRECTIVE_041). This pull request
   applies the order to `kernel.domain`: 14 survivors killed, 11 marked equivalent.

   An equivalent verdict is recorded in a triage file beside the work that made it; this pull request's is
   `kitty-specs/test-mutation-floors-01M6NSDR/tasks/WP05-kernel-survivor-triage/triage.md`. Stock PIT, as configured
   here, cannot mark a mutant equivalent: the `EQUIVALENT` status comes only from PIT extensions. So an equivalent
   mutant still counts as SURVIVED: it stays in the score's denominator and in every run's survivor count.
8. **The nightly** (`.github/workflows/mutation-nightly.yml`) runs on a schedule and on `workflow_dispatch` only.
   - It syncs open-packs at the pin, then runs `./gradlew pitest --continue`, so every module reports even when one
     fails. The summary script's unit tests run after PIT, whatever its result, so they never keep PIT from running.
   - An `if: always()` step writes a job summary of every module and group from the `floors.json` files: mutants,
     score, test strength, uncovered mutants, floor, verdict and the classes with the most surviving or uncovered
     mutants.
   - Every module's report and `floors.json` are uploaded, on a red run too, and kept for 14 days.
   - When a scheduled run does not succeed, a timeout included, an alert job with only `issues: write` opens one
     issue labelled `mutation-nightly`, or comments on the open one. A `workflow_dispatch` with `force_issue` takes
     the same path, marked as a forced test run. Proving the alert with `force_issue` costs a full PIT run.
   - `timeout-minutes` is 90 (operator ruling). The mutation step has 80 of them, so the summary and the upload
     still run when PIT runs out of time.
   - PIT runs in two shards, each on its own runner with that budget: `:packs` alone, and every other module (#461).
     A full `:packs:pitest` measured 1h06m34s alone on a 4-core machine on 2026-11-12, so the modules no longer fit one
     runner. A summary job merges the shards' `mutation-report-<shard>` artifacts into the one table; a red summary
     job raises the alert as a red shard does.
9. **Never on a pull request.** No pull-request or `merge_group` trigger runs PIT, `verifyAll` contains no `pitest` or
   `checkMutationFloors` task, and the nightly is never part of the required check of
   [ADR-0023](/adr/0023-ci-gate-required-check/).

A run narrowed with `service/packs/pit-targets.init.gradle` (`-Dpit.classes`, `-Dpit.tests`) is not a floor
measurement: it skips the floor check, and the `floors.json` it leaves may be stale.

## Consequences

### Positive

- A fall in a mutation score that takes a group below its floor turns the nightly red, names the module and group,
  and raises one issue. A smaller fall, inside the group's margin, leaves the nightly green.
- The logic in `packs` is mutated, with floors where its scores are.
- The floors ratchet up by ordinary pull requests and only move down by a recorded decision.

### Negative

- The nightly takes about 51 minutes of mutation testing on four cores, 48 of them `:packs:pitest`, plus the
  set-up steps.
- Mutation coverage of a change is seen a day late, never in the pull request that caused it.
- The missions floor of 70 sits well below the packs `domain+application` and `infra.storage` floors until its
  score clears it by 5 points.
- A floor does not catch a single weakened test: the margins of packs and kernel absorb tens of mutants. Missions and
  profiles, with margins of 7 and 4 mutants, are close to the trigger instead.

### Risks

- A red nightly can be ignored. The single open issue keeps it visible until a maintainer closes it.
- `packs` growth can push the run toward its 90-minute timeout. A timeout raises the issue like a failed floor.
- The score counts uncovered mutants, so moving code between modules can move a score without any test changing.

## Alternatives considered

### Option A: A survivor delta on changed classes

The QA-strategy lens of [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) preferred it, because it
never punishes retiring a tautological test. Not chosen: it needs a stored baseline and new tooling, and a fixed floor
is simpler to read and enforce.

### Option B: Gating test strength

Not chosen. Test strength ignores uncovered mutants, so it can stay high while coverage falls; it is reported instead.

### Option C: Running mutation testing per pull request

Not chosen. A full run takes about 51 minutes and would add that to every pull request, and
`DM-01M57RMMBAS7275PXGNSYJZBQY` keeps mutation testing out of the merge gate.
[ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/) and
[ADR-0058](/adr/0058-sonarcloud-nightly-reported-and-checkstyle-complexity-per-pr/) keep slow or noisy signals off pull
requests for the same reason.

### Option D: PIT's own `mutationThreshold`, or floors in `gradle.properties`

The first design of [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) set `mutationThreshold` per
module, and asked whether the thresholds should live in `gradle.properties`. Not chosen:

- PIT's threshold is one rounded integer per module, so it cannot hold the packs groups to their own floors;
- it writes no machine-readable summary for the nightly's job summary and issue;
- a floor belongs in the build file of the module it holds, beside that module's PIT scope, not in a shared
  properties file.

## References

- [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239) — the design and the operator's decisions
- [#384](https://github.com/spec-kitty/spec-kitty-charter/pull/384) — kernel and profiles test gaps; it has merged, and the
  kernel and profiles floors are raised to 75 and 90 (amendment below)
- [#386](https://github.com/spec-kitty/spec-kitty-charter/issues/386) — a pre-existing `Pep440Range.overlaps` defect
  found during the kernel triage
- [The service build guide](/guides/service-build/#mutation-testing) — how to run PIT and read the floors
- `build-logic/src/main/kotlin/charter.mutation-conventions.gradle.kts` and `MutationFloors.kt`
- `.github/workflows/mutation-nightly.yml` and `scripts/ci/mutation_summary.py`

## Amendment, 2026-11-06: kernel and profiles floors raised

After #384 added kernel and profiles tests, a full `pitest --continue` (on #394's rebased head `684a8070`) scored
kernel 82.3 % (312 of 379 detected) and profiles 96.9 % (64 of 66). Both clear their floors by far more than the 5
points the raise rule asks for, so the operator approved raising them on 2026-11-06, by the same `floor5(score − 3)`
rule the packs floors use:

| Module | Floor | Raised to | Margin at the raise |
|---|--:|--:|--:|
| kernel | 65 | 75 | 27 |
| profiles | 50 | 90 | 4 |

The other floors are unchanged. Profiles is now close to its trigger, with a margin of 4 mutants.

## Amendment, 2026-11-06 (activations): the activations module joins the nightly

The operator ruled on 2026-11-06 that `service/activations` applies `charter.mutation-conventions`, with one
whole-module group, `activations`, at a floor of 85. This follows the rule of decision 3 (`floor5(score − 3)`): a trial
run scored 91.4 %, and `:activations:pitest` on this pull request's head scored 91.3 % (531 of 581 mutants detected,
28 survived, 22 uncovered, test strength 94.9 %), a margin of 37 mutants over the 85 % floor (494 of 581 needed).
`scripts/ci/mutation_summary.py` lists `activations` after `packs`, and the job summary shows "no report" for it when
no `floors.json` is written. The nightly's `./gradlew pitest` already reaches every module that applies the plugin, so
the workflow needs no change. The other floors are unchanged.

## Amendment, 2026-11-10 (profiles-edge): the profiles-edge module joins the nightly

`service/profiles-edge` applies `charter.mutation-conventions` directly, with one whole-module group, `profiles-edge`,
at a floor of 85. Its work package measured a mutation score of 100 % (121 of 121 mutants detected). The floor follows
the precedent of `:activations`, not `floor5(score − 3)`, which would give 95 on a module of this size: a first floor
of 85 leaves room for the module to grow before it is raised by pull request once the nightly score clears it by 5. The margin is 18 mutants
(103 of 121 needed). The group mutates `ai.speckitty.charter.profiles.application` and `ai.speckitty.charter.profiles.infra`,
without the four Spring wiring packages (`application.config`, `infra.api.config`, `infra.packs.config`,
`infra.activations.config`). These packages live in a different module from the `profiles` group's
`ai.speckitty.charter.profiles`, so the two scopes do not overlap. The nightly's `./gradlew pitest` already reaches the
module, and `scripts/ci/mutation_summary.py` lists it. The other floors are unchanged.

## Amendment, 2026-11-10 (missions-edge): the missions-edge module joins the nightly

Status: Accepted, as an amendment of this accepted ADR.

`service/missions-edge` applies `charter.mutation-conventions` directly, with one whole-module group, `missions-edge`,
at a floor of 85. The floor follows the precedent of `:activations` and `:profiles-edge`: a first floor of 85 until the
first nightly score is known, raised by pull request once the score clears it by 5. No mutation score has been
measured for this module yet, so the table states no margin. The group mutates
`ai.speckitty.charter.missions.application` and `ai.speckitty.charter.missions.infra`, without the four Spring wiring
packages (`application.config`, `infra.api.config`, `infra.packs.config`, `infra.activations.config`). These packages
live in a different module from the `missions` group's `ai.speckitty.charter.missions`, so the two scopes do not
overlap. The nightly's `./gradlew pitest` already reaches the module. The other floors are unchanged.

## Amendment, 2026-11-11 (#387, #388, #389): the nightly guards its own scope and shows its margins

No floor and no decision changes. The three follow-ups the operator ruled from the first squad are delivered as one
change:

- **Mutation testing stays out of the required check (#387).** The root build checks the task graph of every invocation:
  when `verifyAll` or any `check` depends on `pitest` or `checkMutationFloors`, however indirectly, the invocation fails
  before it runs (`MutationIsolation` in build-logic). Build-logic's `MutationWorkflowGuardTest` pins that
  `mutation-nightly.yml` has only the schedule and `workflow_dispatch` triggers, that no other workflow calls it or
  waits for it, and that no other workflow runs PIT or the floor check.
- **The module list comes from the build (#388).** `./gradlew mutationModules` writes
  `build/reports/mutation-floors/modules.txt`, one directory per module that applies `charter.mutation-conventions`.
  The nightly runs it beside `pitest`, and `scripts/ci/mutation_summary.py --modules` reads it, so a module whose PIT
  run writes no `floors.json` still gets its "no report" row. The hand-kept list is gone. It had already missed
  `:missions-edge`: the 2026-11-11 nightly showed no row for it. Without the list, the summary says so and is never
  green.
- **Margins and timed-out mutants are shown (#389).** `floors.json` gains `timedOut`, `marginMutants` (the detected
  mutants a group can lose and still pass, negative when below its floor) and `marginPoints` (the printed score minus
  the floor). The summary table shows them as the "Timed out" and "Margin" columns, so a slide shows before a floor
  trips.
- **A red night names its failing test (#461).** The mutation step's output is kept as
  `build/reports/mutation-floors/pitest-<shard>.log` in the `mutation-report-<shard>` artifacts (14 days), and the summary, which the
  alert issue quotes, lists PIT's lines about tests that fail with no mutation applied. Such a test leaves its module
  without a report. On 2026-11-11 the job log that named one returned errors and then expired before anyone read it.
