---
okf_version: "0.2"
title: Validate mode refuses the sidecars the load refuses, and the payload write and template_path check refuse a path spelled in another case
description: Validate mode applies the store's asset payload rule and lists every payload and template; the payload write and the template_path check compare listed names, case folded.
doc_status: active
updated: 2026-11-01T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0040-content-form-and-payload-kinds
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - changelog/2026-10-27-asset-and-template-round-trip
  - changelog/2026-10-31-pack-model-cleanup
audience:
  - profile: pack-author
    guidance_text: Read this for the sidecars validate mode now reports and the template paths the load now refuses with the pack's own spelling.
  - profile: charter-service-contributor
    guidance_text: Read this for the shared payload rule and how a path's spelling is read from the listings.
---

# 2026-11-01 — Validate mode refuses the sidecars the load refuses, and the payload write and template_path check refuse a path spelled in another case

Follow-ups of [the asset and template round trip](/changelog/2026-10-27-asset-and-template-round-trip/) and
[the pack-model cleanup](/changelog/2026-10-31-pack-model-cleanup/). No operation serves validate
mode, so nothing changes on the wire.

**Validate mode judges asset payloads as the load does**
([#251](https://github.com/spec-kitty/spec-kitty-charter/issues/251)). Validate mode reports a
sidecar whose payload the pack does not hold, or that names a payload another sidecar named, and an
asset file the load does not hold (a link that leaves the pack, or a file it cannot read). It uses
the load's rule and words. It also lists every payload and template with its kind. On the built-in pack at the
pin, it now reports the six sidecars the load refuses
([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75)).

**The single payload write refuses a path the pack holds in another case**
([#252](https://github.com/spec-kitty/spec-kitty-charter/issues/252)). Writing `templates/a.md`
into a pack that holds `templates/A.md` is refused, `REGISTRATION_CONFLICT`, `payload
templates/a.md differs only in case from templates/A.md`. On a case-insensitive file system the
write would have replaced the other file.

**A `template_path` spelled in another case is refused by the file check**
([#300](https://github.com/spec-kitty/spec-kitty-charter/issues/300)). The check now compares the
names the pack lists and reports the pack's spelling, for example `templates/Spec-Template.md
(named by step 'scoping'): differs only in case from missions/research/templates/spec-template.md`.
It never asks the file system to open a name, so it agrees with the exact id resolution on
case-sensitive and case-insensitive file systems alike.
