---
title: "ADR-0007: REST-HATEOAS is the v1 contract of record; GraphQL is planned-later, MCP deferred"
description: REST with OpenAPI-HATEOAS ships as the sole v1 contract of record via a reusable Links component; GraphQL is derived from OpenAPI and planned-later; the MCP adapter is deferred.
doc_status: active
updated: 2026-10-18T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0001-java-service-with-openapi-contract
  - adr/0002-contract-source-of-record
  - adr/0004-public-api-returns-generated-dtos
  - adr/0006-onion-vertical-slices-archunit
  - architecture/contract-and-dtos
audience:
  - profile: contract-consumer
    guidance_text: Read this for which API surface is authoritative in v1 and how hypermedia links are modeled.
  - profile: charter-service-contributor
    guidance_text: "Read this for the API strategy: REST now, GraphQL planned-later, MCP deferred, all over one use-case seam."
  - profile: core-maintainer
    guidance_text: Read this to see the single contract of record core generates its client from.
---

# ADR-0007: REST-HATEOAS is the v1 contract of record; GraphQL is planned-later, MCP deferred

## Status

**Accepted** — 2026-10-27 (proposed 2026-10-18). Decider: Stijn Dejongh (operator). This record fixes the entry-point API strategy
for the charter offering service. It preserves [ADR-0002](/adr/0002-contract-source-of-record/) (one OpenAPI
contract is the source of record) and [ADR-0004](/adr/0004-public-api-returns-generated-dtos/) (the API
returns generated, closed DTOs), and sits over the layering of
[ADR-0006](/adr/0006-onion-vertical-slices-archunit/). It resolves four design-mission Decision Moments
(GraphQL vs the single-contract principle, HATEOAS vs closed DTOs, the MCP adapter, and REST/GraphQL parity).

### Amendment 2026-10-28 (#208): the contract serves itself, and the first realized conditional read

`getOpenApiDocument` (`GET /openapi.yaml`, tag Service) adds a read-only projection by which the
contract of record serves its own document, and realizes the first conditional GET in the contract —
a strong `ETag` validator and `If-None-Match` → `304 Not Modified`, weakly compared (RFC 9110
§13.1.2). Both sit within this record's envelope: a read-only Service projection, no structural
change. The service answers `501` as a `default` Problem until #209 serves the bundle, and the RFC
8631 `service-desc` link is deferred to #126. The `304`-has-no-body invariant is recorded in
[ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)'s amendment of the same day.

## Context

The architecture brief names two API versions — OpenAPI-based HATEOAS REST and GraphQL — both as entry-point
adapters over the same domain use cases (never a second implementation of charter resolution). The existing
docs also plan an `adapter-in-mcp`. Three principles were already fixed and had to be preserved: one OpenAPI
document is the source of record core generates its client from (ADR-0002); response DTOs are generated and
**closed** (`additionalProperties: false`, ADR-0004); and no business logic lives in an adapter (ADR-0006).

Introducing GraphQL raised a tension with the single-contract-of-record principle; HATEOAS raised a tension
with closed generated DTOs (hypermedia links must live *inside* those closed schemas); and the two-plus-MCP
surface set raised the question of what actually ships in v1.

## Decision

1. **REST with OpenAPI-HATEOAS ships in v1 as the sole contract of record** (DM `01M577X2EMGY9R5E2JC8PCJVTD`).
   It is the surface core generates its Python client from, the DTO source, and the conformance corpus.
   Controllers implement the OpenAPI-generated server interfaces so a contract/code divergence fails the
   build. It carries the `/api/v1` prefix and the `Problem`/`PageInfo` vocabulary of `architecture/contract-and-dtos`.
2. **OpenAPI stays the single source of record; GraphQL is derived from it** (DM `01M575AAJW9R833MV6ZDA4G64J`).
   GraphQL is never the thing core pins or generates from; its SDL is generated/derived from the OpenAPI
   contract, so the two surfaces cannot drift into two sources of truth. ADR-0002 is preserved intact.
3. **GraphQL is planned-later, not built in v1** (DM `01M577X2EMGY9R5E2JC8PCJVTD`, DM
   `01M575AAJW9R833MV6ZDA4G64J`). The use-case layer is kept **adapter-ready** so a GraphQL entry-point
   adapter drops in over the identical use cases without domain change, but no GraphQL adapter is implemented
   in this version. Because every surface adapts the same use cases (ADR-0006), domain-operation parity holds
   by construction whenever GraphQL does land.
4. **HATEOAS hypermedia is modeled as a reusable `Links` schema component** (DM `01M575AC288BKPRCCTJKT33H3G`).
   A single `Links` component is referenced from each response schema; response DTOs stay **closed**
   (`additionalProperties: false`). Hypermedia relations (for example a `RegisteredPack` linking to its
   `distribute`, `verify` and `source` relations) are carried by that component, not by opening the DTOs.
   ADR-0004 is preserved intact.
5. **MCP is deferred** (DM `01M575ADHN04ZSRK007Y2S0CRF`). The planned `adapter-in-mcp` is not built here; it
   remains a later third entry-point adapter over the same use cases. Deferring it changes nothing structural —
   it would be one more thin adapter over the shared use-case seam.
6. **All surfaces are thin adapters over one use-case seam.** DTO/GraphQL-type ↔ use-case-I/O mapping lives
   only in `..infra.api..`; a transport type never crosses into the application or domain ring
   (ArchUnit-pinned, ADR-0006). "REST and MCP may never implement charter resolution independently" (the
   `overview` invariant) extends to GraphQL.
7. **Resolution is a read-only projection, not a public action resource** (DM `01M578WFJ84BNS2ZSE6SN35CFG`).
   There is **no** `POST /packs/{id}/resolutions` action endpoint. Resolution happens *internally* during
   register and distribute; `PackResolver` stays a **domain service** (ADR-0005 §10), never a REST resource.
   The integrity, engine-compatibility, cache and reachability outcomes surface as the **result of the
   distribute action** (success payload, or a `Problem` with the canonical refusal code such as
   `PACK_ENGINE_INCOMPATIBLE` / `INTEGRITY_FAILURE` / `SOURCE_UNREACHABLE`). Resolution *state* is exposed as
   a **read-only projection** — `GET /packs/{id}/resolution`, or embedded in the pack resource and reachable
   through its `Links` component — not as something a client triggers.
8. **The requester tier is derived from the authenticated identity, never a client-set field** (DM
   `01M578W9FK24NNSFRYT7C5ZZZ1`). No request body or query parameter carries the tier; the adapter derives it
   from the authenticated principal, keeping tier selection fail-closed (authn/authz is a separate downstream
   concern).

## Consequences

### Positive

- Exactly one contract of record in v1; core has one thing to pin and generate from, and the conformance
  corpus compares like with like.
- HATEOAS ships without opening the generated DTOs — the `Links` component keeps ADR-0004's closed-schema
  guarantee while adding hypermedia.
- Deferring GraphQL and MCP removes dual-specification and extra conformance surface from v1 while the
  use-case seam keeps both a cheap, non-structural add later.
- Generating GraphQL from OpenAPI means a contract change reaches both surfaces by regeneration, never by
  hand-syncing two schemas.
- Keeping resolution a read-only projection means the API exposes no way to trigger a fetch/verify out of
  band of register/distribute — resolution stays an internal domain concern with a single owner, and the
  tier cannot be spoofed because it is derived from auth, not sent by the client.

### Negative

- GraphQL clients get nothing in v1; the graph-shaped read/traversal ergonomics wait for a later version.
- Modeling hypermedia inside closed schemas via a shared component is more constrained than free-form
  `_links`, and every linked response must reference the component explicitly.
- Deriving GraphQL SDL from OpenAPI needs a generator/toolchain that does not exist yet; the "adapter-ready"
  claim is only as good as that future generation step.

### Risks

- If GraphQL is later authored beside OpenAPI rather than generated from it, decision 2 is violated and two
  sources of record reappear — the generation path must be held.
- A reusable `Links` component that grows per-relation fields could pressure the closed-schema rule; relation
  modeling should stay inside the component's own shape.

## Alternatives considered

### Option A: GraphQL as a co-equal, independently authored contract

Author GraphQL SDL beside OpenAPI with full write parity. Not chosen: it creates a second source of record
(violating ADR-0002), doubles the conformance and maintenance surface, and GraphQL cannot be the thing core
pins and generates from anyway.

### Option B: Open the DTOs (drop `additionalProperties: false`) to carry `_links`

Add free-form hypermedia by relaxing the closed schemas. Not chosen: it discards ADR-0004's closed-schema
guarantee for every response. The reusable `Links` component gets hypermedia without that cost.

### Option C: Ship GraphQL (and/or MCP) in v1 alongside REST

Build all surfaces now. Not chosen: it front-loads cost and conformance surface for surfaces with no current
consumer, against a write set that is small and already canonical in REST. The use-case seam keeps them cheap
to add when demand is real.

### Option D: Model resolution as a public `POST /packs/{id}/resolutions` action resource

Expose resolution as a client-triggered action that returns a resolution resource. Not chosen (DM
`01M578WFJ84BNS2ZSE6SN35CFG`): resolution is an internal step of register/distribute, not an independent
client action; a public resolve verb would duplicate the distribute path, invite out-of-band fetch/verify,
and let a client imply a tier. The outcomes are surfaced on the distribute result and as a read-only
projection instead.

## References

- This repo: [ADR-0001](/adr/0001-java-service-with-openapi-contract/) (service + OpenAPI contract),
  [ADR-0002](/adr/0002-contract-source-of-record/) (single source of record, preserved),
  [ADR-0004](/adr/0004-public-api-returns-generated-dtos/) (generated closed DTOs, preserved),
  [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) (adapters over the use-case seam);
  `architecture/contract-and-dtos`.
- Decision Moments: `01M575AAJW9R833MV6ZDA4G64J` (OpenAPI sole source, GraphQL derived),
  `01M575AC288BKPRCCTJKT33H3G` (Links component HATEOAS), `01M575ADHN04ZSRK007Y2S0CRF` (MCP deferred),
  `01M577X2EMGY9R5E2JC8PCJVTD` (REST v1 of record, GraphQL planned-later), `01M578WFJ84BNS2ZSE6SN35CFG`
  (resolution as read-only projection), `01M578W9FK24NNSFRYT7C5ZZZ1` (requester tier from auth).
- Directives: DIRECTIVE_044 (canonical source).
</content>
