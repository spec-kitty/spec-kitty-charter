---
title: "ADR-0014: The creed decisions D-1 to D-5 are recorded here, with refinements, and supersede core's open-decisions table"
description: Records the operator's rulings on value-bearing kinds, the impacts relation, arithmetic, the interview and rationale, and when creed work reaches the API.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0008-rewrite-and-replace-against-bdd-and-openapi-contracts
  - adr/0012-pack-mutability-policy
  - architecture/logical-model
  - plans/roadmap
audience:
  - profile: governance-lead
    guidance_text: Read this for how a project's creed is collected and validated, and what stays research-grade.
  - profile: pack-author
    guidance_text: Read this for which artifact kinds carry values and how the impacts relation replaces in_tension_with.
  - profile: core-maintainer
    guidance_text: Read this for the requests to core, the design document amendment among them.
---

# ADR-0014: The creed decisions D-1 to D-5 are recorded here, with refinements, and supersede core's open-decisions table

## Status

**Accepted** — 2026-10-27 (provisional since 2026-10-19). Decider: Stijn Dejongh (operator). Taken on
2026-10-19 and ratified on 2026-10-27, this decision is still revisited as the system evolves,
on the triggers under [Revisit when](#revisit-when).

This record supersedes, for D-1 to D-5, the open-decisions table (§13) of core's design
[`docs/plans/doctrine/foundational-values-and-creed.md`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/plans/doctrine/foundational-values-and-creed.md).
D-6 (the asymmetric matrix pair) stays open there.

Superseded in part by [ADR-0028](/adr/0028-impacts-is-a-value-list-and-tension-is-a-relationship/) (2026-10-26): D-2's relation is named `tension`, and `impacts` is an artifact's per-value list; tension is not `impacts < 0`.

## Context

The Foundational Values and Creed programme gives the charter a repository-backed value set
(AMMERSE by default), a signed `impacts` relation between artifacts, and a weighted creed on
each charter. Its five open design decisions block the schema work.

- **The operator ruled on all five on 2026-07-26, but the rulings were never written into the
  design authority.** They sit in core ADR
  [`3.x/2026-07-26-3`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-07-26-3-impacts-edge-subsumes-in-tension-with.md)
  (D-2), the delivery sequence (D-3, `manifesto-program-delivery-sequence.md:105`, `:412-418`)
  and the mission-D scaffold spec (D-1, D-4, D-5,
  `kitty-specs/foundational-values-creed-band-01KYFV8N/spec.md:74-80`, `:127-139`). The design's
  §13 still lists them as open (`foundational-values-and-creed.md:577-586`).
- **The scaffold spec contradicts itself.** It calls D-4 "unanswered" (`spec.md:18`) and ruled
  (`:74-80`), and it softens D-5 (`:34`) after ruling it hard (`:139`).
- **Nothing produces value deltas.** The arithmetic multiplies creed weights by artifact
  deltas. The deltas would come from `value_impact` fields on artifacts (I14), and I14 is
  closed (`manifesto-program-delivery-sequence.md:146`, `:219`). Only the reference corpus has
  delta vectors.
- **The I8 perturbation probe has no pass or stop rule.** Issue
  [#21](https://github.com/spec-kitty/spec-kitty-charter/issues/21) asks for a go or stop call
  without a jitter size, a metric or a threshold.
- **Two epics overlap.** [#16](https://github.com/spec-kitty/spec-kitty-charter/issues/16)
  (steps #17 to #23) covers the charter value layer.
  [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77) (steps #78 to #85) was
  filed from drafts that rebuild I14, call tension a computed sign (core ADR `2026-07-26-3`
  makes `impacts` authored only) and duplicate steps of #16.
- **Contract 1.0 is about to ship** with a closed `Relation` enum that lists `in_tension_with`
  and not `impacts` (`contracts/charter/schemas/Relation.yaml`).

## Decision

1. **D-1, value-bearing kinds.** `toolguide` carries no values: it is consulted, not adopted.
   `agent_profile` and `paradigm` carry a bias, as does the charter creed. An agent profile's
   free-text creed description ships with the bias work. Its numeric bias list (about 126 cells
   for 18 profiles and 7 values) is held until the I8 probe returns go.
2. **D-2, the relation.** A new signed, authored `IMPACTS` relation replaces `in_tension_with`
   (core ADR `2026-07-26-3`, Option A). Tension is `impacts < 0` by strict sign; it is never
   derived. #17 re-prices the change, counting the Java service and the contract as well as the
   Python code that the cutover deletes.
   *Superseded in part by ADR-0028: the relation between two rules is `tension`, and `impacts` is the per-value list on one artifact.*
3. **D-3, arithmetic.** The creed feeds arithmetic. Until something produces deltas for shipped
   artifacts, the arithmetic is research-grade: I8 and the ranking (I10) run on the corpus and
   fixtures only, and production reads the creed as prose with its rationales. I8's pass and
   stop rule (jitter size, stability metric, threshold) is fixed on #21 before I8 runs.
4. **D-4, the interview.** A plain budget form: the value set's N values with their
   descriptions, 100 points to distribute, a rationale per value, and the provenance of each
   weight (`operator`, `model-suggested-operator-confirmed` or `default`). Optional prompts per
   value, authored on the value set, are the upgrade path. No authored question bank.
5. **D-5, rationale.** A rationale is required, as a validation error, on everything authored
   for this programme: creed weights, value descriptions and any deltas. The reference corpus
   is imported as labelled reference and is exempt.
6. **The epics are split by layer.** #77 is the `impacts` vocabulary epic: #78 records these
   rulings, #79 adds the relation (B1) and #80 migrates the edges (B2). #16 keeps the charter
   value layer. The steps of #77 that duplicate #16 close as duplicates, and those that rebuild
   I14 get a narrower scope or close.
7. **Creed data reaches the API after the cutover.** Value sets, the creed and ranking arrive as
   a new contract set of additional operations, which clients adopt when they choose. The only
   creed-related change in contract 1.0 is the `Relation` enum (ADR-0009): it carries `impacts`
   and drops `in_tension_with`, and `reconciles_tension` targets `impacts < 0` pairs. Core's seam
   maps an `in_tension_with` edge to a negative `impacts` until core's migration (B2) removes
   it. Nothing creed-related gates the cutover.

## Consequences

### Positive

- The rulings live in one accepted record instead of three documents that disagree.
- The numeric work that may not survive I8 (the bias list, production ranking) is not built
  before I8 says it is worth building.
- Contract 1.0 already speaks the final relation vocabulary, so `impacts` never forces a 2.0.

### Negative

- Production gets no value arithmetic until a delta producer exists, which needs I14 or a
  substitute to be reopened.
- Core's seam carries an `in_tension_with` to `impacts` mapping until B2 lands.
- The design authority in core stays wrong until core applies the amendment below.

### Risks

- If B1 or B2 changes read answers while the conformance corpus is the cutover gate, the
  corpus and both implementations must change together.
- A hard rationale rule invites filler text. The answer is a quality lint, not a relaxation.

## Alternatives considered

### Record the rulings unchanged

Faithful, but it would ship the numeric bias list and production arithmetic before anything
proves they are useful, and it would leave the D-4 and D-5 contradictions in place.

### Reopen D-2 for the cheaper option (a magnitude on `in_tension_with`)

Five to ten files instead of 45 to 60. Not chosen: the operator chose the new relation with the
cost in view, and the name `in_tension_with` cannot carry a positive impact.

### Keep one epic

Keeping only #16, or only #77, removes the overlap but mixes the relation vocabulary, which
changes the graph and the contract, with the charter value layer, which does not gate the
cutover.

## Revisit when

- I8 returns go: ship the bias list and choose a production delta producer. I8 returns stop:
  drop the numbers and keep the prose layer.
- The re-price on #17 lands well above 60 files including the Java side.
- Operators find the budget form too abstract, or creeds come back flat despite the budget.
- Operators write filler rationales to pass validation.
- A toolguide appears that is adopted rather than consulted.

## Requests to core

- **Amend the design** `foundational-values-and-creed.md`. Proposed text for §13: "D-1 to D-5
  are decided; see spec-kitty-charter ADR-0014, which supersedes this table for them. D-6 stays
  open." Add a note to §6.4 and §7.3 that production deltas have no producer while I14 is
  closed.
- **Resolve the scaffold spec's contradictions** in
  `kitty-specs/foundational-values-creed-band-01KYFV8N/spec.md`: D-4 at `:18` against
  `:74-80`, and D-5 at `:34` against `:139`, in favor of this record.
- **Seam mapping:** until B2 lands, map `in_tension_with` edges to `impacts` with a negative
  sign at the seam, so core reads the 1.0 contract unchanged.

## References

- Epics [#16](https://github.com/spec-kitty/spec-kitty-charter/issues/16) and
  [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77); I8 on
  [#21](https://github.com/spec-kitty/spec-kitty-charter/issues/21).
- Core ADR `3.x/2026-07-26-3` (the `impacts` relation).
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) (contract 1.0 reservations and additive contract sets).
- [Logical model](/architecture/logical-model/), [Roadmap](/plans/roadmap/).
