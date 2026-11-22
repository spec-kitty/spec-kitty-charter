### Breaking

- A whole-pack registration now holds and judges an anti-pattern file, `anti_patterns/<id>.anti-pattern.yaml`
  (ADR-0052 decision 8, as amended by ADR-0053 decision 30; #154; mission pack-doctor-coverage-01M7YMQK, FR-013), which
  it warned `file_not_held` before and did not hold. The file is closed: `schema_version`, `id` (its file stem),
  `title`, `description`, `severity` (`smell`, `severe` or `breaking`, never defaulted) and `bad_example` are required
  and `good_example` is optional (the packs contract's `schemas/anti-pattern.schema.json`). A pack that registered
  with an invalid such file is now refused `validation_failed`: a schema breach (a missing `severity` or
  `bad_example`, an unknown severity, an unknown key) is the violation `artifact_schema_error` at the file, and a file
  outside `anti_patterns/` or whose `id` is not its stem is `artifact_schema_error` by the placement and stem rule.
  `pack-doctor validate` answers the same. No code is added and no schema of this contract changes, so core's seam
  needs no regeneration (scenarios: `registration/anti-pattern-files.feature`).
