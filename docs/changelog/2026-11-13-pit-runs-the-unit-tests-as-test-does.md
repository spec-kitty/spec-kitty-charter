---
okf_version: "0.2"
title: PIT runs a module's unit tests as test does
description: PIT's minion now gets the test task's system properties and excluded JUnit tags, so a module cannot lose its mutation report to a property it forgot.
doc_status: active
updated: 2026-11-13T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - plans/engineering-notes/mutation-testing-no-report-research-2026-11-13
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a system property or an excluded tag to a mutated module's test task.
---

# 2026-11-13 — PIT runs a module's unit tests as `test` does

On 2026-11-10 the mutation run left `:activations` with no report
([#461](https://github.com/spec-kitty/spec-kitty-charter/issues/461)). A test read a system property that only the
`test` task set, so the test failed in PIT's minion with no mutation applied. Each module copied such properties into
`pitest { jvmArgs }` by hand. The
[research note](/plans/engineering-notes/mutation-testing-no-report-research-2026-11-13/) has every red run and its cause.

- **Before:** `:activations` and `:packs` copied their `test` system properties and excluded tags into `pitest {}`. The
  other modules had nothing to copy, until someone added a property and forgot.
- **After:** `charter.mutation-conventions` passes the `test` task's system properties and the JUnit tags it leaves out
  to PIT's minion, for every mutated module. `:activations` and `:packs` drop their copies. `:packs` still leaves
  `stress` out of PIT alone.
- No floor, no mutated class, no shard and no budget changes.
