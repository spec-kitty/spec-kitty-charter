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
- [ADR-0020](/adr/0020-offering-and-activations/) (Proposed) makes `/offering` the charter, the
  merged offering of the packs in force, and moves the action-context read under it before 1.0.
  An activation is a camelCase document the service stores under an opaque `activationId`. Core
  either registers its document or builds one through the API and fetches it. The service is
  authoritative, and core's file is a fetched copy. The activation operations are declared in
  contract 1.0 and answer `501` until built. The contract becomes hosted-first before the tag.
- [Packs resource model](/architecture/packs-resource-model/) and
  [Offering and activation](/architecture/offering-and-activation/) hold the resource trees, link
  graphs, request sequences, examples and proposed contract scenarios.
- The [service contract](/api/service-contract/) lists the proposed operations and what must land
  before the 1.0 tag.
- A new [Development conventions](/development/) section documents REST resource naming,
  hypermedia, conditional requests and the HTTP status codes the contract uses and proposes.

The contract itself is unchanged.
