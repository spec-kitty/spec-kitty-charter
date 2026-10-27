---
affected_files: []
cycle_number: 1
mission_slug: whole-pack-round-trip-gate-01M5Y272
reproduction_command:
reviewed_at: '2026-10-27T07:34:48Z'
reviewer_agent: user
wp_id: WP03
---

# WP03 review (reviewer-renata): REJECT
- HIGH a `kinds:` entry over a MODELLED kind turns every red file of it expected-red: fake green. Add BASELINED_MODELLED_KIND; fix the test that baselines `tactic` by kind.
- MEDIUM EMPTY_MODELLED_KIND trusts the adapter's modelledKinds; WP04 must assert the seven required kinds.
- MEDIUM a baselined file stays expected red whatever its difference becomes; record the expected difference path and fail on drift.
- LOW a files entry whose path is excluded is never flagged; symlinked files are silently skipped by the census.
- NIT reasons accept non-scalars; report cells do not escape backslashes.
