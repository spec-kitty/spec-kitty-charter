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
`guide` (the Markdown, at most 1 MiB by default) or `guideUri` (HTTPS, port 443, an allowlisted host,
empty by default), and the response carries `guide`. Both, neither, an empty `guideUri` or a `null` is
422. An empty `guide`, or a `commands`, `appliesToLanguages` or `references` list over 256 items, breaks a
constraint the schema declares and is refused 400 Bad Request before the controller (ADR-0041 decision 2).
See the [service build guide](/guides/service-build/) and `contracts/charter/CHANGELOG.md`.

**Migrating a client.** Stop sending `guidePath`: send the guide file's Markdown as `guide` (the
structured form, `## Overview` first), or host it on an allowlisted HTTPS host and send `guideUri`.
Read the guide from the response's `guide`; its H1 is the title. Pack authors rename each guide file
to `<id>.guide.md` beside its toolguide.

**New pin.** The pinned procedures, toolguides and guides were rewritten by the service's own writer
and landed as
[spec-kitty/spec-kitty-open-packs#91](https://github.com/spec-kitty/spec-kitty-open-packs/pull/91);
`openPacksRef` moves to `5df92a5d`, with 39 procedures, 24 toolguides and 24 guides. Core still
requires the old procedure fields and follows at its cut-over, per the ruling on
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).

**The shapes.** A procedure has `title`, `description` and steps that name a `role`; its `id` is
its file stem. A toolguide's guide is `<id>.guide.md`, a Markdown companion of fixed blocks and
freeform sections whose H1 is derived from the title. The load and validate mode refuse the same
procedures and guides.
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) (proposed)
records the rules; [ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/) is amended.

Issue: [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
