---
okf_version: "0.2"
title: A registration job past its deadline is never started from the queue
description: A registration that waited in the queue past its deadline is now refused as timed out by the worker that takes it, instead of being started by a worker the watchdog had just freed.
doc_status: active
updated: 2026-11-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/check-packs
audience:
  - profile: pack-author
    guidance_text: Read this if a registration of yours waited a long time behind other registrations.
---

# 2026-11-27 — A registration job past its deadline is never started from the queue

**A registration that waited past its deadline could still be started and could write its pack.** Before, the
watchdog that ends overdue registrations went through them one at a time. Ending a running registration frees its
worker, and that worker could take a queued registration that was overdue too, before the watchdog reached it. The
registration then ran, and could write, although its deadline had passed.

## What changed

A worker that takes a registration from the queue now checks the deadline first. A registration past it ends
`refused` with `registration_timed_out`, is audited once and writes nothing, the same end the watchdog gives it. A
registration that began its commit before the deadline is still never ended by it.
