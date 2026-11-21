---
affected_files: []
cycle_number: 2
mission_slug: pack-doctor-coverage-01M7YMQK
reproduction_command:
reviewed_at: '2026-11-21T21:56:00Z'
reviewer_agent: claude
wp_id: WP04
---

# WP04 cycle 2: fold #636's round-trip-codes slice (operator ruling R-t)

WP04 was approved in cycle 1 (manifest_stale, #630). It is reopened, not rejected: operator ruling R-t
(DM in decisions/, source "operator via dispatcher", operator words "2. as recommended") folds #636's
round-trip-codes slice into WP04, so `WholePackExpectation` changes once. Keep everything cycle 1 delivered.

## Do
1. **Retire `EXCLUDED_WRITTEN`** beside `NOT_HELD_WRITTEN` in
   `service/pack-validation/src/main/java/ai/speckitty/charter/packvalidation/roundtrip/WholePackExpectation.java`
   (around line 506): one code for one cause, canonical model, no alias left behind.
2. **Merge the `STALE_*` wordings** (`STALE_BASELINE` around 241/283/567-573, `STALE_KNOWN_FAILURE` around 246/587-593,
   `STALE_EXCLUSION` around 251/625) into one wording/shape, as #636's slice describes (read issue #636's round-trip-codes
   slice and ADR-0082 for the intended shape before choosing).
3. **Tests red first**: update the pins (`WholePackExpectationTest` around 1035/1046) to the merged form, see them fail,
   then change the code. Assert exact codes by equality.
4. **It changes an existing code**: add a contract change fragment (or extend
   `contracts/charter/changes/2026-11-21-manifest-stale.md` if it is the same family; otherwise a new dated fragment) naming
   the retired and merged codes and core's impact; grep contracts/, docs/ and the deployable/pack-doctor tests for the old
   names and follow the model everywhere (no exclusions).
5. Record in the WP activity log: "Refs #636 (round-trip-codes slice)".

## Gates
`:pack-validation:test`, `:pack-validation:wholePackRoundTrip`, `:pack-doctor:check`, `:contracts:test`, `:deployable:test`,
`spotlessCheck`. No pitest.
