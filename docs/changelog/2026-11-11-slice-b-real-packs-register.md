---
okf_version: "0.2"
title: Real packs register as they are
description: README.md is the pack document and is served; pack.md, PROVENANCE.md and org-charter.yaml are retired; excluded files are reported, and reads ride out a swap.
doc_status: active
updated: 2026-11-12T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0073-real-packs-register-as-they-are
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - guides/enforce-org-presets
audience:
  - profile: pack-author
    guidance_text: Read this for what your pack must change to register, and what the service no longer reads.
  - profile: charter-service-contributor
    guidance_text: Read this for the slice B changes to classification, registration, the doc read and the swap.
---

# 2026-11-11 — Real packs register as they are

Slice B ([#466](https://github.com/spec-kitty/spec-kitty-charter/issues/466)). The calls beyond the rulings are
[ADR-0073](/adr/0073-real-packs-register-as-they-are/).

- **`README.md` is the pack document** (#451). It takes `pack.md`'s place in the model, the digest and the gate.
  `pack.md` and `PROVENANCE.md` at the pack root are refused by name as `retired`, and so is a case variant
  (`Readme.md`).
- **The document is served.** `GET /packs/{packId}/doc` answers `text/markdown` (the file) or JSON (`title`, `body`,
  `_links`), one `ETag` (the pack revision) with `Vary: Accept`, `304`, `406`, and `404 pack_document_not_found`. The
  pack resource links it as `doc`.
- **Excluded files register.** A file an exclusion names, and Markdown no rule models (the new `ignored` fallback), is
  not held and not refused; the registration reports it as `file_not_held`.
- **`org-charter.yaml` and `active_charter` are retired** (#431, #381). Org enforcement is a governing preset plus
  `policy.yaml`: [Enforce presets on an organization's projects](/guides/enforce-org-presets/).
- **Reads during a replace** (#490) read the aside copy without a lock, re-read when the directory changes under them,
  and a verified read inside one swap is answered when its digests agree.
- **The `text` form** comment says text companions exist and no standalone `text` kind does (#432).
- **Divergences** DIV-302 and DIV-303; the usability script no longer filters `unmodelled` refusals.
