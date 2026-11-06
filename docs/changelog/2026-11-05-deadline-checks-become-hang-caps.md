---
okf_version: "0.2"
title: "Deadline checks in the packs tests become hang caps"
description: "Eight wall-clock checks that a loaded CI runner could trip now allow 30 seconds, stay in the gating test run, and still fail when the guard they prove breaks."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - plans/engineering-notes/test-suite-assessment-2026-11-03
  - adr/0057-performance-budgets-never-block-a-pull-request
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a packs test names a 30 s HANG_CAP and you wonder why it is not tagged timing.
---

# 2026-11-05 — Deadline checks in the packs tests become hang caps

**A loaded CI runner can no longer fail the packs deadline checks**
([#320](https://github.com/spec-kitty/spec-kitty-charter/issues/320)), and each still fails when the guard it proves
breaks.

Eight checks in the `packs` tests bounded the time of work that proves a guard: the YAML alias budget, the
linear-time pattern engine, the commit lock, and the guide fetcher's deadline on the lookup and the body. Their
bounds of 2 to 10 seconds could be missed by a loaded CI runner, the way the CPU-time budget of
[#316](https://github.com/spec-kitty/spec-kitty-charter/issues/316) was.

- **Each bound is now a 30 s hang cap**, far above the time of the work it bounds: a few milliseconds, or for
  the fetcher, its own deadline of 0.2 s or 1.5 s. Two more 2 s bounds in the fetcher's deadline tests, and the
  10 s waits in the commit-lock test's helpers, now wait up to the same cap, so none fires before it.
- **They still gate.** They stay in `:packs:test` and are not tagged `timing`, which
  [ADR-0057](/adr/0057-performance-budgets-never-block-a-pull-request/) reserves for report-only budgets. Each
  test still asserts the cause it proves: the refusal, the finding, or the matched problem.
- **A broken guard still fails its test.** The alias-bomb tests now check the node budget where the YAML is
  parsed, so a broken budget fails on that cause in about 3 seconds; under the wider cap alone, the bomb
  would run the test JVM out of memory first. The
  fetcher's slow lookup and dripping body now last 60 seconds, and the fetch is cut off at the cap, so a
  broken deadline fails at 30 s; the deadline tests with no cap of their own stop at a whole-test cap of 90 s.
  The commit-lock test now checks that a listing holding one pack's lock never waits on another pack's, so a
  listing that waits fails on that cause; under the wider cap alone, its wait of 10 s would pass. Every other
  listed bound fails on its cause, or at the 30 s cap at the latest. A green run takes as long as before.
