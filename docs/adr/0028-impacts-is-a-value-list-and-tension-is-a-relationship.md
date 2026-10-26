---
title: "ADR-0028: `impacts` is an artifact's per-value list, and `tension` is the relationship between two rules"
description: Splits the old signed impacts edge into a per-value impacts list on an artifact and an authored tension relationship that may sit beside one structural relationship.
doc_status: active
updated: 2026-10-26T00:00:00Z
type: ADR
kind: ADR
related:
  - adr/0009-contract-1-0-reservations-and-additive-contract-sets
  - adr/0014-foundational-values-and-creed-rulings
  - adr/0025-relationships-first-class-and-lazy-repository-walk
  - adr/0027-drg-relationship-identity-bounded-unidirectional-collapse
  - context/conceptual-model
audience:
  - profile: pack-author
    guidance_text: Read this for how you state what a rule costs or gains on each value, and how you mark two rules as in tension.
  - profile: charter-service-contributor
    guidance_text: Read this before modelling impacts or tension in the domain, and for the relationship-per-pair rule it relaxes.
  - profile: contract-consumer
    guidance_text: Read this for what the two words mean once they reach the contract.
---

# ADR-0028: `impacts` is an artifact's per-value list, and `tension` is the relationship between two rules

## Status

**Accepted** — 2026-10-26. Decider: Stijn Dejongh (operator), concept session of 2026-10-26
("Rename approved" in the [conceptual model working notes](/context/conceptual-model/)).

Supersedes in part:

- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/) decision 1: the
  relation between two artifacts is named `tension`, not `impacts`.
- [ADR-0017](/adr/0017-pack-schemas-imported-as-canonical-json-schema/) Amendment 2026-10-19: the
  modeled `impacts` relation is the `tension` relation; `impacts` is a per-value list.
- [ADR-0014](/adr/0014-foundational-values-and-creed-rulings/) decision 2 (D-2): the naming of the
  relation, and tension as "`impacts < 0`".
- [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/): `impacts` is not a
  field of a `Relationship`.
- [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/) decision 1:
  one relationship per pair, for `tension`.

As far as this repository is concerned, it also supersedes the relation naming of core ADR
`3.x/2026-07-26-3`. Core is not changed.

## Context

Two ideas shared one word. Core ADR `3.x/2026-07-26-3` and ADR-0014 D-2 made `impacts` a signed,
authored edge between two artifacts that replaced `in_tension_with`; tension was a negative
`impacts`. The values work (epic #77) also needs a way to say what adopting one artifact costs or
gains on each value (AMMERSE by default). That is a property of one artifact, not of a pair.

The concept session settled the meaning:

- A rule's value impact is a list on the artifact: `{value, strength, rationale}` per value. A
  strength is never shown without its rationale, and the rationale wins when they disagree.
- Tension between two rules stays an authored artifact-to-artifact relationship. It is never
  derived from value impacts.
- Two rules can require each other and still be in tension. ADR-0027 allows one relationship per
  pair, so it cannot express that.

Nothing mints `impacts` or a tension relationship today, so the rename costs no data migration in
this repository.

## Decision

1. **`impacts` is an artifact's per-value list.** An artifact may carry an `impacts` list. Each
   entry names a value, a strength and a rationale. The rationale is required (ADR-0014 D-5).
2. **`tension` is the relationship between two rules.** It replaces `in_tension_with` and the
   signed `impacts` edge. It is authored, never derived from the value impacts of either rule.
3. **`tension` may coexist with one structural relationship on the same pair.** Between two
   artifacts there is at most one structural relationship (`requires`, `suggests` and the rest,
   ADR-0027) and, beside it, at most one `tension`. ADR-0027's revisit trigger has fired for
   `tension`; the collapse still holds for the structural relationships.
4. **`reconciles_tension` targets pairs joined by `tension`**, not pairs with a negative
   `impacts`.
5. **Representation is not decided here.** How the list and the relationship appear in the domain,
   the pack files and the contract is a candidate design for later records. No contract path or
   schema changes with this record (constraint C-003 of the documentation Mission).

## Consequences

### Positive

- Each word has one meaning: `impacts` describes one artifact, `tension` joins two.
- A pair that requires each other and conflicts can be stated honestly.
- Value impacts and tension stay independent, so tension is never computed silently.

### Negative

- The relation vocabulary changes again before core migrated to the previous one (B2), so core's
  seam mapping from `in_tension_with` targets `tension`, not a negative `impacts`.
- Relationship identity is no longer one rule for every type: `tension` is counted apart from the
  structural relationship of a pair.

### Risks

- Contract text, scenarios and the glossary that still say "signed `impacts` edge" mislead readers
  until they are updated. Mitigation: the glossary owned by this repository
  ([ADR-0029](/adr/0029-charter-glossary-is-owned-here/)) carries the new meanings.
- Core keeps its own naming until a later core change. Mitigation: the core request below.

## Alternatives considered

### Keep `impacts` as the signed edge, and name the per-value list differently

Fewer changes to accepted text. Not chosen: the operator wanted `impacts` to read as "what this
artifact impacts", which is the per-value list, and a relationship named for a conflict reads
better as `tension`.

### Derive tension from the two rules' value impacts

Not chosen: values make trade-offs visible but never decide them, and two rules can conflict for
reasons no value captures.

### Keep one relationship per pair and drop `tension` when a structural relationship exists

Not chosen: it hides a real conflict between two rules that require each other.

## References

- [Conceptual model working notes](/context/conceptual-model/): "Values and creeds" and
  "Decisions for the documentation mission"
- [ADR-0009](/adr/0009-contract-1-0-reservations-and-additive-contract-sets/),
  [ADR-0014](/adr/0014-foundational-values-and-creed-rulings/),
  [ADR-0025](/adr/0025-relationships-first-class-and-lazy-repository-walk/),
  [ADR-0027](/adr/0027-drg-relationship-identity-bounded-unidirectional-collapse/)
- Core ADR [`3.x/2026-07-26-3`](https://github.com/spec-kitty/spec-kitty/blob/main/docs/adr/3.x/2026-07-26-3-impacts-edge-subsumes-in-tension-with.md)
- Epic [#77](https://github.com/spec-kitty/spec-kitty-charter/issues/77)

## Requests to core

- When core next touches its relation vocabulary, map `in_tension_with` to `tension` and keep
  `impacts` for the per-value list. No core change is made by this record.
