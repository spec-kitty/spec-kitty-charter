---
title: Versioning and compatibility
description: How the charter service contract is versioned and what a consumer can rely on across releases, and the rules core's in-process seam surface follows.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: API
kind: Reference
related:
  - api/python-package
  - api/service-contract
  - architecture/contract-and-dtos
audience:
  - profile: contract-consumer
    guidance_text: Read this to learn which changes are minor, which are major, and which release to pin.
---

# Versioning and compatibility

How the charter service contract is versioned and what a consumer can rely on across releases, and the rules core's in-process seam surface follows until the cutover retires it. The rules derive from the `charter.offering.api` docstring in core (`src/charter/offering/api.py`), core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (the staged Java service strangler) and [Contract and DTOs](/architecture/contract-and-dtos/). No Python package ships from this repository ([ADR-0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/), [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)).

> **Partly built.** The service contract exists at `0.1.0-SNAPSHOT`, and its build checks that response schemas are closed and runs the breaking-change check; no contract version is released yet. The Python surface below is core's, not a package of this repository. See the [API overview](/api/).

## Core's in-process seam surface

Core versions this surface, not this repository; it stays in core until the cutover.

- **The public surface is `__all__` of `charter.offering.api`.** A symbol outside it carries
  no compatibility promise.
- Adding a symbol is a **minor** release. Removing or renaming a symbol, changing a
  signature incompatibly, or changing an `ArtifactKind` fact is a **major** release.
- A consumer that imports only from `charter.offering.api` survives the cutover: the seam
  keeps its shape and answers from the service.
- Core enforces the surface's shape (importable, sorted, every symbol tagged public in its
  disposition census).

## Service contract

- The contract has its own `info.version` and `CHANGELOG.md`. An unreleased version carries a
  `-SNAPSHOT` suffix.
- Response schemas are **closed** (`additionalProperties: false`), so any response change is a
  new schema version and a new contract release.
- The contract is published as `0.y.z` while it iterates with core's generated client, and
  1.0.0 is minted once the operator judges it stable (ADR-0022 (proposed in [#123](https://github.com/spec-kitty/spec-kitty-charter/pull/123))).
- A breaking-change check compares the contract with its last released version of the same
  line: from 1.0.0 the same major version, and while the major is 0 the same `0.y`. A breaking
  change needs a new line and a core-side migration of its generated DTOs:
  - while the major is 0, a minor bump (`0.y` to `0.(y+1)`) may break, and a patch may not;
  - from 1.0.0, a major bump may break, and a minor or a patch may not.
- A bump that may break needs a `### Breaking` subsection in the contract `CHANGELOG.md` that
  lists each break. The build then shows what the new version breaks against the previous
  release, for information only. How the released version is found:
  [the released baseline](https://github.com/spec-kitty/spec-kitty-charter/blob/main/contracts/lint/README.md#the-released-baseline).
- The core pins one contract release. Raising the pin regenerates its DTOs in the same change,
  and the release's conformance corpus must pass against every implementation still
  answering.
- The URL prefix `/api/v1` stays through 0.x and at 1.0.0; it names the API generation that
  1.0.0 stabilizes, not the release number. After 1.0.0 it changes only with a major version.

## Between the two surfaces

The package and the service expose one [logical model](/architecture/logical-model/). While
both answer, a difference between them for the same inputs is a defect in one of them,
found by the shadow comparison or the conformance corpus.
