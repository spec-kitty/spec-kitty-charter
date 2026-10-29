---
okf_version: "0.2"
title: The markdown content form is built for file-kind rules
description: A files rule may declare content_form markdown with a structure schema; its files are read into a Markdown model, compared by it and written canonically.
doc_status: active
updated: 2026-10-29T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0040-content-form-and-payload-kinds
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before adding a Markdown file kind, such as a pack's pack.md or a toolguide's guide.
  - profile: pack-author
    guidance_text: Read this for which Markdown is refused as ambiguous and what a canonical write changes.
  - profile: core-maintainer
    guidance_text: Read this for the markdown content form of file-kinds.yaml and its structure schemas.
---

# 2026-10-29 — The markdown content form is built for file-kind rules

**A `files` rule of `contracts/packs/file-kinds.yaml` may now declare `content_form: markdown`.**
Its files are read into a Markdown model: optional frontmatter, an optional H1 title, a preamble,
and the H2 sections in order, each a title and its content. A heading inside a fence, and any H3 or
deeper, stays in its section's content. Two files mean the same when their models are equal, so
the blank lines between blocks and the final newline do not count, while one changed character in
a section does. A file is written back in one canonical form and read back before it is kept.

**Ambiguous Markdown is refused, naming the line**: an unclosed fence, a carriage return or byte
order mark, an H1 that is not the first block, an H1 or H2 indented by one to three spaces, or a
heading underlined with `===` or `---`. An empty file is a valid, empty document.

The rule's `schema` is a **structure schema**: a standard Draft 2020-12 schema over the parsed
model, loaded as strictly as every other pack schema, and refused when the table loads if it does
not load. The whole-pack gate compares a Markdown file by its model and reports a difference at its
place in the model (`sections[1].content`); Markdown text is never read as YAML.

**What changes for an operator:** nothing yet. No bundled rule declares `markdown`, so every gate
outcome at the pin is unchanged; the toolguide guides (#194) and #195's `pack.md` are its first
files. `text` stays declared and not built.
[ADR-0040](/adr/0040-content-form-and-payload-kinds/) (proposed) carries a dated amendment.

Issue: [#194](https://github.com/spec-kitty/spec-kitty-charter/issues/194), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
