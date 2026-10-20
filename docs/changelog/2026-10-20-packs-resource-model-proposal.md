---
title: Packs, offering and activations, proposed
description: ADR-0019 proposes the packs tree and hypermedia, ADR-0020 the offering and stored activations; new development conventions for REST and status codes.
doc_status: active
updated: 2026-10-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0020-offering-and-activations
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0022-contract-versions-start-at-0-x
  - architecture/packs-resource-model
  - architecture/offering-and-activation
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-20 — Packs, offering and activations, proposed

## What changed

- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) (Proposed) gives `/packs` one
  meaning, the packs this service knows. It makes every pack a resource whose artifacts, graph,
  presets and assets are sub-resources, adds an API root, and makes ADR-0007's `Links` component
  concrete: a map with open relation keys and a closed link object.
- [ADR-0020](/adr/0020-offering-and-activations/) (Proposed) adds two top-level resources:
  `/offering`, what can be used, merged from the packs in force, with the action-context read
  moved under it before 1.0.0; and `/activations`, the active charters. An activation is a camelCase document the service stores under an opaque `activationId`. Core
  either registers its document or builds one through the API and fetches it. The service is
  authoritative once activation routes to it; core keeps its own files and maps them. The activation operations are declared in
  the contract early and answer `501` until built. The contract becomes hosted-first before 1.0.0.
- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) (Proposed) makes
  the published contract core's seam now: core vendors a generated client, and a proxy routes
  each operation to core's Python or to the service, switching one operation at a time.
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) (Proposed) publishes the contract as
  0.x while it iterates with core's generated client: a 0.x minor may break, a patch may not, and
  1.0.0 is minted once the contract is stable. The first release, 0.1.0, is today's contract.
- [Packs resource model](/architecture/packs-resource-model/) and
  [Offering and activation](/architecture/offering-and-activation/) hold the resource trees, link
  graphs, request sequences, examples and proposed contract scenarios.
- The [service contract](/api/service-contract/) lists the proposed operations and what must land
  before 1.0.0.
- A new [Development conventions](/development/) section documents REST resource naming,
  hypermedia, conditional requests and the HTTP status codes the contract uses and proposes.

The contract itself is unchanged.
