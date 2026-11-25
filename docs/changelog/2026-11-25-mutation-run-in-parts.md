---
okf_version: "0.2"
title: The mutation run splits :packs into five shards
description: domain+application runs in three merged parts, the summary fails on a merged floor, and the CPU-time nightly fails when a budget class did not run.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a weekly mutation shard timed out, or you read the mutation summary.
---

# 2026-11-25 — The mutation run splits :packs into five shards

**The weekly mutation run of 2026-11-23 failed** (#644): the packs-core shard ran out of its 110 minutes and wrote no
report. `:packs` now runs in five shards ([ADR-0062](/adr/0062-nightly-mutation-floor/), amendment of 2026-11-25).

- **Five shards.** `domain+application` runs in three parts (`domain`, `application`, `rules`), with `infra.storage`
  and `infra.fetch` with `infra.archive` beside it. Every shard of the last dispatch (45652654509) finished within its
  110 minutes. `infra.storage` is the one at risk, at 77 minutes; [#657](https://github.com/spec-kitty/spec-kitty-charter/issues/657) tracks its split.
- **One score per group.** The parts of a group hold disjoint mutants. The summary merges them and scores the group
  once, with the same exact comparison and margins as a whole run. A part's shard never fails on the floor; the
  summary fails when the merged group is below it, so the run is red and the alert fires. A missing part, parts that
  disagree, or a group reported whole and in parts leave the group without a verdict.
- **`:routing` keeps its floor of 90.** Two existing tests gained assertions, and the dispatch measured 74 of 79
  mutants (93.6 %). The five survivors are equivalent
  ([triage](/plans/engineering-notes/routing-survivor-triage-2026-11-25/)).
- **The CPU-time nightly gates on a budget that did not run.** After the report-only step, `verifyTimingBudgetsRan`
  fails the job when a class carrying `@Tag("timing")` left no test result. It checks that the run happened, not how
  long it took. The scan also refuses a tag it would never run: on a nested or abstract class, in a source without a
  package, or in a Kotlin source.

Floors, the PIT scope and the schedule are unchanged.
