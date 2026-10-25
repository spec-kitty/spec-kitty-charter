---
title: "ADR-0026: The write surface is functional registration of packs, artefacts and relationships, not CRUD over storage"
description: Why the API's only writes are functional registration of a pack, an artefact or a relationship edge over the Pack aggregate — not CRUD that exposes storage DSOs.
doc_status: active
updated: 2026-10-25T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0005-service-registers-and-distributes-packs
  - adr/0004-public-api-returns-generated-dtos
  - adr/0024-doctrine-artifact-family-contract
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0020-offering-and-activations
  - adr/0012-pack-mutability-policy
audience:
  - profile: core-maintainer
    guidance_text: Read this for what the charter API lets a client write and why it is registration, not CRUD.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a write endpoint — the three registration operations, how they gate on the Pack aggregate, and what the contract must not expose.
---

# ADR-0026: The write surface is functional registration of packs, artefacts and relationships, not CRUD over storage

## Status

**Proposed** — 2026-10-25. Decider: Stijn Dejongh (operator). Pending ratification; flip to Accepted once confirmed.

Extends [ADR-0005](/adr/0005-service-registers-and-distributes-packs/) (pack registration) to artefact and relationship granularity, and records a deliberate departure from "content enters only as whole packs."

## Context

The contract is read-only today (`GET /info`, `GET /packs/{id}/tactics[/{id}]`;
`info.summary: "Read-only answers of the charter of one project."`). Two write surfaces are already
designed but reserved: pack registration (`POST /packs` by source,
[ADR-0005](/adr/0005-service-registers-and-distributes-packs/)/[ADR-0019](/adr/0019-packs-resource-model-and-hypermedia/))
and activation writes of a client's choices ([ADR-0020](/adr/0020-offering-and-activations/)). The
repository ports declare `save()`, but every caller is a test: `save()`+`load()` exist to prove the
round-trip fidelity of the per-kind DSOs ([ADR-0024](/adr/0024-doctrine-artifact-family-contract/)),
not to serve a write API.

The regular artefacts must be writable from outside, but a per-entity CRUD API would expose the
storage DSOs and persistence shapes through the contract, against
[ADR-0004](/adr/0004-public-api-returns-generated-dtos/) and the service's intent-revealing,
functional design. A strict reading of ADR-0005 would allow content in only as a whole re-registered
pack — too coarse to author a single artefact or add one relationship edge. The question is what the
API writes and how it is framed.

## Decision

1. **The API exposes only functional, intent-named registration — never generic CRUD.** There are
   three write operations:
   1. **Register a pack** — `POST /packs`, content by source, unchanged from ADR-0005.
   2. **Register an artefact** — `POST /packs/{packId}/{kindPlural}` (id optional; the service
      returns `Location`) and `PUT /packs/{packId}/{kindPlural}/{id}` (id in the path; idempotent
      create-or-replace). The body carries the artefact's fields **and its relationships**, as
      generated contract DTOs ([ADR-0004](/adr/0004-public-api-returns-generated-dtos/)); the storage
      DSO is never exposed. One family per kind: tactics, directives, styleguides, paradigms,
      toolguides (procedures pending [#150](https://github.com/spec-kitty/spec-kitty-charter/issues/150)).
   3. **Register a relationship** — `PUT /packs/{packId}/relationships`, idempotent on the unordered
      `{from, to}` pair ([ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/));
      body = `from`, `to`, `type`, `rationale` (directionality derives from the type). A relationship
      may also ride along inside an artefact registration body. The `from` endpoint must resolve
      within the pack; `to` may be external (DRG endpoints need not be local).
2. **No `DELETE`, `PATCH` or generic CRUD.** Removing an artefact from force is **deactivation**, an
   activation concern ([ADR-0013](/adr/0013-persistent-deactivation/)/[ADR-0020](/adr/0020-offering-and-activations/)),
   not a storage mutation.
3. **Every write goes through the domain and the whole aggregate.** A registration application
   service validates with the per-entity `ModularRuleset` invariants and the Pack aggregate
   invariants by **load → mutate → revalidate the whole `Pack`**, so "belongs-to-pack" and
   "no-duplicate-id" gate the write, then persists through the existing `save()` ports +
   `SerializationStrategy` + DSO. The current direct single-child `save()`, which bypasses aggregate
   revalidation, is not the write path.
4. **Refusals reuse the `CharterRefusal`/Problem machinery**; `RefusalCode`/`RefusalHandler` gain the
   write codes they lack (validation `422`, conflict/policy `409`, precondition `412`). The pack
   mutability policy ([ADR-0012](/adr/0012-pack-mutability-policy/): open/extendable/locked) gates a
   write; a write beyond the owning pack's level is refused (`governance_policy_violation`).
5. **Concurrency is optimistic**, reusing ADR-0020's `ETag`/`If-Match` model — distinct from the read
   path's `ConsistentRead`/`Charter-Source-Fingerprint` freshness check.

## Consequences

### Positive

- The contract stays intent-revealing and DTO-bounded; storage shapes never leak through the API.
- Registration is one idiom across packs, artefacts and relationships; relationships become a
  first-class API resource mirroring their first-class domain status (ADR-0025).
- Aggregate invariants finally gate writes, closing the single-child `save()` bypass.

### Negative / costs

- A deliberate departure from "content enters only as whole packs" (ADR-0005 D1, ADR-0020 D11),
  recorded here so it is a decision and not drift.
- The contract must grow `POST`/`PUT` operations from a GET-only base (additive contract set,
  [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/)), and `RefusalCode`/
  `RefusalHandler` must grow write/validation/conflict/precondition codes they do not have today.

### Neutral

- Artefact-body authoring validation (schema) remains upstream in pack tooling (ADR-0017); the
  service validates the assembled aggregate, not the authoring ergonomics.

## Alternatives considered

- **Whole-pack registration only** (strict ADR-0005). Rejected: too coarse — it forces
  re-registering an entire pack to add one tactic or one edge.
- **Generic per-entity CRUD.** Rejected: leaks storage shapes, is not intent-revealing, and invites
  `DELETE`/`PATCH` semantics the service does not want (removal is deactivation).
- **Activation writes as the only write surface** (ADR-0020). Rejected: activation writes *choices*,
  not content; authoring content needs its own content-write path.
