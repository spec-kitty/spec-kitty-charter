---
okf_version: "0.2"
title: Every pack read of one thing carries the pack revision
description: Tactic, step contract, mission type, step and skeleton pack reads answer the pack revision as ETag; an unused resolver path is retired.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0071-procedures-are-a-served-read-kind
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for which pack reads carry an ETag and what it means.
---

# 2026-11-11 — Every pack read of one thing carries the pack revision

The follow-up that procedures announced ([#487](https://github.com/spec-kitty/spec-kitty-charter/issues/487)), and a
clean-up of the graph-walk code ([#488](https://github.com/spec-kitty/spec-kitty-charter/issues/488)).

- **One ETag rule for pack reads** ([ADR-0071](/adr/0071-procedures-are-a-served-read-kind/), amendment). Before: a
  tactic, a mission step contract, a mission type, a step and a skeleton answered no `ETag`. After: every pack read of
  one thing answers the pack revision, read in the same verified read as the body. For the mission type, its steps and
  its skeletons the tag is a concurrency token naming the pack, not a cache validator, because the answer can carry
  content from dependency packs. No pack read takes `If-None-Match`; activation reads are unchanged.
- **Refusals agree across kinds.** A pack whose aggregate or revision cannot be read is `pack_not_found` for every kind
  of the templated item read; a step contract used to answer `pack_unreadable`.
- **One walk path.** `CompositeComponentResolver` keeps only its closure holder check; graph walks use the edge index.
