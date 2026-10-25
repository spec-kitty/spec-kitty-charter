---
title: "ADR-0027: DRG relationship identity is a bounded, conditional unidirectional collapse"
description: Why a relationship is one unidirectional edge per component pair as an upstream normalisation with a revisit trigger, not a claim that relationships are intrinsically one-way.
doc_status: active
updated: 2026-10-25T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0020-offering-and-activations
  - architecture/logical-model
  - development/notes/relationship-identity-collapse-review
audience:
  - profile: core-maintainer
    guidance_text: Read this for why the Java domain keeps one unidirectional relationship per component pair, and the trigger that reopens the decision.
  - profile: charter-service-contributor
    guidance_text: Read this before touching relationship identity — the bounded collapse, where the reverse edge goes, and the revisit trigger.
---

# ADR-0027: DRG relationship identity is a bounded, conditional unidirectional collapse

## Status

**Proposed** — 2026-10-25. Decider: Stijn Dejongh (operator). Pending ratification.

Revises [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/) Decision 1: it keeps
the unordered-pair identity but replaces ADR-0025's premise ("no consumer needs multiplicity; the
reverse edge is derivable") and its "serialized shard shape is unchanged" claim with an honest,
conditional basis.

## Context

ADR-0025 made `Relationship` identity the unordered `{from, to}` pair — at most one per component
pair — justified by the belief that real graphs carry no multigraph pairs and that a reverse edge is
derivable. A post-specify review
([note](/development/notes/relationship-identity-collapse-review/)) disproved that premise:

- Real pack data contains one genuine multigraph pair —
  `tactic:tracker-backlog-iterative-deepening →(suggests) procedure:tracker-organisation-workflow`
  and `procedure:tracker-organisation-workflow →(requires) tactic:tracker-backlog-iterative-deepening`
  (`packs/internal/drg/fragment.yaml`).
- `suggests` is a *primary* relation, not an activation by-product (53 of 54 `suggests` edges are
  standalone forward recommendations); the reverse `suggests` on that pair is *selective* authorial
  information a "derive from `requires`" rule cannot reproduce without over-suggesting.
- `impacts` (tension) and `delegates_to` (runtime handoff) are genuinely symmetric.

So "a relationship is intrinsically unidirectional" is false. What *is* sound for today's data is the
operator's intent stated as a bounded normalisation: keep one unidirectional relationship per pair,
and compute the reverse discovery behaviour elsewhere rather than store it.

## Decision

1. **Identity stays the unordered `{from, to}` pair**: at most one, unidirectional, `Relationship`
   per component pair, stored with its direction. This is a deliberate **normalisation**, not a claim
   that a reverse edge holds no information.
2. **The collapse is realised by upstream normalisation in open-packs** (the canonical source carries
   a single edge per pair), so the charter's stored form round-trips losslessly and PROJECT_002 is
   honoured. The charter **does not silently drop** a second edge on read; a source that still carries
   a redundant reverse edge is a source-normalisation task owned by the CLI / open-packs side
   (tracked as [spec-kitty/spec-kitty#6667](https://github.com/spec-kitty/spec-kitty/issues/6667),
   milestone 11 "4.0.0 release scope"), not a charter read-time rewrite.
3. **The reverse discovery behaviour** — "you activated B, would you like to activate A?" — is **not**
   a stored relationship. It is computed by a discovery/activation domain service (application ring,
   within the charter's post-cutover remit per [ADR-0020](/adr/0020-offering-and-activations/) D5/D6)
   through reverse-reachability over `requires`. It is out of this mission's scope; the CLI-side
   integration is tracked as [spec-kitty/spec-kitty#6667](https://github.com/spec-kitty/spec-kitty/issues/6667).
4. **Revisit trigger (binding).** This identity is revisited the moment a genuinely symmetric relation
   is minted — `impacts` (a tension pair) or bidirectional `delegates_to`. Until then the
   unidirectional collapse holds; neither is minted today (ADR-0025 defers both).
5. `RelationType` remains a closed, behaviour-carrying catalogue with directionality derived from the
   type (unchanged from ADR-0025 Decision 2).

## Consequences

### Positive

- One well-defined relationship per pair, with a lossless round-trip once sources are normalised, and
  a walker that stays lazy, outbound and bounded (ADR-0025 Decision 4).
- The rationale is honest and bounded, with an explicit revisit trigger instead of a false universal.

### Negative / costs

- Loses the selective soft-reverse discovery edge (no reader today); its behaviour must be rebuilt in
  the discovery/activation service.
- Depends on the CLI / open-packs side to normalise sources and to build the discovery service
  (milestone #11); the charter cannot honour PROJECT_002 for an un-normalised source on its own.
- A symmetric relation (`impacts`/`delegates_to`) forces the revisit of Decision 1.

### Neutral

- The provenance divergence — `fragment.yaml` hand-authors `requires` while the procedure body's
  typed reference would mint `SUGGESTS` — must be reconciled against core's extractor
  ([#167](https://github.com/spec-kitty/spec-kitty-charter/issues/167)) before the normalisation
  lands.

## Alternatives considered

- **Ordered `(from, to)` + `type`, multigraph-with-dedup.** Would store both directions and dedup only
  exact duplicates. Rejected by the operator in favour of a single unidirectional relationship plus a
  discovery service.
- **Keep strict unordered identity and drop the second edge silently on read.** Rejected: it violates
  the PROJECT_002 round-trip non-negotiable for a source that carries both edges.
- **Store both `DrgEdge`s and expose the collapsed `Relationship` as a read-time view.** A viable
  fallback that needs no upstream change, not chosen because it keeps two representations in the
  charter; upstream normalisation is cleaner and keeps the domain single-edge.
