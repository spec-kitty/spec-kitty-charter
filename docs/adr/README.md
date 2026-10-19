---
title: Decision Records
description: The immutable log of decisions taken in the spec-kitty-charter repository, and the core Spec Kitty ADRs they build on.
doc_status: active
updated: 2026-10-19
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
[the template](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/templates/adr.template), as in doc-kitty. The *decision* in an accepted record is immutable: a
later decision supersedes it rather than editing it. A record may still carry a dated,
decision-preserving **clarification** (an annotation that sharpens scope or wording without
changing what was decided); a clarification is labelled and dated, and anything that changes the
decision is a new record instead. The one exception, as in core's records, is a dated
**amendment** under the record's Status that cites the operator ruling changing part of the
decision: the original decision text stays, and each point it changes is annotated where it
stands.

| ID | Title | Status | Date |
|----|-------|--------|------|
| [0001](/adr/0001-java-service-with-openapi-contract/) | ADR-0001: The charter is a Java service built against an OpenAPI contract | Accepted | 2026-10-13 |
| [0002](/adr/0002-contract-source-of-record/) | ADR-0002: This repository is the source of record for the charter contract | Accepted, with Amendment 2026-10-18 | 2026-10-18 |
| [0003](/adr/0003-docs-follow-doc-kitty/) | ADR-0003: Documentation follows the doc-kitty convention | Accepted | 2026-10-13 |
| [0004](/adr/0004-public-api-returns-generated-dtos/) | ADR-0004: The public API returns DTOs generated from the OpenAPI contract | Accepted | 2026-10-14 |
| [0005](/adr/0005-service-registers-and-distributes-packs/) | ADR-0005: The charter offering service registers and distributes packs; built-in is its first catfooding consumer | Proposed | 2026-10-18 |
| [0006](/adr/0006-onion-vertical-slices-archunit/) | ADR-0006: Onion layering cut by vertical concern slices, enforced by ArchUnit | Proposed | 2026-10-18 |
| [0007](/adr/0007-rest-hateoas-contract-of-record/) | ADR-0007: REST-HATEOAS is the v1 contract of record; GraphQL is planned-later, MCP deferred | Proposed | 2026-10-18 |
| [0008](/adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts/) | ADR-0008: The Java charter is a rewrite that replaces the Python charter, built against BDD and OpenAPI contracts | Accepted | 2026-10-18 |

## Core decisions this repository builds on

These live in [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty) under
`docs/adr/`. They are not copied here.

| Core ADR | Title | Status in core | What it gives this repository |
|---|---|---|---|
| [`4.x/2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) | The charter becomes an external contract package in its own repository | Accepted | The reason this repository exists; the import-move precondition (formerly #645, now carried by epic #6099) is GA-gating |
| [`4.x/2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) | Replace charter reads and writes through a staged Java service strangler | Accepted (2026-10-18, with Amendment 2026-10-18) | The stages, the hexagonal shape, the write gates, the service boundary |
| [`4.x/2026-10-01-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-01-2-mission-status-read-api-and-dashboard-extraction.md) | A mission status read API replaces the CLI-bundled dashboard | Accepted | The sibling Java service: stack, binding, contract conventions, release model |
| [`4.x/2026-10-06-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-06-2-charter-offering-active-charter-and-activation-presets.md) | Charter offering, active charter and activation presets | Accepted | The domain vocabulary; presets as pack data; no aliases or shims |
| [`4.x/2026-10-11-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-11-2-a-pack-declares-its-level-in-pack-yaml.md) | A Charter Pack declares its level in `pack.yaml` | Accepted | `pack_level` and its refusal codes |
| [`3.x/2026-05-16-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-05-16-1-doctrine-layer-merge-semantics.md) | Doctrine layer merge semantics | Accepted | How built-in, org and project layers merge |
| [`3.x/2026-08-02-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-08-02-1-charter-wheel-assessment.md) | Charter wheel assessment | Accepted | The earlier, deferred wheel split that 2026-10-12-2 extends |
| [`3.x/2026-04-25-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-04-25-1-shared-package-boundary.md) | Shared package boundary | Accepted | How external contract packages are consumed |
