---
affected_files: []
cycle_number: 1
mission_slug: whole-pack-round-trip-gate-01M5Y272
reproduction_command:
reviewed_at: '2026-10-27T08:03:46Z'
reviewer_agent: user
wp_id: WP04
---

# WP04 review (reviewer-renata): REJECT
- MEDIUM expected-red.yaml: 16 plain-scalar reasons contain " #187", which YAML reads as a comment; the reasons are silently cut short. Quote or fold them.
- MEDIUM the ratchet holds only the first difference; a later loss in a baselined file hides behind it. Record it or pin all differences.
- LOW directive.graph.yaml is a pack inconsistency; name the upstream fix.
- LOW the lossy proxy does not unwrap InvocationTargetException.
- LOW the clean-tree check runs only before the gate (NFR-004).
