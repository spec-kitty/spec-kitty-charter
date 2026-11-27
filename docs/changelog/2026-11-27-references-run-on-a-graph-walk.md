---
okf_version: "0.2"
title: REFERENCES runs on a pack-graph walk
description: The delegation, reference and enhancement target checks run on a PackGraph packs walk, the members chosen by the scope; answers are unchanged.
doc_status: active
updated: 2026-11-27T00:00:00Z
type: Changelog
kind: Changelog
related:
  - adr/0082-the-domain-owns-every-graph-walk-and-ports-transform-and-interface
  - architecture/architectural-patterns-applied
  - changelog/2026-11-26-the-relation-rules-run-on-the-walker
audience:
  - profile: charter-service-contributor
    guidance_text: Read this before you change how registration or pack-doctor's --full judge a closure's references.
---

# 2026-11-27 — REFERENCES runs on a pack-graph walk

**`REFERENCES` is on the graph walk** (ADR-0082 slice 4, #639; operator ruling 01M8DT4CGSQZBPJCC6K9CN3ZX4).

- `PackRulesets.referencesCheck(closure, factsOf)` hands out a `GraphCheck` in the `PACKS` mode with three phases, in
  registration's order: the substep delegations, the references, the step enhancements' targets. The scope's
  selection chooses the members; each phase judges every pack node it is handed. No new walk mode: `PACKS` already
  hands over each pack and no edge.
- `PackRulesets.references(closure, factsOf)` runs it over the closure's graph. The graph holds the closure's packs in
  the closure's order and every pack is a root, so the walk hands over every member once, in the order the callers
  passed before. The phase gate is still the stop rule.
- The separate `members` argument is gone: registration's composing judge, pack-doctor's `--full` and the tests all
  passed the closure as its own members. The callers are updated, with no overload left beside the new shape.
- The "Interim" notes in `PackRulesets` and on the architecture page are removed. `PackGraphWalkBoundaryTest` adds a
  rule: only `GraphCheck` runs a `ValidationWalker`, so no rule factory feeds the walker by hand.
- `ReferencesWalkTest` pins it: the first check with a fault answers, member by member in the closure's order; a
  member the scope does not select is not judged and its facts are not read.

No refusal code, detail, origin, order of findings or exception changes, on registration or pack-doctor. A closure
with no pack has no fault, as before.
