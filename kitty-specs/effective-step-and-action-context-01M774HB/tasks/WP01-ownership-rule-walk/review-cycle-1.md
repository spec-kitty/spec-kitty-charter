---
affected_files: []
cycle_number: 1
mission_slug: effective-step-and-action-context-01M774HB
reproduction_command:
reviewed_at: '2026-11-12T07:06:28Z'
reviewer_agent: claude::reviewer-renata
wp_id: WP01
---

# WP01 review, cycle 1: changes requested

Reviewer: reviewer-renata (claude). Reviewed lane-a at 981a6959d (red d8185a75, feat 007a0734).

## Blocking

### 1. [HIGH] Passes 2 and 3 drop what a root reaches through an artifact an earlier pass listed (FR-008, R3, T003)

`ActionContextWalk.runPass` (about l.264-274) feeds each step only the `fresh` nodes the step before it returned.
`Engine.expand` returns only newly listed nodes. So a node that pass 2 or 3 reaches, but that an earlier pass already
listed, never seeds the later steps of that pass. R3 and T003 say the opposite: "nothing listed earlier is listed
again, but listed nodes still seed (D3)". `whatARootReachesThroughAnArtifactAnEarlierPassListedNamesThatRoot` covers
only the case inside one step, where `reachOf` walks through V. The case across steps is not covered.

Two probes (throwaway tests, deleted afterwards). Each has `M/a -scope-> tactic:T`, and `directive:W` is project-wide.

- Probe 1: `T -vocabulary-> glossary_pack:V`, `W -requires-> V`, `V -vocabulary-> glossary_pack:G`. The answer is
  W, V (ACTION), T. **G is missing.** Drop only `T -vocabulary-> V` and the answer has G as VOCABULARY, PROJECT, root W.
- Probe 2 (depth 1): `T -vocabulary-> procedure:S`, `W -suggests-> S`, `S -requires-> procedure:R`. **R is missing.**
  Drop only `T -vocabulary-> S` and the answer has R as REQUIRES_OF_SUGGESTED, PROJECT, root W.

So the rule stops being monotonic: when the action's own walk reaches more, the context lists less. The ownership
rule says G and R apply, because they are what root W reaches by steps 2 to 5.

Recommendation: seed each step of passes 2 and 3 with every node the previous step reached, whether this pass listed
it or an earlier one did. Do not use only the fresh ones. Leave `walk` and pass 1 byte-identical. Watch the root:
`inherit` has no root to hand down for a seed that an earlier pass listed (`ownRoot` gives null, and so does the fresh
`origins` map). The fix must carry the root W into the next step. Otherwise the new entries have no root, and the
`Entry` constructor throws IllegalArgumentException. Pin both probes with tests, each with its twin, red first.

### 2. [MEDIUM] The NFR-003 gate does not count the operation NFR-003 names

NFR-003 counts "a visit of an edge or a node, the build of the memoized scope-owner index included". The test is
`ActionContextWalkOwnershipTest.operations` (l.589-601). It counts predicate tests, then adds `merged.links().size()`
and `merged.scopeOwners().size()`. Those two terms are sizes of the structure. They grow linearly whatever the code
does, so they can never fail, and they do not measure the memo build.

Mutation evidence: I made `EdgeIndex.isProjectWide` scan every link on each call, which makes the walk quadratic in
edge visits. `theOwnershipRuleAddsAtMostLinearWork` stayed green, while its run time went from 0.29 s to 38.8 s.

Recommendation: count the edge visits themselves. That means the engine's iterations of outgoing links and the edges
the scope-owner build visits, for example through a package-private visit counter or seam. No new dependency and no
suppression. Show that the gate fails under a planted scan of each root.

## Non-blocking

- [LOW] `ActionContextWalkOwnershipTest.java:158-166`, `aScopeEdgeFromAnUnheldSourceStillOwnsItsTarget`: the closure
  holds `action:qa/x` as a place, so `links()` would keep the edge too, and the test stays green when the owners are
  read from `links()`. The A6 hazard is pinned only by `EdgeIndexTest` (`action:gone/y`, the target the pack does not
  hold) and by the evidence test's `mission_type:qa`. Recommendation: leave `action:qa/x` out of the held list, so the
  walk-level test pins a source that no place holds.
- [LOW] Javadoc wording in `ActionContextWalk.java`: the `Pass` type says "The rule of the ownership rule", and
  `walkEffective` says "An artifact one scoped only by other actions or other mission types is not a root". Reword
  both.
- [LOW] The planning text of `research.md` R2 still says "held `scope` edges whose source is a held step". Squad A6
  revised that to "held or not", and the code follows A6. This file is not WP01's; flag it for the WP08 docs pass.

## Verified (no action needed)

- (a) The body of `walk` is byte-identical to the base. The changes to `Engine` do not change behaviour for a single
  pass: the 35 tests of `ActionContextWalkTest` pass unchanged, and so do the effective-read and ClosureViewSession
  tests in activations.
- The claim about `links()` is true: it drops an edge whose source place or target place is not held. Mutation
  evidence: with the owners read from `links()`, the evidence test and two EdgeIndexTest cases go red.
- (b) Mutations are caught. With the kind filter widened to procedures and styleguides, `onlyDirectivesTactics...`
  fails. With the first-pass-wins guard removed from refused suggestions, `aSuggestionCandidateCarriesThePass...`
  fails. The red commit touches tests only.
- (c) Determinism: roots come from a TreeSet, and the 100-read test and the pack-and-link permutation test pass.
- (d) Only directives, tactics and paradigms are roots. (e) Pass roots are listed with hops 1 set explicitly, and
  `hopsOf` is never asked of the origin.
- (f) Scope: the edit to `ClosureView` is the one recorded out-of-map edit, and nothing in slice B changed.
- (g) d8185a75 and 007a0734 are signed, with no model name and no Co-Authored-By. The lane-sync merge commits are
  tool-made and unsigned.
- (h) Checkstyle, Spotless and javadoc are green on `:packs`.
- Contract scenarios: the new listings are exactly what the rule says. US5 AS3 adds `tactic:e`, US5 AS4 adds
  `directive:O` and `tactic:e`, and #484 adds `DX` and `t3` while `px`, `s3` and `g3` stay out. WP02 should note
  that AS3 is not in squad R3's list. In AS3 and AS4 an unscoped enhancer tactic in force now becomes an entry, so
  their narratives ("e is not pulled in as an entry") must change.

## Dependents

WP02 depends on WP01. Once WP01 is approved again, rebase lane-b onto the mission branch:
`git -C /home/user/spec-kitty-charter/.worktrees/effective-step-and-action-context-01M774HB-lane-b rebase kitty/mission-effective-step-and-action-context-01M774HB`.
