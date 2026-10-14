---
title: Charter logical model, descriptors and designed API
description: The docsite now describes the charter's logical model, every descriptor file, schema diagrams and the designed, not yet implemented, API.
doc_status: active
updated: 2026-10-14
type: Changelog
kind: Changelog
related:
  - architecture/logical-model
  - architecture/descriptors
  - api/python-package
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-14 — Charter logical model, descriptors and designed API

## What changed

- **[Logical model](/architecture/logical-model/)** describes the charter's concepts: packs
  and levels, artifact kinds, the Doctrine Reference Graph, activation and resolution.
- **[Descriptors](/architecture/descriptors/)** lists every descriptor file a Charter Pack
  ships, with its fields.
- **[Descriptor schemas](/architecture/descriptor-schemas/)** adds PlantUML diagrams of those
  schemas, derived from core's schemas and models.
- **[API](/api/)** describes the designed Python package surface and service contract.
  Neither exists in this repository yet; the pages say so. The operator ruled that the API
  returns DTOs generated from the OpenAPI contract, per client language
  ([ADR-0004](/adr/0004-public-api-returns-generated-dtos/)).

Everything here is derived from the core repository, which stays the authority until the
charter code moves.
