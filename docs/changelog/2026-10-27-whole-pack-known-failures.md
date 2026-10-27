---
okf_version: "0.2"
title: The whole-pack gate carries known pack failures until the pin moves
description: A third list in the whole-pack gate holds files that fail the round trip because the pack's own data is inconsistent, and expires when the pin moves to the fix.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/service-build
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for where a file goes when the pack is wrong and the service is right.
  - profile: core-maintainer
    guidance_text: Read this for how pack inconsistencies are tracked in the gate until spec-kitty-open-packs fixes them.
---

# 2026-10-27 — The whole-pack gate carries known pack failures until the pin moves

**A file that fails the round trip because the pack is wrong can now be listed, pinned to its issue.** The whole-pack
gate ([#247](https://github.com/spec-kitty/spec-kitty-charter/issues/247)) reads a new file,
`service/packs/src/test/resources/whole-pack/known-failures.yaml`. Each entry names the file, where it differs, a
reason and the spec-kitty-open-packs issue that owns the fix, as `spec-kitty/spec-kitty-open-packs#<number>`. The list ships
empty. The report counts these files as `known failure`, apart from `expected red`.

**An entry expires by itself.** When the fix merges and `openPacksRef` moves, the file round-trips and the gate fails
with `UNEXPECTEDLY_GREEN` until the entry is removed in the same change. A changed difference (`CHANGED_RED`), a stale
entry, an entry for a kind no adapter models and a file listed twice fail it too. The steps are in
[Building the service](/guides/service-build/), and the rule they follow is
[ADR-0036 decision 5](/adr/0036-round-trip-fidelity-is-equality-of-meaning/).
