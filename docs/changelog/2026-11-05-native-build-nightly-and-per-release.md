---
okf_version: "0.2"
title: "The native executables are built nightly and per release, not on each pull request"
description: "The four native executables leave CI gate / gate. They are built nightly on main, for each service release and on demand."
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0023-ci-gate-required-check
  - adr/0061-native-build-runs-nightly-and-per-release
audience:
  - profile: charter-service-contributor
    guidance_text: Read this to know that a pull request no longer builds the native executables, and how to build them on a branch.
---

# 2026-11-05 — The native executables are built nightly and per release, not on each pull request

[ADR-0061](/adr/0061-native-build-runs-nightly-and-per-release/) records the operator's ruling: the native build was too
slow for each pull request.

- **Pull requests:** `CI gate / gate` no longer builds the four native executables. The required check is unchanged.
- **Nightly:** `service-native.yml` builds and starts them on `main` at 03:11 UTC. A red night is a red run of the
  workflow.
- **Releases:** a service release still builds and starts all four before it attaches them.
- **On demand:** run `service-native.yml` on a branch with `workflow_dispatch` for a change that risks the native
  build.
- **What this costs:** a missing native reachability hint is found up to a day after the merge, not on the pull request.
