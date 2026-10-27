---
okf_version: "0.2"
title: Round-trip fidelity is equality of meaning
description: ADR-0036 is proposed. The whole-pack round trip compares meaning, not bytes; unknown fields are refused; CRG edges compare as a set.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
audience:
  - profile: charter-service-contributor
    guidance_text: Read this for what the round-trip gate compares and how an unknown field is refused.
  - profile: pack-author
    guidance_text: Read this for what a pack must look like to pass the round trip.
---

# 2026-10-27 — Round-trip fidelity is equality of meaning

**The whole-pack round trip now has a definition of "the same".** [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) (proposed; it records the operator's rulings on [#188](https://github.com/spec-kitty/spec-kitty-charter/issues/188)). The gate compares the source file with the written file by meaning: key order, quoting, layout and comments do not count, and list order does, except in CRG edges and nodes, which compare as sets. An edge is identified by its directed `(source, target, relation)`. A field outside the schema is refused through the existing schema-violation path, so `pack_unknown_key` will be retired; core already treats such a key as an error. An inconsistency found in a source pack is fixed in `spec-kitty-open-packs`, never worked around here. A per-kind table and sixteen worked examples make the rules buildable for the round-trip gate (#187) and the whole-pack seam (#189).

No code changes yet. Three questions are open on #188: expected failures while an upstream fix is pending, the charter's "round-trips exactly" wording, and the manifest hashes.
