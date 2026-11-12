---
okf_version: "0.2"
title: The pack manifest lists the whole pack, and every write restamps it
description: pack-manifest.yaml's lists are derived from the pack on every write, a manifest is generated where none was, and a registration refuses an uploaded manifest whose lists differ.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0074-the-remaining-cutover-reads-and-their-paths
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which files the manifest lists, which writes restamp it, and the new registration refusal.
  - profile: pack-author
    guidance_text: Read this before you upload a pack with a hand-written pack-manifest.yaml.
---

# 2026-11-12 — The pack manifest lists the whole pack, and every write restamps it

`pack-manifest.yaml` now describes the pack the service holds
([#110](https://github.com/spec-kitty/spec-kitty-charter/issues/110);
[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/), amendment 2026-11-12;
[ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decision 7).

- **Its lists are derived.** Before: a write kept the constituents and presets as the uploaded manifest listed them, so
  an artifact added by a `PUT` was never listed and a deleted one stayed listed. After: the manifest lists one
  constituent per artifact the pack holds (kind, id, file) and one entry per preset, each list in path order.
- **Every write restamps it, and generates it where none was.** A whole-pack registration, a per-artifact `PUT`, a
  `DELETE` and a write of the registered relationships all store the derived manifest with fresh hashes. A pack that
  held no manifest gets one on its next write, with a new fingerprint and `ETag` once.
- **A registration refuses a manifest whose lists differ.** Before: any schema-valid manifest was accepted. After: an
  uploaded manifest that lists an entry the pack does not hold, or leaves out one it holds, is refused
  `422 validation_failed`, code `manifest_membership`, at `pack-manifest.yaml`. Order and hashes are not judged. A pack
  author can leave the manifest out and let the service write it.
- **The example packs.** `built-in`'s manifest lists all 40 of its artifacts (it listed 22), and `org-base` holds one.
