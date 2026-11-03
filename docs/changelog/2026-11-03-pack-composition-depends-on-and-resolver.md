---
okf_version: "0.2"
title: "Pack composition: depends_on, the resolver and pack-qualified references"
description: "Packs declare depends_on and overrides; the offering resolves the compose and refuses a broken one by name. Breaking: pack identities are ULIDs and names an identifier grammar."
doc_status: active
updated: 2026-11-03T00:00:00Z
type: Changelog
kind: Changelog
related:
  - architecture/logical-model
  - architecture/descriptors
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - plans/pack-ecosystem-work-breakdown
audience:
  - profile: pack-author
    guidance_text: Read this for what changes when your pack declares a dependency.
  - profile: governance-lead
    guidance_text: Read this for which items of the pack-ecosystem work breakdown landed.
---

# 2026-11-03 — Pack composition: `depends_on`, the resolver and pack-qualified references

Epic [#142](https://github.com/spec-kitty/spec-kitty-charter/issues/142), keystone
[#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145).

**A pack can now say what it builds on, and the offering resolves the compose or refuses it. Breaking: `pack.yaml`
identities are checked.**

**Before:**

- `pack.yaml` declared no dependency, and `pack_id`, `parent_pack` and `name` were free text.

**Now:**

- **Breaking:** `pack_id`, `parent_pack`, `depends_on[].pack_id` and `overrides[].member` must be ULIDs, and
  `name` must match `^[a-z0-9][a-z0-9_-]*$`. A pack that breaks this is unreadable (`pack_unreadable`). The
  real packs already comply.
- `depends_on: [{pack_id, range}]` (a PEP 440 specifier set; `pack_level` becomes required) and
  `overrides: [{artifact, member}]` are accepted. A malformed range is refused on read, naming the declaring pack.
- `GET /offering` serves one closure: dependencies first, a shared one once, merge order kept. A broken compose
  is refused, naming the pack: cycle, unknown dependency, out-of-range version, unresolvable `parent_pack`,
  level violation, duplicate name or `pack_id`, unsettled siblings. Two ranges on one pack that share no
  version, touching ones such as `<2` and `>=2` included, are refused.
- A cross-pack reference is written `<pack-name>::<urn>` on graph edges and typed references. At compose time a
  bare reference that resolves only in another pack, and a qualified one outside the referring pack's own
  `depends_on` closure, are refused.
- Each offering pack carries `descriptorPackId`, the `pack_id` dependency edges name.

**Not yet:**

- An `overrides` edge only clears the sibling refusal; the member's version is not in force (#357), any pack
  may declare it (#358), the pack pin lacks the facts (#359), and the closure roots come from the activation
  set (#360, with #125).
- An `internal` plus `built-in` compose is refused until internal's references are qualified
  (open-packs#113, charter #363).
