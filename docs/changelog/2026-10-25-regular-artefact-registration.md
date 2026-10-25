---
okf_version: "0.2"
title: Directive, styleguide, paradigm and toolguide can be read and registered
description: The charter service reads all five regular pack artifacts and accepts content through an intent-named registration surface, with relationships as first-class values.
doc_status: active
updated: 2026-10-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0026-write-surface-is-functional-registration
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the registration surface, the relationship value object and the write path.
  - profile: core-maintainer
    guidance_text: Read this for the new read and registration routes, their refusal codes and the relationship shard.
---

# 2026-10-25 — Directive, styleguide, paradigm and toolguide can be read and registered

**Packs are no longer read-only past tactics.** Before, a client could read only a
pack's tactics, and nothing could be written. Now directive, styleguide, paradigm and
the new toolguide kind are readable, and each can be registered through
`POST`/`PUT /packs/{id}/<kind>`. A standalone relationship is registered through
`PUT /packs/{id}/relationships`.

Registration is functional, not CRUD ([ADR-0026](/adr/0026-write-surface-is-functional-registration/)):
there is no `DELETE` or `PATCH`. Each write loads the pack, revalidates the whole
aggregate and commits atomically, and its `ETag` is the pack revision. A refused write
changes no stored file, and a successful one writes only the registered file, so authored
comments in the rest of the pack survive. Writes to one pack run one at a time. A missing artifact in an existing pack answers its own
`<kind>_not_found` code.

Relationships are domain values with one relationship per unordered pair of endpoints
([ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/),
[ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/)).
Registered ones persist to a dedicated `registered.graph.yaml` shard. A read never
rewrites a source shard. No HTTP route reads the registered relationships back yet.
