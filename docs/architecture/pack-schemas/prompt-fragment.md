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

## Rules

- **Body only.** The file is the text to insert: no frontmatter, and non-blank content in the preamble or in at least
  one section. An optional first-level heading is allowed.
- **No slot marker of any id.** A marker (`<!-- <namespace>:<name>:start -->` or `...:end -->`) is refused anywhere in the text,
  fences included. Only a step prompt declares slots.
- **LF only.** A carriage return or a byte order mark is refused; the file is not rewritten.
- **Named by exactly one entry** of one [step enhancement](/architecture/pack-schemas/step-enhancement/). A fragment
  under `missions/<type>/enhancements/` that no entry names (an orphan) is refused, and so is one named by two entries.
- **Canonical render is its meaning.** The service holds the fragment as parsed Markdown and writes it back with equal
  meaning, not byte for byte.

Each refusal is `prompt_fragment_invalid`. A marker or an orphan is refused at the fragment; at registration and on a
read, the other faults are refused at the step enhancement that names the fragment. See
[Load rules](/architecture/composition-and-activation-rules/#load-rules) for the moments and the wire shape.
