---
okf_version: "0.2"
title: Assets and templates round-trip through the domain, byte for byte
description: Asset sidecars, their payloads and top-level templates are read, written and fingerprinted; how a file is compared is declared by its file-kind rule.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0040-content-form-and-payload-kinds
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0039-a-kind-binding-can-carry-companion-files
audience:
  - profile: pack-author
    guidance_text: Read this for when an asset sidecar is refused, and how payloads and templates are written back.
  - profile: charter-service-contributor
    guidance_text: Read this before adding a file kind that is not YAML.
  - profile: core-maintainer
    guidance_text: Read this for the content_form key and payloads section of file-kinds.yaml.
---

# 2026-10-27 — Assets and templates round-trip through the domain, byte for byte

**An asset whose payload is missing is now refused.** A sidecar (`*.asset.yaml`) is read with the
file its `path` names under `assets/`; when that file does not exist, the sidecar is a violation
(`payload assets/<path> does not exist`) and the whole-pack write refuses the pack. An undeclared
sidecar key is refused, naming it. The `built-in` pack's six sidecars anchor `path` at the pack
root and are refused: a pack inconsistency, fixed in `spec-kitty-open-packs` and pinned until then
([ADR-0040](/adr/0040-content-form-and-payload-kinds/), proposed).

**Asset payloads and templates survive a write to the byte**: scripts, Markdown, fonts and
images, a payload no sidecar names, and every file under a pack's top-level `templates/` (a
template's id is its path there, `template:writing/meeting-minutes.md`). The whole-pack gate shows
`asset-payload` and `template` green. The executable bit is not kept.

How a file is compared is declared by its rule in `contracts/packs/file-kinds.yaml`, its
**content form**, never by its extension or `mime`;
[ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)'s extension-based rows are
superseded in part by ADR-0040, through a dated amendment that keeps their text and annotates each
point where it stands. [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) carries an
amendment too: asset payloads and templates are pack-level files, not companions.

**What changes for an operator:** fingerprints and ETags of packs holding assets or templates change
once; a pack with a binary payload commits and answers `If-Match`; relationships may name held
`asset:` and `template:` artifacts. **What does not change:** the shape of the HTTP contract.

Issue: [#193](https://github.com/spec-kitty/spec-kitty-charter/issues/193), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
