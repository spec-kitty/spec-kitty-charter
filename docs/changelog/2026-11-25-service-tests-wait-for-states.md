---
okf_version: "0.2"
title: "Service tests wait for states, and main code holds no test hooks"
description: "The service tests wait for the state they check instead of sleeping, and the static read counters, the registration job gate and the test-only constructors left main code."
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/codebase-health-2026-11-25
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you write a test that waits, counts a read's work or holds a registration job.
---

# 2026-11-25 — Service tests wait for states, and main code holds no test hooks

Actions 8 and 9 of the [codebase health summary of 2026-11-25](/plans/codebase-health-2026-11-25/)
(ADR-0075 AA5, #659):

- **One wait.** `Await.until` (packs test fixtures) waits for a state, never for a span of time. The
  sleeps that waited for a state, or that watched for something not to happen, now wait for a state
  that settles the question. The sleeps that remain are the behaviour under test: a server that sends
  a byte at a time, and the staggering of the concurrency stress tests.
- **No global read counters.** A `WorkMeter` bound around one call counts that call's walks, store
  runs, whole reads, lookups and in-force resolutions. The six JVM-wide static counters are gone, so
  two tests that count at once do not see each other's work.
- **No job gate.** `RegistrationJobs.JobGate` and its production bean are gone. A test holds a job at
  its storage-room check (`HeldRoom`), and keeps a job pending by keeping both workers busy, as the
  queue does in production.
- **No test-only constructors.** The overloads only tests called moved to test fixtures:
  `CompositionFacts.of`, `ClosureViews.of`, `CommitStores` and `StoreSeams`.

Production behaviour does not change.
