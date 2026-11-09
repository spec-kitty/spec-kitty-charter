---
okf_version: "0.2"
title: The CPU-time budgets run nightly
description: CI no longer runs the CPU-time budgets on each pull request; a nightly workflow runs them, still report-only.
doc_status: active
updated: 2026-11-07T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0057-performance-budgets-never-block-a-pull-request
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you look for the CPU-time budget results of a pull request.
---

# 2026-11-07 — The CPU-time budgets run nightly

**The CPU-time budgets no longer run on each pull request or push to `main`**
([ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/), amended 2026-11-07). They were report-only, so
a per-pull-request run cost runner time and gated nothing.

The `service` workflow drops the `Check the CPU-time budgets` step. The new `timing-nightly.yml` workflow runs
`./gradlew :packs:timingBudgets :activations:timingBudgets --no-parallel` every night and on demand
(`workflow_dispatch`). A gating step compiles the tests first. Only the
following budget step is report-only: a failure there raises a workflow warning and a job-summary line, and the job
still passes. An overrun opens no issue and sends no notification; inspect the Actions page for a slowdown that persists
across nights. The budgets and the tests are unchanged, and the correctness checks they time still gate in `verifyAll`.
