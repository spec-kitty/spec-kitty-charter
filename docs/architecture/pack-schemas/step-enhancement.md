---
title: "Pack schema: Step enhancement"
description: "The Step enhancement pack schema: its properties, required fields and a rendered diagram, for authors of pack files."
doc_status: draft
updated: 2026-11-08T00:00:00Z
type: Architecture
kind: Schematic
schematic_path: schemas/step-enhancement.schema.json
audience:
  - profile: pack-author
    guidance_text: Read this when you write a step enhancement that contributes prompt fragments to a mission step.
---

# Pack schema: Step enhancement

This page describes `missions/<type>/enhancements/<id>.step-enhancement.yaml`, the file by which a pack adds to a mission step ([ADR-0066](/adr/0066-a-step-enhancement-may-contribute-prompt-fragments/)). Read it when you write one; the diagram, the property list and the link to the schema file are generated from the schema itself.

The file names no target. The enhancing pack's graph documents carry the one `enhances` edge, to `action:<type>/<step>`. Each `prompt_fragments` entry pairs a slot declared by that step's prompt (`<namespace>:<name>`) with a [prompt fragment](/architecture/pack-schemas/prompt-fragment/) companion beside the enhancement. A preset activates the enhancement through `activated_step_enhancements`; there is no second gate for its fragments.
