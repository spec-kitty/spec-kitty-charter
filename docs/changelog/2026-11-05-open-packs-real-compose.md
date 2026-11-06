---
okf_version: "0.2"
title: The service composes the real open packs
description: The open-packs pin moves to the strict-composition packs. A test now composes each real pack with built-in and all five together, and the known-failure list is empty.
doc_status: active
updated: 2026-11-05T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/compose-packs
  - changelog/2026-11-03-pack-composition-depends-on-and-resolver
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you bump the open-packs pin or work on composition.
  - profile: pack-author
    guidance_text: Read this for the corrected form of a qualified typed reference.
---

# 2026-11-05 — The service composes the real open packs

**`OpenPacksComposeTest` composes the real spec-kitty-open-packs packs through the production offering read**
([#363](https://github.com/spec-kitty/spec-kitty-charter/issues/363),
[open-packs#118](https://github.com/spec-kitty/spec-kitty-open-packs/issues/118)). It reads the pinned checkout, composes
`quality-assurance`, `event-driven`, `spec-kitty-internal` and `cave-kitty` each with `built-in` and all five together,
and asserts the served pack list, so a refusal cannot pass as a success. Refusal tests change one thing in a copy of a
real pack, on a graph edge and on a typed reference.

Moving the pin also changes what the corpus tests expect:

- The known-failure list `whole-pack/known-failures.yaml` is empty, and the six built-in sidecars are `GREEN`
  ([open-packs#75](https://github.com/spec-kitty/spec-kitty-open-packs/issues/75)).
- Validate mode finds nothing in `built-in`, and the whole-pack test now round-trips the real pack, not a corrected copy.
- `qa` has a `pack.yaml`, so it counts as a whole pack: 31 agent profiles, not 26.
- The glossary pack's file stem is `spk-internal-glossary`, and this repository's charter activates it under that stem.

[Compose packs](/guides/compose-packs/) is corrected: a typed reference's `id` is `<pack-name>::<id>`, not
`<pack-name>::<kind>:<id>`.
