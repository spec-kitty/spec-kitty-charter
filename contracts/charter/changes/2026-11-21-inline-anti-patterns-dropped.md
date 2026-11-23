### Breaking

- A styleguide and a procedure no longer carry anti-patterns (ADR-0052 decision 16; ruling R-r; #154; mission
  pack-doctor-coverage-01M7YMQK, FR-015): an anti-pattern is its own file, `anti_patterns/<id>.anti-pattern.yaml`,
  which guidance rejects by a `rejects` edge in the pack's graph. `antiPatterns` leaves `Styleguide` (where it was
  required), `StyleguideRegistration` and `Procedure` (where it was required), and the schemas `StyleguideAntiPattern`
  and `ProcedureAntiPattern` are deleted. The packs contract's `styleguide.schema.json` and `procedure.schema.json`
  drop `anti_patterns`, and both stay closed: a pack file that still carries the key refuses the pack
  `validation_failed` with the violation `artifact_schema_error` at the file, detail `unknown key 'anti_patterns'`,
  and `pack-doctor validate` answers the same; a styleguide registration that still sends `antiPatterns` is refused
  `422 validation_failed` by its closed request schema. No code is added. Core regenerates its client, which drops the
  two types and the `antiPatterns` members (ADR-0022) (scenarios: `registration/inline-anti-patterns.feature`,
  `procedures.feature`, `styleguides.feature`, `artifacts.feature`).
