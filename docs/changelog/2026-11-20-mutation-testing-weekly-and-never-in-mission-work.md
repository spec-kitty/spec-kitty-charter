---
okf_version: "0.2"
title: Mutation testing runs weekly, and never in mission work
description: The mutation run stays weekly (or a manual dispatch), and implement, review, squad and closeout steps no longer run PIT or ask for mutation numbers.
doc_status: active
updated: 2026-11-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you run PIT, or write a brief or acceptance criterion that mentions a mutation score.
---

# 2026-11-20 — Mutation testing runs weekly, and never in mission work

**The mutation run stays weekly** (operator ruling, [ADR-0062](/adr/0062-nightly-mutation-floor/) amendment of
2026-11-20). The cron is unchanged (`43 2 * * 1`), and `workflow_dispatch` starts a manual run. Documents that said
"nightly" now say "weekly scheduled run (or a manual dispatch)".

**No PIT in mission work.** Agent sessions had been running PIT inside implement and review steps, for 5 minutes to
about 85 minutes a run, because briefs, acceptance criteria and the open-packs testing doctrine asked for it.

- The charter's "How work lands" now says so, and the bootstrap render of every action's charter context (its first
  load) shows it.
- No step runs `pitest` or `checkMutationFloors`, a narrowed run or a shard included.
- No brief, acceptance criterion, NFR or Definition of Done asks for a mutation score, margin or floor, except a new
  module's first floor.
- Apart from a new module's first floor, floors and margins move only from the weekly or dispatched run's report, in
  a follow-up pull request.
- Two exceptions, and no other (operator, 2026-11-20): a new module may take one in-mission PIT run, scoped to that
  module, solely to set its first floor; and PIT tuning work (#530, #531) may run narrowed PIT locally, confirmed
  through `workflow_dispatch`.
- The charter's tactic for directive 030 was invalid YAML and was skipped by the CLI; it loads again.
