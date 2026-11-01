---
okf_version: "0.2"
title: The CPU-time budgets run alone, out of the parallel test run
description: The 10 s CPU-time budget moved to a timing-tagged test that :packs:timingBudgets runs alone after verifyAll; the correctness checks stay in :packs:test.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you add a test that asserts how long something takes, or a CI run failed on a CPU-time budget.
---

# 2026-11-01 — The CPU-time budgets run alone, out of the parallel test run

**A CPU-time budget no longer fails on load**
([#316](https://github.com/spec-kitty/spec-kitty-charter/issues/316)). `PackFileSetExactnessTest` held its 200
generated pack trees to a 10 s CPU-time budget (NFR-001) inside `:packs:test`, which `verifyAll` runs beside every
other module's tests. Under that load the same work took 13.1 s of CPU time on a merge-queue runner. It failed a
change that did not touch the packs code, and the queue dropped it.

The budget moved to `PackFileSetBudgetTest`, tagged `timing`. `:packs:test` excludes the tag, and the new
`:packs:timingBudgets` task runs it in one test JVM, outside `check`. CI runs that task as a step of its own after
`verifyAll`, in the same job, so a pull request and the merge queue still check the budget, alone on the runner. The
budget is unchanged at 10 s. The task never reuses a cached result, because a time measured on another run says
nothing about this one. Each budget test prints the CPU time it measured, so the margin shows in every run, and the
task fails rather than skips where symbolic links cannot be made.

The checks themselves stay in `:packs:test`: the fingerprint changes if and only if a file is `pack.yaml`, a loaded
file, a payload or a graph input, and the store loads exactly the planted files of every kind. `PackFileSetExactnessTest`
now asserts no time. Run the budgets with `./gradlew :packs:timingBudgets`, as the only task named; the
[service build guide](/guides/service-build/) says when a test gets the `timing` tag.
