---
affected_files: []
cycle_number: 1
mission_slug: activation-set-01M691HW
reproduction_command:
reviewed_at: '2026-10-31T14:28:01Z'
reviewer_agent: claude
wp_id: WP04
---

# WP04 review (independent reviewer subagent, profile reviewer-renata): REJECT, two small contract fixes

1. Two GETs declare 422 ActivationInvalid (effective artifacts, effective context); D9 lists none for reads and the status-code rules make a bad query parameter a 400. Remove.
2. ActivationNotFound examples lack mission_type_not_found and action_not_found.
3. Plain $ref dropped per-use meaning (deactivated absent, _links self, referencedBy). Restore on the targets.
4. WP05: CHANGELOG must list Allow now naming DELETE, and the bundle growth.
5. Follow-up: x-refusal says governance_policy_violation and drg_schema_error are raised: false although main code raises them.
6. Nits: closure count comment, a stray line break, narrow the reserved-controller ArchUnit allow-list.
