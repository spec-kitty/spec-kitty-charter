---
title: "ADR-0045: A tension is a relationship of its own pair class, served under an activation, and a graph node keeps every declared field"
description: Decides how the model, packs and contract hold a tension, how the effective reads serve it with its reconcilers, and which fields a graph node keeps.
doc_status: active
updated: 2026-11-19T00:00:00Z
decision_status: "Accepted 2026-11-18 (operator rulings on #602; G1 to G3 on 2026-11-19); decisions 1, 2, 5 and 6 of the 2026-10-31 proposal replaced"
decision_date: '2026-11-18'
type: ADR
kind: ADR
related:
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0077-kind-taxonomy-and-step-authority
  - adr/0020-offering-and-activations
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before changing how the model holds a tension, a graph node or a graph document, before writing a graph back, or before changing what the effective reads answer about tensions.
  - profile: pack-author
    guidance_text: Read this for how a pack writes a tension edge, which tension edges refuse a pack, and the tags, provenance and body path of a graph node.
  - profile: contract-consumer
    guidance_text: Read this for the tension relation, the tensions an effective graph and an action context carry, and the activation's tension findings.
---

# ADR-0045: A tension is a relationship of its own pair class, served under an activation, and a graph node keeps every declared field

## Status

**Accepted** — 2026-11-18. Decider: Stijn Dejongh (operator), by rulings D1 to D12, F1 to F3 and the example-pack
ruling on [#602](https://github.com/spec-kitty/spec-kitty-charter/issues/602), relayed by the orchestrating session.

- **Proposed** 2026-10-31 by the mission agent, from issues
  [#235](https://github.com/spec-kitty/spec-kitty-charter/issues/235) and
  [#236](https://github.com/spec-kitty/spec-kitty-charter/issues/236) (epic
  [#186](https://github.com/spec-kitty/spec-kitty-charter/issues/186)); decision moment
  `specify.domain.tension-representation` under `kitty-specs/drg-tension-and-node-fields-01M68A0T/decisions/`.
- **Revised at acceptance.** The proposal held a tension as a `Tension` value beside the relationships, off the HTTP
  contract, spelled `in_tension_with` in the pack files. The operator ruled instead (#602, F1 (a), D1 (b), D11 (b))
  that a tension is a relation of the one catalogue, spelled `tension` everywhere, and that the reads serve it.
  Decisions 1, 2, 5 and 6 are rewritten; decisions 3, 4 and 7 (the graph-node fields) are unchanged; decisions 8 to
  13 are new. The file name keeps the proposal's slug so links hold.
- **Open review items** (calls made without a ruling, for the operator and the plan-point squad): decision 1's
  orientation on a registration write, decision 5's `relation` parameter on a removal, and decision 9's words on a
  tension edge only. The plan-point squad's findings and their dispositions are in
  `kitty-specs/serve-tensions-01M7RDX1/research/squad-plan.md`; rulings G1 to G3 settled the three that changed a
  ruling's meaning.

This record answers [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) decision 5 ("how …
the relationship appear[s] in the domain, the pack files and the contract").

## Context

- **The round trip.** The whole-pack round-trip gate
  ([ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/)) found two losses on a graph document: an
  `in_tension_with` edge read as `impacts` −1 and written back as plain `impacts` (#235), and a node's `provenance`,
  `tags` and `body_path` dropped (#236).
- **Identity.** Relationship identity is the unordered `{from, to}` pair
  ([ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/)), and ADR-0028 decision 3 lets a
  tension stand beside the one structural relationship of its pair: `DIRECTIVE_024` is in tension with
  `DIRECTIVE_025`, and `DIRECTIVE_025` requires `DIRECTIVE_024`.
- **Use.** A tension marks two rules that pull against each other. A third artifact reconciles it with
  `reconciles_tension`. A consumer needs, under an activation: the tensions in force, the tensions that bear on one
  action with what reconciles them, and the co-activated pairs nothing reconciles. Core answers these today (its
  governance bundle and its activation consistency check); that is evidence of behaviour, not the specification
  (ADR-0015).
- **The model is canonical** (charter companion, 2026-11-10): the rationalized shape replaces the old one outright,
  with no reader for the old spelling.

## Decision

### 1. A tension is a relationship, in a pair class of its own

- **The relation.** `RelationType.TENSION`, token `tension`, is a relation of the one closed catalogue. It is
  **bidirectional** (`Directionality.BIDIRECTIONAL`): neither rule is the source in meaning. Its endpoints are guidance
  to guidance (`G ↔ G`, the `GUIDANCE` set of `EndpointTable`). It carries `when` and `rationale` and no strength
  (ADR-0028 decision 1). The `Tension` value is removed; there is one model of a relationship.
- **Pair classes.** A relationship's identity is its unordered `{from, to}` pair **and its pair class**: structural
  (every relation but `tension`) or tension. ADR-0027's collapse holds within the structural class: at most one
  structural relationship per pair. Beside it, at most one tension per pair (ADR-0028 decision 3).
  `Relationship.samePair` stays the pair test; a new `sameSlot` (pair and class) is the identity
  `RelationshipMerge` upserts on, so a tension never replaces a `requires` on the same pair, nor the reverse.
- **Stored orientation** (ruling G1 (b), 2026-11-19). A tension is stored with the side its pack owns as `source`.
  When the pack owns both sides, the lexicographically smaller **bare** URN is the `source` (ADR-0036 decision 4); the
  pack qualifier is provenance and takes no part in the comparison. So every path that keys on an edge's source (the
  governance gate, the registration answer, removal, the references listing) stays in the owning pack.
  - In a pack file, a tension whose `source` the pack does not own, or a reversed tension between two owned sides,
    refuses the pack (decision 6).
  - On a registration write (`putPackRelationship` with `type: tension`), the wire's rule holds (`from` in the pack,
    `to` may be qualified). When both sides are in the pack and `from` is the larger, the service stores the pair the
    other way round. The answer echoes the request as written. *Open review item:* the pair is unordered in meaning,
    so the write normalizes rather than refuses.
  - A tension whose two sides have the same bare URN (two versions of one artifact) is refused, at load and on the
    wire.
- **The reads** answer every tension in canonical order: the smaller bare URN as `source` (decision 8).
- **The contract.** `Relation.yaml` gains `tension`. `TaxonomyRelation` and the `getKindTaxonomy` row rename
  `in_tension_with` to `tension`; the row keeps `heldAs: tension`, which now names the pair class.

### 2. `tension` is the one spelling

The pack schemas (`graph-shard.schema.json`, `org-fragment.schema.json`), the readers, the writers, the
taxonomy and the contract all spell the relation `tension`. `in_tension_with` is removed: a graph document that writes
it names an unknown relation and is refused as any unknown relation is. charter's example packs change in the same
commit. The open-packs content follows in a companion pull request (ADR-0070 decision 3: open-packs moves when it
bumps its pin).

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

### 5. A registration carries tensions

- **Write.** `putPackRelationship` accepts `type: tension`, through the ordinary path: the endpoint table, the
  governance gate, the strict closure check and the pair-class upsert of decision 1.
- **Registered shard.** `registered.graph.yaml` holds tension edges like any other. The rewrite on a registration
  carries every tension of the shard through; `PackPolicy.registeredGraph` no longer finds a tension a fault, and
  the 2026-10-31 refusal of a registration into a pack whose registered shard holds a tension is removed.
- **Duplicate.** A registered tension on a pair that another graph document of the pack already joins by a tension
  is refused, 422 `tension_duplicated`, so a write never leaves a pack that would be refused at load.
- **Removal.** `deletePackRelationship` takes an optional `relation` query parameter (a `Relation` token), judged per
  pair class. An unknown token is 400. Given, it removes only the registered edge of that relation on the pair.
  Absent, it removes every registered edge of the pair, structural and tension; when the pair also has an edge an
  author wrote outside `registered.graph.yaml`, the answer is 409 `relationship_not_registered`, whose detail names
  `relation`. *Open review item.*

### 6. A pack's tensions are consistent, or the pack is refused

Within one pack, across all its graph documents, each of these is a violation: it refuses the pack at load, at
validation and at registration, and is never corrected by the reader.

- **A second tension on the same unordered pair** (ADR-0028 decision 3's "at most one").
- **A tension in the wrong orientation**: its `source` is not owned by the pack, or both sides are owned and its
  `source` is not the smaller bare URN.
- **A tension of an artifact with itself**: both sides have the same bare URN.

Across packs, two tensions on one pair are not a fault: the merge collapses them (decision 8). charter's example
packs are canonical.

### 7. Merging keeps the first node whole

`DoctrineGraph` keeps the first declaration of a URN, now with all its fields, tags and provenance
included. Its relationships, tensions among them, merge on the pair-class identity of decision 1. `merge` has no
production caller today.

### 8. How a tension resolves under an activation

The effective reads resolve a tension as they resolve an edge (ADR-0020 D7):

- **Symmetric under overrides** (ruling G2 (a), 2026-11-19). A tension is absent when the authoring pack's own
  version of either side loses the layer merge. A side the authoring pack does not hold is re-pointed to the winner.
- After resolution, the endpoints are put in canonical order, and a resolved self-pair is dropped. Two tensions on
  the same pair from different packs collapse to one; the first in merge order (the closure's writer order, then
  document order) keeps its `when` and `reason`.
- The storage is never rewritten: the pack files keep the tension as written.

**Reconciled.** An artifact `R` reconciles the tension on `{A, B}` when `R` is in force and, after resolution, has a
`reconciles_tension` edge to `A` and one to `B`. An edge to one side only does not reconcile it. Every reconciler in
force is listed, sorted by URN, whether or not it is delivered to the action being read.

### 9. The effective graph carries the tensions in force

- `getEffectiveGraph` answers each tension whose two sides are both in force as an `EffectiveGraphEdge` with relation
  `tension`, its `source` the smaller URN, among the other edges in their usual sort. The tension edges come from the
  one projection of decision 8, never from the plain edge list, so a pair answers one tension edge.
- A tension edge also carries, from the authored edge, `when` and `reason` when it writes them, and always
  `reconciledBy`, the URNs of its reconcilers in force (possibly empty, unique, sorted). No other relation's edge
  carries them; the schema says so with a documented `oneOf`. *Open review item:* the words are added for tension
  edges only.

### 10. The effective action context carries the tensions delivered to it

- `getEffectiveActionContext` answers a new required member, `tensions`: each tension whose two sides are both listed
  in the context's `artifacts` in force (state active; a suggestion does not count), with `source`, `target`, `when?`,
  `reason?` and `reconciledBy` (every reconciler in force, decision 8).
- `depth` limits it only through what the context lists: `depth` governs the `suggests` reach, and a suggestion does
  not count.
- A mission type the activation does not put in force answers `tensions: []`.
- The governance walk does not follow `tension` (it is not a reach relation): a tension never brings an artifact into
  a context, a governance read or a cascade.

### 11. A new read: the activation's tensions and its findings

- `GET /api/v1/activations/{activationId}/tensions` (`getActivationTensions`) answers `ActivationTensions`:
  `activationId`, `revision`, `governanceFingerprint` (the closure fingerprint, as the skipped report),
  `tensions` (every tension in force, as in decision 9), `findings` and `_links` (`self`).
- It is linked from the `Activation` `_links` as `tensions`. Its ETag is its own (`EffectiveTag.Read.TENSIONS`) over
  the skipped report's inputs: the revision and the closure fingerprint. The effective tag's domain moves to v4,
  because the graph, the context and the activation answer new bodies for the same inputs.
- **A finding** is a tension in force that nothing in force reconciles. Each finding names `code`
  `unreconciled_tension`, the pair (`source`, `target`, canonical, so a tension authored either way gives one finding
  keyed on the sorted pair), `resolutions`, and `_links`.
- **It warns.** A finding never refuses a write or a read, and no activation write carries it as a warning.

### 12. The resolution vocabulary

- `resolutions` is a list over the closed vocabulary `[reconcile, deactivate_one_side]`, in that order (ruling G3
  (a), 2026-11-19).
  - `reconcile`: author an artifact that reconciles both sides (pack work). It is always listed and has no link.
  - `deactivate_one_side`: exclude one side. A finding links `exclude-source` and `exclude-target`, the exclusion
    writes (`deactivateActivationArtifact`, PUT, If-Match: the answer's `revision`) for each side, except a side that
    is locked or required, whose exclusion is refused. `deactivate_one_side` is listed only when at least one of the
    two links is present.
  - Every finding links `effective-source` and `effective-target`, the effective reads of its sides. The relation
    names follow the hypermedia table (`exclude` is already the name of that write).
- Core's two fixed strings are evidence, not the specification. The difference is in the divergence register.

### 13. What else the relation reaches

- **References.** `listPackArtifactReferences` and `listEffectiveArtifactReferences` answer a tension edge whose
  source is the artifact, as stored, like any other edge; the stored source is always owned by the pack (decision 1).
- **The taxonomy.** `getKindTaxonomy`'s row is `tension`, `heldAs: tension`, `directed: false`, with its `G ↔ G`
  pairs.
- **Not reached.** The governance walk, the activation cascade and the closure-reach rules do not follow `tension`.

## Consequences

### Positive

- **One model.** A tension is a relationship, with one catalogue, one merge, one writer and one wire spelling. The
  pair-class identity keeps ADR-0028 decision 3 and ADR-0027's collapse both true.
- **Tensions are served.** A consumer reads the tensions in force, those bearing on an action with their reconcilers,
  and the unreconciled co-activated pairs, with links to the fix.
- **No silent loss.** A graph node keeps every declared field; a registration keeps a pack's tensions; an inconsistent
  pack is refused, not corrected.

### Negative

- **Breaking for pack files.** `in_tension_with` is no longer read. open-packs migrates its content in a companion
  pull request before it bumps its pin; until then its pin stays on the release before this one.
- **Stricter loads.** A pack with a duplicate or a reversed tension, which loaded before, is refused.
- **Two writers.** The domain has a shard writer and a fragment writer, and the fragment writer has no production
  caller yet.

### Risks

- **The `impacts` relation is retired** with its strength ([ADR-0077](/adr/0077-kind-taxonomy-and-step-authority/)
  decision 6); nothing reads a negative strength as a tension.
- **Identity drift.** Code that compares relationships with `samePair` where it means identity would let a tension
  replace a structural relationship. `RelationshipMerge` and the registration use `sameSlot`; the tests pin both.

## Alternatives considered

### Keep the `Tension` value beside the relationships (the 2026-10-31 proposal)

Not chosen at acceptance. It kept the contract unchanged, but left two models of an edge, and a read that serves
tensions would have needed a shape outside `Relation`.

### A separate `tensions` list beside `edges` in the effective graph

Not chosen (D1). One edge list answers every relation, and a consumer that walks the graph sees the tension where it
sees the relationships.

### Answer the findings on the skipped report

Not chosen (D6). The skipped report lists each artifact not in force, once; a finding is a pair of artifacts in force.

### A shard-node type and a fragment-node type in the kernel

Not chosen. The graph has one node concept; the document shape is a storage concern.

## References

- [ADR-0020](/adr/0020-offering-and-activations/),
  [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/),
  [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/),
  [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/),
  [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/),
  [ADR-0070](/adr/0070-charter-tests-own-example-packs-not-open-packs-content/),
  [ADR-0077](/adr/0077-kind-taxonomy-and-step-authority/)
- `service/kernel/src/main/java/ai/speckitty/charter/kernel/domain/RelationType.java`, `Relationship.java`,
  `DrgNode.java`, `DoctrineGraph.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackPolicy.java`,
  `service/packs/src/main/java/ai/speckitty/charter/packs/application/RelationshipMerge.java`
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/GraphShardWriter.java`
- `contracts/charter/schemas/Relation.yaml`, `EffectiveGraphEdge.yaml`, `EffectiveActionContext.yaml`
