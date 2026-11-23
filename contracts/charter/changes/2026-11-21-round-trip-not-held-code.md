### Changed

- The whole-pack round trip (`pack-doctor validate --round-trip`, the `round_trip_red` detail) has one problem code for
  a file the store wrote that the census does not hold (#636, round-trip-codes slice; ADR-0082). `EXCLUDED_WRITTEN` is
  retired, outright and with no alias: an excluded file the store wrote is `NOT_HELD_WRITTEN`, like a file no rule
  covers and a mapped file nothing names, and the detail gives the census's reason,
  `NOT_HELD_WRITTEN <path> (<kind>): not held (<reason>); <what was observed>`. The `STALE_BASELINE` and
  `STALE_KNOWN_FAILURE` wordings for such an entry are merged into one, `<path>: not held (<reason>); remove it from
  <list>`, where they said `the file is excluded` or `the file is not held`; an entry over a file a gate exclusion takes
  says `excluded by whole-pack/exclusions.yaml`. `STALE_EXCLUSION` for an exclusion that matches no file ends
  `; remove it from whole-pack/exclusions.yaml`, like the other stale entries over a pack's files. The gate's per-file
  result for an excluded file the store did not write reads `not held (<reason>): not written`, where it read
  `excluded by <reason>: not written` (the whole-pack gate's report only). No violation code, schema or field
  changes. Core's seam: none; core does not read round-trip problem text. A tool that matches `EXCLUDED_WRITTEN` in a
  `round_trip_red` detail matches `NOT_HELD_WRITTEN`.
