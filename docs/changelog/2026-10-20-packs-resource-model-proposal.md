---
title: Packs as a navigable resource, proposed
description: ADR-0019 proposes one meaning for /packs, a per-pack resource tree, a closed Links component and an API root; development conventions document REST, HATEOAS and status codes.
doc_status: active
updated: 2026-10-20T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0019-packs-resource-model-and-hypermedia
  - architecture/packs-resource-model
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-20 — Packs as a navigable resource, proposed

## What changed

- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) (Proposed) gives `/packs` one
  meaning, the packs this service knows. It makes every pack a resource whose artifacts, graph,
  presets and assets are sub-resources, adds an API root, and makes ADR-0007's `Links` component
  concrete. The composition of the charter for a worktree moves to a separate `/charter` resource,
  still to be designed.
- [Packs resource model](/architecture/packs-resource-model/) holds the resource tree, the link
  graph, example responses and proposed contract scenarios.
- The [service contract](/api/service-contract/) lists the proposed operations, and states that the
  `Links` component, the root and `_links` on the existing responses must land before the 1.0 tag.
- A new [Development conventions](/development/) section documents REST resource naming,
  hypermedia and the HTTP status codes the contract uses.

The contract itself is unchanged.
