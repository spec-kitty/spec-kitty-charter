---
title: "ADR-0076: The activation's vocabulary reads and projections"
description: How a client reads the terms in force under an activation, and the Contextive file and taboo list projected from them, and how a glossary context overrides across levels.
doc_status: draft
updated: 2026-11-13T00:00:00Z
decision_status: Proposed
decision_date: '2026-11-13'
type: ADR
kind: ADR
related:
  - adr/0049-glossary-contexts-and-terms
  - adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions
  - adr/0074-the-remaining-cutover-reads-and-their-paths
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0029-charter-glossary-is-owned-here
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you build or change the vocabulary reads of an activation, or the Contextive and avoid projections.
  - profile: core-maintainer
    guidance_text: Read this for the reads a terminology gate or an editor plug-in uses instead of its own vocabulary list.
---

# ADR-0076: The activation's vocabulary reads and projections

## Status

**Proposed**, 2026-11-13, by Mission `glossary-contexts-and-terms-01M7B1PP` (#106). It awaits the operator's
acceptance. The plan-point squad has reviewed it, and its findings are folded in. The operator ruled its main points
in the Mission's interviews on 2026-11-13 and 2026-11-14, each a Decision Moment of that Mission:

| Decision Moment | Ruling |
| --- | --- |
| `DM-01M7B7ZYK5H532Y4HR5FE1AGRX` | The projections are in scope. |
| `DM-01M7B804ASKBSKS6TH817WYT7X` | A projection covers the contexts in force under an activation. |
| `DM-01M7B809BWYR7HJE1HTRWM11BC` | Projections are contract reads. |
| `DM-01M7B8570B11V5ZNYA65Y1GVS1` | The reads live under `vocabulary`. |
| `DM-01M7B85ADQMWP9ZEG99MXFGJKF` | A context overrides whole. |
| `DM-01M7B85D68EEHK3TTE0DCTA8RT` | Contextive answers YAML; the other reads answer JSON. |
| `DM-01M7CEZ6V0F8XD95NP8Q5E88KV` | The load warning of ADR-0049 decision 13 is retired. |
| `DM-01M7CEZC44PK37ZHTR82P9N1VV` | The words-to-avoid projection is `taboos`. |

It **extends** [ADR-0049](/adr/0049-glossary-contexts-and-terms/):

- decision 10: the active-terms read, which ADR-0049 left to "the contract set that adds it";
- decision 15: the projections;
- decision 13: it states how that decision reads once a context overrides across levels as every artifact does, and
  it retires that decision's load warning.

## Context

- ADR-0049 makes the pack glossary the one authored source of vocabulary. A Contextive file for an editor, and the
  banned lists of terminology gates, are to be projections of the contexts in force, never hand-kept lists.
- ADR-0074 decisions 9 to 14 give a glossary context and a term their reads, in a pack and under an activation. They do
  not say how a client reads the whole vocabulary in force, nor a projection of it.
- An activation already resolves, per artifact id, one winning copy across its closure (project over org over
  built-in) and its state: selected, deselected, denied or not selected.
- A path under `…/artifacts/glossary/` would collide with a context id: `terms` is a valid id.

## Decision

<!-- markdownlint-disable MD029 -->
<!-- Decisions are numbered across the sections so they can be cited by number. -->
### What is in force

1. **A glossary context overrides across levels whole**, as every artifact does. Two copies at one level are a
   sibling conflict, refused `pack_sibling_conflict` unless settled ([composition rules](/architecture/composition-and-activation-rules/)
   C14 and C15). Terms are not merged across copies of a context.
2. **A term is in force exactly when the winning copy of its context is in force and holds it.** A term has no state
   of its own. The two clash cases of ADR-0049 decision 13 read as follows:
   - A clash "on the same `term:` URN" across levels can only arise between two copies of one context, and decision 1
     settles it.
   - Two contexts that define one name are two terms (ADR-0049 decision 12).

   ADR-0049 decision 13's load warning is retired. An override is reported by the effective reads (`overridden`), as
   for every kind, and no glossary-specific warning is raised.
3. **Every term of a context in force is served, whatever its `status`**, and each answer carries the status. A client
   filters by status, as `applies_to` stays advisory (ADR-0049 decision 14).

### The reads

4. **The vocabulary is a read of the activation, not of an artifact**:
   - `GET /activations/{activationId}/vocabulary/terms`;
   - `GET /activations/{activationId}/vocabulary/contextive`;
   - `GET /activations/{activationId}/vocabulary/taboos`.

   These are activation reads, not artifact reads, so ADR-0074 decision 12's twin rule does not apply to them. A
   pack's own vocabulary is its contexts, read by ADR-0074's paths.
5. **`terms` answers JSON**: `activationId`, `revision`, `governanceFingerprint`, `terms` and `_links`. The links are
   `self` and `activation`.

   Each active term carries:
   - its `urn` (`term:<context-id>/<term-id>`), `contextId` and `termId`;
   - its fields: `name`, `definition`, `status`, and `examples`, `aliases`, `avoid` and `seeAlso` when present;
   - the `packId` and `level` of its context's winning copy;
   - `_links` with `effective-term` (the activation's term read) and `effective-glossary` (its context).

   Terms are ordered by context id, then term id.
6. **`contextive` answers `application/yaml`**, a Contextive v1 definitions document:
   - `contexts`, one per context in force, ordered by id. Each has `name` (the context's name),
     `domainVisionStatement` (its description) and `paths` (its `applies_to.paths`, when present).
   - `terms`, in term id order. Each has `name`, `definition`, `examples` and `aliases` when present, and
     `meta: {urn, status}`.

   The contract describes the body as a string with an example, as it does for `/openapi.yaml`. It is not a JSON
   Schema member.

   The REST conventions' "success is `application/json`" gains this exception. A request whose `Accept` excludes
   `application/yaml` is a 406, as the conventions say for any operation.
7. **`taboos` answers JSON**: `activationId`, `revision`, `governanceFingerprint`, `entries` and `_links` (`self` and
   `activation`).
   - There is one entry per avoided word per term: `word`, `term` (the URN) and `name` (the term's name).
   - Entries are ordered by word, then URN.
   - A word avoided by two terms appears twice: the list states facts and leaves the gate to decide.
8. **Each of the three reads is cached by the activation, as the other effective reads are.**
   - It answers its own `ETag` and the `Charter-Source-Fingerprint` header, and honors `If-None-Match` with `304`.
   - Refusals are judged before the `304`: `404 activation_not_found`, and the closure's `500` refusals such as
     `pack_sibling_conflict`.
   - An activation with no glossary in force answers empty lists and an empty `contexts`, never an error.
9. **Links.**
   - An activation's `_links` gains these relations:
     - `glossaries`: the activation's glossary context list;
     - `vocabulary-terms`, `vocabulary-contextive` and `vocabulary-taboos`.
   - A context item links `terms` as a templated relation `term`.
   - The relation table in the hypermedia conventions lists each one.
<!-- markdownlint-enable MD029 -->

## Consequences

### Positive

- A terminology gate and an editor plug-in read one source, which ADR-0049 decision 15 asked for, and no hand-kept list
  can drift from it.
- No reserved context id is needed, and the artifact tree keeps one meaning: artifacts and their parts.
- Override needs no new merge rule and no new warning: a context behaves as every other artifact does.

### Negative

- One more YAML response body in a JSON contract, after `/openapi.yaml`. The lint and link checks already skip
  non-JSON bodies, so the proof is a structural scenario of its own.
- A project that wants to add one term to a built-in context copies the whole context.
- The term refusal shares `artifact_not_found` with the context refusal (operator ruling). A client tells them apart by
  the detail.

### Risks

- A per-term merge may be asked for later. It can be added as an `enhances`-style rule without changing these reads'
  shapes.

## Alternatives considered

- **Under `…/artifacts/glossary/`, with reserved ids.** Rejected: it adds a reserved-word rule to the id grammar, and
  it puts reads that are not artifacts in the artifact tree.
- **Merge terms per URN across levels.** Rejected for now: it is a new merge mechanism for one kind.
- **All JSON, or content negotiation.** Rejected: a YAML body lets a tool save the Contextive file as it is, and
  negotiation doubles the scenarios for no consumer.
- **Filter out deprecated terms.** Rejected: a gate needs a deprecated term's `avoid` entries, and a reader decides
  what to show.
- **`avoid` as the leaf.** Rejected by the operator: a final verb segment reads as a controller (REST conventions).

## References

- [ADR-0049](/adr/0049-glossary-contexts-and-terms/), decisions 10 and 12 to 15;
  [ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) decision 13;
  [ADR-0074](/adr/0074-the-remaining-cutover-reads-and-their-paths/) decisions 9 to 14.
- [Contextive](https://contextive.tech/) definitions file format.
- [#106](https://github.com/spec-kitty/spec-kitty-charter/issues/106) and slice A2,
  [#536](https://github.com/spec-kitty/spec-kitty-charter/issues/536).
