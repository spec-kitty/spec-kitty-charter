---
okf_version: "0.2"
title: Pack ids are capped, and an overlong id or YAML scalar gets a coded refusal instead of a stack overflow
description: The pack schemas cap the listed ids, refs, entries and artifact keys, the domain caps the ids it types, and main-code regex repeats are possessive.
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

- **The caps.** The pack schemas cap these ids:
  - **64 characters**: the `id` of a guard, lifecycle, procedure, toolguide, mission type, mission step, step
    enhancement, styleguide, tactic, paradigm and directive; a lifecycle state's `id`; and the pack descriptor's `name`.
  - **136 characters**: a lifecycle guard entry's `ref` (`<pack>::guard:<id>`).
  - **255 characters**: an executable's `entry` and an expected artifact's `artifact_key`.

  A longer value makes the pack unreadable (`500 pack_unreadable`). Not capped: a preset item id, a lifecycle
  transition `event`, the `role` of a step or procedure, an agent profile's `roles`, the URNs, and the ids of an asset,
  an agent profile, a glossary pack and a contract. A guard's `implemented_by` is capped in the domain only: its
  `<pack>::` qualifier at 64 characters. The open-packs are unaffected (longest id: 55 characters).
- **The domain applies the same caps** to the ids it has a type for: all of the 64-character ids above except a
  lifecycle state, a step enhancement and the pack `name`, which only the schema caps; and the guard reference, entry
  and artifact key. A directive `PUT` with a 65-character id is now refused with `422 validation_failed`; before, it
  was stored and the pack then answered `500 pack_unreadable`.
- **No recursion on long input.** Every repeated group the service's main code matches with is possessive, including
  the YAML int and float resolvers and the container allowed-hosts entry.
- **A guard keeps it that way.** `RegexRecursionGuardTest` in build-logic fails on a repeated regex group in main code
  that is not possessive, unless an allow-list entry says why it is safe.

Charter request ids follow in [#443](https://github.com/spec-kitty/spec-kitty-charter/issues/443).
