---
okf_version: "0.2"
title: "Service tests wait for states, and the named test hooks leave main code"
description: "The service tests wait for the state they check instead of sleeping; the static read counters, the registration job gate and the test-only constructors left main code."
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/codebase-health-2026-11-25
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you write a test that waits, counts a read's work or holds a registration job.
---

# 2026-11-25 — Service tests wait for states, and the named test hooks leave main code

Actions 8 and 9 of the [codebase health summary of 2026-11-25](/plans/codebase-health-2026-11-25/)
(ADR-0075 AA5, #659):

- **One wait.** `Await.until` (packs test fixtures) waits for a state, never for a span of time. A test that proves
  something does not happen first waits for the state that settles it. The sleeps that remain are the behavior under
  test: a server that sends a byte at a time, the staggering of the concurrency stress tests, and a swap whose slowness
  is what the test measures.
- **No static counters.** A `WorkMeter` (`kernel.metering`) bound around one call counts that call's walks, store runs,
  whole reads, lookups, cache loads and hits, and in-force resolutions. The six JVM-wide static counters and the
  cache's counting getters are gone, and an ArchUnit rule keeps static counters out of main code
  ([ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/), amendment of
  2026-11-26).
- **No job gate.** `RegistrationJobs.JobGate` and its production bean are gone. A test holds a job at its storage-room
  check (`HeldRoom`), and keeps a job pending by keeping both workers busy, as the queue does in production.
- **No test-only constructors.** The overloads only tests called moved to test fixtures (`CompositionFacts.of`,
  `ClosureViews.of`, `CommitStores`, `StoreSeams`), and `ClosureQueries.allConfigured()` left main.

Kept on purpose, each with its reason in its javadoc: the meter itself, `RegistrationJobs.retained()`,
`RegistryWriteLock.hasQueuedThread` and `PackCommitStore.SwapPhases`. Production behavior does not change.
