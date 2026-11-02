---
okf_version: "0.2"
title: Docsite on DocKitty 0.6.0, charter API reference, and pack-ecosystem diagrams
description: "The docsite runs on DocKitty 0.6.0, has a charter API reference, and shows the decided pack-ecosystem design in its diagrams."
doc_status: active
updated: 2026-11-02T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/descriptor-schemas
  - architecture/descriptors
  - architecture/pack-schemas/README
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
audience:
  - profile: pack-author
    guidance_text: Read this for where the schema pages, the API reference and the decided-design diagrams now live.
---

# 2026-11-02 — Docsite on DocKitty 0.6.0, charter API reference, and pack-ecosystem diagrams

**The architecture diagrams now draw the decided pack-ecosystem design, and every pack schema has its own page with a
diagram.** The docsite also runs on DocKitty 0.6.0 and publishes the charter contract as an API reference. Nothing in
the schemas, the contract or the service changes.

**Before:**

- The docsite ran on an older DocKitty and had no readable view of the charter contract.
- Pack schemas were described in prose only, and the architecture diagrams drew today's shapes from core's models.
- The pages said the diagrams would be redrawn once the decided design was built.

**Now:**

- The docsite runs on DocKitty 0.6.0, which renders diagrams at build time.
- The charter contract is readable at [the API reference](/reference/api/main/), with the raw contract and its
  checksum beside it.
- Each of the 23 pack schemas has a page with a diagram, listed in the [Pack schemas hub](/architecture/pack-schemas/).
- The five pack-model diagrams on [Descriptor schemas](/architecture/descriptor-schemas/) draw the decided design of
  ADR-0048 to ADR-0052. Yellow «pending» classes and `[pending ADR-00xx OQn]` marks show open questions that no
  ruling has settled.
- A new diagram draws the mission type as a declared state machine
  ([ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/)).
- The text around each diagram says that it draws the decided design and that today's code and schemas still have the
  old shape. The page also describes how to regenerate the diagrams with the pinned PlantUML jar.
