---
title: "Pack schema: Prompt fragment"
description: "The Prompt fragment pack schema: its properties, required fields and a rendered diagram, for authors of pack files."
doc_status: draft
updated: 2026-11-08T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/prompt-fragment.schema.json
audience:
  - profile: pack-author
    guidance_text: Read this when you write the Markdown companion of a step enhancement's prompt fragment.
---

# Pack schema: Prompt fragment

This page describes the parsed Markdown structure of `<id>.prompt-fragment.md`, the companion a [step enhancement](/architecture/pack-schemas/step-enhancement/) names in `prompt_fragments` ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)). Read it when you write one; the diagram, the property list and the link to the schema file are generated from the schema itself.

A fragment has no frontmatter and must hold non-blank text. It may not contain a slot marker: only a step prompt declares slots, and the raw-text check that refuses markers in a fragment is recorded beside the schema, because the parsed model cannot express it.
