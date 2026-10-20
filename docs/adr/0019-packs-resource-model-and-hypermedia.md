---
title: "ADR-0019: Packs are a navigable REST resource with hypermedia links"
description: Proposes one meaning for /packs, a per-pack resource tree for pack contents, a closed Links component and an API root, all landing before the 1.0 tag.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0007-rest-hateoas-contract-of-record
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0018-action-context-read-and-seam-access-surface
  - architecture/packs-resource-model
  - architecture/pack-registration-distribution
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the pack resource tree, the Links component and what must land before the 1.0 tag.
  - profile: contract-consumer
    guidance_text: Read this for how to find a pack and its contents by following links instead of building URLs.
  - profile: core-maintainer
    guidance_text: Read this for what core's seam and generated client need to follow links.
---

# ADR-0019: Packs are a navigable REST resource with hypermedia links

## Status

**Proposed** — 2026-10-20. Decider: Stijn Dejongh (operator). Like the decisions of
2026-10-19, it is provisional once accepted: it is revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when). The design is in
[Packs resource model](/architecture/packs-resource-model/); the conventions it follows are in
[Development conventions](/development/).

## Context

The contract has two pack operations today: `GET /packs` lists the loaded packs, and
`POST /packs/refresh` re-reads them ([ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)).
Nothing reads one pack or its contents.

The registration design ([ADR-0005](/adr/0005-service-registers-and-distributes-packs/),
[Pack registration and distribution](/architecture/pack-registration-distribution/), and the
suites under `src/test/resources/features/`) adds three more operations:

- `POST /packs` registers a pack and answers a `RegisteredPack` with `_links` to `self`,
  `resolve`, `verify`, `distribute` and `source`;
- `POST /packs/{packId}/distributions` distributes it;
- `GET /packs/{packId}/resolution` is the read-only resolution state
  ([ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) decision 7).

Two gaps follow:

- **`/packs` would mean two things.** The listing answers this worktree's loaded packs; the
  registration design makes the same collection the place a pack is registered into.
- **The `self` link has nowhere to point.** No operation answers one pack, its artifacts, its
  graph, its presets or its assets. The cutover read inventory (epic
  [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94)) lists those reads as
  undecided: artifact bodies (R2, [#96](https://github.com/spec-kitty/spec-kitty-charter/issues/96)),
  graph (R3, [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97)), presets (R4,
  [#98](https://github.com/spec-kitty/spec-kitty-charter/issues/98)), assets (R11,
  [#105](https://github.com/spec-kitty/spec-kitty-charter/issues/105)) and glossary packs (R12,
  [#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106)).

ADR-0007 decision 4 chose a reusable `Links` component for hypermedia, but no schema has one.
Response schemas are closed (`additionalProperties: false`), so under
[ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) a field added to an
existing response after the 1.0 tag is breaking.

## Decision

1. **`/packs` has one meaning: the packs this service knows.** Today those are the configured
   packs; once registration lands, the registered ones too. Which packs are in force for this
   worktree, and in which merge order, is the composition of the charter, a separate resource
   named here only as `/charter`. It is designed next, with the merged graph (R3,
   [#97](https://github.com/spec-kitty/spec-kitty-charter/issues/97)) and the effective sets
   (R9, [#103](https://github.com/spec-kitty/spec-kitty-charter/issues/103)).
2. **Every pack is a resource, and its contents are sub-resources.** The proposed map:

   | Method | Path | What it is |
   |---|---|---|
   | `GET` | `/` | The API root: links to every top-level resource |
   | `GET` | `/packs` | The pack collection; filters `level` and, later, `state` |
   | `POST` | `/packs` | Register a pack (ADR-0005); `201 Created` with `Location` |
   | `POST` | `/packs/refresh` | Re-read packs and configuration; a controller resource, kept from ADR-0018 |
   | `GET` | `/packs/{packId}` | One pack: its descriptor, source, counts and diagnostics |
   | `GET` | `/packs/{packId}/artifacts` | The pack's artifacts; `?kind=`, paged with `PageInfo` (R2) |
   | `GET` | `/packs/{packId}/artifacts/{kind}/{artifactId}` | One artifact with its body (R2) |
   | `GET` | `/packs/{packId}/graph` | The nodes and edges this pack declares (R3, per pack) |
   | `GET` | `/packs/{packId}/presets` | The pack's activation presets (R4) |
   | `GET` | `/packs/{packId}/assets/{path}` | One asset file (R11) |
   | `GET` | `/packs/{packId}/resolution` | Read-only resolution state (ADR-0007 decision 7) |
   | `POST` | `/packs/{packId}/distributions` | Distribute the pack (ADR-0005) |

   `{packId}` is the pack's minted `pack_id` ULID, which `PackSummary.packId` already answers.
   Glossary packs are packs: their terms are artifacts of kind `glossary`, read through
   `/packs/{packId}/artifacts?kind=glossary` (R12), not a resource of their own.
3. **Hypermedia is one closed `Links` component.** Every response schema carries a required
   `_links` property that references it. `_links` maps a relation name to a link object
   `{href, templated?, method?, title?}`, and the link object is closed. Relation names are the
   IANA registered ones where one fits (`self`, `collection`, `item`, `next`, `prev`, `first`,
   `last`, `related`); charter relations are lowercase and hyphenated (`artifacts`, `graph`,
   `presets`, `assets`, `resolution`, `distribute`, `refresh`, `register`, `info`,
   `mission-types`). A link is present only when its action is available to this caller in this
   state. A client follows links; it does not build paths. The relation table and the rules are
   in [Hypermedia and HATEOAS](/development/hypermedia-hateoas/).
4. **The hypermedia lands before the 1.0 tag; the new operations follow additively.** The
   `Links` component, `GET /`, and `_links` on every existing response (`PackListing`,
   `PackSummary`, the refresh answer, `ActionContext`, `ServiceInfo`) change closed response
   schemas, so they must land before the first release
   ([#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45)). The per-pack operations
   are new paths, which are additive under ADR-0009 and can land after 1.0, each decided by its
   read-inventory issue.
5. **Status codes follow [HTTP status codes](/development/http-status-codes/).** Registration
   answers `201 Created` with a `Location` header to the new pack; reads answer `200`; an unknown
   `packId`, kind or artifact is a `404` charter refusal. The open status-code questions that page
   lists are follow-ups, not part of this decision.

## Consequences

### Positive

- A client, core's seam included, needs one URL: the root. Everything else it reaches by following
  links, so a path can change without a breaking change for clients that follow links.
- The read inventory gets a home for five reads (R2, R3 per pack, R4, R11, R12) in one consistent
  tree instead of five unrelated paths.
- `/packs` keeps one meaning when registration lands; the worktree's composition gets its own
  resource instead of overloading the collection.

### Negative

- Every response schema grows a required `_links` property before the 1.0 tag, and every handler
  has to build links. The contract scenarios and the conformance mapping (ADR-0016) must account
  for them.
- A pack without a `pack.yaml` has no `packId` today (`PackSummary.packId` is nullable), so it has
  no addressable resource until it gets a descriptor. The listing still answers it, without a
  `self` link.

### Risks

- A relation vocabulary that grows ad hoc. Mitigation: every relation is listed in the relation
  table before it appears in the contract, and a lint rule can check `_links` keys against it.
- Links that leak availability rules into clients. Mitigation: a link's presence is the only
  signal; clients never infer an action from a missing field.

## Alternatives considered

### B: keep `/packs` as the loaded offering and register under `/registrations`

Keeps today's listing meaning. Rejected as the default: it splits one thing, a pack, across two
collections, and a registered pack that is also loaded would have two URLs. It stays open if the
operator prefers to keep `/packs` worktree-scoped.

### C: no hypermedia; the OpenAPI document is the only map

Clients build paths from the contract, as core's generated client would by default. Rejected:
ADR-0007 decided REST with HATEOAS, and every path then becomes a client-side constant that a
later path change breaks.

### D: open the response schemas so `_links` can be added later

Rejected by ADR-0007 (option B there): it gives up the closed-response guarantee of ADR-0004 for
every response.

## Revisit when

- Registration (ADR-0005) is designed in detail and needs a lifecycle, such as revocation, that
  this tree does not hold.
- A second consumer arrives that cannot follow links, for example a generated client without
  hypermedia support.
- `/charter` is designed and shows that per-pack graph reads are not needed.

## Requests to core

- **The seam follows links.** Core's seam reads `GET /` once per service start and follows
  relations, instead of hard-coding paths.
- **The generated client includes the `Links` type.** It needs no hypermedia library; following a
  link is a GET or POST to its `href`.

## References

- [Packs resource model](/architecture/packs-resource-model/), the design.
- [Development conventions](/development/): [REST resource conventions](/development/rest-resource-conventions/),
  [Hypermedia and HATEOAS](/development/hypermedia-hateoas/), [HTTP status codes](/development/http-status-codes/).
- [ADR-0005](/adr/0005-service-registers-and-distributes-packs/),
  [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/),
  [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/),
  [ADR-0015](/adr/0015-charter-repository-is-canonical-for-charter-structure/),
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/).
- Read inventory epic [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94); release
  [#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45).
