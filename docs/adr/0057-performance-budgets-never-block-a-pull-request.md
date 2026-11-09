---
title: "ADR-0057: Performance budgets never block a pull request"
description: "Operator ruling of 2026-11-04: CI runs the CPU-time budgets report-only, so a budget over its limit warns but never fails a job, a pull request or main."
doc_status: active
updated: 2026-11-07T00:00:00Z
decision_status: Accepted
decision_date: '2026-11-04'
type: ADR
kind: ADR
related:
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a test that holds work to a time or CPU-time budget, or when a CI run warns about one.
---

# ADR-0057: Performance budgets never block a pull request

## Status

**Accepted**, 2026-11-04. Decider: Stijn Dejongh (operator). The operator ruled that performance tests never block a
pull request, because they are notoriously flaky, while landing pull request
[#365](https://github.com/spec-kitty/spec-kitty-charter/pull/365).

The decision changes how CI treats the tests tagged `timing`
([#316](https://github.com/spec-kitty/spec-kitty-charter/issues/316)). It does not change the budgets or the tests.

## Context

The CPU-time budgets (`./gradlew :packs:timingBudgets`) already run alone, after `verifyAll`, because under parallel
load they failed on load rather than on code (#316). That was not enough. The runners of the `service` workflow fall
into CPU speed bands about 2x apart:

- **Identical code, double the time.** The same tree measured the digest checks of `PackFileSetBudgetTest` at 4.80 s
  and at 9.56 s of CPU time, five minutes apart.
- **The slow band crosses the budget.** Runs in the slow band measure 9.4 to 11.4 s against a 10 s budget, whatever
  the change under test. PR #365, which does not touch the digest code, failed at 11.41 s.

A budget that a slow runner fails says nothing about the change. Scaling the budget against a calibration
workload was considered, but it still leaves the result at the mercy of the runner.

## Decision

1. **CI runs the CPU-time budgets report-only.** The `Check the CPU-time budgets` step of `service.yml` (since the
   2026-11-07 amendment, of `timing-nightly.yml`) has `continue-on-error: true`. A budget over its limit never fails
   the job, so it never fails the required gate of a pull request and never turns `main` red.
2. **A miss stays visible.** When the step fails, the next step raises a workflow warning and a job-summary line. The
   measured times stay in the step log, printed by each budget test.
3. **Any future performance test follows the same rule.** A test that holds work to a time or CPU-time budget is
   tagged `timing`, stays out of `verifyAll` and runs report-only in CI.

Correctness is unaffected: the checks the budgets time (`PackFileSetExactnessTest`) stay in `:packs:test`, which
gates.

## Consequences

### Positive

- A slow runner can no longer block a pull request or turn `main` red.
- The budgets still run on every pull request and every push to `main`, so a real slowdown shows as a warning with
  numbers beside it. (Superseded by the 2026-11-07 amendment: they run nightly.)

### Negative

- A real performance regression no longer stops a merge. Someone must notice the warning and compare it with other
  runs.

### Risks

- Warnings can be ignored. A slowdown that persists across runs (every run over the budget, not one slow runner) is
  the signal to file an issue.

## Amendment, 2026-11-07: the budgets run nightly

Operator decision, 2026-11-07 (Stijn Dejongh). The CPU-time budgets no longer run on each pull request or push to
`main`: the `service` workflow drops the step, and `timing-nightly.yml` runs
`./gradlew :packs:timingBudgets :activations:timingBudgets` nightly and on `workflow_dispatch`. A per-pull-request
run cost runner time for a result that, being report-only, gated nothing. The run stays report-only: a budget over its
limit raises a warning and a job-summary line and never fails the job. Overruns are pull-only: nobody is notified, and a slowdown that persists across nights is read
from the Actions page. The test classes compile in a gating step first, so real breakage fails the run. The decision above stands; only where the
budgets run changes, so the consequence "the budgets still run on every pull request" no longer holds.

## Alternatives considered

### Option A: Scale the budget against a calibration workload

Not chosen. It narrows the noise but still gates on a measurement of shared hardware, and it makes the budget harder
to read.

### Option B: Raise the budget

Not chosen. A budget loose enough for the slowest runner no longer catches anything on the fast ones.

## References

- [Service build guide](/guides/service-build/)
- [#316](https://github.com/spec-kitty/spec-kitty-charter/issues/316): the CPU-time budgets run alone
