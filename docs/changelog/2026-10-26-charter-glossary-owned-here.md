---
title: The charter glossary is owned by this repository
description: The glossary is now authored here instead of imported from core, and carries the terms of the new conceptual model, with superseded terms marked.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0029-charter-glossary-is-owned-here
  - guides/docsite
  - context/conceptual-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what changed on this date.
---

# 2026-10-26 — The charter glossary is owned by this repository

## What changed

- **This repository owns the [Glossary](/glossary/)**
  ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)). Terms are authored in
  `site/.contextive/definitions.yaml`; the import from the core glossary and
  `site/tools/import_core_glossary.py` are retired. The pages are still generated.
- **New terms from the [conceptual model](/context/conceptual-model/)**: role, primary role,
  alt-role, operator, profile selection, fallback profile, role pin, ethos, creed, value,
  value impact, `tension` and `supports`, integrity level, requirement, derived pack, removal
  and deactivation, mission type, recipe ingredients, mission step, step contract and step
  template, paradigm test and principles, rules and resources, upgrade, write-through active
  charter, and local and hosted deployment.
- **Terms carry a status**: `canonical`, `proposed` where the model is still open, or
  `deprecated`. `in_tension_with`, Mission-Type Profile and `selected_<kind>` /
  `required_<kind>` are deprecated and name their replacements; activation preset, active
  charter, activation context, charter offering and the selection terms are reworded.
- [Building the docsite](/guides/docsite/) describes the new authoring flow.
