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

- [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/): every pack is a resource with
  sub-resources, there is an API root, and `Links` has open relation keys and a closed link
  object.
- [ADR-0020](/adr/0020-offering-and-activations/): two more top-level resources.
  - `/offering` is what can be used; the action-context read moves under it.
  - `/activations` holds active charters, which the service stores under an opaque
    `activationId`.
  - Core registers its content or builds it through the API, and maps it to its own files.
  - Core's response to the handover is folded in. The two remaining divergences are DIV-201 and
    DIV-202 in the [divergence register](/conformance/divergence-register/).
- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/): the published
  contract is core's seam now, through a generated client and a proxy that switches one operation
  at a time.
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/): the contract is published as 0.x until
  it is stable. A minor may break and a patch may not. The first release is 0.1.0.
- [Packs resource model](/architecture/packs-resource-model/) and
  [Offering and activation](/architecture/offering-and-activation/) hold the designs. The
  [service contract](/api/service-contract/) lists the proposed operations.
- New [Development conventions](/development/) cover REST resources, hypermedia, conditional
  requests and HTTP status codes.

All four records are Proposed. The contract itself is unchanged.
