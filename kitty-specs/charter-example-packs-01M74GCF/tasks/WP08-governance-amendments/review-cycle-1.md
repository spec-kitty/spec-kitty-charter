---
affected_files: []
cycle_number: 1
mission_slug: charter-example-packs-01M74GCF
reproduction_command:
reviewed_at: '2026-11-11T05:56:34Z'
reviewer_agent: reviewer-renata
wp_id: WP08
---

Generated copies do not match the authored source.
- Source `.kittify/charter-packs/directive/002-risk-appetite-directive.directive.yaml` reads: "...round-trips without loss of meaning (ADR-0036: meaning, not bytes...), proven on charter's own example packs; spec-kitty-open-packs proves its content against a pinned charter release (ADR-0070), and no unverified dependency..."
- Generated `.kittify/charter/generated/directives/002-risk-appetite-directive.directive.yaml:17` and `.kittify/charter/charter.yaml:145` read: "...proven on charter's own example packs (spec-kitty-open-packs proves ... ADR-0070), (ADR-0036: meaning, not bytes, ...), and no unverified...". The clauses are in a different order, and the text has a garbled "), (ADR-0036" sequence.
Fix: re-run `spec-kitty charter generate` from the authored sources (or correct the hand edit) so the generated copies and charter.yaml match the source wording.
OK: no stale "open-packs round trip" wording; ADR-0070 links open-packs#188.
