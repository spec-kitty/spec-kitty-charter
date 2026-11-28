---
okf_version: "0.2"
title: The relation rules run on the walker, from the factory
description: The relation and specialization-cycle rules move into packs.domain.rules and run on the ValidationWalker over a closure's relations; answers are unchanged.
doc_status: active
updated: 2026-11-26T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - architecture/architectural-patterns-applied
  - changelog/2026-11-25-one-walker-runs-the-own-closure-rules
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add or change a rule over the typed edges of a composed closure.
---

# 2026-11-26 — The relation rules run on the walker, from the factory

**The relations phase is built** (ADR-0082 slice 4, #639, the relations part).

- `PackRulesets.relations(composedRelations)` hands out one phase in the `RELATIONS` mode. Its eight checks run in the
  order the rules ran before:
  - a self-override and conflicting relations;
  - an override of an artifact that is not open, and an enhancement of a step whose mission type is locked;
  - an override cycle, an enhances cycle and an ungrounded enhancer;
  - a specialization cycle.

  The relation checks run only when the closure writes an `enhances` or `overrides` edge; the specialization check
  runs regardless.
- `ComposedRelations` in `packs.domain` is the subject. It holds:
  - the `enhances` and `overrides` edges in writer-then-declared order, each end placed by
    `PackCompositionResolver.holderOf`;
  - the `specializes_from` graph;
  - the enhances cycles, computed once;
  - the closure's mutability policies, scoped by `ScopedPolicies`, which moved into `packs.domain` from
    `PackMutabilityPolicy`; the mutability check and the read side's locks call it there.
- `PackRulesets.relationFaults(packs, factsOf)` ran the check over a closure for `ClosureImpactCheck`; the edge-index
  slice replaced it the same day with one walker per closure
  ([The edge-index rules answer one code per fault](/changelog/2026-11-26-edge-index-refusal-codes/)).
- `ImpactRelations` and `SpecializationCycleRule` are deleted. `ImpactRelations`' tests move to `packs.domain.rules`
  unchanged; `SpecializationCycleImpactTest` stays where it is, since it judges through registration. A new case pins
  that a rule's findings come in rule order, not in the order the edges are written.

No refusal code, detail, origin, order of findings or exception changes, on registration, a write, removal or the
census. Every closure judged holds at least the written pack; a closure with no pack has no fault, as before.
