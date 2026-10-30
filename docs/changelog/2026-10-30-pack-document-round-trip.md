---
okf_version: "0.2"
title: A pack's pack.md round-trips through the markdown content form
description: The pack document is read into the Pack aggregate, written back in the canonical Markdown form, digested before the manifest and green in the whole-pack gate.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest
  - adr/0040-content-form-and-payload-kinds
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
audience:
  - profile: pack-author
    guidance_text: Read this for how pack.md is now read and judged, and what a canonical write changes in it.
  - profile: charter-service-contributor
    guidance_text: Read this for the pack document slot, its store path and its place in the fingerprint.
  - profile: core-maintainer
    guidance_text: Read this for the pack-document structure schema and the new pack.md rule of file-kinds.yaml.
---

# 2026-10-30 — A pack's pack.md round-trips through the markdown content form

**The service now reads each pack's `pack.md`.** A `files` rule of `contracts/packs/file-kinds.yaml`
maps `*/pack.md` (the pack root only) to the new `pack-document` structure schema, in the `markdown`
content form #194 built. The store reads the file into the `Pack` aggregate as its document
(optional frontmatter, an optional title, a preamble and the H2 sections), and the whole-pack write
puts it back in the canonical Markdown form. Two `pack.md` files mean the same when their models
are equal, so blank lines between blocks and the final newline do not count.

**A `pack.md` the Markdown form refuses is now a violation.** A carriage return (a CRLF checkout), a
byte order mark or an ambiguous heading is refused at its line, by the load and by validate mode
alike, where the file was ignored before. It is never normalized; fix the file in the pack.

**What changes for an operator:**

- The whole-pack gate models `pack.md` as `pack-document`, and `other:pack.md` is off its
  expected-red list: the built-in pack's `pack.md` is green.
- The source fingerprint digests `pack.md` between the presets and `pack-manifest.yaml`. A pack
  that has one, the built-in pack included, gets a new `Charter-Source-Fingerprint` and ETag once,
  so a client holding the old ETag gets one `412` on its next conditional write. A pack without one
  keeps its fingerprint.
- Frontmatter compares as loaded YAML values, stricter than ADR-0036: `1` and `1.0` differ, and an
  empty frontmatter differs from an absent one. The built-in `pack.md` has none.

[ADR-0042](/adr/0042-pack-level-files-sanitized-shapes-and-the-derived-manifest/) (decision 8 done),
[ADR-0040](/adr/0040-content-form-and-payload-kinds/) and
[ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/) carry dated amendments.

Issue: [#260](https://github.com/spec-kitty/spec-kitty-charter/issues/260), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
