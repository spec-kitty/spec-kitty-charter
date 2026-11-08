---
okf_version: "0.2"
title: A preset item id outside the id grammar refuses the pack
description: A preset item spelled as a URN, with whitespace or pack-qualified now refuses its pack as pack_unreadable, instead of governing nothing or failing the offering with internal_error.
doc_status: active
updated: 2026-11-07T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/compose-packs
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a pack's preset is refused as pack_unreadable after an upgrade.
---

# A preset item id outside the id grammar refuses the pack

A preset item's `id` must match `^[A-Za-z0-9][A-Za-z0-9_/.-]*$`, the characters a `policy.yaml` key allows. A preset
that breaks it refuses the pack as `500 pack_unreadable`, and the detail names the preset file.

Before, an item such as `directive:DIRECTIVE_001`, `DIRECTIVE:directive_001` or `DIRECTIVE_001` with a trailing space
matched no held id, so a must-have item put nothing on the required floor and deactivating the artifact answered `201`
instead of `409 artifact_required`. A non-must-have `alpha-pack::DIRECTIVE_001` made `GET /offering` answer
`500 internal_error`. Write the id as the artifact declares it. The pinned open-packs presets all pass.
