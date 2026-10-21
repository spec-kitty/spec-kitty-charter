---
title: Pack repositories, a validation kernel and the slice layout
description: The service gains a pure validation kernel library, per-type repository ports with an aggregating pack repository, and the kernel, packs and bootstrap layout.
doc_status: active
updated: 2026-10-21T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0006-onion-vertical-slices-archunit
  - architecture/architectural-patterns-applied
  - development/code-style-and-conventions
  - development/how-to-ddd
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the structural change to the service and where the new code lives.
  - profile: core-maintainer
    guidance_text: Read this for what moved and which names changed in the service modules.
---

# 2026-10-21 — Pack repositories, a validation kernel and the slice layout

The pack repositories and validation mission reshaped the service and set the house style that
later slices follow. [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) is now Accepted.

## What changed

- **A new `kernel.validation` library.** A pure package (`java` and `jspecify` only, gated by
  ArchUnit) with `ModularRuleset`, `Constraint`, `Evaluation`, `Reason` and `Rationale`.
  `guard()` fails fast and `check()` collects every breach. Domain types enforce their
  invariants with it.
- **The repository reshape.** Per-type ports (`TacticRepository`, `PackDescriptorRepository`),
  an aggregating `PackRepository` and a `PackInspectionQuery` live in the domain. One
  `@Service` adapter implements them over a `SerializationStrategy`, and storage uses
  DSO records (`TacticDso`, `PackDescriptorDso`). `Pack` is an aggregate root and `Tactic` a
  child entity with a typed identifier; a round trip is asserted on the DSO's value equality.
- **The screaming-architecture slice layout.** The flat modules `charter-domain`,
  `charter-read-app`, `adapter-out-yaml`, `adapter-in-rest` and `charter-service` become `kernel`,
  `packs` (`domain`, `application`, `infra.storage`, `infra.api`) and `bootstrap`.
- **New documentation.** [Code style and conventions](/development/code-style-and-conventions/),
  [How to DDD here](/development/how-to-ddd/),
  [Architectural patterns applied](/architecture/architectural-patterns-applied/) with its C4
  and class diagrams, and a [DDD patterns](/glossary/ddd-patterns/) glossary page.

## Who is affected

Contributors who referred to the old module names: the docs now use the slice names.
