---
okf_version: "0.2"
title: One walker runs the own-closure rules the factory hands out
description: PackGraph drives a generic visitor over typed pack-graph nodes within a scope, and a ValidationWalker runs the own-closure rules and REFERENCES; answers are unchanged.
doc_status: active
updated: 2026-11-25T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - architecture/architectural-patterns-applied
  - changelog/2026-11-25-pack-graph-owns-the-walks
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you add a check of a pack's own closure or a walk over the pack graph.
---

# 2026-11-25 — One walker runs the own-closure rules the factory hands out

**The graph walk and the rule factory are built** (ADR-0082 slice 3, #638).

## What changed

- **The visitor contract is generic.** `GraphWalker<N, E>` in the kernel; the artifact walk keeps
  `GraphWalker<DrgNode, Relationship>`, unchanged.
- **The pack graph has typed nodes and a driver.** `packs.domain.graph` holds `PackNode`, `ArtifactNode`, `EdgeNode`
  (the edge as a pack writes it), `EdgeKind` and `Scope`, which only selects. `PackGraph#walk(scope, mode, visitor)`
  visits the roots' composition closure once each, in merge order; the domain's mode decides which edges it emits.
- **One walker runs the rules.** `ValidationWalker` runs phases of checks, each with a node kind and a predicate, keeps
  one bucket per check so findings come rule first, and stops after a phase that records a violation.
- **The own-closure rules come from the factory.** `PackRulesets.ownClosure(..)` hands out the fallback profile, the
  role pins, the pin conflicts and the profile enhancements as one phase over the declaring pack's node; the rules live
  in `packs.domain.rules`. `REFERENCES` runs on the same walker. The `ClosureWalk` port and `OwnClosureWalk` are
  deleted; `ClosureImpactCheck` takes no closure walk.

## What did not change

No refusal code, detail, origin or order of findings changes: the fallback's breaches first, then the pins', then the
enhancements', all collected. The tests of the old walk moved to `OwnClosureRulesTest` with their cases; a registration
test pins the fallback-then-pins order.
