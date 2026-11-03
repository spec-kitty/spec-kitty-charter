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
- **Migration:** add `depends_on` on built-in (`01ARWG13C000000000000000FG`) with a range, and write each built-in
  reference as `built-in::<urn>` ([guide](/guides/compose-packs/#migrating-an-existing-pack)).
- `depends_on: [{pack_id, range}]` (PEP 440) and `overrides` are accepted.
- `GET /offering` serves the compose, the closure of the configured packs, dependencies first. A broken compose
  refuses every composed read, the reference walk included. A pack-scoped read of an artifact still answers.
- A cross-pack reference is `<pack-name>::<urn>`; the artifact's URN stays bare.
- Eleven composition refusals, each a 500 (not retryable) naming the violating packs; causes and fixes are
  in the [table](/guides/compose-packs/#read-a-refusal). `pack_sibling_refinement_conflict` is now `pack_sibling_conflict`.
- New: `GET /packs/{packId}/artifacts/{kind}/{artifactId}/references` shows how an artifact's references
  [resolve](/guides/compose-packs/#see-how-references-resolve). It is a composed read.
- Wire: `dependsOn[].packId` is now `dependsOn[].descriptorPackId`. `OfferingPack.packId` is the configured id,
  `OfferingPack.descriptorPackId` the descriptor ULID. `PackPin` carries no `dependsOn` (#359).

**Deferred:** [#357](https://github.com/spec-kitty/spec-kitty-charter/issues/357) (`overrides` clears the refusal only), #358
(any pack may declare it), #359 (pack pin facts), #360 (activation roots), #363 (`internal`, via open-packs#113).
