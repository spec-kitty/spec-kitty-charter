---
okf_version: "0.2"
title: spec-kitty-charter documentation
description: Documentation home for the Java charter service and its OpenAPI contract, the Brain Kitty governance producer of Spec Kitty.
doc_status: active
updated: 2026-10-19T00:00:00Z
kind: Hub
audience:
  - profile: charter-service-contributor
    guidance_text: Start here to find the section that answers your question, and the one status note that applies to every page.
  - profile: core-maintainer
    guidance_text: Use the section list to find the plans and decisions that need core action.
  - profile: governance-lead
    guidance_text: Read this for what the charter is and which section answers your question.
---

# spec-kitty-charter documentation

Documentation for **spec-kitty-charter**: the Java charter service and the OpenAPI
contract it is built against. The charter is the governance authority of the
[Brain Kitty](/glossary/product-lines/#brain-kitty) product line. It resolves Charter Packs (built-in, org and project) into the guidance that
Spec Kitty's runtime acts on.

This tree follows the [doc-kitty](https://github.com/spec-kitty/doc-kitty) convention:
every folder is a section whose `README.md` is its index, every page carries frontmatter,
and the sections are registered in [`_meta/sections.yaml`](https://github.com/spec-kitty/spec-kitty-charter/blob/main/docs/_meta/sections.yaml).

> **Status: bootstrap.** No code lives here yet. [What each page's status line means](#status).

## Sections

- [Context](/context/) — why the charter is being extracted and rewritten, the
  vocabulary it uses, and the systems around it. **Start with
  [the product](/context/product/).**
- [Glossary](/glossary/) — the charter, governance and product-line terms, imported from
  the core glossary.
- [Architecture](/architecture/) — the target design: containers, the hexagonal service,
  the contract and its generated DTOs, the strangler stages and the service lifecycle. The
  [logical model](/architecture/logical-model/) and the [descriptors](/architecture/descriptors/)
  describe the charter components themselves.
- [Decision Records](/adr/) — decisions taken in this repository, and the core ADRs
  they build on.
- [Plans](/plans/) — the bootstrap roadmap and the open questions.
- [API](/api/) — the public API: the service contract, whose three read operations are built,
  the Javadoc of the service, and the Python package surface, which is designed only.
- [Conformance](/conformance/) — where the service answers differently from core on purpose,
  in one divergence register.
- [Development](/development/) — the conventions for changing the API: resource naming,
  hypermedia links and HTTP status codes.
- [Guides](/guides/) — contributing, building the service and building the docsite.
- [Changelog](/changelog/) — dated record of notable changes.

## Quick reference

- Core CLI: [`spec-kitty/spec-kitty`](https://github.com/spec-kitty/spec-kitty)
- Event contracts: [`spec-kitty/spec-kitty-events`](https://github.com/spec-kitty/spec-kitty-events)
- Open Charter Packs: [`spec-kitty/spec-kitty-open-packs`](https://github.com/spec-kitty/spec-kitty-open-packs)
- Docs toolkit: [`spec-kitty/doc-kitty`](https://github.com/spec-kitty/doc-kitty)
- Extraction epic: [spec-kitty#6099](https://github.com/spec-kitty/spec-kitty/issues/6099).
  Its import-move precondition is done (spec-kitty#6188)

## Status

The first code lives here: the charter contract at `1.0.0-SNAPSHOT` with three read
operations, and the Java service that answers them ([Roadmap](/plans/roadmap/) steps 1 and
2). Everything else the pages describe is the agreed direction and the target design. Each
page opens with what it gives you, then carries one status line, using these labels:

- **Partly built.** Some of it exists in this repository; the line says which part.
- **Design only.** Described here, built nowhere.
- **Exists in core.** The behavior is core's Python charter today. The design here must
  reproduce it, and core stays the reference until this repository's contract is the source
  of record.
- **Direction.** Stated by the operator, not yet in an ADR.
- **Designed API.** A part of the public service contract designed here but not yet built.
  No contract version has been released from this repository.

The governing decisions are core ADR [`2026-10-12-2`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-12-2-charter-extraction-to-its-own-repository.md) (the charter gets its own repository),
core ADR [`2026-10-03-1`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/4.x/2026-10-03-1-charter-read-write-service-strangler.md) (a staged Java service strangler) and this repository's
[ADR-0001](/adr/0001-java-service-with-openapi-contract/) (Java, contract-first). Where the
Python charter lives while the strangler runs is decided in [open question 2](/plans/open-questions/).
