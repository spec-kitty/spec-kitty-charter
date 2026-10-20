---
okf_version: "0.2"
title: Hypermedia (HATEOAS)
description: How charter API responses link to what a client can do next, through one closed Links component and documented relation names.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Guide
kind: Reference
related:
  - development/rest-resource-conventions
  - development/http-status-codes
  - adr/0007-rest-hateoas-contract-of-record
  - adr/0004-public-api-returns-generated-dtos
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a response or a relation, so every response links the same way.
  - profile: contract-consumer
    guidance_text: Read this to navigate the API from its root by following links instead of building URLs.
  - profile: core-maintainer
    guidance_text: Read this for how the seam should discover operations from links.
---

# Hypermedia (HATEOAS)

> **Status:** proposed. [ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) decision 4 chose a
> reusable `Links` component; [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) proposes its shape and the root resource. Neither is in
> the contract yet: no response carries `_links` today.

## The principles

Following [the core principles of HATEOAS](https://www.javathinking.com/blog/rest-api-discoverability-and-hateoas/#the-core-principles-of-hateoas):

- **One entry point.** A client knows one URL, the root `GET /`, and discovers everything else.
- **Links say what can be done next.** A response carries links to related resources and to the
  actions available on it.
- **The server owns URLs.** A client follows a link by its relation name; it never builds a path
  from parts. The server can move a resource without breaking a client that follows links.
- **Availability is a link.** A link is present only when its action is available to this client
  now. A missing `distribute` link means the pack cannot be distributed, without a separate flag.

## The shape in this contract

- **One component.** Every response schema has a `_links` property that references one reusable
  `Links` schema. Response DTOs stay closed (`additionalProperties: false`,
  [ADR-0004](/adr/0004-public-api-returns-generated-dtos/)); the `Links` component is the only
  place hypermedia lives.
- **HAL-style map.** `_links` maps a relation name to a link object:

  | Field | Required | Meaning |
  |---|---|---|
  | `href` | Yes | An absolute path relative to `/api/v1`, for example `/packs/01M4P01D5TWB5FKZGNNQG3943R` |
  | `templated` | No | `true` when `href` is an [RFC 6570](https://www.rfc-editor.org/rfc/rfc6570) template |
  | `method` | No | The method to use when it is not `GET`, for example `POST` for `refresh` |
  | `title` | No | A human-readable label; never parsed |

- **Example** (proposed), one pack:

  ```json
  {
    "packId": "01M4P01D5TWB5FKZGNNQG3943R",
    "name": "spec-kitty-internal",
    "level": "org",
    "_links": {
      "self": { "href": "/packs/01M4P01D5TWB5FKZGNNQG3943R" },
      "collection": { "href": "/packs" },
      "artifacts": { "href": "/packs/01M4P01D5TWB5FKZGNNQG3943R/artifacts{?kind}", "templated": true },
      "graph": { "href": "/packs/01M4P01D5TWB5FKZGNNQG3943R/graph" }
    }
  }
  ```

- **Templated links** carry parameters: the action context is
  `/mission-types/{missionType}/actions/{action}/context{?depth}`.
- **Paging** uses the shared `PageInfo` plus `first`, `prev` and `next` links; a `next` link is
  absent on the last page.

## Relation names

Use an [IANA registered relation](https://www.iana.org/assignments/link-relations/link-relations.xhtml)
when one fits: `self`, `collection`, `item`, `first`, `prev`, `next`, `last`, `related`,
`describedby`. Otherwise use a charter relation: lowercase, hyphenated, documented in this table
before it is used.

| Relation | Points to | Method | On | Status |
|---|---|---|---|---|
| `self` | The resource itself | `GET` | Every response | Proposed |
| `root` | `GET /` | `GET` | Every response | Proposed |
| `info` | `/info` | `GET` | Root | Proposed |
| `packs` | `/packs` | `GET` | Root | Proposed |
| `pack` | `/packs/{packId}` | `GET` | A pack listing item | Proposed |
| `register` | `/packs` | `POST` | Pack collection | Proposed |
| `refresh` | `/packs/refresh` | `POST` | Pack collection | Proposed |
| `artifacts` | `/packs/{packId}/artifacts{?kind}` | `GET` | A pack | Proposed |
| `artifact` | `/packs/{packId}/artifacts/{kind}/{artifactId}` | `GET` | An artifact listing item | Proposed |
| `graph` | `/packs/{packId}/graph` | `GET` | A pack | Proposed |
| `presets` | `/packs/{packId}/presets` | `GET` | A pack | Proposed |
| `assets` | `/packs/{packId}/assets` | `GET` | A pack | Proposed |
| `resolution` | `/packs/{packId}/resolution` | `GET` | A pack | Proposed ([ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) decision 7) |
| `distributions` | `/packs/{packId}/distributions` | `POST` | A pack | Proposed |
| `mission-types` | `/mission-types` | `GET` | Root | Proposed |
| `context` | `/mission-types/{missionType}/actions/{action}/context{?depth}` | `GET` | An action | Proposed |

## Anti-patterns

- **Clients building URLs** from ids instead of following a link.
- **Links only in headers** (`Link:`) for resources a client navigates; the body's `_links` is the
  contract.
- **Opening DTOs** (`additionalProperties: true`) to carry links.
- **Links that 404** or that advertise an action the client cannot take. A link is a promise.
- **An undocumented relation name.** Add it to the table above in the same change.
