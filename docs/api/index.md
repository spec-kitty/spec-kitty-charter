---
title: API reference
description: Reference placeholder for charter.offering.api, the single public surface through which Build Kitty surfaces consume the charter offering module.
doc_status: draft
updated: '2026-10-13'
related:
- docs/index.md
- docs/architecture/index.md
---

# API reference

The charter offering module exposes a single public surface,
`charter.offering.api`. Every consumer — each Build Kitty surface that retrieves
charter packs — reaches the module only through this surface, never through its
internals.

> **To be written.** This section will document the `charter.offering.api`
> contract: the charter-pack retrieval entry points, their inputs and outputs,
> and the compatibility guarantees the module makes as an independently
> versioned contract package. The reference is deliberately left as a
> placeholder until the extracted public surface is settled; see
> [Architecture](../architecture/index.md) for the boundary this API enforces.
