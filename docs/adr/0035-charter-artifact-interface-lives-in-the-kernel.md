---
title: "ADR-0035: The shared artifact interface lives in the kernel"
description: CharterArtifact and PackIdentifier move to kernel.domain so every slice can implement the interface; the schema version becomes optional.
doc_status: active
updated: 2026-10-27T00:00:00Z
decision_status: Accepted
decision_date: '2026-10-27'
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0024-doctrine-artifact-family-contract
  - context/conceptual-model
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding an artifact kind in any slice, including mission types and profiles.
  - profile: core-maintainer
    guidance_text: Read this for why the artifact interface moved to the kernel; nothing on the wire changes.
---

# ADR-0035: The shared artifact interface lives in the kernel

## Status

**Accepted** — 2026-10-27. Decider: Stijn Dejongh (operator), settling
[#171](https://github.com/spec-kitty/spec-kitty-charter/issues/171).

Supersedes in part [ADR-0024](/adr/0024-doctrine-artifact-family-contract/) decision 1: the
interface's home (`packs.domain`) and its mandatory schema version.

## Context

ADR-0024 placed `CharterArtifact` in `packs.domain` because it returns a `PackIdentifier`, which
lived there. ADR-0006 lets a slice depend on the kernel and its own domain only, so a type in
another slice could not implement an interface in `packs.domain`. The conceptual model makes
mission types and agent profiles their own concepts with their own domain models
([Conceptual model](/context/conceptual-model/)), so they will live in their own slices; and the
whole-pack round trip ([#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)) adds
kinds that have no schema version (assets) or make it optional (profiles).

## Decision

1. **`CharterArtifact` lives in `kernel.domain`**, the only home every slice can depend on.
2. **`PackIdentifier` moves to `kernel.domain` with it.** A pack's identity is referred to by
   every slice; it depends only on `kernel.validation`.
3. **The interface holds only what every kind has**: kind, pack, local id, label, an optional
   description, relationships (possibly none) and the derived identity. **The schema version is
   optional.** A kind whose schema requires a version still always returns one.
4. **Every artifact kind implements it**, mission types and agent profiles included, each from its
   own slice and domain model.

## Consequences

### Positive

- A new slice can implement the interface without crossing a slice; the architecture test shows it
  with a positive control in a second slice.
- No kind returns a placeholder schema version.

### Negative

- Callers of the five existing kinds unwrap the schema version, which their schema guarantees.
- The kernel grows by two types; `KERNEL_IS_PURE` still holds (they depend on the kernel and `java`
  only).

### Risks

- The interface could grow back into a fat base type. It holds only what every kind has; anything
  more stays on the kind (ADR-0024 decision 2).

## Alternatives considered

### Keep the interface in `packs.domain` and put every kind in the `packs` slice

Rejected: mission types and profiles are separate concepts with their own domain models and
endpoints, which is what slices are for (ADR-0006).

### Drop the schema version from the interface

Rejected for now: the kinds that have one are read and written through the interface's consumers;
an optional member keeps that without a placeholder.

## References

- `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/CharterArtifact.java`,
  `PackIdentifier.java`
- `service/deployable/src/test/java/ai/speckitty/charter/deployable/HexagonalArchitectureTest.java`
  (`aSecondSliceCanImplementTheSharedArtifactInterface`)
- [ADR-0006: Onion vertical slices with ArchUnit](/adr/0006-onion-vertical-slices-archunit/)
- [ADR-0024: Charter artifact family contract](/adr/0024-doctrine-artifact-family-contract/)
