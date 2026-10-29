---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T08:27:34Z'
reviewer_agent: claude
wp_id: WP04
---

# WP04 review 1 (reviewer-renata, independent delegate): changes requested

1. **[MEDIUM] `DomainRoundTrip.preset()` has no adapter-level test.** The built-in gate refuses both presets, so these
   paths are never exercised:
   - the `Written` branch;
   - the read-back branches;
   - `holdsPreset`.

   The gate's joined refusal also has no clean control. Add a `DomainRoundTripTest` case: one valid preset →
   `Written("presets/p.yaml")`, and beside it one broken preset → `Refused` with the exact joined text. Optionally,
   add a lossy-writer case.

LOW findings, which the author takes:

- Lines over 120 characters: `WholePackStore` javadoc (:36, :49) and `DomainRepositoryService:282`.
- The stale test name `...ExactlyThePinnedSix` in `WholePackOpenPacksRoundTripTest`.
- `PresetRoundTripTest` checks the refusal with `anySatisfy(contains)`; prefer the exact text.

INFO findings, recorded for WP07:

- Validate mode and the load can disagree on a domain-only break, such as a duplicate item id.
- The `PRESETS` constant lives in `PresetFiles`, not in `PackFileSet` as data-model.md says.
