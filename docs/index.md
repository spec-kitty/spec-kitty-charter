---
title: spec-kitty-charter documentation
description: Documentation home for the charter offering module in the Brain Kitty product line, providing charter-pack retrieval to Build Kitty surfaces.
doc_status: active
updated: '2026-10-13'
related:
- docs/architecture/index.md
- docs/guides/index.md
- docs/development/index.md
- docs/api/index.md
- docs/adr/index.md
---

# spec-kitty-charter documentation

This is the documentation root for **spec-kitty-charter**, the charter offering
module in the Brain Kitty product line. The module retrieves charter packs and
serves them to the Build Kitty surfaces that consume governance, through its
public API `charter.offering.api`.

> **Status: early extraction — under active redesign.** The charter is being
> extracted and re-designed out of the Spec Kitty core CLI into this repository.
> Pages here describe the module's intent and shape; detail is filled in as the
> extraction progresses. See the core ADR `2026-10-12-2`
> (charter-extraction-to-its-own-repository) for the governing decision.

## Browse by topic

- [Architecture](architecture/index.md) — why the module exists, its boundary,
  and C4-style container notes.
- [Guides](guides/index.md) — task-oriented how-tos for consumers of the module.
- [Development](development/index.md) — contributor and maintainer how-tos, plus
  the reference policy.
- [API reference](api/index.md) — the `charter.offering.api` public surface.
- [Decision records](adr/index.md) — architecture decision records for this
  module.
