---
title: The architecture gate fails when a concern slice is not registered
description: A new SLICE_COVERAGE ArchUnit rule fails the build when a slice is absent from SLICES, and a contributor orientation page explains the service architecture and its gotchas.
doc_status: active
updated: 2026-10-22T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0006-onion-vertical-slices-archunit
  - development/service-architecture-orientation
  - architecture/architectural-patterns-applied
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new build gate you must satisfy when you add a concern slice.
  - profile: core-maintainer
    guidance_text: Read this for how the slice list is now self-checking.
---

# 2026-10-22 — The architecture gate fails when a concern slice is not registered

The hand-kept list of concern slices in `HexagonalArchitectureTest` is now self-checking, so a
new slice can no longer ship ungated on a green build.

## What changed

- **New rule `SLICE_COVERAGE` (`EVERY_SLICE_IS_REGISTERED`).** It discovers every concern slice
  present in the source — any top-level `ai.speckitty.charter.<owner>` that owns a `domain` ring,
  excluding the kernel, the generated contract and the composition root — and fails the build when
  a discovered slice is absent from the `SLICES` list. Its known-bad control proves it bites.
- **New orientation page.** [Service architecture
  orientation](/development/service-architecture-orientation/) is a one-sitting read for
  contributors: the three patterns, the module map, the inward-only rule, how ArchUnit gates it,
  and the gotchas that bite.

## Who is affected

Contributors adding a second concern slice: register its name in `SLICES` or the build fails,
naming the slice. Parameterizing the `packs`-pinned `APPLICATION_IS_EDGE` and
`PACKS_DOMAIN_IS_PURE` over `SLICES` remains a follow-up, so registration is necessary but not yet
sufficient to fully gate a new slice.
