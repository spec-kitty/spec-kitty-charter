---
affected_files: []
cycle_number: 1
mission_slug: adr-0082-graph-walk-slices-01M8A867
reproduction_command:
reviewed_at: '2026-11-26T16:39:14Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP02
---

# WP02 review, cycle 1: changes requested (claude::reviewer-renata)

The design is right and follows D1. GraphWalker<N, E> is generic and has exactly four implementers, all
`<DrgNode, Relationship>` (GraphWalk, Reachability.Cascade, Reachability.Ancestry, GraphWalkTest.Recorder). The node
types, EdgeKind, Scope and PackGraph.of/walk match R-1, R-2 and data-model.md. The step-link classification reuses
MissionStepLinkRule.isOwnStepLink with descriptor().name(), as RelationshipRemovalService does. Every touched service
file is byte-identical to refactor/638-graph-walker (4217f0a53, plus dc10633df for the four restricted files). The
only out-of-map file is PackGraph.java (WP01, approved). The only blocker is test coverage of the driver's own contract.

## MEDIUM

**Issue 1: the element-level selection and STOP on an element are untested, so mutants survive.**
PackGraph.java:147-149 (`if (!scope.selection().test(element)) continue;`) and PackGraph.java:152 (`return false;` on
STOP from an edge) can each be deleted or flipped with PackGraphWalkTest still green:
- The one test with a narrowing selection (`theSelectionFiltersAndNeverPrunes`, PackGraphWalkTest.java:144) runs in
  `PACKS` mode, so no element is ever filtered. Two contracts are therefore unpinned. Both come from the `walk`
  Javadoc, T009/T010 and "a scope that only selects" (FR-004):
  - "an element it does not select is still walked through, so its pack's elements are visited": an unselected pack's
    edges are still handed over. A mutant that adds `continue` for an unselected pack would also pass.
  - an edge the selection rejects is not handed over.
- `stopEndsTheWalk` (PackGraphWalkTest.java:201) stops on a pack node only. STOP returned from `onRelationship` is never
  exercised. The documented SKIP-on-an-element-is-CONTINUE is not exercised either.

Fix: add red-first tests in `RELATIONS` mode:
- (a) the selection rejects pack `a`: `a` is not handed over, but its edges are, in declared order.
- (b) the selection rejects one edge kind: that edge is not handed over and its siblings are.
- (c) STOP on the first edge of a pack: no later edge and no later pack is handed over.
- (d) SKIP on an edge: the next edge and the next pack are still handed over.

## LOW

**Issue 2: the out-of-map edit is not recorded in the commit message.** T010 says "the instance part is an out-of-map
edit, recorded in the commit message". Neither a0f644e14 nor 8d3d30cd5 says so. The for_review reason does record it.
Do not rewrite the signed commits for this alone: record it in the message of the fold commit for Issue 1.

**Issue 3: a duplicate pack_id descends through the first holder only.** PackGraph.java:123 resolves the closure with
`holders.getFirst().descriptor()`. A root that is the second configured pack under a shared pack_id has its own
depends_on/parent_pack ignored. Both holders are still visited (PackGraph.java:124-126). A duplicate pack_id is refused
elsewhere, so this is an edge case. Either document it on `walk`/`of`, or descend through every holder's buildsOn. Also
pin "duplicates stay visible" (T010) with a test: two packs under one pack_id are both visited, in input order.

**Issue 4: PackGraph is now a stateful instance with a non-thread-safe memo.** PackGraph.java:67 and :185 use a
`LinkedHashMap.computeIfAbsent` memo. Say in the `of` Javadoc that a graph is confined to one thread (one judge or
closure), so WP03 does not share one across requests.

## NIT

- PackGraph.java:41: the Javadoc line is 126 characters. The dc10633df edit was not reflowed; the neighbouring lines are
  at most 120.
- PackGraph.java:52: "a rule's check carries its mode ({@code PackRulesets})" is present tense, but no check carries a
  mode until WP03. Write "will carry", or leave it for WP03 to make true and say so.
- a0f644e14's message still names the removed modes (COMPOSE, ARTIFACTS, REFERENCES) and "once per judge". 8d3d30cd5
  corrects this, so no action beyond awareness.
- EdgeNode.Referenced(String) differs from the prompt's `Referenced(PackQualifiedReference)`. No such type exists in
  packs, and PackCompositionFacts.references() holds Strings "as written", so the deviation is correct. Mention it in the
  fold commit.

## Anti-pattern checklist
1. Dead code: N/A by design. The WP's DoD says no rule uses the driver yet; WP03 adds the callers. The unemitted
   EdgeKinds, ArtifactNode and the Written variants are required by T008.
2. Synthetic fixture: PASS. Every test drives PackGraph.walk.
3. Silent empty return: PASS. relationKind's Optional.empty() is documented: the walk does not follow that edge.
4. FR coverage: PARTIAL. FR-004 is covered for packs; the element-level selection is not (Issue 1). NFR-002 is covered
   by the 200,000-pack chain.
5. Frozen surface: PASS.
6. Locked decision: PASS. Scope never names edges, and the caller never picks the edges.
7. Shared-file ownership: PASS. PackGraph.java is WP01-owned; the handoff records it, and so does this review.
8. Production fragility: PASS. The new IllegalArgumentExceptions (unknown root, empty roots) are documented argument
   errors.

## Verification notes
- verifyAll: not re-run (no Gradle in review). Build outputs on lane-b show :packs tests run at 16:25-16:31Z, after the
  final code (16:21:54Z), with 0 failures and 0 errors, and checkstyle reports with 0 errors. The kernel test results are
  from cache. This is consistent with the claim but not proof of the verifyAll task itself.
- Adversarial review: no artifact is recorded in the mission, so the claim cannot be checked.
- Signatures: a0f644e14 and 8d3d30cd5 are signed. The four spec-kitty CLI commits after de30d9975 (54f54d2e8,
  26c431497, 5aa577b07, and the sync merge ca6f75ac2) are unsigned. ca6f75ac2 changes only status files against
  8d3d30cd5. No message names a model, and none has a Co-Authored-By trailer.
