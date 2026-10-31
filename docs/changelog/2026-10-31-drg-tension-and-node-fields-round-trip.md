---
okf_version: "0.2"
title: A tension edge and the fields of a graph node round-trip as written
description: An in_tension_with edge is held as a tension and written back as written, and graph nodes keep their tags, provenance and body path through a read and a write.
doc_status: active
updated: 2026-10-31T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
audience:
  - profile: pack-author
    guidance_text: Read this for how an in_tension_with edge and a graph node's tags, provenance and body path are now read and written.
  - profile: charter-service-contributor
    guidance_text: Read this for the Tension domain value, the new graph-node fields and the writers' refusals.
---

# 2026-10-31 — A tension edge and the fields of a graph node round-trip as written

**A tension edge stays a tension** ([#235](https://github.com/spec-kitty/spec-kitty-charter/issues/235)).
An `in_tension_with` edge used to be read as the `impacts` relation with strength −1. It was then
written back as a plain `impacts` edge with no strength, so the tension was lost.

The service now holds it as a tension, the ADR-0028 relationship between two rules, beside the
structural relationships and outside their one-per-pair identity. It writes the edge back as the
`in_tension_with` edge it was, with its condition and reason. A pair may hold a tension and a
structural relationship at once, as `DIRECTIVE_024` and `DIRECTIVE_025` do.

**A graph node keeps every declared field** ([#236](https://github.com/spec-kitty/spec-kitty-charter/issues/236)).
The domain node now holds:

- a shard node's `provenance`;
- a shard node's `tags`, in written order;
- a fragment node's `body_path`.

A writer refuses a field its document shape cannot hold instead of dropping it.

**What changes for an operator:**

- **The whole-pack gate.** `directive.graph.yaml` and `anti_pattern.graph.yaml` are off its
  expected-red list: the built-in pack's tension edges and tagged anti-pattern nodes are green.
- **The registered-relationship shard.** A hand-edited `registered.graph.yaml` that holds a tension
  is now reported. The next registration in that pack is refused (`pack_unreadable`) instead of
  dropping the tension. The service never writes a tension there.
- **No HTTP change.** No operation, schema, enum value or wire shape changes. The `Relation`
  description no longer states the −1 reading.

The representation is recorded in
[ADR-0045](/adr/0045-tension-beside-the-relationships-and-nodes-keep-every-declared-field/), which
is proposed and pending the operator's acceptance.
