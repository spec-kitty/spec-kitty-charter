---
okf_version: "0.2"
title: A pack file in another letter case is judged as its lower-case spelling
description: A pack file in another letter case, such as ReadMe.md or a.Directive.yaml, now gets the verdict of its lower-case spelling, so it is excluded, retired or checked as its kind.
doc_status: active
updated: 2026-11-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - guides/check-packs
audience:
  - profile: pack-author
    guidance_text: Read this if your pack holds a file whose name differs from a rule's file only in letter case.
---

# 2026-11-26 — A pack file in another letter case is judged as its lower-case spelling

**A file's letter case no longer changes what the service does with it** (#661, #671). Before, the file-kinds rules
matched letter case exactly, so `assets/logos/ReadMe.md` was stored as an asset payload while `README.md` beside it
was excluded, `Mission_Step_Contract.graph.yaml` was read as a graph shard while its lower-case spelling was refused as
retired, and `assets/a.Directive.yaml` was held as bytes, never checked against the directive schema.

## What changed

The rules also run with the path and the globs in lower case, and that run decides where it reaches another rule:

- **An exclusion takes its file in any letter case.** `missions/x/templates/ReadMe.md` is not held; a registration
  reports it as a `file_not_held` diagnostic under the exclusion's reason.
- **A retired family is refused in any letter case, wherever it stands.** `a.Step-Contract.yaml` and
  `Mission-Steps/a.tactic.yaml` are refused, and the pack with them.
- **A kind is judged as its kind.** `assets/a.Directive.yaml` is validated as a directive, and
  `PRESETS/a.guard.yaml` as a preset.

## What did not change

A file no rule takes as written, which only a kind or payload rule takes in lower case, is still warned as
`file_not_held` and not held (`toolguides/a.guide.MD`). A root file such as `Readme.md` is still refused with the code
`case_variant`. The packs in spec-kitty-open-packs and the charter's example packs hold no file whose verdict changes.

See [Check packs](/guides/check-packs/) and the "Letter case" section of `contracts/packs/file-kinds.yaml`.
