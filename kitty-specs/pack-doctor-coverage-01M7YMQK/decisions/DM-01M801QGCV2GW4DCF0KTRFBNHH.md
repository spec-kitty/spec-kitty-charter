# Decision Moment `01M801QGCV2GW4DCF0KTRFBNHH`

- **Mission:** `pack-doctor-coverage-01M7YMQK`
- **Origin flow:** `plan`
- **Slot key:** `round-trip-codes-fold`
- **Input key:** `round_trip_codes_fold`
- **Status:** `resolved`
- **Created:** `2026-11-21T21:55:48.763689+00:00`
- **Resolved:** `2026-11-21T21:55:50.996781+00:00`
- **Resolved by:** `operator via dispatcher`
- **Opened by:** `claude`
- **Other answer:** `false`

## Question

Fold #636's round-trip-codes slice (retire EXCLUDED_WRITTEN, merge the STALE_* wordings in WholePackExpectation) into WP04?

## Options

- Yes, fold into WP04
- No, leave for #636

## Final answer

Yes, fold into WP04

## Rationale

Operator ruling R-t (2026-11-21), source: operator via dispatcher, operator words "2. as recommended". Fold #636's round-trip-codes slice into WP04 so WholePackExpectation changes once: retire EXCLUDED_WRITTEN beside NOT_HELD_WRITTEN (~506) and merge the STALE_* wordings (~241/246/251). It changes an existing code: contract changelog fragment and PR body note it; red-first tests; WP04 gets a fresh review cycle. PR writes Refs #636 for the slice.

## Change log

- `2026-11-21T21:55:48.763689+00:00` - opened
- `2026-11-21T21:55:50.996781+00:00` - resolved (final_answer="Yes, fold into WP04")
