---
okf_version: "0.2"
title: "Pack composition: depends_on, the resolver and qualified references"
description: "Breaking: pack identities are ULIDs and bare built-in references are refused. Packs declare depends_on; the offering resolves it."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/logical-model
  - architecture/descriptors
  - architecture/resolution-model
  - guides/compose-packs
  - adr/0055-cutover-sequencing-the-java-service-first
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: pack-author
    guidance_text: Read this for what changes for your pack.
  - profile: governance-lead
    guidance_text: Read this for what landed.
---

# 2026-11-03 — Pack composition: `depends_on`, the resolver and qualified references

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142), keystone [#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145).

**Breaking: `pack.yaml` identities are checked, and a bare reference to a built-in artifact is now refused.** Packs can
declare `depends_on`.

**Now** (before, identities were free text):

- **Breaking:** `pack_id`, `parent_pack`, `depends_on[].pack_id` and `overrides[].member` must be ULIDs, and `name`
  must match `^[a-z0-9][a-z0-9_-]*$`; else the pack is unreadable (`pack_unreadable`). `pack_level` is required
  when `depends_on` is declared.
- **Migration:** depend on built-in and write its references as `built-in::<urn>`
  ([guide](/guides/compose-packs/#migrating-an-existing-pack)).
- `GET /offering` serves the compose, dependencies first. A broken compose refuses every composed read; a
  pack-scoped read still answers.
- `depends_on: [{pack_id, range}]` (PEP 440), `overrides` and `<pack-name>::<urn>` references are accepted.
- Eleven composition refusals, each a 500 (not retryable), naming the violating pack in `detail` and in `packs`, a list
  of `{descriptorPackId, packId, name}` ([table](/guides/compose-packs/#read-a-refusal)).
- Registering a relationship whose `to` the compose cannot resolve is refused with 422 before anything is written:
  `pack_reference_*` ([ADR-0057](/adr/0057-a-refusal-code-may-declare-a-status-per-operation/)).
- **Breaking:** `GET /offering` refuses with `pack_unreadable` when any file of a configured pack is invalid or cannot
  be read, not only its `pack.yaml`. Known gap: the pinned built-in pack cannot be served by it until open-packs#75 lands.
- New: `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references`
  ([how references resolve](/guides/compose-packs/#see-how-references-resolve)).

**Deferred:** [#357](https://github.com/spec-kitty/spec-kitty-charter/issues/357) (`overrides` clears the refusal only), #358
(any pack may declare it), #359 (pack pin facts), #360 (activation roots), #363 (`internal`).
