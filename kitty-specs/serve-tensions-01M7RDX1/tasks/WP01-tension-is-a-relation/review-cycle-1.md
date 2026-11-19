---
affected_files: []
cycle_number: 1
mission_slug: serve-tensions-01M7RDX1
reproduction_command:
reviewed_at: '2026-11-19T07:46:35Z'
reviewer_agent: user
wp_id: WP01
---

# WP01 review cycle 1: rejected, small fixes (independent reviewer session_019C2Tk2RcGZ2qH78xUiRot5)

Verified: verifyAll green; no pair-meaning equals/contains/Set/distinct missed outside WP03's files; qualified
self-tension refused in GraphBinding.endpoints and the Relationship constructor; DomainRoundTrip compares
fromPack/toPack; taxonomy order follows RelationType; javadocs updated.

Required:
1. T008.1: TensionIsNotWalkedTest has no reach case: add "the reach from A never lists B" with a requires positive control.
2. G1 extension (neither side owned: smaller bare URN is the source) missing from the Relation description in
   graph-shard.schema.json and org-fragment.schema.json, and ADR-0045 decisions 1 and 6.
3. DoD: record the `git grep -n in_tension_with -- service contracts/charter contracts/packs` result in the activity log.

Nit: reflow the RelationshipEndpointRule catch comment.

Handoff to WP03: refuseStepEnhancementEdge's private samePair must be slot-aware.
