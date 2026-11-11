---
okf_version: "0.2"
title: One identifier grammar and one pack locator, a root-relative contract bundle, and anchors that keep underscores
description: Identifier grammars and the pack id lookup each live in one place, a relative contract bundle path resolves against the repository root, and anchors keep underscores.
doc_status: active
updated: 2026-11-11T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you add an identifier type, build the service with a prebuilt bundle, or link to a heading that holds an underscore.
---

# 2026-11-11 — One identifier grammar, one pack locator, and anchors that keep underscores

Tidy-ups from the 2026-11-11 triage
([#495](https://github.com/spec-kitty/spec-kitty-charter/issues/495)). The service answers as before,
except in the one case named below.

- **One identifier grammar.** `kernel.domain.IdentifierGrammar` holds the kebab slug, the letter-first slug,
  the dotted key and the relative path. Each checks its length cap (64, or 255 for a key or a path) before it
  matches, and every repeat is possessive. The pack and mission identifier types check through it. A step
  enhancement's id keeps its own pattern: ADR-0066's second phase changes that code ([#444](https://github.com/spec-kitty/spec-kitty-charter/issues/444)). One deliberate tightening: a
  guard's `builtin:<id>` takes the slug grammar, so a leading, trailing or double hyphen is refused as neither
  spelling, not as an unknown built-in; the closed list is unchanged. The unused `Checks` is gone ([#383](https://github.com/spec-kitty/spec-kitty-charter/issues/383)).
- **One pack locator.** Reads, commits and writes resolve a pack id to its directory through `PackLocator`
  ([#184](https://github.com/spec-kitty/spec-kitty-charter/issues/184)). Two behavior changes came with it. A pack
  location at the filesystem root no longer makes an id lookup on the read side refuse `500 pack_unreadable`; it
  is passed over, as the write side already did, like a missing `pack.yaml` or one that is no descriptor. A
  `pack.yaml` that is there but cannot be read (a directory, no read permission) is refused
  `500 pack_unreadable` on all three sides, so it cannot hide behind a later location that declares the id.
- **A relative `-PcontractBundle` is relative to the repository root**, as `-PopenPacksDir` is. Before, it
  resolved against `service/deployable`, so the build packaged no bundle and still passed. A path that names no
  file, or a directory, now fails the build with `-PcontractBundle names no file: <path>`
  ([#324](https://github.com/spec-kitty/spec-kitty-charter/issues/324)).
- **The links check slugs a heading as the site does**
  ([#134](https://github.com/spec-kitty/spec-kitty-charter/issues/134)). It keeps underscores and what a code
  span shows, so `#requires_core-stays-in-packyaml` passes and `#requirescore-stays-in-packyaml` does not.
