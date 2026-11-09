---
okf_version: "0.2"
title: Pack ids are capped, and an overlong id or YAML scalar gets a coded refusal instead of a stack overflow
description: The pack schemas and the domain cap ids, states, guard refs, entries and artifact keys; main-code regex repeats are possessive, and a build-logic guard keeps them so.
doc_status: active
updated: 2026-11-09T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/descriptor-schemas
  - guides/author-a-procedure
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if a pack is refused as unreadable for an overlong id, or RegexRecursionGuardTest fails on a pattern you added.
---

# 2026-11-09 — Pack id length caps

**A pack with an overlong id is now refused as unreadable, not answered with a bare `500`** ([#440](https://github.com/spec-kitty/spec-kitty-charter/issues/440), [#441](https://github.com/spec-kitty/spec-kitty-charter/issues/441),
[#442](https://github.com/spec-kitty/spec-kitty-charter/issues/442)). Before, an id of about 10 000 characters, or a long sexagesimal YAML scalar, threw `StackOverflowError`
while it was matched and the caller got a bare `500`. Now the value is judged and refused with a code.

- **The caps.** The pack schemas and the domain apply the same limits:
  - **64 characters**: an artifact id, a lifecycle state, and the pack descriptor's `name`.
  - **136 characters**: a lifecycle guard entry's `ref` (`<pack>::guard:<id>`).
  - **255 characters**: an executable's `entry` and an expected artifact's `artifact_key`.

  A longer value makes the pack unreadable: `500 pack_unreadable`. The open-packs are unaffected: their longest id is
  55 characters at pin `22c14b2`.
- **No recursion on long input.** Every repeated group the service's main code matches with is possessive, including
  the YAML int and float resolvers and the container allowed-hosts entry.
- **A guard keeps it that way.** `RegexRecursionGuardTest` in build-logic fails on a repeated regex group in main code
  that is not possessive, unless an allow-list entry gives the reason it is safe.

Only the pack schemas are capped here; charter request ids follow in [#443](https://github.com/spec-kitty/spec-kitty-charter/issues/443).
