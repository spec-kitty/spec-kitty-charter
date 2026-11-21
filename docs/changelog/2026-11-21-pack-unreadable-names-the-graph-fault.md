---
okf_version: "0.2"
title: A composing read's pack_unreadable names the graph document, the reason and every violation
description: An offering or other composing read over a pack with a broken graph document answers which document, why, and the violations a registration of the pack lists.
doc_status: active
updated: 2026-11-21T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
audience:
  - profile: pack-author
    guidance_text: Read this if the offering answered 500 pack_unreadable for your pack and you need to find the broken graph document.
---

# 2026-11-21 — A composing read's pack_unreadable names the graph document, the reason and every violation

**A composing read says what to fix** (#617, #628). Before, `GET /offering` over a pack with an unknown relation in a
graph document (spec-kitty-open-packs' foundation pack with `in_tension_with`) answered only
`a graph document of foundation cannot be read`. A registration of the same pack named the document and the reason.

## What changed

- **The detail names the first document and its reason**, in the words the stored edge read and a removal already
  used, for example:

  ```text
  a graph document of foundation cannot be read: foundation/directive.graph.yaml: unknown relation 'in_tension_with'
  ```

- **The problem lists `violations`**, one per fault of the pack's graph documents: the pack-relative document as the
  `pointer`, the `code` (for example `drg_schema_error`) and the `detail`, exactly as a registration of the pack lists
  them, in its order. Tests pin that the two agree over every planted pack and over two broken documents.
- **One way to build it.** The edge read, the composing read and a removal refuse through the domain's one mapping of a
  `Rationale` to a refusal (`PackRefusalMapping`,
  [ADR-0082](/adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface/)); no fixed sentence is left.
- **Unchanged:** the code `pack_unreadable` and the status `500` of the composing reads.
