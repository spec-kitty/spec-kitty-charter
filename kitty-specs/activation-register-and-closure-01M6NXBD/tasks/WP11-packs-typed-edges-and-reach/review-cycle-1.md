---
affected_files: []
cycle_number: 1
mission_slug: activation-register-and-closure-01M6NXBD
reproduction_command:
reviewed_at: '2026-11-05T23:30:52Z'
reviewer_agent: claude:claude-opus-5-5:reviewer-renata
wp_id: WP11
---

# WP11 review (reviewer-renata) — changes requested (one test gap)

The design and code are sound: one traversal (GraphWalk over EdgeIndex; no Deque/Queue/recursion in the new code), J4 cases pass, (pack, URN) keying, `<pack>::` via holderOf + checkReferences at index build, Vias recorded in onRelationship, behindStopped = open walk − (reached ∪ stopped), single ConsistentRead (call-count test), merge preserved WP17 (UnknownRootException, heldPresets, parents). Gates green: targeted 86/86; :packs:check -x openPacksRoundTrip 2795 tests (4 skipped, pre-existing), 0 failed; :deployable CharterContractSuite+Hexagonal 351/351; timingBudgets p95 11.672 ms (report-only).

**Issue 1 (blocking): the dedup key's relation is not pinned by any test (surviving mutant).**
`DomainRepositoryService.keep` — replacing `edge.type()` in `new EdgeKey(...)` with a constant (`RelationType.REQUIRES`) leaves `DomainRepositoryServiceTest` fully green. This is exactly the kernel `Relationship.equals` caveat (equals ignores relation and qualifiers): a regression to keying by value, or by a key without the relation, would silently drop e.g. `D suggests T` when `D requires T` is also written, and the cascade would lose an edge.
Fix: add a case to `theEdgesHoldComponentAndRegisteredShardEdgesInOrderWithoutRepeats` (or a new test) where the same (from, to) is written with two relations (e.g. graph `directive:D requires tactic:t1` and shard `directive:D suggests tactic:t1`), asserting both survive in order; and ideally the same (from, relation, to) written once bare and once `<pack>::`-qualified, asserting both survive (pins the qualifier part of the key too). Re-run the mutant to confirm it is killed.

Non-blocking note: an ancestor passed through (in `ancestorsPassed`) that is also reachable only through a stopped node will appear in `behindStopped`. Probably the intended semantics (it is not in force), but WP12 should be aware; a one-line Javadoc on `ReachResult.behindStopped` would settle it.

Other mutants checked and killed: lineage pass-through filter (3 fail), `behind.removeAll(seen)` (3 fail), first-Via-only recording (2 fail).
