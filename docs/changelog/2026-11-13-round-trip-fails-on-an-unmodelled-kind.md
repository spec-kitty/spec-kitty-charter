---
okf_version: "0.2"
title: The whole-pack round trip fails loudly on a file kind it does not model
description: The drift test names the kind and path of any file the round trip skips, the gate ties its kinds to file-kinds.yaml, and one gate test covers every example pack.
doc_status: active
updated: 2026-11-13T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0070-charter-tests-own-example-packs-not-open-packs-content
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - guides/service-build
audience:
  - profile: charter-service-contributor
    guidance_text: Read this if you touch the whole-pack round trip, its expectation lists or the open-packs drift test.
---

# 2026-11-13 — The round trip fails loudly on an unmodelled kind

[#547](https://github.com/spec-kitty/spec-kitty-charter/issues/547). Before this change, `drg/fragment.yaml` was
skipped by the round trip without a message, and it surfaced only through its effect on `spdd`
([#509](https://github.com/spec-kitty/spec-kitty-charter/issues/509)). Now a file of a kind the round trip does not
model fails the run that meets it.

- **The drift test judges every file.** `:packs:driftTest` judges each pack through the gate's expectation with no
  baseline. A file that is not excluded and has no round-trip outcome fails as
  `UNEXPECTED_RED <path> (<kind>): no adapter models kind <kind>`. spec-kitty-open-packs runs this task as a gate from
  its pinned charter checkout. At open-packs `736e613` it reports the three manifest reds of
  spec-kitty-open-packs#250 and nothing new.
- **The gate's kinds follow `file-kinds.yaml`.** Every schema of the table is modelled by the round trip, or listed in
  `expected-red.yaml` `kinds:` with a reason (`UNMODELLED_SCHEMA` otherwise). Five schemas are listed: `charter` and
  the four Markdown companion schemas, which are modelled with their owners.
- **One parameterized gate test runs over `built-in`, `org-base` and `project`**, with one report per pack under
  `build/reports/whole-pack/`.
  - File entries, known failures and exclusions now start with the name of their example pack.
  - `empty_kinds` is gone. Every modelled kind must have a file in some example pack.
  - A refused file entry pins the whole refusal.
- **`project` is round-tripped for the first time.** Its `project.graph.yaml` is red on its `impacts` edge, like the
  `org-base` fragment ([#477](https://github.com/spec-kitty/spec-kitty-charter/issues/477)). Both are pinned, and they
  expire when #477 lands.
