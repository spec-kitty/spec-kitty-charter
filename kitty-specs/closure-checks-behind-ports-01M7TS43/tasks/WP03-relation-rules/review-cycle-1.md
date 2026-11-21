---
affected_files: []
cycle_number: 1
mission_slug: closure-checks-behind-ports-01M7TS43
reproduction_command:
reviewed_at: '2026-11-20T07:14:07Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP03
---

# WP03 review (claude::reviewer-renata) - commit 76b065f - CHANGES REQUESTED

## [MEDIUM] ImpactRelations.java:354-394 - `cyclesIn` misses a cycle that shares no node with any reported cycle; the set of faults depends on edge order

FR-003 requires every relation fault, each cycle once; plan.md says "a node on a reported cycle starts no new one", and the
javadoc says only "a cycle through a reported one (two cycles that share a node) is not reported again". The walk drops
more than that. Reproduction (the method copied verbatim into a harness, graph given as edges in insertion order):

```
v>x, v>u, x>y, y>x, y>u, u>v   ->  [[x, y]]
v>u, v>x, x>y, y>x, y>u, u>v   ->  [[v, u], [x, y]]
```

Same graph, two edge orders, different faults. In the first order the walk goes v, x, y, finds x-y, then descends
y -> u, sees the back edge u -> v whose path segment [v, x, y, u] contains x (on a reported cycle) and drops it, and
finishes u. Back at v, the edge v -> u is skipped because u is finished, so the cycle v -> u -> v, which shares no node
with x-y, is never reported. The same walk serves `SpecializationCycleRule` and the override cycles. (Nothing else is
wrong in the walk: until the first cycle it is step-for-step the old `cycleIn`, so the first detail is unchanged; it
terminates; nodes are pushed at most once.)

Recommendation: choose a rule that does not depend on edge order and say it in FR-003/plan, then make the walk follow it
and the javadoc describe it. Two options:
- one fault per strongly connected component that holds a cycle (an iterative Tarjan/Kosaraju pass, linear), reporting
  the DFS-first cycle of each SCC so the first detail stays as it is; or
- keep "every cycle that shares no node with a reported one", which means a node that was finished must not hide a
  disjoint cycle (for example, rerun the search over the nodes not on a reported cycle until it finds nothing new, and
  check the cost against NFR-002).
Add a `cyclesIn` test with the graph above in both edge orders, asserting the same faults.

## [LOW] ImpactRelations.java:286-321 - the scope of the ungrounded exclusion goes beyond the written rule

FR-003 and plan.md drop "a place on a reported enhances cycle". The code also drops every enhancer that leads into one.
That reading is sound, because such an enhancer is ungrounded only through the cycle, and fixing the cycle decides its
grounding. But the spec text no longer matches the code. Record the wider reading in FR-003/plan (or in the WP05 or
docs amendment).

## [LOW] ImpactRelations.java:379-380 - the back-edge cost is not linear

Each back edge to an on-path node copies the path tail and scans it (`indexOf`, `subList`, `noneMatch`). Many back edges
from deep in a long path cost O(V*E), which goes against NFR-002's "linear in the edges". Real closures are small, so
fix this together with the MEDIUM.

## [LOW] Test gap for T011

No test shows `SpecializationCycleRule.check` returning two separate `specialization_cycle` faults. Add one in
`SpecializationCycleImpactTest`, at the unit level or through registration.

## Verified, no finding

- T009: `check` returns `List<ClosureFault>` from the seven rules in their fixed order, each in edge order. `Violation`
  is gone. Packs are the writer, then the places' packs.
- Self-override is excluded from the override graph.
- Bridge: a single fault goes through `PackRefusals.of`'s one-fault path, so its code, detail ("In the closure rooted at
  ..."), packs (involved, then roots), violations and requiredBy are the same as before. Collecting
  `specialization_cycle` alongside the other relation faults, where before it was judged only when they passed, follows
  FR-003's rule order and FR-009. The census keeps the first fault until WP05.
- The tests assert outcomes (codes, counts, details), and the targeted suites are green.
