---
title: Packs resource model
description: "The proposed REST resource tree for packs and their contents: the API root, the per-pack sub-resources, the link graph and example responses."
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Architecture
kind: Explanation
related:
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0007-rest-hateoas-contract-of-record
  - architecture/pack-registration-distribution
  - architecture/contract-and-dtos
  - api/service-contract
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the resource tree, the link graph and how each resource maps onto the domain.
  - profile: contract-consumer
    guidance_text: Read this for how to start at the root and reach any pack, artifact or action by following links.
---

# Packs resource model

How a client finds a pack and its contents: start at the API root, follow links. This page is the
design behind [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/); the conventions it
follows are in [Development conventions](/development/).

> **Status:** design only, proposed by ADR-0019. Today the contract has `GET /packs`,
> `POST /packs/refresh`, `GET /info` and the action-context read, and no response carries links.
> [What the labels mean](/#status).

## The resource tree

```text
/                                         API root
├── info                                  service info (built)
├── mission-types/{missionType}/actions/{action}/context   action-context read (built)
└── packs                                 the packs this service knows (GET built; POST proposed)
    ├── refresh                           controller: re-read packs and configuration (built)
    └── {packId}                          one pack (proposed)
        ├── artifacts                     ?kind=, paged
        │   └── {kind}/{artifactId}       one artifact with its body
        ├── graph                         the nodes and edges this pack declares
        ├── presets                       activation presets
        ├── assets/{path}                 one asset file
        ├── resolution                    read-only resolution state
        └── distributions                 POST: distribute this pack
```

`{packId}` is the pack's `pack_id` ULID from `pack.yaml`. The composition of the charter (which
packs are in force for this worktree, in which merge order, and the merged graph) is not under
`/packs`; it gets its own resource, `/charter`, designed next.

## The link graph

```mermaid
flowchart LR
  root["/"] -->|packs| packs["/packs"]
  root -->|info| info["/info"]
  root -->|mission-types| mt["/mission-types"]
  packs -->|item| pack["/packs/{packId}"]
  packs -->|refresh| refresh["/packs/refresh"]
  packs -->|register| packs
  pack -->|collection| packs
  pack -->|artifacts| arts["/packs/{packId}/artifacts"]
  pack -->|graph| graph["/packs/{packId}/graph"]
  pack -->|presets| presets["/packs/{packId}/presets"]
  pack -->|resolution| res["/packs/{packId}/resolution"]
  pack -->|distribute| dist["/packs/{packId}/distributions"]
  arts -->|item| art["/packs/{packId}/artifacts/{kind}/{artifactId}"]
  arts -->|next| arts
  art -->|up| pack
```

A link appears only when its action is available. `register` and `distribute` are absent until
registration is built; `distribute` is absent on a pack that has nothing to distribute.

## Example responses

`GET /`:

```json
{
  "contractVersion": "1.0.0",
  "_links": {
    "self": { "href": "/api/v1/" },
    "packs": { "href": "/api/v1/packs" },
    "info": { "href": "/api/v1/info" },
    "mission-types": { "href": "/api/v1/mission-types" },
    "action-context": {
      "href": "/api/v1/mission-types/{missionType}/actions/{action}/context{?depth}",
      "templated": true
    }
  }
}
```

`GET /packs/01M4P01D5TWB5FKZGNNQG3943R`:

```json
{
  "packId": "01M4P01D5TWB5FKZGNNQG3943R",
  "name": "spec-kitty-internal",
  "level": "org",
  "packVersion": "1.0.0",
  "parentPack": null,
  "nodeCount": 35,
  "edgeCount": 16,
  "diagnostics": [],
  "_links": {
    "self": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R" },
    "collection": { "href": "/api/v1/packs" },
    "artifacts": {
      "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R/artifacts{?kind}",
      "templated": true
    },
    "graph": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R/graph" },
    "presets": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R/presets" }
  }
}
```

The fields other than `_links` are today's `PackSummary`; the values are illustrative. A pack without a `pack.yaml` has no
`packId`; it is listed without a `self` link.

## How each resource maps onto the service

| Resource | Application | Domain |
|---|---|---|
| `/`, `/info` | `ServiceInfoQuery` | none |
| `/packs`, `/packs/refresh` | `PackListingQuery` | `CharterOffering`, `CharterPack` |
| `/packs/{packId}` | a pack query over the same load | `CharterPack`, `PackDescriptor` |
| `/packs/{packId}/artifacts…` | an artifact query | the artifact repositories (R2) |
| `/packs/{packId}/graph` | a graph query | the pack's `DoctrineGraph` |
| `/packs/{packId}/presets`, `assets` | pack content queries | pack content (R4, R11) |
| `POST /packs`, `/distributions`, `/resolution` | registration use cases | `RegisteredPack`, `PackResolver` ([Pack registration and distribution](/architecture/pack-registration-distribution/)) |

Links are built in the REST adapter (`adapter-in-rest`), from the use-case answer and the request
URL. No link crosses into the application or domain ring (ADR-0007 decision 6).

## Relation to the registration design

The registration suites already assume this tree. They name `POST /packs`,
`POST /packs/{packId}/distributions` and `GET /packs/{packId}/resolution`, and a `RegisteredPack`
with `self`, `resolve`, `verify`, `distribute` and `source` links. Under ADR-0019 `resolve` and
`verify` are not separate links: resolution is a read-only projection (`resolution`), and
verification is part of distribution (ADR-0007 decision 7). `source` stays: it links the source
the pack was registered from.

## Proposed contract scenarios

These are proposed, not bound. They become contract scenarios when the contract changes.

```gherkin
Scenario: A client finds the packs from the root
  When I GET "/"
  Then the response status is 200
  And the response has a "packs" link

Scenario: A client follows a pack from the listing
  Given the response to GET "/packs"
  When I follow the "item" link of the pack "spec-kitty-internal"
  Then the response status is 200
  And the response field "name" is "spec-kitty-internal"
  And the response has a "self" link to the same URL

Scenario: A client lists one kind of artifact in a pack
  Given I followed the "artifacts" link of the built-in pack with kind "directive"
  Then the response status is 200
  And every item has kind "directive"
  And every item has a "self" link

Scenario: A link is absent when its action is unavailable
  When I GET the built-in pack
  Then the response has no "distribute" link
```
