---
affected_files: []
cycle_number: 1
mission_slug: adr-0066-step-enhancement-service-alignment-01M6ZRYK
reproduction_command:
reviewed_at: '2026-11-09T21:11:06Z'
reviewer_agent: claude
wp_id: WP06
---

# WP06 review cycle 1 — REJECT (reviewer-renata, opus)

Blocking:
- B1 Fragment content faults at load: a blank body, frontmatter, CR or BOM is refused at the enhancement's *.step-enhancement.yaml at load (store companion read, remapped in DomainRepositoryService.judged), the reason naming the fragment; only a slot marker (FRAGMENT_HAS_MARKER) points at the fragment at L. Fix composition-and-activation-rules.md F1 (:1264-1268), pack-schemas/step-enhancement.md:52 (split the row), pack-schemas/prompt-fragment.md:32.
- B2 F6 order (composition-and-activation-rules.md:1305-1307): companion faults (F1 blank/frontmatter/CR/BOM, F2 missing/shared) stay among the store's read violations, first; rule faults (marker, repeated slot, edges, directory, repeated id, no entries) are appended last by path then rule; slot faults (F5) before them.
- B3 ADR-0066 D6 annotation (:225): only an absent prompt_fragments key is prompt_fragment_invalid (NO_FRAGMENTS); prompt_fragments: [] is a schema violation (minItems).

Non-blocking (fold): N1 F7 "Moment: C" not in the legend; N2 "The sub-headings for those follow this one" (none exist); N3 "built under #434" → "will be built under #434"; N4 "three load checks of decision 8 are built" → "three load codes of decision 8 are raised"; N5 changelog "five pinned built-in prompts give five" → at the pin the built-in pack loads with five warnings, one per software-dev step prompt; N6 drop the "Id caps" bullet (another mission's fragment).
