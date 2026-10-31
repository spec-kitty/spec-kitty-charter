---
affected_files: []
cycle_number: 1
mission_slug: activation-set-01M691HW
reproduction_command:
reviewed_at: '2026-10-31T14:03:27Z'
reviewer_agent: claude
wp_id: WP03
---

# WP03 review (independent reviewer subagent, profile reviewer-renata): REJECT, small fixes

1. `_links` descriptions in the six success schemas name a relation that does not exist; name the actual keys (`self`).
2. WP03 depends on WP02 (`minItems` in ContractConformance) without declaring it; deployable suite verified in WP04's lane, which merges both.
3. `x-derived` inputs cite WP04's parameter files by forward reference; WP04 must use exactly ActivationId, MissionTypePath, ActionPath.
4. ClosureChecker recognises the Links map by title only; key it on a `_links` property whose values are a closed `Link` (aligned with #126), refuse patternProperties, add lookalike fixtures.
5. The parity tests merge `$ref` siblings over the target; assert a `$ref` has no validating sibling.
6. Request-body and Link provenance is circular; cite ADR-0020 D6 / the REST adapter instead.
7. Corpus README and Javadoc name a test that lands in WP04; acceptable within the mission.
8. Follow-up: an explicit `null` list on a registration body is now treated as `[]`; refuse it with 422 in a follow-up.
9. data-model.md: add `PackPin.version` maxLength 64.
