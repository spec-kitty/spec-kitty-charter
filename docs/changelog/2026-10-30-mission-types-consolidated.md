---
okf_version: "0.2"
title: Mission types are one sanitized file each, and an artifact kind
description: One mission-type file per type with Markdown step companions; eight retired file families refused by name; a missions module; relationships from a mission type.
doc_status: active
updated: 2026-10-30T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0039-a-kind-binding-can-carry-companion-files
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new artifact kind, the missions module and the retired mission files.
  - profile: pack-author
    guidance_text: Read this for the one file a mission type is now authored in, and the old files the service refuses.
---

# 2026-10-30 — Mission types are one sanitized file each, and an artifact kind

**One file per mission type.** A mission type is authored in
`missions/<id>/<id>.mission-type.yaml`, its steps as sub-items: `steps` in run order and
`on_demand_steps` for steps a command invokes. A step has a role, a one-line description, its
expected input and output artifacts (pre- and postconditions) and its references; its prompt and
guidelines are Markdown companions under `steps/`. Fields:
[Descriptors](/architecture/descriptors/); decision:
[ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/) (accepted 2026-10-31).

**Retired files.** `mission_types/<t>.yaml`, `mission.yaml`, `mission-runtime.yaml`,
`governance-profile.yaml`, `expected-artifacts.yaml`, action indexes, `mission-steps/**` and
`command-templates/*.md` are retired by the new `retired` rules of
`contracts/packs/file-kinds.yaml`. The load and validate mode refuse each by name.

**The model.** `mission_type` is an artifact kind, its domain a new `:missions` module. Core
keeps mission types outside its artifact kinds and still reads the retired files; it follows at
its cut-over (DIV-501, DIV-502).

**HTTP.** No schema or operation changes. `PUT /packs/{packId}/relationships` now registers an
edge from `mission_type:<id>` when the pack holds that type, where it answered 409 before
(DIV-503; `contracts/charter/CHANGELOG.md`).

**New pin.** The service's own writer rewrote the pinned mission tiers for `spec-kitty-open-packs`
(spec-kitty/spec-kitty-open-packs#95; the changes it leaves to the pack authors are
spec-kitty/spec-kitty-open-packs#96 to #100). `openPacksRef` moves to that commit, `86d92846`: six mission types,
46 steps, and 153 retired files removed. What is left is in the
[epic #186 follow-ups](/development/epic-186-sanitized-shape-follow-ups/).

Issue: [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
