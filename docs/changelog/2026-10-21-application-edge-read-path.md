---
title: Tactic read path, and the application layer becomes an edge
description: The service serves a pack's tactics by identity on one Pack model, the application layer is now an outer edge ring, and the pack-listing and action-context endpoints are gone.
doc_status: active
updated: 2026-10-21T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0006-onion-vertical-slices-archunit
  - architecture/architectural-patterns-applied
  - architecture/hexagonal-service
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new layering rule and where the read path lives.
  - profile: core-maintainer
    guidance_text: Read this for the contract change and the module rename.
---

# 2026-10-21 — Tactic read path, and the application layer becomes an edge

The service gains its first real read path: a pack's tactics by identity, on the single `Pack`
model. The application layer is now an outer edge ring, not a pure one.

## What changed

- **New endpoints.** `GET /packs/{packId}/tactics` and `GET /packs/{packId}/tactics/{tacticId}`.
- **Removed endpoints (contract change).** The pack-listing and action-context endpoints are gone,
  along with the `CharterPack` scaffolding behind them.
- **Layering.** Only `domain` and `kernel` are pure. The new `APPLICATION_IS_EDGE` rule replaces
  `APPLICATION_IS_PLAIN_JAVA`. See the [ADR-0006](/adr/0006-onion-vertical-slices-archunit/) revision.
- **Rename.** `bootstrap` is now `deployable`, with a `@Configuration` per layer.

## Who is affected

Clients of the removed endpoints must stop calling them. Contributors use the new module name.
