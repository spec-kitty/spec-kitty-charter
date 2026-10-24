---
title: "ADR-0019: Packs are a navigable REST resource with hypermedia links"
description: Proposes one meaning for /packs, a per-pack resource tree for pack contents, a Links component with a closed link object, and an API root, all landing before 1.0.0.
doc_status: active
updated: 2026-10-24T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0007-rest-hateoas-contract-of-record
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0015-charter-repository-is-canonical-for-charter-structure
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
  - adr/0022-contract-versions-start-at-0-x
  - architecture/packs-resource-model
  - architecture/pack-registration-distribution
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the pack resource tree, the Links component and what must land before 1.0.0.
  - profile: contract-consumer
    guidance_text: Read this for how to find a pack and its contents by following links instead of building URLs.
  - profile: core-maintainer
    guidance_text: Read this for what core's seam and generated client need to follow links.
---

# ADR-0019: Packs are a navigable REST resource with hypermedia links

## Status

**Accepted (provisional)** — 2026-10-24, proposed 2026-10-20 and merged with #123. Decider: Stijn Dejongh (operator). Like the decisions of
2026-10-19, it is provisional: it is revisited as the system evolves, on the
triggers under [Revisit when](#revisit-when). The design is in
[Packs resource model](/architecture/packs-resource-model/); the conventions it follows are in
[Development conventions](/development/). The charter itself, which this record first named
`/charter`, is designed in [ADR-0020](/adr/0020-offering-and-activations/) as two top-level
resources: `/offering` and `/activations`.

Decisions marked **(default)** follow the design squad's recommendation; they stand as decided until revisited.

**Implementation note, 2026-10-24.** PR #139 rebuilt the service on one canonical `Pack` aggregate
and serves tactics at `/packs/{packId}/tactics[/{tacticId}]`, one route per kind, rather than the
generic `/packs/{packId}/artifacts/{kind}/{artifactId}` of D2. It also removed `GET /packs` and
`POST /packs/refresh`. Aligning the routes with D2 before more kinds are exposed is tracked in
[#126](https://github.com/spec-kitty/spec-kitty-charter/issues/126) and
[#169](https://github.com/spec-kitty/spec-kitty-charter/issues/169).

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

- **`/packs` would mean two things.** The listing answers the packs the service loaded; the
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
existing response after 1.0.0 is breaking. Until then the contract is 0.x, and a minor version may
break ([ADR-0022](/adr/0022-contract-versions-start-at-0-x/)).

## Decision

1. **`/packs` has one meaning: the packs this service knows.** Today those are the configured
   packs; once registration lands, the registered ones too. The merged offering of the packs in
   force is `/offering`, and the active charters that choose from it are `/activations`. Both are
   decided in [ADR-0020](/adr/0020-offering-and-activations/). `/packs` never answers merged
   content or a client's choices.
2. **Every pack is a resource, and its contents are sub-resources.** The proposed map:

   | Method | Path | What it is |
   |---|---|---|
   | `GET` | `/` | The API root: links to every top-level resource |
   | `GET` | `/packs` | The pack collection; filters `level` and, later, `state` |
   | `POST` | `/packs` | Register a pack (ADR-0005); `201 Created` with `Location` |
   | `POST` | `/packs/refresh` | Re-read the packs; a controller resource, kept from ADR-0018 |
   | `GET` | `/packs/{packId}` | One pack: today's `PackSummary` fields; its registration source once registration lands |
   | `GET` | `/packs/{packId}/artifacts` | The pack's artifacts; `?kind=`, paged with `PageInfo` (R2) |
   | `GET` | `/packs/{packId}/artifacts/{kind}/{artifactId}` | One artifact with its body (R2) |
   | `GET` | `/packs/{packId}/graph` | The nodes and edges this pack declares (R3, per pack) |
   | `GET` | `/packs/{packId}/presets` | The pack's activation presets (R4) |
   | `GET` | `/packs/{packId}/assets` | The pack's asset paths (R11) |
   | `GET` | `/packs/{packId}/assets/{path}` | One asset file (R11) |
   | `GET` | `/packs/{packId}/resolution` | Read-only resolution state (ADR-0007 decision 7) |
   | `POST` | `/packs/{packId}/distributions` | Distribute the pack (ADR-0005) |

   `{packId}` is the pack's minted `pack_id` ULID, which `PackSummary.packId` already answers.
   Glossary packs are packs: their terms are artifacts of kind `glossary`, read through
   `/packs/{packId}/artifacts?kind=glossary` (R12), not a resource of their own.
3. **Hypermedia is one `Links` component with a closed link object (shape: default).** Every
   success schema carries a required `_links` property that references it; `Problem` and
   `CharterRefusal` keep their shape. `_links` maps a relation name to a link object
   `{href, templated?, method?, title?}`; the link object is closed, and the map's keys are open.
   - **Why open keys.** A closed set of keys per resource would freeze every relation at 1.0.0: a
     relation added later is a new response property, which the release gate reports as
     breaking. With open keys a new relation is additive, and only the link object is frozen.
   - **What keeps it honest.** The closure checker reports a schema-valued
     `additionalProperties` as open, so it gets one exemption: a property named `_links` whose
     values reference the link object, pinned by a fixture. A lint rule checks every relation
     key the contract's examples use against the relation table. Clients must tolerate a
     relation they do not know, which HAL clients already do.
   - Rejected: closed per-resource link schemas, which reserve every future relation at 1.0.0.
   - **Relation names** are the IANA registered ones where one fits (`self`, `collection`,
     `item`, `next`, `prev`, `first`, `last`, `edit`); charter relations are lowercase and
     hyphenated. The one relation table is in [Hypermedia and HATEOAS](/development/hypermedia-hateoas/);
     a relation is added there before it appears in the contract.
   - **`href` carries the server prefix**, for example `/api/v1/packs`, so it resolves as an
     absolute path under RFC 3986. A client follows it as given.
   - A link is present only when its action is available to this caller in this state, and only
     when its target is built: a link never points to a reserved operation (ADR-0020 D8). A
     client follows links; it does not build paths.
4. **The hypermedia lands before 1.0.0; the new operations follow additively.** The
   `Links` component, `GET /`, and `_links` on every existing success schema (`PackListing`,
   which the refresh also answers, `PackSummary`, `ActionContext`, `ServiceInfo`) change closed
   response schemas, so they must land before 1.0.0. In 0.x they arrive in a minor version
   (ADR-0022). With open relation keys,
   the per-pack operations are new paths and new relations, both additive under ADR-0009; each
   lands when its read-inventory issue decides it. Until `GET /packs/{packId}` is built, a pack
   listing item carries no `self` link.
5. **Status codes follow [HTTP status codes](/development/http-status-codes/).** Registration
   answers `201 Created` with a `Location` header to the new pack, which needs the lint amendment
   of ADR-0020 D9; reads answer `200`; an unknown `packId`, kind or artifact is a `404` charter
   refusal. The registration design's `resolve` and `verify` links are dropped: resolution is the
   read-only `resolution` resource, and verification is part of distribution. This supersedes the
   example relations of [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) decision 4.

## Consequences

### Positive

- A client, core's seam included, needs one URL: the root. Everything else it reaches by following
  links, so a path can change without a breaking change for clients that follow links.
- The read inventory gets a home for five reads (R2, R3 per pack, R4, R11, R12) in one consistent
  tree instead of five unrelated paths.
- `/packs` keeps one meaning when registration lands; the merged offering and the active
  charters get their own resources (ADR-0020) instead of overloading the collection.

### Negative

- Every success schema grows a required `_links` property before 1.0.0, and every handler
  has to build links. The contract scenarios and the conformance mapping (ADR-0016) must account
  for them.
- A pack without a `pack.yaml` has no `packId` today (`PackSummary.packId` is nullable), so it has
  no addressable resource until it gets a descriptor. The listing still answers it, without a
  `self` link.

### Risks

- A relation vocabulary that grows ad hoc. Mitigation: every relation is listed in the relation
  table before it appears in the contract, and a lint rule checks `_links` keys against it.
- Open relation keys hide a new relation from the release gate. Mitigation: the lint rule above,
  and the contract CHANGELOG records every relation added.
- Links that leak availability rules into clients. Mitigation: a link's presence is the only
  signal; clients never infer an action from a missing field.

## Alternatives considered

### B: keep `/packs` as the loaded offering and register under `/registrations`

Keeps today's listing meaning. Rejected: it splits one thing, a pack, across two collections,
and a registered pack that is also loaded would have two URLs.

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
- The offering's read inventory (ADR-0020) shows per-pack graph reads are not needed.

## Requests to core

- **The seam follows links.** Core's seam reads `GET /` once per service start and follows
  relations, instead of hard-coding paths. It ignores relations it does not know.
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
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/),
  [ADR-0020](/adr/0020-offering-and-activations/).
- Read inventory epic [#94](https://github.com/spec-kitty/spec-kitty-charter/issues/94); release
  [#45](https://github.com/spec-kitty/spec-kitty-charter/issues/45).
