---
title: "ADR-0055: Cutover sequencing: the Java service first, then core's integration"
description: "Operator rulings of 2026-11-03: build the Java service before core's integration, on the depends_on resolver, accepted against the design."
doc_status: active
updated: 2026-11-04T00:00:00Z
decision_status: Accepted; open question 1 answered by ADR-0059 (2026-11-04)
decision_date: '2026-11-03'
type: ADR
kind: ADR
related:
  - adr/0021-contract-first-seam-generated-client-and-routing-proxy
  - adr/0048-pack-composition-and-the-named-packs-split
  - adr/0010-cutover-scope-and-seam-boundary
  - adr/0020-offering-and-activations
  - plans/roadmap
  - plans/milestones
audience:
  - profile: governance-lead
    guidance_text: Read this for the order in which the cutover is built, and what core waits for.
  - profile: charter-service-contributor
    guidance_text: Read this before picking up cutover work; it says what is on the critical path and in which order.
---

# ADR-0055: Cutover sequencing: the Java service first, then core's integration

## Status

**Accepted**, 2026-11-03. Decider: Stijn Dejongh (operator). The operator ruled these points in one session on
2026-11-03, while planning the resolution domain ([#10](https://github.com/spec-kitty/spec-kitty-charter/issues/10),
[#11](https://github.com/spec-kitty/spec-kitty-charter/issues/11)) and the activation register
([#291](https://github.com/spec-kitty/spec-kitty-charter/issues/291)).

This record amends [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/) D2 and D6, and
moves [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/)'s resolver onto the cutover path. The same session ruled
[ADR-0053](/adr/0053-operator-rulings-on-the-pack-ecosystem-open-questions/) open question 2: an enhancing artifact
lives beside the kind it adds to, with the `enhances` edge in its pack's graph shard. That ruling is annotated there.

## Context

The 4.x cutover makes every offering read in core go through this service
([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/)). On 2026-11-03 the service could list packs, but it could
not answer what is in force for a project:

- no layer merge (built-in, org, project);
- no activation or cascade;
- an activation register that answers `501` for every write.

ADR-0021 D2 had core vendor a generated client "as soon as 0.1.0 is published, before any operation is served by
the service". In that plan, core's integration work and the service's domain work ran in parallel (roadmap steps 4
to 6).

Separately, ADR-0048 added a second axis to the merge: within one level, packs declare `depends_on`, so a level
must be resolved and ordered before the levels stack. The work breakdown had planned that resolver (items 2 and 3)
after the cutover.

## Decision

### D1. The service is built first, in Java; core's integration changes after (ruled)

The cutover is built in two phases.

1. **The service.** The charter service answers the cutover reads and writes from its own Java domain:
   - the layer merge;
   - activation and cascade;
   - the org and project layers;
   - the effective reads;
   - the activation register.
2. **Core's integration.** Only then does core change its integration: it vendors the generated client, builds the
   anti-corruption layer and the routing proxy, and starts the per-operation shadow.

This amends ADR-0021:

- **D2:** core vendors the client when the service is built, not as soon as 0.1.0 is published.
- **D6:** core's adoption no longer waits on the first release alone.

ADR-0021 D1 (the published contract is the seam), D3 to D5 (the proxy, the shadow, per-operation switching, fail
closed) are unchanged. They now apply in phase 2.

**Why.** Core switches onto a stable service, not one whose domain is still being built. A core integration
written against a service that cannot answer would be reworked as the service grows.

### D2. The `depends_on` resolver comes before the layer merge, and before the cutover (ruled)

The layer merge is built on ADR-0048's dependency resolver: each level is resolved through `depends_on` first, then
the levels stack. The resolver is not deferred behind a single-pack-per-level shim.

- Work-breakdown items 2 and 3 ([#145](https://github.com/spec-kitty/spec-kitty-charter/issues/145),
  [#146](https://github.com/spec-kitty/spec-kitty-charter/issues/146)) move from Post-cutover to the 4.x cutover
  milestone.
- The pack-composition mission (branch `issue-145-pack-composition`) lands before the resolution domain is
  specified.

**Why.** The merge is right for the pack ecosystem from the start, and is not rebuilt when the resolver lands.

### D3. The design is the acceptance oracle, not core's Python answers (ruled)

The service is accepted against this repository's own design and structure, not against a golden set taken from
core's Python implementation.

1. **The packs come first.** They are hand-authored or updated to the current design: the packs the service ships
   and loads, and fixture packs that exercise each rule. The rules include `depends_on`, the levels, activation,
   cascade, `required_<kind>`, `specializes_from`, and `enhances` and `overrides`.
2. **Then the domain is built.** The resolution domain and the activation register are accepted against what the
   ADRs say those packs resolve to.

This supersedes, as an acceptance criterion:

- the conformance corpus over core's CLI ([ADR-0016](/adr/0016-conformance-corpus-harness-over-core-cli/));
- "the conformance corpus alone gates the switch" ([ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/);
  ADR-0021 D5);
- roadmap step 3.

The steps that said "the corpus slice passes against Java" (#10, #11) are accepted against the design-derived packs
instead.

**Why.** The service now implements the redesigned model (ADR-0048 to ADR-0054). Core's Python implements the old
one, so matching it would pin the service to the design it replaces.

## Consequences

### Positive

- Core integrates once, against a service that answers the cutover reads.
- The layer merge is built once, on the final composition model.

### Negative

- The cutover's critical path grows by the resolver (items 2 and 3).
- Core's client, ACL and proxy work (spec-kitty#6518, #6522, #6523) waits longer.

### Risks

- Contract shape mismatches surface later, when core adopts the client, not as the contract evolves. The contract
  tests on this side, and the corpus, carry that risk until then.

## Open questions

1. **What gates each core switch in phase 2,** now that core's Python answers are not the reference (D3)? It is
   needed before the first shadow (#14), not before the service is built.
   *Answered 2026-11-04 (operator): the design's BDD scenarios and contract tests gate each switch
   ([ADR-0059](/adr/0059-switch-gate-cascade-edges-and-activation-rulings/) decision 1).*

## Follow-ups

- Move #145 and #146 to the 4.x cutover milestone.
- Re-scope #9 (the corpus harness) and #72 (conformance as the switch gate) under D3. Add the
  supersession notes to ADR-0010, ADR-0016 and ADR-0021 D5.
- Re-word the acceptance criteria of #10 and #11 to the design-derived packs.
- Update the roadmap's step order (steps 4 to 7) and the strangler-transition page to the two phases.
- Note the amendment on ADR-0021 D2 and D6.
- Tell core: the amendment affects core ADR `2026-10-16-1` and the held spec-kitty#6522 and #6523.

## References

- [ADR-0010](/adr/0010-cutover-scope-and-seam-boundary/), cutover scope.
- [ADR-0021](/adr/0021-contract-first-seam-generated-client-and-routing-proxy/), the seam, client and proxy.
- [ADR-0048](/adr/0048-pack-composition-and-the-named-packs-split/), pack composition.
- [Roadmap](/plans/roadmap/) and [Milestones](/plans/milestones/).
