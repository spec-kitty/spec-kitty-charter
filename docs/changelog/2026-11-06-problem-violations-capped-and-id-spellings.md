---
okf_version: "0.2"
title: "A problem lists at most 100 violations, and an activation id may be spelled several ways"
description: "A refusal lists at most 100 violations and counts the rest in detail. An activation may spell an id as its URN or in another case; the stored document holds the declared id."
doc_status: active
updated: 2026-11-06T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0020-offering-and-activations
  - adr/0063-pack-mutability-policy-file
  - conformance/divergence-register
audience:
  - profile: core-maintainer
    guidance_text: Read this before relying on a problem body listing every violation, or on how the service spells an artifact id.
---

# 2026-11-06 — A problem lists at most 100 violations, and an activation id may be spelled several ways

Three refinements to the activation register, found in review before it shipped:

- **At most 100 violations.** `Problem.violations` declares `maxItems: 100`. A refusal with more lists the first 100,
  and `detail` ends with the number left out, so one large body can no longer produce a response many times its size.
- **Id spellings.** An activation may spell an artifact id as the declared id, as its full URN (`directive:DIRECTIVE_001`)
  or in another case, as [ADR-0020](/adr/0020-offering-and-activations/) D4 rules (operator ruling of 2026-11-06). The
  stored document holds the declared id, so every spelling gives one document and one revision. A spelling that
  matches two declared ids differing only by case is `422 activation_invalid` (`ambiguous_artifact`), and so is an
  unknown id or a URN of another kind than the list's.
- **A pack that is not configured is `pack_not_found`** in the mutability gate ([ADR-0063](/adr/0063-pack-mutability-policy-file/)),
  as on every other read. A stored activation that names a pack no longer configured is still a `500 pack_unreadable`
  on its closure read: that is the service's own state, not the request.

`PUT` and `DELETE` with `If-Match` on an activation that does not exist answer `412`; the contract now says so.
