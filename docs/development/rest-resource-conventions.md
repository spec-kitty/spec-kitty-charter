---
okf_version: "0.2"
title: REST resource conventions
description: How the charter API names resources and paths, uses methods and parameters, negotiates media types and versions the contract.
doc_status: draft
updated: 2026-10-20T00:00:00Z
type: Guide
kind: Reference
related:
  - development/hypermedia-hateoas
  - development/http-status-codes
  - api/service-contract
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0018-action-context-read-and-seam-access-surface
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add or rename a path, a parameter or a property.
  - profile: contract-consumer
    guidance_text: Read this for the naming and method rules every operation follows.
  - profile: core-maintainer
    guidance_text: Read this for what the seam can rely on in every path and response.
---

# REST resource conventions

These rules keep every operation of the charter contract shaped the same way. They follow
[REST resource naming](https://restfulapi.net/resource-naming/) and the decisions in
[ADR-0007](/adr/0007-rest-hateoas-contract-of-record/) (REST is the contract of record) and
[ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) (additive contract
sets). The contract is the source of record: `contracts/charter/openapi.yaml`.

## Resources are nouns, in four archetypes

A path names a resource, never an action. Every resource is one of four archetypes.

| Archetype | What it is | Name | Example in this contract |
|---|---|---|---|
| Document | One thing | Singular noun or an id | `/info`; `/packs/{packId}` (proposed) |
| Collection | A server-managed set | Plural noun | `/packs` |
| Store | A client-managed set | Plural noun | Not used |
| Controller | A procedure no method expresses | A verb, as the last segment | `POST /packs/refresh` |

- **Rule:** plural nouns for collections, singular nouns for documents, a verb only for a controller.
- **Controller exception:** `POST /packs/refresh` is the one controller. A new controller needs a
  reason no method fits; first try to express the action as a new resource, such as
  `POST /packs/{packId}/distributions`.
- **Anti-pattern:** `GET /getPacks`, `POST /packs/create`, `POST /packs/{packId}/delete`.

## Paths

- **Hierarchy with `/`.** A child resource sits under its parent:
  `/mission-types/{missionType}/actions/{action}/context`.
- **No trailing slash.** `/packs`, not `/packs/`.
- **Lowercase, with hyphens.** `/mission-types`, not `/missionTypes` or `/mission_types`.
- **No file extensions.** The media type is negotiated (see below), never `/packs.json`.
- **The `/api/v1` prefix** comes from the contract's `servers` entry. Paths in the contract and in
  links are written without it.

## Parameters

- **Path parameters identify.** They are stable ids: `{packId}` is the pack's minted ULID
  `pack_id`, `{missionType}` and `{action}` are graph node names. A path never identifies by a
  query parameter.
- **Query parameters shape.** They filter, sort, page or tune a read, never select the resource:
  `?depth=2` on the action context; `?level=org` on `/packs` (proposed).
- **Bounded and defaulted.** Every query parameter declares its range and default
  (`depth`: 0 to 2, default 2). A value outside the range is a 400 `Problem`, not a charter refusal.
- **Anti-pattern:** `/packs?id=…` for one pack; an unbounded integer parameter.

## Names in JSON

- **Properties and query parameters are camelCase:** `packId`, `packVersion`, `nodeCount`,
  `governanceFingerprint`, `missionType`.
- **Enum wire tokens are snake_case:** `pack_level_mismatch`, `action_not_found`. They never change
  once released; consumers branch on them.
- **Reserved hypermedia property:** `_links` (see
  [Hypermedia](/development/hypermedia-hateoas/)); nothing else starts with an underscore.

## Methods

| Method | Meaning | Safe | Idempotent | Used here |
|---|---|---|---|---|
| `GET` | Read a resource | Yes | Yes | Every read |
| `POST` | Create in a collection, or run a controller | No | No, unless documented | `POST /packs/refresh` (documented idempotent); register and distribute (proposed) |
| `PUT` | Replace a resource at a known URI | No | Yes | Not used |
| `PATCH` | Change part of a resource | No | No | Not used |
| `DELETE` | Remove a resource | No | Yes | Not used |

- A `GET` never changes state. A method the path does not have is a 405 with an `Allow` header.
- **Anti-pattern:** a `GET` that records a load, or a `POST` used to read.

## Media types

- **Success** is `application/json`; every non-2xx body is `application/problem+json`
  ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)).
- A request that accepts no representation an operation has is a 406. The problem is still served
  as `application/problem+json`, as RFC 9457 allows for errors.
- An operation that takes no body refuses a non-empty one with a 400 (`POST /packs/refresh`).

## Headers every response carries

- **`Charter-Contract-Version`, `Charter-Worktree` and `Charter-Source-Fingerprint`** are on every
  response, errors included ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)). The fingerprint is absent
  when no pack file was read or a pack file could not be read. The lint rule
  `charter-headers-on-every-response` fails a response that does not declare them.
- **Every operation needs the worktree token** (`Authorization: Bearer …`, ADR-0018). A missing or
  wrong token is a 401 before the request is routed.

## Versioning

- **The contract version is `info.version`.** There is no version in a path beyond `/api/v1`.
- **Response schemas are closed** (`additionalProperties: false`), so adding a response property,
  an enum value or a refusal code is a breaking change. Adding an operation is not: new
  capabilities arrive as additive contract sets (ADR-0009).
- **Reserve before release.** A field the next contract set will need goes in before the release
  that would otherwise freeze it out.
- **Anti-pattern:** adding a field to a released response "because clients ignore unknown
  fields". Generated clients with closed models do not.
