---
okf_version: "0.2"
title: Mutation testing runs weekly, in three shards
description: Mutation testing moves to weekly, splits :packs across two runners by floor group, gives each shard 120 minutes, and lists each module's groups so a missing shard shows.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for when the mutation run now happens, how it is split, and the new module list and floors file names.
---

# 2026-11-12 — Mutation testing runs weekly, in three shards

The nightly ran out of its 80-minute step two nights running, with `:packs` still mutating
([#461](https://github.com/spec-kitty/spec-kitty-charter/issues/461)). The operator's rulings are in the
[ADR-0062 amendment of 2026-11-12](/adr/0062-nightly-mutation-floor/).

- **Weekly, not nightly.** Before: every night at 02:43 UTC. After: Mondays at 02:43 UTC, and on `workflow_dispatch`.
  The workflow file and the `mutation-nightly` label keep their names.
- **Three shards.** Before: one runner for every module. After: `:packs`'s floor groups in two halves, and every other
  module, each on its own runner. `-PmutationGroups=<groups>` runs a module's PIT on those groups only and writes
  `build/reports/pitest-<shard>` and `floors-<shard>.json`; each group's score is the one a whole-module run gives.
- **120 minutes a shard.** Before: 90, 80 of them for PIT. After: 120, 110 of them for PIT.
- **The module list names each module's groups.** Before: `modules.txt` held one directory per line. After: the
  directory, then its floor group names, separated by tabs; the summary shows "no report" for a group no shard
  reported.
- **A red summary raises the alert.** Before: only a red PIT job did. After: a red summary job does too.
- **Faster `:packs` tests under PIT, same assertions.** `RegistrationJobsTest` releases every held worker before it
  closes the jobs and polls every 100 µs; `PackFileSetExactnessTest` writes its trees on first use.
