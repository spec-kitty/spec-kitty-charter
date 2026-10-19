---
title: Glossary imported from the core repository
description: The docsite now has a Glossary section with the charter, governance and product-line terms, copied from the core glossary and linked across the docs.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/docsite
  - context/domain
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-13 — Glossary imported from the core repository

## What changed

- **A [Glossary](/glossary/) section** lists 71 terms in three contexts: Charter,
  Governance and Product lines. They are copied from the core repository's glossary,
  which stays the authority for them.
- **Terms are linked** where a page first uses them in a section, with the definition shown
  on hover.
- **`site/tools/import_core_glossary.py`** repeats the import after the core glossary
  changes. See [Building the docsite](/guides/docsite/).

Core defines `charter` in two contexts. Only the canonical entry, from the Charter context,
is imported, so that the term can be linked.
