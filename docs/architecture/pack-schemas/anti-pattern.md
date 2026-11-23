---
title: "Pack schema: Anti-pattern"
description: "The Anti-pattern pack schema: its properties, required fields and a rendered diagram, for authors of pack files."
doc_status: draft
updated: 2026-11-21T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/anti-pattern.schema.json
audience:
  - profile: pack-author
    guidance_text: Read this when you ship an anti-pattern in a pack.
---

# Pack schema: Anti-pattern

This page describes an anti-pattern, a practice that guidance rejects, which a pack ships as its own file at
`anti_patterns/<id>.anti-pattern.yaml`
([ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) decision 8, as amended by
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 30). Every file states its
`severity` (`smell`, `severe` or `breaking`; there is no default) and a `bad_example`. The guidance that rejects it
does so with a `rejects` edge to its graph node, `anti_pattern:<id>`, in the pack's graph documents
([ADR-0064](/adr/0064-graph-documents-are-the-only-source-of-relationships/)). Read it when you ship an anti-pattern in a
pack; the diagram, the property list and the link to the schema file are generated from the schema itself.
