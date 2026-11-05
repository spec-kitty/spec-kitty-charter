---
work_package_id: WP10
title: packs typed edges and the reach operation
dependencies:
- WP08
requirement_refs:
- FR-012
- FR-013
- FR-014
- FR-015
- NFR-006
- NFR-007
- C-002
- NFR-001
planning_base_branch: issue-360-activated-closure
merge_target_branch: issue-360-activated-closure
branch_strategy: Planning artifacts for this mission were generated on issue-360-activated-closure. During /spec-kitty.implement this WP may branch from a dependency-specific base, but completed changes must merge back into issue-360-activated-closure unless the human explicitly redirects the landing branch.
subtasks:
- T062
- T063
- T064
- T065
- T066
- T067
agent: claude
history: []
agent_profile: java-jenny
authoritative_surface: service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java
create_intent:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachRequest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachResult.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ReachabilityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ReachabilityBudgetTest.java
execution_mode: code_change
model: ''
owned_files:
- service/packs/src/main/java/ai/speckitty/charter/packs/application/Reachability.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachRequest.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/ReachResult.java
- service/packs/src/main/java/ai/speckitty/charter/packs/application/EdgeIndex.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ReachabilityTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/EdgeIndexTest.java
- service/packs/src/test/java/ai/speckitty/charter/packs/application/ReachabilityBudgetTest.java
role: implementer
tags: []
tracker_refs: []
---

# WP10: packs typed edges and the reach operation

## ⚡ Do This First: Load Agent Profile

Use the `/spk-charter-profile-load` skill to load the agent profile specified in the frontmatter, and behave according to its guidance before parsing the rest of this prompt.

- **Profile**: `java-jenny`
- **Role**: `implementer`
- **Agent/tool**: `claude`

If no profile is specified, run `spec-kitty agent profile list` and select the best match for this work package's `task_type` and `authoritative_surface`.

---

## Objective

Give `packs` the one traversal the cascade needs (squad A5: no second walker anywhere):

- `PackCompositionFacts.edges` carries every edge `(source, relation, target)` as written, for every graph-authored
  kind. That covers graph-document edges, component relationships (typed references, `ArtifactEdges.build`) and
  registered relationship shards (`PackWriteStore.registeredRelationships`);
- `EdgeIndex`, built from a closure's facts, is an edge-backed `ComponentResolver`/`ComposeResolver` that resolves
  `<pack>::` targets through the compose's `holderOf`;
- `Reachability.reach(view, request)` runs on `GraphWalk` and returns:
  - the reached nodes, with the relation and the predecessor by which each was reached (every predecessor, not
    only the first);
  - the nodes it stopped at;
  - the nodes behind a stop;
- `enhancersOf(view, urns)` answers the reverse `enhances` lookup;
- all of it runs on the `ClosureView` WP04 built, so a write still takes one verified read (NFR-007).

This is PR B's first WP. It starts after PR A's last code WP (WP08); the PR A docs WP09 runs in the planning lane.

Implementation command:
`spec-kitty agent action implement WP10 --agent claude --mission activation-register-and-closure-01M6NXBD`

## Context

- **PR:** B (WP10–WP14), stacked on PR A. **Concern:** IC-07. **Research:** R7; squad A5.
- **Rulings:** ADR-0059 D2 as revised on 2026-11-05:
  - D2.1: five relations, forward, no depth limit, the visited guard;
  - D2.2: `enhances` both ways **at read**. At write time it is used only for `includeDeactivated` deny-clearing and
    for deactivate's shared check;
  - D2.3: `<pack>::` followed;
  - D2.4: lineage over `specializes_from` passes `requires` and `scope` only, up the whole chain;
  - D2.5: no other edge.
  This WP supplies the mechanism; WP11 supplies the policy.
- **Collisions:** PR #377 touches `DomainRepositoryService`. Rebase after it.
- **Grounding:**
  - `DomainRepositoryService.compositionFacts` (lines ~314–344) reads graph edges and flattens them to targets
    (`changed`, `references`). The typed edge is lost there today.
  - `CompositeComponentResolver.resolve` dispatches only five kinds (directive, styleguide, paradigm, toolguide,
    tactic); every other kind resolves empty, so a `GraphWalk` over it never reaches profiles or procedures.
  - `GraphWalk(ComposeResolver)` already walks across packs, with a visited guard keyed by (pack, URN), and offers
    `onNode` and `onRelationship` with `Descent.CONTINUE/SKIP/STOP`. The predecessor of a node is the relationship
    passed to `onRelationship` just before the neighbour's `onNode`.
  - `GraphWalk` also crosses a bidirectional edge backwards (`IMPACTS`). None of the cascade relations is
    bidirectional, and the request's relation filter skips the rest (`Descent.SKIP` in `onRelationship`).
  - The kernel `Relationship` already carries `from`, `to`, `type`, `fromPack` and `toPack`, so it **is** the typed
    edge. Do not invent a parallel record.

## Branch Strategy

- Planning branch and merge target: `issue-360-activated-closure`. PR B is a stacked PR on PR A.
- Lane worktree from `lanes.json`. It depends on WP08, so PR A's code is complete before PR B starts (docs WPs live in the planning lane, which no code lane may depend on).
- Rebase onto the lane base (and onto `main` once PR A merges) before review.

## Out-of-map edits (sequenced by dependency)

- `service/packs/src/main/java/ai/speckitty/charter/packs/domain/PackCompositionFacts.java` (owner WP03): add
  `List<Relationship> edges`.
- `service/packs/src/main/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryService.java` (owner WP03): fill
  the edges.
- `service/packs/src/test/java/ai/speckitty/charter/packs/infra/storage/DomainRepositoryServiceTest.java` (owner WP03):
  cases for each edge source.
- `service/packs/src/main/java/ai/speckitty/charter/packs/application/ClosureView.java` and `ClosureQueries.java` (owner
  WP04): hold the `EdgeIndex`, and expose `reach` and `enhancersOf` on the view.

## Subtask Guidance

### Subtask T062: Typed edges on the facts

**Steps (red first, `DomainRepositoryServiceTest`):**
1. A pack whose graph document has `directive:D requires tactic:T`, `tactic:E enhances tactic:T` and
   `agent_profile:P specializes_from agent_profile:Q`: `compositionFacts(pack).edges()` holds all three, with
   relation types and any `toPack` qualifier as written.
2. A tactic whose typed reference names a styleguide (`ArtifactEdges.build` output): present as `requires` or
   `suggests`, as the component's `relationships()` gives it.
3. A registered relationship shard (`PackWriteStore.registeredRelationships`): present. Read how
   `CommitStorePackWriteStore` exposes it, and use the same store read `compositionFacts` already has.
4. The order is stable (graph documents in file order, then components in `assembled.artifacts()` order, then
   shards), and duplicates (the same from, type, to and qualifiers) are removed.

**Implementation:** add `edges` to `PackCompositionFacts`, keeping the shorter constructors defaulting to `[]`. In
`DomainRepositoryService`, extract `typedEdges(graphs, assembled, shards)`, which keeps `compositionFacts` under 60
lines (it is already split from WP03; keep it so). Graph-document edges come from `GraphDocument.Edge`: build a
`Relationship` with `Relationship.of(...)` and the qualifiers via `PackQualifiedReference.parse`.

### Subtask T063: `EdgeIndex`, an edge-backed resolver

**Purpose:** let `GraphWalk` walk every graph-authored kind using only the facts already read.

**Steps (red first, `EdgeIndexTest`):**
1. `EdgeIndex.of(closure, factsByPack)` indexes the edges by (holding pack, source URN), where the holding pack of
   a source is the pack whose facts carry the edge.
2. `inPack(pack).resolve(urn)` returns a lightweight `CharterArtifact` (a private record `EdgeNode` implementing
   the kernel interface) whose `relationships()` are the indexed edges from that URN in that pack, whose `packId()` is
   the pack, and whose `label()` is the URN text. It answers empty when the pack does not hold the URN (use
   `PackUnderResolution.holds`).
3. `holdingPack(reference, referringPack)` delegates to `PackCompositionResolver.resolveReference`/`holderOf`, the
   compose's one closure rule. A `<pack>::` target outside the referring pack's `depends_on` closure is refused as the
   compose refuses it, which cannot happen after a successful compose (spec Edge Cases).
4. `incoming(urn, RelationType)` returns the reverse index (used for `enhancersOf`), built once.

### Subtask T064: `Reachability.reach`

**Purpose:** the traversal (FR-012, FR-014 mechanism).

**`ReachRequest`:** `Set<Urn> starts`; `Set<RelationType> relations` (the forward cascade relations);
`Predicate<Urn> stopAt` (the walk does not descend into a stopped node; the caller passes the deny set);
`Optional<Lineage> lineage`, where `Lineage(RelationType ancestry, Set<RelationType> passThrough)` is
`specializes_from` with `{requires, scope}` for ADR-0059 D2.4.

**`ReachResult`:**
- `Map<Urn, List<Via>> reached`, where `Via(Urn predecessor, RelationType relation)` and a start has no `Via`;
- `Map<Urn, List<Via>> stopped`: the nodes the stop predicate matched, with every predecessor (ADR-0059 D2.2
  reporting: "it must not drop a later artifact that also reaches it");
- `Set<Urn> behindStopped`: nodes reachable only through a stopped node. Compute it with a second, unstopped walk from
  the starts, then take the difference with `reached ∪ stopped`;
- `Set<Urn> ancestorsPassed`: lineage ancestors walked through but not put in force.

**Steps (red first, `ReachabilityTest` over a hand-built `EdgeIndex`):**
1. D requires T, D requires X, and X is stopped: reached {D, T}, stopped {X via D requires}.
2. A cycle (A requires B requires A) terminates; each node is visited once.
3. Relations filter: D suggests S with relations {requires} leaves S unreached.
4. Behind a stop: X requires Y, and nothing else reaches Y: `behindStopped` is {Y}. If D also reaches Y another way,
   Y is reached and not behind.
5. Lineage: P `specializes_from` Q; Q requires R, Q suggests S. Starting at P, with lineage: R is reached (via Q,
   requires), S is not, and Q is in `ancestorsPassed`, not in `reached`. Q stopped: R is in `behindStopped`, and Q
   is in `stopped`.
6. Transitive lineage: P → Q → Q2 (`specializes_from`), and Q2 scopes R2: R2 is reached.
7. Two predecessors of one stopped node: both `Via`s are recorded.
8. A `<pack>::` target in another closure pack is reached in that pack.

**Implementation:** a `GraphWalker` visitor that, in `onRelationship`, answers `SKIP` for relations outside the
request (and outside `passThrough` when the current node is a lineage ancestor), and records the pending `Via`. In
`onNode` it records reached or stopped, and answers `SKIP` for stopped nodes. A lineage ancestor is entered over
`specializes_from` only from a profile in the walk, and is never recorded as reached. Keep the visitor's state in a
small private class with one method per decision, to stay within the limits. `GraphWalk.visit` dedups visits, so a
second predecessor of an already-visited node is seen only in `onRelationship`. Record it there by peeking at the
target URN.

### Subtask T065: `enhancersOf`

**Steps (red first):**
1. `enhancersOf(view, {T})` answers every E with `E enhances T` in the closure, with E's pack.
2. It is reverse only, and one hop: the clearing and shared rules (WP11) decide what to do with the answer.
3. A pack-qualified `enhances` target resolves through `holderOf`.

### Subtask T066: Expose on the closure view

**Steps (out-of-map, `ClosureView` and `ClosureQueries`):**
1. `ClosureQueries.snapshot` builds the `EdgeIndex` from the facts it already read (no extra read; extend the call-count
   test of WP04's T024).
2. `ClosureView.reach(ReachRequest)` and `ClosureView.enhancersOf(Set<Urn>)` delegate to `Reachability`.
3. The activations adapter (WP12) calls these on the view it took for the request.

### Subtask T067: The 500-artifact report (NFR-006, report-only)

**Steps:**
1. `ReachabilityBudgetTest`, tagged `timing` (so it runs only in `:packs:timingBudgets`, which CI reports and never
   gates, #316 and ADR-0057): a generated `EdgeIndex` of 500 artifacts with the five relations and some `enhances`;
   reach from 20 starts, 50 times; print the p95 in ms.
2. Assert a generous ceiling only if the existing timing tests do; otherwise print only. Never add it to `test`.

## Checkstyle limits (NFR-001)

CC ≤ 15, NPath ≤ 1000, ≤ 60 lines, nested-if depth ≤ 1, by construction. The walk visitor is the hotspot: one small
method per decision. No new suppression, and no disabled test.

## Gate commands

```bash
./gradlew :packs:test --tests '*ReachabilityTest*' --tests '*EdgeIndexTest*' --tests '*DomainRepositoryServiceTest*' \
  --tests '*ClosureQueriesTest*' --tests '*GraphWalkTest*'
./gradlew :packs:check -x openPacksRoundTrip
./gradlew :packs:timingBudgets                     # report-only, in an invocation of its own
./gradlew :deployable:test --tests '*CharterContractSuite*' --tests '*HexagonalArchitectureTest*'
```

## Definition of Done

- [ ] `PackCompositionFacts.edges` holds graph, component and shard edges as `Relationship`s.
- [ ] `EdgeIndex` resolves every graph-authored kind and `<pack>::` targets via the compose's rule.
- [ ] `Reachability.reach` uses `GraphWalk` (no second walker), and returns reached (with every `Via`), stopped (with
      every `Via`), behind-stopped and the ancestors passed.
- [ ] `enhancersOf` answers the reverse `enhances` lookup.
- [ ] The view exposes both with no extra read; WP04's call-count test still passes.
- [ ] The 500-artifact timing is printed, report-only.
- [ ] No new suppression, and no disabled test.

## Risks

- **Multiple predecessors versus the visited guard.** The guard must stay (cycles); recording extra `Via`s in
  `onRelationship` is the way around it. The two-predecessor test is the guard.
- **Edge volume.** Shards and typed references can duplicate graph edges. Dedup by value.
- **`GraphWalk` changes.** Avoid editing it. If a hook is truly missing, add it minimally, keep `GraphWalkTest`
  green, and say why in the review notes (it is outside the map).

## Reviewer guidance (reviewer-renata)

- **No second walker:** grep the new code for a `Deque`, a `Queue` or a recursive `visit`. The traversal must be
  `GraphWalk.walk`.
- **Lineage:** check that the test where Q suggests S proves S is **not** reached, and that Q is never in `reached`.
- **Multiple predecessors:** check the two-predecessor test asserts both `Via`s.
- **Non-fakeable evidence:** the `timingBudgets` output line with the p95; the `DomainRepositoryServiceTest` case per
  edge source.
