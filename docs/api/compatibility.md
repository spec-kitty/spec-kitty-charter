---
title: Versioning and compatibility
description: How the designed charter Python package and service contract are versioned, and what a consumer can rely on across releases. Designed only.
doc_status: draft
updated: 2026-10-14
type: API
kind: Explanation
related:
  - api/python-package
  - api/service-contract
  - architecture/contract-and-dtos
audience:
  - profile: contract-consumer
    guidance_text: Read this to learn which changes are minor, which are major, and which release to pin.
---

# Versioning and compatibility

> **Designed API — not implemented here.** No package or contract has been released from
> this repository. These rules are derived from core's `charter.offering.api` docstring
> (`src/charter/offering/api.py`), core ADR `2026-10-12-2`, core ADR `2026-10-03-1` and
> [Contract and DTOs](/architecture/contract-and-dtos/).

## Python package

- **Semantic versioning** of the wheel, with `py.typed` shipped so type checkers see the
  surface.
- **The public surface is `__all__` of `charter.offering.api`.** A symbol outside it carries
  no compatibility promise.
- Adding a symbol is a **minor** release. Removing or renaming a symbol, changing a
  signature incompatibly, or changing an `ArtifactKind` fact is a **major** release.
- Consumers pin a version range, as they do for `spec-kitty-events` and
  `spec-kitty-tracker`. A consumer that imports only from `charter.offering.api` survives the
  move to this repository (core ADR `2026-10-12-2`).
- Core enforces the surface's shape today (importable, sorted, every symbol tagged public in
  its disposition census). This repository needs an equivalent gate before the first
  release.

## Service contract

- The contract has its own `info.version` and `CHANGELOG.md`. An unreleased version carries a
  `-SNAPSHOT` suffix.
- Response schemas are **closed** (`additionalProperties: false`), so any response change is a
  new schema version and a new contract release.
- A breaking-change check compares each release with the previous one. A breaking change
  needs a new major version and a core-side migration of its generated DTOs.
- The core pins one contract release. Raising the pin regenerates its DTOs in the same change,
  and the release's conformance corpus must pass against every implementation still
  answering.
- The URL prefix `/api/v1` changes only with a major version.

## Between the two surfaces

The package and the service expose one [logical model](/architecture/logical-model/). While
both answer, a difference between them for the same inputs is a defect in one of them,
found by the shadow comparison or the conformance corpus.
