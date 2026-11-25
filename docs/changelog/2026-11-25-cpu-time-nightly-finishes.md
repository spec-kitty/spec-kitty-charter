---
okf_version: "0.2"
title: The CPU-time nightly finishes and reports again
description: The timing tasks fork a JVM only for the budget classes, and the two budget fixtures the domain rules had outgrown use the canonical shapes.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0057-performance-budgets-never-block-a-pull-request
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if the CPU-time nightly was cancelled, or if you add a budget test.
---

# 2026-11-25 — The CPU-time nightly finishes and reports again

**The `CPU-time budgets (nightly)` workflow was cancelled in five of its six runs from 2026-11-20 to 2026-11-25**, at
the job's 20-minute limit. The run that finished hid two failing budget tests.

- **One JVM per budget class, not per test class.** `timingBudgets` forks a JVM per test class (`forkEvery = 1`), and
  the JUnit tag filter applies only inside the fork, so `:packs:timingBudgets` started about 1,400 JVMs to run six
  tests. Both timing tasks now fork only the classes whose source carries `@Tag("timing")` (`TimingBudgets.kt`); a new
  budget class is picked up from its tag. The check step went from 17 min 51 s to 5 min 8 s locally, and from about 15
  min to 56 s on a runner (45614144471).
- **The contract is validated once a run.** The check step skips `:charter-api:validateContract`, which the compile
  step already ran (about 2 minutes on a runner).
- **Two fixtures use the canonical shapes.** `ReachabilityBudgetTest` built tactic-to-tactic `scope` and
  `instantiates` edges, which the endpoint table refuses (ADR-0064 decision 13). Its 500 artifacts are now tactics,
  agent profiles, mission types, actions and templates, with every edge its relation allows. The graph has 43 % fewer
  edges (about 1,460 against 2,570), so its p95 is not comparable with earlier nights; the test is report-only.
  `ResolverCostBoundTest` configured each pack under a 46-character text that is no pack id; it now uses a ULID and
  the registered id, as the resolver tests do. Both failed before measuring anything; `test` never runs them.
- **The two timing tasks run in separate Gradle calls.** With the configuration cache, `--no-parallel` did not keep
  them apart: run 45614144471 measured `:activations`' budgets while `:packs`' wall-time budget ran.

**Not changed: the digest-check budget.** `PackFileSetBudgetTest.theDigestChecksAreWithinBudget` measured 6.31 to
11.40 s against its 10 s budget on the 16 nights that reached it (2026-11-07 to 11-25), and went over on four: 11-12,
11-18, 11-20 and this change's dispatch (10.17 s). Its untouched tree generation moves with it (0.3 s on fast nights,
0.5 to 0.8 s on slow ones), so the spread is the runner. The owner of NFR-001 rules on it.

The budgets stay report-only ([ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/)).
