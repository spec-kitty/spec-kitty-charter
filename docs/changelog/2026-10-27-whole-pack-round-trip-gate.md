---
okf_version: "0.2"
title: A whole-pack round-trip gate shows what of the built-in pack survives
description: A red-first gate round-trips every file of the pinned built-in pack and compares it by meaning, per kind and per file; the pack store gets one storage root.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for the new whole-pack gate, how to turn your kind green, and the new charter.storage.root setting.
  - profile: core-maintainer
    guidance_text: Read this for which built-in pack content the service reads and writes back faithfully today.
---

# 2026-10-27 — A whole-pack round-trip gate shows what of the built-in pack survives

**Every file of the pinned built-in pack now has a visible round-trip status.** The new gate
([#187](https://github.com/spec-kitty/spec-kitty-charter/issues/187)) reads the whole pack into the domain, writes it
to a fresh directory through the storage service, reads it back and compares each file with its source by meaning,
not bytes, by the rules of [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/). Of 505 files, 229
round-trip today; 256 are expected red (254 in kinds with no reader and writer yet, and two graph shards with real
losses) and 20 are excluded with a reason. Each child of epic [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186) turns its own part
green.

The gate cannot pass by doing nothing: an unexpected red fails it, and so does a listed item that now round-trips, a
listed file whose differences changed, or a stale entry. Its first run found two losses in the graph-shard writer: it
drops node tags, and it writes the legacy `in_tension_with` edges as plain `impacts` without their negative strength,
so the tension is lost. It runs as `./gradlew :packs:wholePackRoundTrip` and in its own CI
workflow, and is not a required check until it is green ([Building the service](/guides/service-build/)).

**The pack store is configured by one storage root.** `charter.storage.root` names the directory whose
subdirectories are the packs the service reads and writes (`built-in` at the built-in level, any other at org). It is
the storage layer's own setting, documented in `application.yaml`, and replaces `charter.packs[*]`.
