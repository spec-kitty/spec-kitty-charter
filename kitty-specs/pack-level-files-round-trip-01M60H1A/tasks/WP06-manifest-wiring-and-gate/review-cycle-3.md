---
affected_files: []
cycle_number: 3
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T09:46:54Z'
reviewer_agent: claude
wp_id: WP06
---

# WP06 reopened (operator ruling on #195, 2026-10-28): check the preset entry's path

The ruling resolves research R14, which was planned and never built: a manifest preset entry's `path` must be
`presets/<id>.yaml`. The schema only checks the pattern, so the check must be added where each side reads the
manifest:

- the whole-pack load: a violation on `pack-manifest.yaml`;
- validate mode: `artifact_schema_error`;
- the gate: refused.

Every refusal gets a clean control.
