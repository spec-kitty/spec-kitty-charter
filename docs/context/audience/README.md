---
okf_version: "0.2"
title: Audience
description: The readers this documentation is written for — one persona page per reader, with the concern each page must answer — and how a page names its reader.
doc_status: active
updated: 2026-10-14T00:00:00Z
type: Context
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Use this index to find which pages are written for you, and to name your reader when you add one.
---

# Audience

Every page in this documentation names the reader it is written for. A page does that in
its frontmatter, with an `audience` entry that points at one of the persona pages below and
says what that reader should take from the page. Pages without a reader tend to explain
everything to everyone.

## The readers

| Persona | Group | Primary concern |
|---|---|---|
| [Charter service contributor](/context/audience/charter-service-contributor/) | inside this repository | What to build, in what order, and which core behavior to reproduce |
| [Core maintainer](/context/audience/core-maintainer/) | outside, owns the consuming side | What core must change, and what evidence gates each stage |
| [Pack author and doctrine curator](/context/audience/pack-author/) | outside, writes the inputs | Which descriptor files exist, what their fields mean, and how packs resolve |
| [Governance lead](/context/audience/governance-lead/) | outside, adopts what is produced | Governance that reaches every agent, or fails loudly |
| [Evaluator](/context/audience/evaluator/) | outside, decides on adoption | Boundaries, status and trade-offs stated plainly |
| [Individual developer](/context/audience/individual-developer/) | outside, adopts what is produced | Their own rules in force across agent sessions, with little setup |
| [Non-engineer developer](/context/audience/non-engineer-developer/) | outside, adopts what is produced | Good practice applied for them, explained without engineering jargon |
| [Contract consumer](/context/audience/contract-consumer/) | outside, calls the charter | A stable, versioned surface and a predictable error model |

The group is the delivery boundary of this repository, as in the
[core audience catalog](https://github.com/spec-kitty/spec-kitty/blob/main/docs/context/audience/index.md):
a contributor works inside it, the others depend on what it produces.

## Naming a reader on a page

```yaml
audience:
  - profile: pack-author
    guidance_text: Read this to find the file, fields and naming of each descriptor you author.
```

- `profile` is the file name of a persona page in this folder, without `.md`. The docsite
  resolves it to `context/audience/<profile>` and links it from the page.
- `guidance_text` is one sentence, specific to the page, on what this reader gets from it.
- A page may name more than one reader. Name a second reader only when the page really
  serves both.

Core's catalog records the reader as a file path in an `audience:` string. This repository
uses the doc-kitty form instead, a list of `profile` and `guidance_text`, because the
docsite toolkit validates and renders that form and rejects a bare path.

## Conventions

1. A persona page describes a role, not a person, and stays short: goal, what the reader
   needs, what frustrates them, and the signal that the documentation served them.
2. Personas follow this repository's decisions. If an ADR changes who consumes the charter,
   change the persona in the same pull request.
3. Add a persona only when a page cannot be written well for one of the six above.
