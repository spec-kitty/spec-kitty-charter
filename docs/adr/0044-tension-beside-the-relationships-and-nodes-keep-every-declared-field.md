---
title: "ADR-0044: A tension is held beside the relationships, and a graph node keeps every declared field"
description: Decides the domain representation of the ADR-0028 tension relationship and of the graph-node fields, so a graph document round-trips without loss of meaning.
doc_status: draft
updated: 2026-10-31T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - adr/0025-relationships-first-class-and-lazy-repository-walk
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before changing how the domain holds a tension, a graph node or a graph document, or before writing a graph back.
  - profile: pack-author
    guidance_text: Read this for how the service reads and writes an in_tension_with edge and the tags, provenance and body path of a graph node.
  - profile: contract-consumer
    guidance_text: Read this for why tension is not a relationship type on the HTTP contract.
---

# ADR-0044: A tension is held beside the relationships, and a graph node keeps every declared field

## Status

**Proposed** — 2026-10-31, by the mission agent. Decider: Stijn Dejongh (operator); acceptance
pending.

- **Origin**: issues [#235](https://github.com/spec-kitty/spec-kitty-charter/issues/235) and
  [#236](https://github.com/spec-kitty/spec-kitty-charter/issues/236), part of epic
  [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186).
- **Decision moment**: `specify.domain.tension-representation`, under
  `kitty-specs/drg-tension-and-node-fields-01M68A0T/decisions/`. The mission agent recorded it because
  it needs no HTTP contract change, and it is logged as an open review item for the operator.
- **Squad review**: the plan-point adversarial squad's findings S1–S20 are in that mission's
  `research.md`.

This record answers, for the domain and the pack files,
[ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) decision 5 ("how … the
relationship appear[s] in the domain, the pack files and the contract"). The contract is unchanged.

## Context

The whole-pack round-trip gate ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/))
found two losses on the domain path of a graph document (reader → pack policy → shard writer).

- **#235.**
  - An `in_tension_with` edge was read as the `impacts` relation with strength −1. That was the reading
    of core ADR `3.x/2026-07-26-3`, which ADR-0028 replaced.
  - It was written back as a plain `impacts` edge with no strength, so the tension was lost.
  - The built-in pack holds the case ADR-0028 decision 3 allows: `DIRECTIVE_024` is in tension with
    `DIRECTIVE_025`, and `DIRECTIVE_025` requires `DIRECTIVE_024`.
- **#236.**
  - The domain node had a URN and a label only, so a shard node's `provenance` and `tags`, and a
    fragment node's `body_path`, were dropped.
  - ADR-0036 makes every declared field part of a node's value. E10 requires that `tags` are kept;
    E15 makes `tags` a sequence.

Relationship identity is the unordered `{from, to}` pair
([ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/)), and ADR-0028
decision 3 counts a tension apart from it. A tension therefore cannot be a `Relationship` of another
type without breaking either the identity or the coexistence rule.

A relation-type constant would also grow the `Relation` catalogue and the `Relation.yaml` enum, and
`/relationships` would accept and answer a new token. That is an HTTP contract change.

## Decision

### 1. A tension is a domain value beside the relationships

- **The value.** The kernel holds a tension as `Tension(from, to, when, rationale)`. It has no
  strength (ADR-0028 decision 1).
- **Where it lives.** A `DoctrineGraph` holds its tensions in declared order, beside its
  relationships.
- **What it is not.** A tension is not a `Relationship` and not a relation type, so:
  - the one-structural-relationship-per-pair identity does not apply to it;
  - relationship registration (`RelationshipMerge`) never sees it;
  - the HTTP contract does not change.
- **Identity.** A tension is compared as written, orientation included (ADR-0036 decision 4: the
  reader never turns an edge around).

### 2. `in_tension_with` is a storage spelling

Today's pack files write the tension `in_tension_with` (ADR-0036 decision 4). The word belongs to the
pack policy and the writers (`PackPolicy.TENSION`), not to the kernel.

- **Reading.** The pack policy binds the word to a `Tension`. An `in_tension_with` edge that also
  writes `impacts` is a finding, as before.
- **Writing.** The writers spell a tension back with the same word, with its condition and reason and
  no strength.
- **Renaming.** When the files move to ADR-0028's `tension`, only storage changes.

### 3. A graph node keeps every declared field

- **The node.** `DrgNode` holds its URN, its label, `provenance`, `tags` (an ordered list, duplicates
  kept) and `bodyPath`.
- **Which shape holds which field.**
  - A shard node is written `urn, kind, label?, provenance?, tags?`. A shard has no `body_path`.
  - A fragment node is written `id, kind, title?, body_path?`. A fragment has no node `provenance` or
    `tags`, and no edge `when`.
- **Refusals.** Each writer refuses a field its shape cannot hold (`IllegalArgumentException`) rather
  than drop it.
- **Absent fields.** An absent field is not written. An absent and an empty `tags` mean the same.

### 4. A fragment is written from the domain

`GraphShardWriter.writeFragment` writes an org fragment with no header. Its edges are written
`source, target, relation, impacts?, reason?`. No production path writes a fragment from the domain
yet; the writer proves that a fragment node's `body_path` survives the round trip.

### 5. The registered-relationship shard holds no tension

The service-written `registered.graph.yaml` holds registered, structural relationships only. A tension
found there is a finding (`PackPolicy.registeredGraph`), and the store then refuses to rewrite the
shard. Without the finding, the tension would be dropped in silence on the next registration.

Loading and validating a pack (`PackPolicy.judge`) still read that file with the ordinary graph rules,
so a hand-edited tension there loads and validates clean. Only a registration into the pack is refused
(`pack_unreadable`). Making validation refuse it too would stop the whole pack from loading after a
hand-edit. Carrying tensions through a registration rewrite would remove the refusal altogether.
Choosing between those is left to the operator; until then the paths differ on purpose.

### 6. Deferred: pack consistency of tensions

The reader keeps what is written. These two cases are pack inconsistencies that the gate reports and
that are fixed in `spec-kitty-open-packs`; the reader neither corrects them nor refuses them here:

- a second tension on the same pair, which breaks ADR-0028 decision 3's "at most one";
- a tension written in the non-canonical orientation (ADR-0036 decision 4).

The pinned pack has neither.

### 7. Merging keeps the first node whole

`DoctrineGraph` keeps the first declaration of a URN, now with all its fields, tags and provenance
included. Tensions from two merged graphs are concatenated, not de-duplicated. `merge` has no
production caller today.

## Consequences

### Positive

- **Tension stays tension.** An `in_tension_with` edge, alone or beside a structural relationship on
  the same pair, round-trips as written. Both expected-red entries for #235 and #236 leave the gate's
  baseline.
- **No silent loss.** A graph node no longer loses a declared field in silence. A field a shape cannot
  hold is refused, and a tension in the registered shard is reported.
- **No contract change.** `RelationType`, `Relation` and the `Relation.yaml` enum are unchanged. The
  `Relation.yaml` description and the contract CHANGELOG stop stating the −1 reading.

### Negative

- **Tension is not on the HTTP contract.** Exposing it is a separate contract decision.
- **Two writers.** The domain has a shard writer and a fragment writer, and the fragment writer has no
  production caller yet.
- **A hand-edited registered shard.** One that holds a tension now refuses the next registration in that
  pack until the tension is removed.

### Risks

- **The `impacts` relation stays.** It keeps today's reading and rules until it is retired (ADR-0028).
  Reviewers should not read its −1 to +1 range as tension.

## Alternatives considered

### A `RelationType.TENSION` constant

Not chosen. It would be an HTTP contract change, and it would still need an exception to pair
identity.

### Keep `impacts` −1 and special-case the writer

Not chosen. It models tension as the relation ADR-0028 retires, inside the pair identity it is not
part of. No `impacts` strength is carried in the domain (ADR-0025), so the writer could not tell an
authored `impacts: -1` from a tension.

### A shard-node type and a fragment-node type in the kernel

Not chosen. The graph has one node concept; the document shape is a storage concern.

## References

- [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/),
  [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/),
  [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/),
  [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)
- `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/Tension.java`, `DrgNode.java`,
  `DoctrineGraph.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java`
