---
okf_version: "0.2"
title: Charter's nightly open-packs drift watch is retired; open-packs tracks charter itself
description: open-packs-drift.yml is removed. open-packs' charter-track.yml checks each new charter release daily and charter main on demand; a nightly main run follows in open-packs#311.
doc_status: active
updated: 2026-11-19T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you looked for the open-packs-drift workflow or its issue label.
---

# 2026-11-19 — Charter's nightly open-packs drift watch is retired

**`.github/workflows/open-packs-drift.yml` is removed**
([#604](https://github.com/spec-kitty/spec-kitty-charter/issues/604)), as the ADR-0070 amendment of 2026-11-13 planned.
spec-kitty-open-packs now runs charter's round trip over its own content
([spec-kitty-open-packs#308](https://github.com/spec-kitty/spec-kitty-open-packs/pull/308)).

- **At its pin, on every pull request.** The required gate runs `:pack-validation:driftTest` from the pinned charter.
- **Daily, against each new charter release.** `charter-track.yml` checks the newest `service-v*` release when it is
  newer than the pin: green opens a pull request that bumps the pin, red opens a `charter:release-red` issue in
  open-packs.
- **On demand, against charter `main`.** A dispatch with `charter_ref`, or the `charter:head` label on an open-packs pull
  request, builds charter from source and runs the same checks.
- **Not yet: nightly against charter `main`.** Until
  [spec-kitty-open-packs#311](https://github.com/spec-kitty/spec-kitty-open-packs/issues/311) adds that run, an
  unreleased charter change that breaks open-packs content shows at the next release or on demand.

Charter keeps the test code (`OpenPacksDriftTest`, the `driftTest` source set and `-PopenPacksDir`); no charter workflow
runs it. The `open-packs-drift` label has no open issue.
