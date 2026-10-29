---
affected_files: []
cycle_number: 1
mission_slug: pack-level-files-round-trip-01M60H1A
reproduction_command:
reviewed_at: '2026-10-28T08:51:29Z'
reviewer_agent: claude
wp_id: WP06
---

# WP06 review 1 (reviewer-renata, independent delegate): changes requested

1. **[MEDIUM] `WholePackRoundTripTest.withStale` has no positive control.** The red path ("source stale" /
   "written stale") never runs. Add `observe`-level tests over manifest-pack:
   - a stale-hash writer, red at "written stale: …";
   - an edited source, red at "source stale: …";
   - a clean control.

LOW findings, which the author takes:

- a direct test of `DomainRoundTrip.pairing` with underscore tokens (`agent_profile`, `glossary_pack`,
  `mission_step_contract`);
- lines over 120 columns (PackFileSet:34, WholePackOpenPacksRoundTripTest:46, and others);
- a broken comment in WholePackExpectationTest;
- out-of-map edits, recorded in the WP note.

INFO findings, recorded:

- NFR-004 timing: the internal pack's whole-pack load took 43 ms before and 45 ms after. It holds no manifest, and the
  built-in pack was not timed separately.
- `sanitizedManifest` ignores `missing()`; `stale` would surface it.
- A self-listed manifest is refused as "does not hold".

Out-of-map edits, recorded:

- manifest overrides in the strategy test doubles (CompanionFilesTest, DomainRoundTripTest, DomainRepositoryServiceTest,
  PresetRoundTripTest);
- `FilesystemPackStore.violationsOf` made package-private for the seam's manifest violations;
- the `PackPresetsTest` constructor reconciliation after merging WP03.
