---
title: API
description: The charter API — two read operations built in the Java service, its Javadoc, and the planned reads, writes and Python package surface that are still design only.
doc_status: draft
updated: 2026-10-18
type: API
kind: Hub
related:
  - architecture/contract-and-dtos
  - architecture/logical-model
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
  - adr/0004-public-api-returns-generated-dtos
audience:
  - profile: contract-consumer
    guidance_text: Read this to find which public surface to call and how far to trust it.
---

# API

> **Partly built, partly designed.** Two read operations are in the contract
> (`contracts/charter/openapi.yaml`) and served by the Java charter service: pack listing
> (`listPacks`, `GET /packs`) and action-scoped context (`getActionContext`,
> `GET /mission-types/{missionType}/actions/{action}/context`). The
> [Java API reference](/api/javadoc/) documents the service's hand-written modules. Still
> only designed: the other planned reads of the [service contract](/api/service-contract/)
> (profile resolution, active charter, artifact lookup, graph query and diagnostics), its
> write operations, and the Python package surface, derived from core's
> `charter.offering.api` (`src/charter/offering/api.py` in
> [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty)). The planned service
> operations derive from core ADR
> [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md)
> (the staged Java service strangler), [ADR-0001](/adr/0001-java-service-with-openapi-contract/)
> and [ADR-0002](/adr/0002-contract-source-of-record/). Their names and shapes may change
> until code and a released contract land here.

The charter has two public surfaces, one per stage of the extraction:

| Surface | Kind | Consumers | Basis |
|---|---|---|---|
| [Python package](/api/python-package/) | in-process Python API, versioned by wheel semver and `py.typed` | the CLI's charter facades, open-core consumers | core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) D-1: callers use only `charter.offering.api` |
| [Service contract](/api/service-contract/) | OpenAPI 3.1 over loopback REST, later MCP | the core's seam (through generated DTOs, per [ADR-0004](/adr/0004-public-api-returns-generated-dtos/)), agent harnesses | ADR-0001, ADR-0002, core ADR `2026-10-03-1` |

## Decision: the API returns generated DTOs

[ADR-0004](/adr/0004-public-api-returns-generated-dtos/) (Accepted, 2026-10-14) settles it:
the public API returns DTOs generated from the OpenAPI contract, generated per client
language for each integration client surface (Python for core's seam and the CLI, Java for
the service, others as needed). Core's hand-written types behind `charter.offering.api`
are transitional; amending core's constraint C-003 is a core follow-up.

Both expose the same [logical model](/architecture/logical-model/). While both exist, they
must give one answer for the same inputs; the conformance corpus in
[Contract and DTOs](/architecture/contract-and-dtos/) is what holds them to it.

- [Python package](/api/python-package/) — the curated symbol set, its types, operations and
  errors.
- [Service contract](/api/service-contract/) — the planned read and write operations, the
  error model and the transports.
- [Versioning and compatibility](/api/compatibility/) — how each surface is versioned and
  what a consumer can rely on.
- [Java API reference (Javadoc)](/api/javadoc/) — the Javadoc of the service's hand-written
  Java modules, built by the main build and published with this site.
