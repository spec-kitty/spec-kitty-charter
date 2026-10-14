---
title: Decision Records
description: The immutable log of decisions taken in the spec-kitty-charter repository, and the core Spec Kitty ADRs they build on.
doc_status: active
updated: 2026-10-13
type: ADR
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Find the decisions taken here and the core decisions they build on.
  - profile: core-maintainer
    guidance_text: See which core decisions this repository builds on and where it asks core to rule.
  - profile: evaluator
    guidance_text: Read this to see which decisions are taken, proposed or inherited from core.
---

# Decision Records

Decisions taken in this repository. Records are numbered `NNNN-<slug>.md` and copied from
[the template](/adr/template/), as in doc-kitty. A record is immutable once accepted; a later
decision supersedes it rather than editing it.

| ID | Title | Status | Date |
|----|-------|--------|------|
| [0001](/adr/0001-java-service-with-openapi-contract/) | ADR-0001: The charter is a Java service built against an OpenAPI contract | Accepted | 2026-10-13 |
| [0002](/adr/0002-contract-source-of-record/) | ADR-0002: This repository is the source of record for the charter contract | Proposed | 2026-10-13 |
| [0003](/adr/0003-docs-follow-doc-kitty/) | ADR-0003: Documentation follows the doc-kitty convention | Accepted | 2026-10-13 |
| [0004](/adr/0004-public-api-returns-generated-dtos/) | ADR-0004: The public API returns DTOs generated from the OpenAPI contract | Accepted | 2026-10-14 |

## Core decisions this repository builds on

These live in [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty) under
`docs/adr/`. They are not copied here.

| Core ADR | Title | Status in core | What it gives this repository |
|---|---|---|---|
| [`4.x/2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) | The charter becomes an external contract package in its own repository | Accepted | The reason this repository exists; the import-move precondition (formerly #645, now carried by epic #6099) is GA-gating |
| [`4.x/2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) | Replace charter reads and writes through a staged Java service strangler | **Proposed** | The stages, the hexagonal shape, the write gates, the service boundary |
| [`4.x/2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) | A mission status read API replaces the CLI-bundled dashboard | Accepted | The sibling Java service: stack, binding, contract conventions, release model |
| [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) | Charter offering, active charter and activation presets | Accepted | The domain vocabulary; presets as pack data; no aliases or shims |
| [`4.x/2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md) | A Charter Pack declares its level in `pack.yaml` | Accepted | `pack_level` and its refusal codes |
| [`3.x/2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) | Doctrine layer merge semantics | Accepted | How built-in, org and project layers merge |
| [`3.x/2026-08-02-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-02-1-charter-wheel-assessment.md) | Charter wheel assessment | Accepted | The earlier, deferred wheel split that 2026-10-12-2 extends |
| [`3.x/2026-04-25-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-04-25-1-shared-package-boundary.md) | Shared package boundary | Accepted | How external contract packages are consumed |
