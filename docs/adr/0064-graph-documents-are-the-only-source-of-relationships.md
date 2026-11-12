---
title: "ADR-0064: Relationships are authored only in a pack's graph documents"
description: "Operator ruling of 2026-11-05 on #240: graph documents are the only home of an edge, putPackRelationship its only write, and every edge-minting field is dropped."
doc_status: active
updated: 2026-11-12T00:00:00Z
decision_status: Accepted, with Amendment 2026-11-07
decision_date: '2026-11-05'
type: ADR
kind: ADR
related:
  - adr/0024-doctrine-artifact-family-contract
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0026-write-surface-is-functional-registration
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship
  - adr/0036-round-trip-fidelity-is-equality-of-meaning
  - adr/0037-kind-keyed-pack-registry-and-whole-pack-seam
  - adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides
  - adr/0044-mission-types-consolidated-into-one-sanitized-shape
  - adr/0050-mission-types-and-lifecycles-as-declared-state-machines
  - adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0022-contract-versions-start-at-0-x
  - adr/0065-pack-registration-replaces-the-whole-pack
  - architecture/logical-model
audience:
  - profile: pack-author
    guidance_text: Read this for where a relationship between two artifacts is written, and which artifact fields no longer exist.
  - profile: governance-lead
    guidance_text: Read this for the per-field decision on #240, and which reference-like fields stay as data.
  - profile: charter-service-contributor
    guidance_text: Read this before touching an artifact's fields, its registration body, the references read or the round-trip gate.
---

# ADR-0064: Relationships are authored only in a pack's graph documents

## Status

**Accepted**, 2026-11-05. Decider: Stijn Dejongh (operator). The operator ruled on
[#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240) on 2026-11-05: the artifact fields
that mint relationship edges are dropped, an edge is authored only in a pack's graph documents, and it is
written through one relationship operation, `putPackRelationship`. This record states that ruling field by
field.

Five questions the ruling left open were asked on #240
([comment 6663603068](https://github.com/spec-kitty/spec-kitty-charter/issues/240#issuecomment-6663603068))
and answered by the operator the same day
([comment 6664407049](https://github.com/spec-kitty/spec-kitty-charter/issues/240#issuecomment-6664407049)):

- **Q1**: a directive's `procedures` goes. Its prose steps become procedure artifacts in open-packs, shared
  between directives where a slice is reusable, and each directive reaches them through a graph edge
  (decision 3).
- **Q2 and Q3**: every typed artifact-to-artifact reference field goes, whether it mints an edge today or
  not, and so do the styleguide and toolguide `references`. Intended behavior does not change: every
  dropped reference to an artifact has a live graph edge, already authored or added by the migration, and
  a citation that names no artifact moves into the artifact's text (decision 2).
- **Q4**: the fields of decision 11 look like references but stay as data.
- **Q5**: the read shapes lose their `relationships` arrays, and the references read takes each relation
  from the graph documents (decision 6).

This record amends [ADR-0024](/adr/0024-doctrine-artifact-family-contract/),
[ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/),
[ADR-0026](/adr/0026-write-surface-is-functional-registration/),
[ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/),
[ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) decision 4,
[ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/),
[ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/),
[ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/),
[ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) decision 9 and
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 22, with an inline
note on [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/). Each carries a dated amendment,
or an inline note where only a citation moves. It decides the edge authority that
[#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189) and
[#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167) left open.

### Amendment 2026-11-07

[ADR-0065](/adr/0065-pack-registration-replaces-the-whole-pack/) amends decision 4 ("One write path").
`putPackRelationship` is no longer the only operation that writes an edge. A whole-pack registration, `PUT /packs/{packId}`
or `POST /packs` (which names a source instead of sending the archive), writes graph documents: the author's shards and
`drg/fragment.yaml`, replaced whole. `registered.graph.yaml` is included in
the replace only when it carries the registration stamp (`generated_by: charter-registration`); an unstamped one is
refused. The relationship `DELETE` removes a registered edge, from `registered.graph.yaml` and from no other file; an
authored or derived edge answers `409 relationship_not_registered` and a `DELETE` never rewrites an author file. The
artifact `DELETE` (`deletePackArtifact`) also writes edges: it drops the artifact's outgoing registered edges from
`registered.graph.yaml` with the artifact, and, as the relationship `DELETE` does, refuses (`409 registration_conflict`)
an artifact that an author-written graph document names or starts an edge from, rather than rewrite that file.
`putPackRelationship` remains the only operation that writes one edge into a pack without re-sending the pack.
Decisions 1 and 2 stand: no artifact field mints an edge, and the author's graph documents remain the home of an
authored edge. An artifact registration still writes the artifact's own content only.

## Context

An edge has two homes today.

- **Fields.** [ADR-0024](/adr/0024-doctrine-artifact-family-contract/) gave the artifact spine its
  edges, and [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/) made them
  `CharterArtifact.relationships()`. A directive's, paradigm's and tactic's reference fields mint edges
  through an interim mapping (`ArtifactEdges`): a typed reference mints `suggests` and a bare id mints
  `requires`. That mapping was provisional pending #167.
- **Graph documents.** A pack's root `*.graph.yaml` shards and its `drg/fragment.yaml` also carry edges
  ([ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/),
  [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) decision 4). The relationship
  write stores into the registered shard, `registered.graph.yaml`.

The registration bodies add a third path. A directive or paradigm body carries a `relationships` array
([ADR-0026](/adr/0026-write-surface-is-functional-registration/) decision 1.2), which the service merges
into the registered shard. The read shape derives its own `relationships` from the fields. So a GET never
shows the edges a body registered: the two copies disagree, and nothing reconciles them.

The same pair can therefore hold one relation in a field and another in a graph document. In the pinned
open-packs, among the references of the fields that minted an edge, 317 match a graph edge with the same
relation, 38 match one with a different relation, and 17 have no graph edge at all. Over every dropped
field, the minting ones and the rest, the migration's map counts 743 references already held by a graph edge
and 222 it adds (`research/dropped-reference-map.md` of mission `graph-documents-sole-edge-source-01M6P9D0`). The provenance divergence that
[ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) left to #167 is one
such pair. Several later records kept their reference fields "until #189 rules on edge authority"
(ADR-0043, ADR-0044, ADR-0050, ADR-0052).

## Decision

1. **An edge is authored only in a pack's graph documents.** These are the root `*.graph.yaml` shards,
   including the registered shard `registered.graph.yaml`, and `drg/fragment.yaml`. No artifact field
   creates an edge.

2. **Each edge-minting or artifact-reference field is dropped** from the domain, the pack schemas and the
   contract shapes. An edge the author wants is written in a graph document instead.

   | Kind | Field | What it minted | Where its edges live now |
   |---|---|---|---|
   | directive | typed `references` | `suggests`, with `when` and `reason` | a graph edge from `directive:<id>`, with its `when` and `reason` |
   | directive | `procedures` | `requires procedure:<sentence>`, which could not be stored | a `requires` graph edge to each procedure artifact the steps became (decision 3) |
   | paradigm | `references` | `suggests` | a graph edge from `paradigm:<id>` |
   | paradigm | `directive_refs` | `requires directive:<id>` | a `requires` graph edge from `paradigm:<id>` |
   | tactic | top-level `references` | `suggests`, with `when` | a graph edge from `tactic:<id>`, with its `when` |
   | tactic step | `references` | nothing | a graph edge from the tactic |
   | procedure | `references` | nothing ([ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/) decision 2) | a graph edge from `procedure:<id>` |
   | mission type | `references` | nothing ([ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/) decision 3) | the graph's `scope` edges, which already equal them |
   | mission step | `references` | nothing | a graph edge from the step's `action:<type>/<step>` node |
   | styleguide | citation `references` | nothing | a graph edge where the citation names a pack artifact; otherwise the artifact's text |
   | toolguide | citation `references` | nothing | a graph edge where the citation names a pack artifact; otherwise the guide's text |

   Where a field and a graph edge name the same pair with a different relation, the graph edge stands. A
   field reference with no graph edge is added to the graph by the open-packs migration, unless the pair
   already has an edge in the other direction (decision 10). A styleguide or toolguide citation that is a
   URL or a docs path, not a pack artifact, has no edge to become: it moves into the artifact's text. A
   path to a template, an asset or an agent profile is a pack artifact, and becomes an edge.

3. **A directive's steps are procedure artifacts.** The directive's `procedures` field goes. Its 192
   entries in the pinned open-packs are sentences, not procedure ids, so the `requires procedure:<value>`
   edge they minted named no procedure and could not be stored. The open-packs migration turns the
   sentences into procedure artifacts, sharing a procedure between directives where the steps are the
   same practice, and each directive `requires` its procedures through a graph edge.

4. **One write path.** The directive and paradigm registration bodies carry no `relationships` array.
   `putPackRelationship` is the only operation that writes an edge. An artifact registration writes the
   artifact's own content only.

5. **A registration body is closed.** A body with a member its shape does not declare, a removed field
   among them, is refused with 422 `validation_failed`, naming the member, and nothing is written. A
   client that still sends `relationships` cannot get a silent success with no edge. A pack file is
   closed by its schema (`additionalProperties: false`), so a file that still carries a removed field is
   refused as an unknown field.

6. **The read shapes carry no `relationships` array** (Q5). The references read
   (`listArtifactReferences`) answers the artifact's outgoing edges from this pack's graph documents,
   each with its relation: the root shards in file order, the registered shard among them, then
   `drg/fragment.yaml`. A source written self-qualified (`<thispack>::<kind>:<id>`) counts. Edges that
   another pack authors from the artifact are not listed. The artifact's derived relations follow the
   authored edges.

7. **Derived relations are named as such.** `CharterArtifact.relationships()` becomes
   `derivedRelationships()`, empty by default: relations derived from runtime data, never authored. A
   guard's `IMPLEMENTED_BY` to an in-pack asset is the only one today. It is refused on write and never
   compared by the round trip. It is not a second authoring source.

8. **The interim mapping is retired.** `ArtifactEdges` (#167) and the relation values it pinned
   (typed reference to `suggests`, bare id to `requires`) are gone. No artifact derives an authored edge
   from a field.

9. **The round trip compares edges from the graph documents only.** This amends ADR-0036 decision 4:
   the artifact files carry no edge, so the canonical edge set is the graph documents' set. An org
   fragment's edge gains `when`, as a root shard's edge has, so a fragment edge can hold what a shard
   edge holds, and no field `when` text is lost in the move. The 27 `when` texts that move into org
   fragments are written there by the open-packs change that follows this one (part B,
   [open-packs#127](https://github.com/spec-kitty/spec-kitty-open-packs/issues/127)), because the
   validator open-packs pins does not accept a fragment `when` until this contract is released; until
   then they are held in the migration's report.

10. **Identity is unchanged.** One structural relationship per pair stands
    ([ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) decision 1 and
    [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/)). With one source, two
    edges on a pair can no longer come from two homes. The #167 provenance divergence is gone: the graph
    document's relation is the relation.

11. **Kept as data, not relationship authoring.** These fields look like references but say something
    other than how two artifacts relate. They keep their shape. The operator confirmed this table
    (Q4).

    | Field | Why it is not an edge |
    |---|---|
    | guard `implemented_by` | runtime data; its `IMPLEMENTED_BY` relation is derived (decision 7) |
    | agent-profile `directive-references` and its tactic, toolguide and styleguide references | selection data: they say which doctrine the profile is handed, not how two artifacts relate |
    | skill `enhances` and `overrides` | the skill's own install and replacement data |
    | pack `depends_on` | a pack-level dependency, not an artifact edge ([ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)) |
    | `activated_*` and `selected_*` | a preset's or charter's selection |
    | glossary `see_also` | a cross-reference between terms |
    | mission-step-contract delegations | a runtime handoff |
    | lifecycle guards | a transition's guard |
    | per-value `impacts` | the artifact's value list ([ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/)) |

    An agent profile can also be the source of a graph edge. That edge relates the profile to an
    artifact; the profile's references choose what it is handed. The two answer different questions, so
    they are not two homes for one edge.

12. **Every "until #189 rules" deferral on edge authority resolves here.** The procedure, mission type
    and step references are dropped (decision 2). The `rejects` list of ADR-0052 decision 9 and ADR-0050
    is not added to any kind: guidance rejects an anti-pattern with a `rejects` edge in its graph
    documents. A step enhancement's additions (ADR-0053 decision 22) hold no `references`. The graph
    documents keep `action:<type>/<step>` as a step's node (ADR-0044 decision 2). Whether the domain's
    merge of the graph documents is set-based (ADR-0036, ADR-0037) is not ruled here: with one source,
    the merge no longer meets a field-minted edge, and comparison stays set-based.

13. **Each relation names the kinds it may connect** (amendment, operator ruling of 2026-11-12 on
    [#515](https://github.com/spec-kitty/spec-kitty-charter/issues/515)). With the reference fields gone,
    core's question of which kinds a `<kind>_reference.type` may name becomes: which source and target
    kinds each relation may join. The answer is part of the model, not a validation table beside it. In
    the domain, a relationship is built through constructors typed by its endpoint kinds (generics), so a
    combination the model does not allow does not compile. A graph document that authors such an edge
    is refused when the pack is read or written, with a named refusal. The endpoint kinds of each
    relation are set by slice F (#537), which also settles #447 (an `overrides` edge from a kind an
    activation cannot select).

## Consequences

### Positive

- An edge has one source. The read, the write and the round trip all agree, because they all consult
  the same documents.
- The contract is smaller: the registration and read shapes lose the reference fields and the
  `relationships` arrays, and their item schemas go with them.
- The interim mapping and its provisional relation values no longer have to be confirmed against
  anything.

### Negative

- open-packs must migrate first: every pinned pack carries a removed field. The migration removes the
  fields, adds the missing graph edges and merges the `when` and `reason` texts. The service's pin then
  moves to it.
- The change breaks the contract on the unreleased `0.2.0-SNAPSHOT` line. Under
  [ADR-0022](/adr/0022-contract-versions-start-at-0-x/) a 0.x break needs no version bump; it is
  recorded as a `### Breaking` changelog fragment.
- A styleguide or toolguide citation that names no pack artifact moves into the artifact's own text, so
  no citation is lost and none is read as an edge.

### Risks

- The procedure extraction is a content-writing pass in open-packs. A step that loses its meaning when
  split out is caught in that pull request's review, not by the charter's gates, which check shape only.
- `GraphWalk` follows a component's derived relations only. Over a stored artifact that is a guard's
  `implemented_by` alone, so it can never follow a graph-document edge there. ADR-0059's cascade
  ([PR #399](https://github.com/spec-kitty/spec-kitty-charter/pull/399)) walks it over its own edge index,
  whose nodes answer the closure's graph-document edges as their relations; any other caller must do the
  same. Retiring the walk, or feeding it from the edge port, amends ADR-0025's walk decision and is left to
  its own change ([#397](https://github.com/spec-kitty/spec-kitty-charter/issues/397)), which must keep the
  cascade's edges.
- The pinned packs hold 17 pairs with two edges in the same direction under different relations, which
  ADR-0027's one-edge-per-pair rule does not allow. The references read lists each relation rather than
  hiding one; reconciling the pairs is open-packs curation.

## Interaction with ADR-0059

*Note 2026-11-07 (ADR-0059 amendment, A7 Q1):* the cascade from a mission type to its steps is not a relationship
this record forbids. A step's containment comes structurally from the id hierarchy (`action:<M>/<step>`); it is neither
an authored edge (decisions 1 to 6) nor a derived relation (decision 7), so no graph document carries it.

[ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) (merged from [PR #376](https://github.com/spec-kitty/spec-kitty-charter/pull/376)): its D2 cascade
follows `requires`, `suggests` and the other listed relations. After this record those relations are
found only in the graph documents, so the cascade reaches only graph edges. Its open question 4 asks
whether graph-shard edges at an overridden id still apply. That question keeps its subject, but it loses
its second case: an overriding artifact carries no edges of its own, so a whole-artifact override can
neither bring nor drop an edge through its fields. This record does not answer the question.

## Alternatives considered

### Keep the fields as a derived view

The fields stay, and the service derives a read-only view from them. Rejected: the view and the graph
can still disagree, and an author still has two places to write an edge.

### Keep the fields as authoring sugar, with an agreement check

The fields stay as a second way to author, and a check refuses a field that disagrees with the graph.
Rejected: two write paths for one edge is the defect the ruling removes, and the check is permanent
extra machinery.

### Land the change with a known-failure list instead of migrating first

Rejected: every pinned pack carries a removed field, a pack with a violation skips its round trip, and
several corpus tests never consult the list. The gate would go vacuous. The migration lands first.

## References

- [#240](https://github.com/spec-kitty/spec-kitty-charter/issues/240), the ruling and the open
  questions ([comment 6663603068](https://github.com/spec-kitty/spec-kitty-charter/issues/240#issuecomment-6663603068)).
- [#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167), the interim mapping;
  [#189](https://github.com/spec-kitty/spec-kitty-charter/issues/189), the edge-authority deferrals.
- [ADR-0022](/adr/0022-contract-versions-start-at-0-x/), contract versions on 0.x.
- [ADR-0024](/adr/0024-doctrine-artifact-family-contract/) to
  [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/), the artifact spine,
  relationships, registration and identity.
- [ADR-0036](/adr/0036-round-trip-fidelity-is-equality-of-meaning/) and
  [ADR-0037](/adr/0037-kind-keyed-pack-registry-and-whole-pack-seam/), the round trip and the whole-pack
  seam.
- [ADR-0043](/adr/0043-sanitized-artifact-shapes-procedures-and-toolguide-guides/),
  [ADR-0044](/adr/0044-mission-types-consolidated-into-one-sanitized-shape/),
  [ADR-0050](/adr/0050-mission-types-and-lifecycles-as-declared-state-machines/),
  [ADR-0052](/adr/0052-procedures-tightened-and-anti-patterns-as-authored-artifacts/) and
  [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/), the records that kept
  references until #189 ruled.
- [ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/), the cascade edges.
