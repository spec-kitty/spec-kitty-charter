---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T07:48:22Z'
reviewer_agent: claude
wp_id: WP02
---

# WP02 review 1 (reviewer-renata, independent delegate): changes requested

1. **[HIGH] Breach messages.** Each `Preset` breach message names the preset's id and the offending value: the item
   id, the kind, or the duplicate.
2. **[MEDIUM] `required()` contract.** Either include the mission types in `required()`, or amend data-model.md and
   the WP to record `requiredMissionTypes()`. The author keeps the split and amends the documents.
3. **[MEDIUM] Paired controls.** Each `PresetTest` breach is paired with a valid control on the same fixture:
   description, kind gate, empty list, duplicate id, blank item id.

LOW findings, which the author also takes:

- copy-safety tests for the inner lists, `activatedKinds` and `missionTypes`;
- the `Pack` class doc names `PackLevelContent` and says the pack document comes with #194's form.
