---
okf_version: "0.2"
title: The pack-check round trip leaves out files the charter does not hold
description: A stray or unmodelled file in a pack, such as qa/notes.txt, is a file_not_held warning under pack-check --round-trip too, and no longer fails it with round_trip_red.
doc_status: active
updated: 2026-11-21T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check
  - guides/check-packs
audience:
  - profile: pack-author
    guidance_text: Read this if pack-check --round-trip failed on a note or log file in your pack.
---

# 2026-11-21 — The pack-check round trip leaves out files the charter does not hold

**A stray file never fails a pack** (#631, operator ruling R34,
[ADR-0075](/adr/0075-one-pack-validation-module-on-the-kernel-rule-vocabulary-and-pack-check/)). Before, a file no pack
file kind models, such as `qa/notes.txt`, was a `file_not_held` warning under plain `pack-check` but failed
`pack-check --round-trip` with `round_trip_red`: `UNEXPECTED_RED qa/notes.txt (other:qa): no adapter models kind other:qa`.

## What changed

- **The round trip leaves out every file the census does not hold.** That is an excluded file (such as Markdown
  nothing models), a file no rule covers (such as `qa/notes.txt`), and a mapped file nothing names (a `policy.yaml` the
  descriptor does not name, a companion no artifact names). The census decides it, not the file's kind. None of them is
  handed to an adapter or compared. The verdict checks only that the store did not write one (`NOT_HELD_WRITTEN` if it
  did).
- **The warning stays.** Both modes warn `file_not_held` at each such file on standard error, and exit `0`.
- **#547 holds.** A held file of a schema the round trip does not model, such as an `org-fragment`, still fails with
  `round_trip_red`.

spec-kitty-open-packs accepts the gap until this ships in a charter release (spec-kitty-open-packs#331); it does not
override the charter's verdict.
