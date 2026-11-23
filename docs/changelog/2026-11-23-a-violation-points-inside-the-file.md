---
okf_version: "0.2"
title: A pack-file violation points at the edge or field inside the file
description: A violation of a registration, a per-file write or a pack_unreadable read names the place inside the pack file, file#/json/pointer, when the fault has one.
doc_status: active
updated: 2026-11-23T00:00:00Z
type: Changelog
kind: Changelog
related:
  - changelog/2026-11-21-pack-unreadable-names-the-graph-fault
audience:
  - profile: pack-author
    guidance_text: Read this if a refusal of your pack lists violations and you want to find the edge or field at fault.
---

# 2026-11-23 — A pack-file violation points at the edge or field inside the file

**A violation says where in the file to look** (operator ruling 2026-11-23, follow-up of #617). Before, a violation of a
pack file named the file alone. A graph document with forty edges and one bad one left the author to find the edge.

## What changed

- **The `pointer` is a URI reference**: the pack-relative file, then `#` and an RFC 6901 JSON Pointer into the file,
  when the place inside it is known, for example:

  ```text
  drg/directive.graph.yaml#/edges/3
  tactics/a.tactic.yaml#/schema_version
  ```

- **A fault at an edge points at the edge**, `/edges/<index>` in the document's `edges` list: a malformed pack
  qualifier, an edge that joins an artifact to itself, an endpoint the relation's table or the source rule refuses. A
  schema fault keeps the place the schema names, such as `/edges/0/relation` for an unknown relation.
- **With no place inside the file**, the pointer stays the bare path, or `pack`, as before. A graph document that is
  not YAML is one example.
- **Every surface that lists pack-file violations** carries it: a registration's `422 validation_failed`, a per-file
  write or removal whose pack would not revalidate, and the `500 pack_unreadable` of a read over a broken graph
  document. A read and a registration of the same pack still list the same violations.
- **One place builds it**: `PackRefusalMapping` joins the file and the in-file pointer. The registration's problem
  detail names the first violation at its pointer.
- **Unchanged**: the codes, the statuses and each violation's `detail`.
