---
okf_version: "0.2"
title: Main lands through a merge queue, and a maintainer can pause a parked pull request's CI
description: The CI gate runs on merge queue entries too, a pr:pause-ci label stops CI on a parked pull request, and the docsite no longer builds the service twice per pull request.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0047-generated-indexes-and-merge-queue
  - adr/0023-ci-gate-required-check
  - guides/contributing
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for how a pull request lands now, and how to pause CI on one.
---

# 2026-11-01 — Main lands through a merge queue, and a maintainer can pause a parked pull request's CI

Under [ADR-0047](/adr/0047-generated-indexes-and-merge-queue/), amending
[ADR-0023](/adr/0023-ci-gate-required-check/).

**Before:**

- Pull requests merged on green with `gh pr merge --rebase --auto`, each tested against the `main` it
  branched from.
- Nothing could stop CI on a parked pull request. On 2026-10-31 Actions stalled for an hour with 18
  runs queued.
- Every pull request that touched the service built it up to three times.

**Now:**

- **The merge queue.** `CI gate / gate`, still the one required check, also runs on `merge_group`. The
  queue tests each pull request against the `main` it will land on. On a queue entry, change detection
  diffs the queue's base against its head.
- **Pausing a pull request.** Label it `pr:pause-ci` to stop its CI.
  - Adding the label cancels the running check.
  - While the label is on, the gate fails, so a paused pull request never reads as green.
  - Removing the label runs CI again.
  - Any other label neither runs the build nor cancels it.
- **One service build per pull request.** On a pull request, `docs-site.yml` builds the Javadoc alone
  and cancels its own stale runs. A Pages publish is never cancelled.

**What to do:** land with "Merge when ready" once the operator turns the queue on. To pause CI on a
pull request that is already queued, take it out of the queue first.
