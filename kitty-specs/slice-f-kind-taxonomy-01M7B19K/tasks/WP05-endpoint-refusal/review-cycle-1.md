---
affected_files: []
cycle_number: 1
mission_slug: slice-f-kind-taxonomy-01M7B19K
reproduction_command:
reviewed_at: '2026-11-13T23:58:38Z'
reviewer_agent: claude-reviewer
wp_id: WP05
---

# WP05 review, cycle 1: changes requested (claude-reviewer)

Reviewed 2ec4455c2 against c9435c1a4. :kernel/:packs/:activations/:deployable tests are green (up to date), the commit is signed and RELATIONSHIP_IS_BOUND's control bites. The write path, its fault order, the composing-read 500 and the registration 422 are correct. A probe against the compiled classes (validate mode through PackPolicy.judge and graph build) confirmed both issues below.

**Issue 1 (MEDIUM): some step_enhancement edges are silently dropped on every read path.**
`RelationshipEndpointRule.refusal` (RelationshipEndpointRule.java:51, `stepEnhancementRule`) skips every `enhances`/`overrides` edge whose source kind is STEP_ENHANCEMENT. StepEnhancementRules only judges enhancements the pack holds (bare URN equal to `step_enhancement:<held id>`). Edges like `step_enhancement:ghost enhances directive:D` or `other::step_enhancement:e overrides mission_type:m` therefore get no finding from any pass:
- `PackPolicy.judge` returns 0 findings (the pack validates clean);
- `PackPolicy.build` drops the `EndpointRule.Fault` (`case EndpointRule.Fault refused -> {}`);
- `FilesystemPackStore` records no violation;
- `DomainRepositoryService.typedEdge` returns empty.

Before WP05 the edge was kept, so this is a new silent drop. It contradicts the javadoc of `PackPolicy.build`/`typedEdge`/`RelationshipEndpointRule`, and also the contract prose in CharterRefusalCode.yaml and the change fragment ("an enhances or overrides edge from a step enhancement keeps prompt_fragment_invalid").
Fix: defer to the step-enhancement rules only for an edge they actually judge (source is a held enhancement, bare). Report every other such edge as relationship_endpoint_not_allowed. Add validate-mode and load tests for both examples above, and correct the prose to "a held step enhancement".

**Issue 2 (MEDIUM): a bare self-loop is reported twice.**
`action:m/s instantiates action:m/s` (schema-valid) gives DRG_SCHEMA_ERROR and RELATIONSHIP_ENDPOINT_NOT_ALLOWED at the same /edges/0. `RelationshipEndpointRule.fault` binds with placeholder packs, so it never skips a self-loop, although its class javadoc says self-loops are left to the graph rules. The test `theFaultsOfOtherRulesAreLeftToThem` uses `directive:D requires directive:D`, which the table allows, so it proves nothing.
Fix: skip a bare (or self-qualified) self-loop in `refusal`, and skip a non-authoredInGraphs endpoint the same way, to match the javadoc. Then test with a diagonal the table refuses (instantiates action->action, or specializes_from procedure->procedure).

**Issue 3 (MEDIUM): a weakened contract assertion.**
mission-types.feature:554 changed the step contract `/references` check from 1 item (urn + relation) to "holds exactly 0 items". The scenario no longer proves that a step contract's references are read. Per AGENTS.md, an assertion may not be weakened to pass. Keep the 1-item proof with a canonical edge from `mission_step_contract:specify`, for example `vocabulary -> glossary_pack:<g>`, which the table allows for every graph-authored source.

**Issue 4 (MEDIUM): the deviation on planted invalid packs is not acceptable as justified.**
The stated blocker does not exist. No test consumes `planted/*/expected.json` or `core-verdicts.json`: PlantedFilesTest was deleted in 2bb3f7626, and `scripts/packs/core_verdicts.py` documents itself as "not part of the build, never gating". FR-016, NFR-002 and the DoD each require an invalid example pack for #350, and create_intent names r30/r31.
Add `planted/r30-edge-procedure-requires-action` and `planted/r31-edge-overrides-across-kinds` (files + expected.json, one fault each). Add a named test, as AssetIdNotKebabTest does, that drives validate mode and the filesystem load (the registration violation at the graph document). Per-family coverage may stay in RelationshipEndpointRuleTest. The core verdict record can be noted as pending; it is not gating.

**LOW (fix now or record for a later WP):**
- L1: EndpointRule.java:79 changes WP03's semantics: an `overrides` edge from a step (action) is now the table's fault, not slice C's. The change is right, because it aligns with OverrideSourceRule, but no test pins it and the commit gives no out-of-map rationale for it. Add an EndpointRuleTest case (`action:m/s overrides directive:d` -> relationship_endpoint_not_allowed) and a read-side case.
- L2: T025 is only partly met. `PackPolicy.tension` (PackPolicy.java:847) still builds a non-guidance `Tension` without `bindTension`. The pack-level pass refuses it, but the Tension constructor is public with no ArchUnit guard like RELATIONSHIP_IS_BOUND.
- L3: The javadoc of `RelationshipShard.ShardRead` says "never a silent drop", but `registeredGraph` now drops table faults and relies on the load having refused first. Emit a finding for a Fault in registered mode, or correct the javadoc.
- L4: The `Relationship.of` 5-arg and 7-arg overloads and `RelationshipDraft.of` have no production caller (anti-pattern item 1). Mark them as test support or move them.
- L5: The `@throws` list of `RelationshipRegistrationService.registerRelationship` interleaves the codes out of the actual fault order (step-enhancement rules, then /from, then /type).
- L6: The fault-order test should say why the pair step-enhancement vs slice C has no row: step_enhancement is activatable, so it cannot occur.

Anti-pattern checklist:
1. Dead code: minor FAIL (L4).
2. Synthetic fixture: PASS.
3. Silent empty return: FAIL (Issue 1).
4. FR coverage: FR-014, FR-015 and C-004 pass; FR-016 and NFR-002 are partial (Issue 4).
5. Frozen surface: PASS.
6. Locked decision: PASS (no legacy reader).
7. Shared-file ownership: minor FAIL (L1 rationale missing).
8. Production fragility: PASS.
