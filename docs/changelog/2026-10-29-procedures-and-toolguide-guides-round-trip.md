---
okf_version: "0.2"
title: Procedures and toolguide guides round-trip, and a toolguide registration carries its guide
description: Breaking HTTP change to toolguide registration; procedures and structured toolguide guides in sanitized shapes; packs migrated and re-pinned.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0039-a-kind-binding-can-carry-companion-files
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the toolguide contract change and the new procedure and guide shapes.
  - profile: pack-author
    guidance_text: Read this for the shapes a procedure and a guide must now have.
---

# 2026-10-29 — Procedures and toolguide guides round-trip, and a toolguide registration carries its guide

**Breaking, HTTP.** A toolguide registration no longer takes `guidePath`. It carries exactly one of
`guide` (the guide's Markdown, at most 1 MiB) or `guideUri` (HTTPS), and the response carries
`guide`. Both, neither or an empty value is 422 `validation_failed`. A URI is fetched only from a
host on `charter.guide-fetch.allowed-hosts`, which is empty by default, directly and never through
a proxy. See the [service build guide](/guides/service-build/).

**New pin.** The pinned procedures, toolguides and guides were rewritten into the new shapes by the
service's own writer and landed in spec-kitty-open-packs as
[spec-kitty/spec-kitty-open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91);
`openPacksRef` moves to `5df92a5d02e47e8877364de5e2deb06c016d30a9`, which holds 39 procedures, 24
toolguides and 24 guides. An authored YAML comment the writer cannot keep went into the procedure's
top-level `notes`, once, and only when the procedure had none. The maintainer merged open-packs#91
with core's validation check red, by waiver: core (spec-kitty) still requires the old procedure
fields and follows at its cut-over to the service, per the ruling on
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).

**The shapes.** A procedure has `title`, `description` and steps that name a `role`
(`actor: human` became `role: operator`); its anti-pattern entries are `{title, content?, ...}`;
its references are kept and never minted as relationships. A toolguide's guide is
`<id>.guide.md`, a Markdown companion of fixed blocks (`## Overview` required) and freeform
sections, its H1 written from the toolguide's title.
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) (proposed)
records the rules; [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) is amended.

Issue: [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
