---
okf_version: "0.2"
title: The CPU-time budgets no longer block a pull request
description: CI runs the CPU-time budgets report-only; a budget over its limit raises a warning and a job-summary line but never fails the job or the PR gate.
doc_status: active
updated: 2026-11-04T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0057-performance-budgets-never-block-a-pull-request
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a CI run warns that a CPU-time budget is over its limit.
---

# 2026-11-04 — The CPU-time budgets no longer block a pull request

**A slow CI runner can no longer fail a pull request on a CPU-time budget**
([ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/)). The `service` workflow's runners differ in CPU
speed by about 2x: the same code measured the digest checks at 4.80 s on one run and 9.56 s on another, and a slow
runner measured 11.41 s against the 10 s budget on a change that did not touch that code.

The `Check the CPU-time budgets` step is now report-only. A budget over its limit raises a workflow warning and a
job-summary line, and the job still passes. The budgets, the tests and `./gradlew :packs:timingBudgets` are unchanged,
and the correctness checks they time still gate in `:packs:test`.
