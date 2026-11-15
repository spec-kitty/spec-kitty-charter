---
affected_files: []
cycle_number: 3
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-15T08:07:31Z'
reviewer_agent: claude-reviewer
wp_id: WP10
---

# WP10 review (landing fix pass 24f906cbb..7521d546c): REJECT

M1 (MEDIUM) Registered-shard half of ruling DM-01M7F1AR untested: ArtifactRemovalService.java:157 `goesWithAType` clause can be deleted with all suites green. Add a unit test (qaWithADelegatingType(), remove mission_type/rx, registered.graph.yaml keeps no action:rx/ scope edge) and a pack-removal.feature twin (PUT relationships bare + qa:: qualified, DELETE type -> 204).
L1 Remove NodeKind.authoredInGraphs(); node declaration checks use declarableInGraphs(), endpoint checks use declarableInGraphs() for source and edgeTarget() for target.
L2 Docs (requires-edges fragment:17, service-contract.md, docs changelog) must say registered edges go too.
L3 ADR-0077 cites the 2026-11-15 rulings F1AR/F1AX/F1B2/F1B7 at decisions 4-7; update the count.
L4 KindRow.plural description states why it can be absent (#106 term).
L5 activation-retired-keys.feature header: ActivationConfig has no catalog member.
