---
okf_version: "0.2"
title: Presets and the pack manifest round-trip, and pack.yaml is closed
description: Presets carry a must-have mark per item, the manifest's hashes are recomputed on write, and an unknown pack.yaml key makes the pack unreadable.
doc_status: active
updated: 2026-10-28T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0031-requirement-is-authored-as-a-preset
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: pack-author
    guidance_text: Read this for the preset and manifest shapes, and why an unknown pack.yaml key is now refused.
  - profile: charter-service-contributor
    guidance_text: Read this for what the whole-pack load and write now hold beside the artifacts.
---

# 2026-10-28 — Presets and the pack manifest round-trip, and pack.yaml is closed

**A `pack.yaml` key the schema does not declare now makes the pack unreadable.** It is refused as
`pack_unreadable`, naming the key, where it loaded with the `pack_unknown_key` warning. That value
is retired and never raised (DIV-005 and DIV-006 closed).

**A preset can mark an item must-have.** A preset is read and written in one closed shape, with
`must_have: true` per item. A preset with a must-have item governs; one without is a convenience
preset. Its `id` must be its file stem, and an empty list is refused.

**The manifest describes the written pack.** Its hashes are recomputed from the bytes written:
`content_hash` over LF-normalized bytes, `manifest_hash` over RFC 8785 canonical JSON. The gate
verifies both.

The built-in presets and manifest are not in these shapes. They are refused and pinned as known
failures until open-packs fixes them. Validate mode reports them too.
[ADR-0043](/adr/0043-pack-level-files-sanitized-shapes-and-the-derived-manifest/) (proposed)
records the decisions, with amendments to ADR-0031, 0036, 0037 and 0040. `pack.md` waits for #194.

**What changes for an operator:** fingerprints and ETags of packs holding presets or a manifest
change once. **What does not change:** the shape of the HTTP contract.

Issue: [#195](https://github.com/spec-kitty/spec-kitty-charter/issues/195), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
