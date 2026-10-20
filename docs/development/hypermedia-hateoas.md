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
  - adr/0019-packs-resource-model-and-hypermedia
  - adr/0020-offering-and-activations
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
> reusable `Links` component; [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) proposes
> its shape and the root resource, and [ADR-0020](/adr/0020-offering-and-activations/) the
> offering and activation relations. None is in the contract yet: no response carries `_links`
> today.

## The principles

Following the hypermedia constraint of REST
([Fielding, section 5.1.5](https://ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm#sec_5_1_5))
and [HATEOAS](https://restfulapi.net/hateoas/):

- **One entry point.** A client knows one URL, the root `GET /api/v1/`, and discovers everything
  else.
- **Links say what can be done next.** A response carries links to related resources and to the
  actions available on it.
- **The server owns URLs.** A client follows a link by its relation name; it never builds a path
  from parts. The server can move a resource without breaking a client that follows links.
- **Availability is a link.** A link is present only when its action is available to this client
  now. A missing `distribute` link means the pack cannot be distributed, without a separate flag.

## The shape in this contract

- **One component.** Every success schema has a required `_links` property that references one
  reusable `Links` schema. `Problem` and `CharterRefusal` carry no links. Response DTOs stay
  closed (`additionalProperties: false`, [ADR-0004](/adr/0004-public-api-returns-generated-dtos/));
  the `Links` component is the only place hypermedia lives.
- **A map with open keys and a closed link object.** `_links` maps a relation name to a link
  object. The keys are open, so a new relation is additive; the link object is closed:

  | Field | Required | Meaning |
  |---|---|---|
  | `href` | Yes | An absolute path that includes the server prefix, for example `/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R`, or an RFC 6570 template of one |
  | `templated` | No | `true` when `href` is an [RFC 6570](https://www.rfc-editor.org/rfc/rfc6570) template |
  | `method` | No | The method to use when it is not `GET`, for example `POST` for `refresh` |
  | `title` | No | A human-readable label; never parsed |

- **How the open keys are checked.** The closure checker reports a map as an open response
  position; `_links` is its one exemption, pinned by a fixture. A lint rule checks the relation
  keys the contract uses against the table below. A client ignores a relation it does not know.
- **Example** (proposed), one pack:

  ```json
  {
    "packId": "01M4P01D5TWB5FKZGNNQG3943R",
    "name": "spec-kitty-internal",
    "level": "org",
    "_links": {
      "self": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R" },
      "collection": { "href": "/api/v1/packs" },
      "artifacts": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R/artifacts{?kind}", "templated": true },
      "graph": { "href": "/api/v1/packs/01M4P01D5TWB5FKZGNNQG3943R/graph" }
    }
  }
  ```

- **Templated links** carry parameters: an action's `context` link is
  `/api/v1/offering/mission-types/software-dev/actions/implement/context{?depth}`.
- **One link, one action.** A link has one `method`. A resource that takes `PUT` and `DELETE` gets
  two relations, such as `exclude` and `include`.
- **Paging** uses the shared `PageInfo` plus `first`, `prev` and `next` links; a `next` link is
  absent on the last page.

## Relation names

Use an [IANA registered relation](https://www.iana.org/assignments/link-relations/link-relations.xhtml)
when one fits: `self`, `collection`, `item`, `up`, `first`, `prev`, `next`, `last`, `edit`.
Otherwise use a charter relation: lowercase, hyphenated, documented in this table before it is
used.

| Relation | Points to | Method | On | Status |
|---|---|---|---|---|
| `self` | The resource itself | `GET` | Every success response | Proposed |
| `collection` | The collection the resource belongs to | `GET` | A pack | Proposed |
| `item` | One member of a collection | `GET` | A listing item: pack, artifact | Proposed |
| `up` | The parent resource | `GET` | An artifact (its pack) | Proposed |
| `info` | `/api/v1/info` | `GET` | Root | Proposed |
| `packs` | `/api/v1/packs` | `GET` | Root | Proposed |
| `offering` | `/api/v1/offering` | `GET` | Root; a pack in force; an activation | Proposed ([ADR-0020](/adr/0020-offering-and-activations/)) |
| `register` | `/api/v1/packs` | `POST` | Pack collection, once registration is built | Proposed |
| `refresh` | `/api/v1/packs/refresh` | `POST` | Pack collection | Proposed |
| `artifacts` | `…/artifacts{?kind}` | `GET` | A pack; the offering; an activation (`{?kind,state}`) | Proposed |
| `graph` | `…/graph` | `GET` | A pack; the offering; an activation | Proposed |
| `presets` | `…/presets` | `GET` | A pack; the offering | Proposed |
| `assets` | `/api/v1/packs/{packId}/assets` | `GET` | A pack | Proposed |
| `resolution` | `/api/v1/packs/{packId}/resolution` | `GET` | A pack | Proposed ([ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) decision 7) |
| `distribute` | `/api/v1/packs/{packId}/distributions` | `POST` | A pack that can be distributed | Proposed |
| `kinds` | `/api/v1/offering/kinds` | `GET` | The offering | Proposed |
| `mission-types` | `/api/v1/offering/mission-types` | `GET` | The offering | Proposed |
| `context` | `/api/v1/offering/mission-types/{missionType}/actions/{action}/context{?depth}` | `GET` | An action | Proposed |
| `activations` | `/api/v1/offering/activations` | `POST` | The offering, once activation writes are built | Proposed |
| `edit` | The activation | `PUT` | An activation, once writes are built | Proposed |
| `delete` | The activation | `DELETE` | An activation, once writes are built | Proposed |
| `skipped` | `…/activations/{activationId}/skipped` | `GET` | An activation | Proposed |
| `effective-context` | `…/activations/{activationId}/mission-types/{missionType}/actions/{action}/context{?depth}` | `GET` | An activation | Proposed |
| `select`, `unselect` | `…/selections/{kind}/{artifactId}` | `PUT`, `DELETE` | An effective artifact | Proposed |
| `exclude`, `include` | `…/exclusions/{kind}/{artifactId}` | `PUT`, `DELETE` | An effective artifact; `exclude` is absent on a required or locked one, `include` present only on an excluded one | Proposed |
| `apply-preset` | `…/presets/{presetId}` | `PUT` | An offered preset, seen from an activation | Proposed |

## Anti-patterns

- **Clients building URLs** from ids instead of following a link.
- **Links only in headers** (`Link:`) for resources a client navigates; the body's `_links` is the
  contract.
- **Opening DTOs** (`additionalProperties: true`) to carry links.
- **Links that 404, or that point to a reserved operation.** A link is a promise; a reserved
  operation answers `501` and is never linked.
- **A link that advertises an action the client cannot take.**
- **An undocumented relation name.** Add it to the table above in the same change.
