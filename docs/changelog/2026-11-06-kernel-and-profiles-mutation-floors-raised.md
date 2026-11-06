---
okf_version: "0.2"
title: The kernel and profiles mutation floors are raised to 75 and 90
description: The nightly now turns red when kernel falls below 75 % or profiles below 90 %, up from 65 and 50, after #384's tests lifted both scores.
doc_status: active
updated: 2026-11-06T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0062-nightly-mutation-floor
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if the mutation nightly is red on kernel or profiles.
---

# The kernel and profiles mutation floors are raised to 75 and 90

The mutation nightly now fails `:kernel:pitest` below **75 %** (was 65) and `:profiles:pitest` below **90 %** (was 50)
([ADR-0062](/adr/0062-nightly-mutation-floor/), [#239](https://github.com/spec-kitty/spec-kitty-charter/issues/239)).

PR #384's module-local tests lifted kernel to 82.3 % (312 of 379 mutants detected) and profiles to 96.9 % (64 of 66).
Both cleared their floors by more than the 5 points the raise rule asks for, so they were raised by the same
`floor5(score − 3)` rule the packs floors use. Profiles now has a margin of 4 mutants: losing a few kills there turns
the nightly red. The missions and packs floors are unchanged.
