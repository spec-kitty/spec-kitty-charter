---
okf_version: "0.2"
title: Glossary packs and skills round-trip through the domain
description: Glossary packs and skills are validated by closed schemas and round-trip through the domain; a skill's body is read, written and fingerprinted with it.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0039-a-kind-binding-can-carry-companion-files
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
audience:
  - profile: pack-author
    guidance_text: Read this for what a glossary pack and a skill must look like now that both are validated, and how a skill's body travels with it.
  - profile: charter-service-contributor
    guidance_text: Read this before binding a kind whose artifact names another file.
  - profile: core-maintainer
    guidance_text: Read this for the glossary-pack schema now canonical here; nothing on the wire changes.
---

# 2026-10-27 — Glossary packs and skills round-trip through the domain

**A glossary pack or a skill with a field its schema does not declare is now refused.** Both kinds
were excluded from validation; `contracts/packs/file-kinds.yaml` now maps `**/*.glossary-pack.yaml`
to a new `glossary-pack` schema, authored here from core's model (core exports none), and
`**/*.skill.yaml` to `skill`, core's schema imported as JSON. Both are closed. Every glossary pack
and skill of the pinned packs passes. A quoted `confidence` or `"true"` flag, which core's model
would convert, is refused as written ([DIV-010](/conformance/divergence-register/)).

**A skill's prompt body travels with the skill.** The file its `body_path` names is read with it,
written back beside it byte for byte, and digested into the source fingerprint. A body that is
missing or not allowed makes the skill a `companion` violation naming the path. A kind binding can
carry such companion files ([ADR-0039](/adr/0039-a-kind-binding-can-carry-companion-files/), proposed), which
asset payloads and templates (#193) extend next.

Both kinds write back with their meaning unchanged
([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)): terms keep their order,
`confidence` stays a number, and no default or forced `model_invocable` is added to a skill. The
whole-pack gate shows the built-in glossary pack green; every pinned glossary pack and skill
round-trips in a corpus test.

**What changes for an operator:** the fingerprints and ETags of packs that hold glossary packs or
skills (`built-in`, `qa`, `internal`, `event-driven`) change once. **What does not change:** the HTTP contract.
Glossary packs and skills are not served on the wire.

Issue: [#192](https://github.com/spec-kitty/spec-kitty-charter/issues/192), part of
[#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
