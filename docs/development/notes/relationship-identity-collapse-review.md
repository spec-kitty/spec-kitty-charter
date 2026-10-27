---
title: "Note: relationship identity and the multigraph-collapse question"
description: Review-squad findings on whether the CRG's multigraph pairs can collapse to one unidirectional relationship, feeding a future identity ADR.
doc_status: draft
updated: 2026-10-25T00:00:00Z
type: Guide
kind: Reference
related:
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0020-offering-and-activations
  - architecture/logical-model
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before the identity ADR that follows — what the collapse can and cannot claim, and the conditions it must carry.
  - profile: core-maintainer
    guidance_text: Read this for how the charter intends to treat multigraph CRG pairs and where the discovery/activation behavior would live.
---

# Note: relationship identity and the multigraph-collapse question

**Status: investigation note, not a decision.** It captures a review-squad finding and a
two-lens (architect + analyst) corroboration, to feed a future ADR on `Relationship` identity.
Nothing here changes code or a committed ADR.

## Why this note exists

ADR-0025 made `Relationship` identity the unordered `{from, to}` pair — at most one relationship
per component pair (a simple graph). A post-specify review squad found, against real pack data,
a genuine **multigraph pair** that this identity collapses:

- `tactic:tracker-backlog-iterative-deepening →(suggests) procedure:tracker-organisation-workflow`
- `procedure:tracker-organisation-workflow →(requires) tactic:tracker-backlog-iterative-deepening`

(`packs/internal/drg/fragment.yaml:311-319`.) Collapsing them risks the PROJECT_002 round-trip
non-negotiable. The operator's hypothesis in response: a relationship is intrinsically
**unidirectional**; the reverse `suggests` is *derivable* from the `requires` edge and exists
mainly for activation support, so the pair can collapse to one unidirectional relationship and the
"you activated B, want A?" behavior can move to a discovery/activation domain service.

## What the data shows (analyst)

- **One** multigraph pair in real shipped data — the tracker pair — out of 130 distinct pairs
  (0.8%); no exact-duplicate edges. It *is* the `requires`+reverse-`suggests` shape the hypothesis
  targets, so there is no real counter-example — but **n = 1** cannot confirm "every pair collapses".
- Real relation mix (131 edges): `suggests` 61, `requires` 43, `applies` 14, `refines` 13. No
  `impacts`/`in_tension_with`/`scope` in real data.
- A **test fixture** deliberately carries reciprocal `impacts` edges (opposite directions, different
  signed strengths) — the schema **intentionally permits reciprocal edges**, so a blanket collapse
  needs an explicit carve-out.
- **47% of edges are `suggests`**, almost all single-direction forward recommendations with no
  `requires` counterpart — moving `suggests` semantics wholesale touches ~60 edges, not just the pair.

## What the architecture review found

**Verdict: CORROBORATE-WITH-CONDITIONS on the narrow case; DISPROVE on the blanket claim.**

The narrow claim — "today's data can be a simple graph and the one tracker pair can collapse" — is
already ADR-0025's decision and survives *under conditions*. The blanket claim — "a relationship is
*intrinsically* unidirectional; the reverse `suggests` is *derivable*; `suggests` is *mainly* for
activation" — is disproven:

1. **`suggests` is a primary relation, not activation-only.** 53 of 54 `suggests` edges in the
   fragment are standalone forward recommendations with no `requires` to derive them from.
2. **The reverse `suggests` carries independent authorial information.** The same directive is
   `requires`-targeted by ~6 procedures, yet the author added a reverse `suggests` for **only** the
   tracker pair. A "derive A-suggests-B whenever B-requires-A" rule would over-suggest (all 6). The
   dropped edge *is* selective information — directly refuting "carried no independent information".
3. **PROJECT_002 round-trip.** The fragment literally holds both edges; a store-and-collapse-on-read
   charter cannot re-emit it byte-for-byte. Collapse is round-trip-safe **only** if it is (a) an
   **upstream open-packs source normalization** (remove the reverse edge at the canonical source), or
   (b) storage keeps **both `DrgEdge`s** and serializes from them, with the collapsed `Relationship`
   as a lossy **view** only. A silent drop-on-read is excluded. ADR-0025 has an internal tension here
   (it says both "shard shape is unchanged" *and* "DoctrineGraph holds Relationships for
   serialization") that the identity ADR must settle.
4. **No relation has a mechanically derivable inverse.** `requires ↔ suggests` is an authoring
   heuristic, not a law; the other 13 relations have no inverse pairing.
5. **"Intrinsically unidirectional" is false for symmetric relations.** `impacts` (tension) and
   `delegates_to` (runtime handoff) are naturally bidirectional; it holds today only because
   `impacts` is unminted and bidirectional `delegates_to` is out of scope — ADR-0025 already defers
   both. The claim breaks the moment either is built.
6. **Recovering the discovery direction is not free.** With only `requires B→A`, activating A does
   not reach B. A discovery service computing "activated A → suggest B" needs a **reverse walk**
   ("who requires A?") — an inbound index or scan, against ADR-0025's lazy, outbound-only,
   bounded-memory walker.
7. **The pair is asymmetric** (hard forward `requires`, soft reverse `suggests`); no single existing
   `RelationType` represents "hard one way, soft the other" — faithfully keeping both would need a
   new asymmetric type, i.e. *more* domain, not less.
8. **Provenance divergence (confirm, tie to #167).** The procedure body lists the tactic as a
   **typed** ref, which `ArtifactEdges` would mint as `SUGGESTS`, yet `fragment.yaml` hand-authors
   `requires`. Two edge-production paths disagree on this pair's relation; confirm which surface is
   canonical before collapsing, and reconcile against core's extractor (#167).

## Discovery / activation service

Coherent, and **this repository's remit post-cutover**: ADR-0020 D5/D6 move activation state and
cascade computation to the charter service. So a service computing "activate B → surface A" is
legitimately the charter's — but the cascade engine is unbuilt, it must live in the application ring
(not the pure kernel, ADR-0006), and it needs the inbound-reachability capability ADR-0025 does not
currently provide. Coherent in principle; a real new seam, not a free derivation.

## Synthesis and recommendation

The collapse is **viable for today's data as a bounded, conditional decision** — but its honest
justification is *not* "relationships are intrinsically unidirectional" (disproven). Recommend the
future ADR record it as:

> For the current data, collapse the one `requires`+reverse-`suggests` pair by **normalizing
> open-packs upstream** (so one edge is genuinely stored and round-trips), accepting the loss of a
> selective soft-reverse discovery edge that has **no reader yet**; keep registration bodies carrying
> an explicit `type`; and **revisit the simple-graph identity the moment `impacts` (tension) or
> bidirectional `delegates_to` is minted**.

Alternative if upstream normalization is unacceptable: keep both `DrgEdge`s at the storage layer and
treat the collapsed `Relationship` as a read-time view, with any discovery-direction behavior as an
application-layer reverse-reachability computation.

### Preconditions for the collapse to stay safe

- **P1 (eroding):** no consumer reads the A→B discovery direction (true today; ADR-0020 D6 will change it).
- **P2 (false in general):** the reverse `suggests` is recoverable from `requires` — only if you accept losing the author's selectivity.
- **P3 (required):** collapse is upstream source normalization *or* storage-preserving view — never drop-on-read (PROJECT_002).
- **P4 (time-limited):** no genuinely symmetric relation needs an opposite-direction pair — true only while `impacts`/`delegates_to` are unbuilt.
- **P5 (design cost):** reverse-requires is either never needed or backed by an inbound index reconciled with the lazy walker.
- **P6:** the hard-forward/soft-reverse asymmetry is acceptable to lose, or a new asymmetric `RelationType` is introduced.

## Other decisions recorded from this review (for the remediation pass)

- **Write atomicity → aggregate commit.** NFR-002's "zero partial writes" needs an aggregate-level
  commit (stage pack dir + atomic swap, or a write journal); per-file temp+rename is not enough.
- **Queued doc remediation** (to fold into the identity ADR / a text pass, not yet applied):
  normalize `artifact → artifact`; reconcile `component` vs `CharterArtifact`; state `RelationType`
  supersedes the kernel `Relation` enum; add `428` and record that `412/428` are `Problem`-only, not
  `RefusalCode`s, and that `RefusalHandler` needs a real code→status map; reframe mutability (ADR-0012)
  and ETag as net-new work, not "reuse"; reword "nothing mints `impacts`" (PackPolicy/shards do); add
  `when` to `Relationship` to avoid a round-trip loss; add ADR References sections.

## Open items to confirm

- Provenance: is `fragment.yaml` the sole canonical edge source, or do body `references:` also mint? (ties to #167).
- Whether core auto-generates a reverse `suggests` for every `requires` — if so the charter must match, not collapse.
