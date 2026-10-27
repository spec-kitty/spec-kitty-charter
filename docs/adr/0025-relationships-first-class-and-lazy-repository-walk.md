---
title: "ADR-0025: Relationships are first-class domain value objects, walked lazily through per-kind repositories"
description: Why relationships are first-class domain value objects over a behavior-carrying RelationType, identified by the component pair, and walked on demand through per-kind repositories.
doc_status: active
updated: 2026-10-27T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0006-onion-vertical-slices-archunit
  - adr/0024-doctrine-artifact-family-contract
  - adr/0017-pack-schemas-imported-as-canonical-json-schema
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - architecture/logical-model
  - context/domain
audience:
  - profile: core-maintainer
    guidance_text: Read this for how the Java domain models the Charter Reference Graph — relationships as value objects, the behavior-carrying relation type, and on-demand traversal.
  - profile: charter-service-contributor
    guidance_text: Read this before touching the graph — what a relationship is, what relationships() returns, where the walker lives, and why DrgEdge is to become a storage DSO.
---

# ADR-0025: Relationships are first-class domain value objects, walked lazily through per-kind repositories

## Status

**Accepted** — 2026-10-25. Decider: Stijn Dejongh (operator).

Revises the charter-reference-graph view of [ADR-0024](/adr/0024-doctrine-artifact-family-contract/): the family spine's `edges()` becomes `relationships()`, and `DrgEdge` leaves the domain interface for the storage edge.

Decision 1 (identity) is revised by [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/): the unordered-pair collapse is retained on a bounded, conditional basis.

Superseded in part by [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) (2026-10-26): `impacts` is an artifact's per-value list, not a field of a `Relationship`; tension is the `tension` relationship.

Superseded in part by [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) (accepted 2026-10-27): decision 5's "record equality is the round-trip fidelity assertion" no longer holds. Round-trip fidelity is equality of meaning between the source file and the written file, which `DrgEdge` record equality alone cannot show.

## Context

The charter service already names the Charter Reference Graph (CRG) in the kernel — `DrgNode`,
`DrgEdge`, the `Relation` enum, and a `DoctrineGraph` that indexes edges by `(source, relation)`.
Today that machinery is a latent **multigraph**: record equality on `DrgEdge` is all-fields, the
index admits several edges per ordered pair, and `DoctrineGraph.outgoing(source, relation)` is
written for a transitive walk. None of it has a production consumer. `ArtifactEdges` mints exactly
one relation per authored reference (typed → `suggests`, bare → `requires`), no *artifact-derived*
edge mints an `impacts` strength (authored graph shards can, via PackPolicy), and real built-in
graph shards carry `edges: []` or single edges. The
multigraph capacity is unused, and the cascade/activation engine the index anticipates is unbuilt.

Modelling the two remaining regular artifact kinds (toolguide, procedure) forced the question of
how a component expresses its relationships, and how a consumer walks them. Two shapes were on the
table: keep the kernel's edge-list-and-index multigraph, or promote relationships to a first-class
domain concept the components own. The second also raised a memory concern: a naïve walker would
materialize the whole CRG in application memory, which scales with pack size and defeats the
per-kind repository boundaries of [ADR-0006](/adr/0006-onion-vertical-slices-archunit/).

## Decision

1. **A `Relationship` is a first-class domain value object** (`kernel.domain`): `from: Urn`,
   `to: Urn`, `type: RelationType`, `rationale: Optional<String>`, `when: Optional<String>` — an
   authored edge condition that must round-trip (today `ArtifactEdges` carries `when` on typed
   refs). Its **identity is the unordered
   pair `{from, to}`** — `equals`/`hashCode` compare the pair only — so **at most one relationship
   may exist between two components**. The domain CRG is a **simple graph**, not a multigraph. The
   `from`/`to` order is still stored so directed types know their orientation; uniqueness is on the
   unordered pair. The identity and its round-trip handling are governed by
   [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) (bounded upstream
   normalization; a revisit trigger when a symmetric relation is minted).

2. **`RelationType` is a behavior-carrying enum** (`kernel.domain`): the closed catalogue of the
   CRG relations, each constant holding its `Directionality` (so **directionality is derived from
   the type**, never stored on the relationship) and a `Propagation` functional interface the
   traversal consults to decide activation flow. This is a strategy-factory on the enum: the
   cascade walker asks `type.propagation()` instead of switching on the relation. New relation
   semantics are a new constant with its own behavior, not a change to every consumer.
   `RelationType` supersedes the kernel `Relation` enum (a rename plus added behavior); the
   glossary's "CRG Relation" denotes a `RelationType` constant.

3. **A charter component owns its relationships.** `CharterArtifact` gains
   `relationships(): List<Relationship>`, superseding `edges()`. It returns the relationships for
   which the component is the **effective origin**: every relationship whose `from` is this
   component, **plus** every `BIDIRECTIONAL` relationship whose `to` is this component. A purely
   directed relationship with this component as `to` is **not** returned — a walk does not climb
   back up a `requires`.

4. **Traversal is lazy and repository-backed; the whole graph is never materialized.** A per-kind
   repository `load` returns the domain object **with its `relationships()` already populated** (the
   storage adapter assembles them from the graph shard). Following a relationship resolves its `to`
   URN on demand: a **`ComponentResolver` port** dispatches on `urn.kind()` to that kind's
   repository/read service and fetches only that neighbor. A **`GraphWalker`** visitor
   (`onNode`, `onRelationship`, each returning a `Descent` of `CONTINUE` / `SKIP` / `STOP`) is driven
   from the **application layer**, where the ports exist, with a visited-URN cycle guard. The kernel
   stays pure (ADR-0006): it defines `Relationship`, `RelationType` and the `GraphWalker` type; it
   does not fetch.

5. **`DrgEdge` is demoted to a storage DSO** (`packs.infra.storage`): a flat, relation-token-based
   record whose record equality is the round-trip fidelity assertion (the DSO pattern of
   [ADR-0024](/adr/0024-doctrine-artifact-family-contract/)), carrying `impacts`, `when` and
   `reason`. A mapper converts `Relationship` ↔ `DrgEdge` at the YAML boundary; the serialized shard
   round-trips per [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) —
   achieved through upstream source normalization, never a charter read-time rewrite, not asserted as
   automatic. `DoctrineGraph` holds domain `Relationship`s for serialization and validation;
   its `(source, relation)` in-memory index no longer serves runtime traversal, which is the walker's
   job.

## Consequences

### Positive

- The cascade/activation engine, when built, has one seam: `GraphWalker` + `RelationType.propagation()`.
  No consumer switches on the relation.
- Memory is bounded by the walk, not the pack: components are fetched on demand through the
  repository they already belong to, honouring the vertical-slice boundaries of ADR-0006.
- Simple-graph identity makes "the relationship between A and B" a single, well-defined object and
  rules out contradictory or duplicate edges; a `requires` cycle (A→B and B→A) cannot be expressed.
- The domain no longer leaks `DrgEdge`, a serialization-shaped record, through its interface.

### Negative / costs

- A walk pays one repository call per resolved neighbor (bounded by the cycle guard). Acceptable;
  the alternative materializes the whole CRG.
- Unordered-pair identity forbids opposite-direction directed edges between the same pair. This is
  intended for the regular artifacts; genuine bidirectional **runtime handoffs** (`delegates_to`)
  are mission/profile territory and are deferred, not solved here.
- `impacts` signed strength lives on the DSO only. `Relationship` gains an `impacts` field when the
  tension model is actually built; nothing mints `impacts` today.
  *Superseded in part by ADR-0028: `impacts` belongs on the artifact as a per-value list, and tension is a `tension` relationship, so `Relationship` gains no `impacts` field.*

### Neutral

- The interim relation mapping (typed → `suggests`, bare → `requires`) is unchanged and still
  provisional pending confirmation against core's extractor
  ([#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167)); it now lives in the
  `ArtifactEdges` minting and the `Relationship` ↔ `DrgEdge` mapper.

## Alternatives considered

- **Keep the kernel multigraph and its `outgoing` index.** Rejected: no production consumer needs
  multiplicity, `impacts` is unminted, and a simple graph keyed by the component pair matches the
  domain the operator wants to express.
- **Materialize the full `DoctrineGraph` in application memory and walk it there.** Rejected: it
  scales with pack size and crosses the per-kind repository boundaries; the on-demand resolver keeps
  the walk inside the slices.
- **Keep `type` a plain enum with no behavior.** Rejected: it forces the cascade engine and every
  other consumer to switch on the relation, the condition this ADR exists to remove.
- **Carry `impacts` on `Relationship` now.** Deferred: the tension model is unbuilt and nothing
  mints `impacts`; adding the field before there is a writer or reader would be speculative.

## References

- [ADR-0006: Onion vertical slices with ArchUnit](/adr/0006-onion-vertical-slices-archunit/)
- [ADR-0024: Charter artifact family contract](/adr/0024-doctrine-artifact-family-contract/)
- [ADR-0017: Pack schemas imported as canonical JSON Schema](/adr/0017-pack-schemas-imported-as-canonical-json-schema/)
- [ADR-0027: CRG relationship identity is a bounded, conditional unidirectional collapse](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/)
- [Logical model](/architecture/logical-model/)
- [Relationship identity collapse review](/development/notes/relationship-identity-collapse-review/)
