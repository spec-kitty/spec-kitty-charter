---
okf_version: "0.2"
title: REST resource conventions
description: How the charter API names resources and paths, uses methods and parameters, negotiates media types and versions the contract.
doc_status: draft
updated: 2026-10-28T00:00:00Z
type: Guide
kind: Reference
related:
  - development/hypermedia-hateoas
  - development/http-status-codes
  - api/service-contract
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0018-action-context-read-and-seam-access-surface
  - adr/0020-offering-and-activations
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
sets). The contract is the source of record: `contracts/charter/openapi.yaml`. Rules marked
*proposed* come from [ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/) and
[ADR-0020](/adr/0020-offering-and-activations/) and are not in the contract yet; *reserved* ones are the activation
set, declared in the contract (#125) and answered `501` until built.

## Resources are nouns, in four archetypes

A path names a resource, never an action. Every resource is one of four archetypes.

| Archetype | What it is | Name | Example in this contract |
|---|---|---|---|
| Document | One thing | Singular noun or an id | `/info`; `/packs/{packId}` (proposed) |
| Collection | A server-managed set; `POST` adds a member and the server names it | Plural noun | `/packs`; `POST /activations` (reserved, #125) |
| Store | A client-managed set; `PUT` puts a member at an id the client chose | Plural noun | `/activations/{activationId}` (reserved, #125) |
| Controller | A procedure no method expresses | A verb, as the last segment | `POST /packs/refresh` |

- **Rule:** plural nouns for collections, singular nouns for documents, a verb only for a controller.
- **Controller exception:** `POST /packs/refresh` is the one controller. A new controller needs a
  reason no method fits; first try to express the action as a new resource, such as
  `POST /packs/{packId}/distributions`.
- **Anti-pattern:** `GET /getPacks`, `POST /packs/create`, `POST /packs/{packId}/delete`.

## Paths

- **Hierarchy with `/`.** A child resource sits under its parent:
  `/offering/mission-types/{missionType}/actions/{action}/context` (proposed; built today at
  `/mission-types/…`).
- **No trailing slash.** `/packs`, not `/packs/`.
- **Lowercase, with hyphens.** `mission-types`, not `missionTypes` or `mission_types`.
- **No file extensions.** The media type is negotiated (see below), never `/packs.json`.
- **The `/api/v1` prefix** comes from the contract's `servers` entry. Paths in the contract are
  written without it. A link's `href` includes it, so the link resolves as written
  ([Hypermedia](/development/hypermedia-hateoas/#the-shape-in-this-contract)).

## Parameters

- **Path parameters identify.** They are stable ids: `{packId}` is the pack's minted ULID
  `pack_id`, `{missionType}` and `{action}` are graph node names, and `{activationId}` (reserved, #125)
  is an opaque id the service never parses. A path never identifies by a query parameter.
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
| `POST` | Create in a collection, or run a controller | No | No, unless documented | `POST /packs/refresh` (documented idempotent); register and distribute (proposed); create an activation (reserved, #125) |
| `PUT` | Create or replace a resource at a known URI | No | Yes | Store an activation, and its selections, exclusions and presets (reserved, #125) |
| `PATCH` | Change part of a resource | No | No | Not used. Incremental changes are sub-resource `PUT` and `DELETE` |
| `DELETE` | Remove a resource | No | Yes, in effect: a second call is a 404 | Remove an activation, a selection or an exclusion (reserved, #125) |

- A `GET` never changes state.
- A method the path does not have is a 405 with an `Allow` header. That is the service's
  behavior; the contract does not declare it on each path.
- An operation the contract declares but the service has not built yet is a `501`, never a 405
  (see [Versioning](#versioning)).
- **Anti-pattern:** a `GET` that records a load, or a `POST` used to read.

## Media types

- **Success** is `application/json`; every non-2xx body is `application/problem+json`
  ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)).
- A request that accepts no representation an operation has is a 406. The problem is still served
  as `application/problem+json`, as RFC 9457 allows for errors.
- An operation that takes no body refuses a non-empty one with a 400 (`POST /packs/refresh`).
- An operation that takes a body accepts `application/json` only; another `Content-Type` is a 415
  (proposed, with the first write).

## Headers every response carries

- **`Charter-Contract-Version`, `Charter-Worktree` and `Charter-Source-Fingerprint`** are declared
  on every response, errors included ([ADR-0011](/adr/0011-seam-runtime-fail-closed-daemon-and-freshness/),
  [ADR-0018](/adr/0018-action-context-read-and-seam-access-surface/)). The lint rule
  `charter-headers-on-every-response` fails a response that does not declare them.
  - `Charter-Source-Fingerprint` is sent only on a `200`: never on a refusal or a failure.
  - The one exception is the servlet container's own 400s (a bad path encoding, an oversized
    header, a missing `Host`), which answer before the service sees the request and carry no
    `Charter-*` header.
- **Every operation needs the worktree token** (`Authorization: Bearer …`, ADR-0018). A browser
  request (an `Origin` or `Sec-Fetch-*` header, or a `Host` other than loopback) is a 403, checked
  first; then a missing or wrong token is a 401. Both answer before the request is routed.
- **Proposed hosted-first reshape** (ADR-0020 D12, before 1.0.0): the token becomes an
  issuer-backed bearer with scopes (`offering:read`, `activation:read`, `activation:write`);
  the per-worktree token and the browser refusal move to a local deployment profile;
  `Charter-Worktree` becomes `Charter-Instance`; the fingerprint covers pack files only.

## Conditional requests

`getOpenApiDocument` (`GET /openapi.yaml`) is the one conditional read built into the contract: its
answer carries a strong `ETag`, the SHA-256 digest of the bundled contract, and an `If-None-Match`
that is `*` or matches it is a 304 without a body.

Proposed: the activation resources (ADR-0020) use HTTP preconditions so two writers cannot lose each other's
changes:

- **`ETag`** on every activation answer: the document's `revision`, a strong validator.
- **`If-Match`** on a write: apply only if the document is still at that revision; otherwise 412.
  Required on sub-resource writes, so a missing one is a 428.
- **`If-None-Match: *`** on `PUT`: create only; an existing document is a 412.

## Versioning

- **The contract version is `info.version`.** There is no version in a path beyond `/api/v1`.
- **0.x first** (proposed, [ADR-0022](/adr/0022-contract-versions-start-at-0-x/)): the contract is
  published as `0.y.z` while core's integration shapes it. A 0.x minor may break and says so in a
  `### Breaking` CHANGELOG subsection; a patch may not. The rules below hold within a 0.x minor,
  and across every 1.x release once 1.0.0 is minted.
- **Response schemas are closed** (`additionalProperties: false`), so adding a response property,
  a response enum value or a refusal code is a breaking change. Adding an operation is not: new
  capabilities arrive as additive contract sets (ADR-0009).
- **Requests break the other way.** A new value in a request enum is compatible; a new required
  request body, parameter or header on an existing operation is breaking, and so is a new success
  status.
- **Reserve before release.** A field the next contract set will need goes in before the release
  that would otherwise freeze it out.
- **A reserved operation is declared in full** (proposed, ADR-0020 D8): request body, success
  responses, refusals and headers, plus a `501` and an `x-reserved` marker. The service answers
  `501` until it is built, and no link points to it.
- **Anti-pattern:** adding a field to a released response "because clients ignore unknown
  fields". Generated clients with closed models do not.
