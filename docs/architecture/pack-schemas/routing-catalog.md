---
title: "Pack schema: Routing catalog"
description: "The Routing catalog pack schema: its properties, required fields and a rendered diagram, for authors of pack files."
doc_status: draft
updated: 2026-11-18T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/routing-catalog.schema.json
audience:
  - profile: pack-author
    guidance_text: Read this when you ship a routing catalog in a pack.
---

# Pack schema: Routing catalog

This page describes a routing catalog, the task types, models, routing policy and sources a pack ships at
`routing_catalogs/<id>.routing-catalog.yaml` ([ADR-0080](/adr/0080-the-routing-catalog-kind-and-the-model-recommendation/)).
Read it when you ship a routing catalog in a pack; the diagram, the property list and the link to the schema file are
generated from the schema itself. How to compose and select one is in
[Compose packs](/guides/compose-packs/#ship-a-routing-catalog).
