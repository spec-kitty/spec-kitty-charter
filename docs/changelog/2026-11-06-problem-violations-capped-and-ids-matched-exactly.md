---
okf_version: "0.2"
title: "A problem lists at most 100 violations, and activation ids are matched as declared"
description: "A refusal lists at most 100 violations and counts the rest in detail. An activation names artifacts by their declared id; other spellings are not normalized yet."
doc_status: active
updated: 2026-11-06T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0020-offering-and-activations
  - adr/0062-pack-mutability-policy-file
  - conformance/divergence-register
audience:
  - profile: core-maintainer
    guidance_text: Read this before relying on a problem body listing every violation, or on the service normalizing an artifact id.
---

# 2026-11-06 — A problem lists at most 100 violations, and activation ids are matched as declared

Three refinements to the activation register, found in review before it shipped:

- **At most 100 violations.** `Problem.violations` declares `maxItems: 100`. A refusal with more lists the first 100,
  and `detail` ends with the number left out, so one large body can no longer produce a response many times its size.
- **Ids matched as declared.** An activation names each artifact by its declared id. Another spelling of the same
  artifact, which [ADR-0020](/adr/0020-offering-and-activations/) D4 allows, is not normalized yet and is refused as an
  unknown id (DIV-208 in the [divergence register](/conformance/divergence-register/)).
- **A pack that is not configured is `pack_not_found`** in the mutability gate ([ADR-0062](/adr/0062-pack-mutability-policy-file/)),
  as on every other read. A stored activation that names a pack no longer configured is still a `500 pack_unreadable`
  on its closure read: that is the service's own state, not the request.

`PUT` and `DELETE` with `If-Match` on an activation that does not exist answer `412`; the contract now says so.
