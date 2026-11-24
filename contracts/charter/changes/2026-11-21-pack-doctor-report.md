### Breaking

- `PackDoctorReport` is the one report of `pack-doctor validate`, on standard output and in the new report file (FR-008
  of mission pack-doctor-coverage-01M7YMQK; rulings R-i and R-n). It gains three required keys: `tool` (`name`,
  `version`), `inputs` (the `config_root` or the `packs` as written, and the `options` `full` and `round_trip`) and
  `summary` (`packs`, `violations`, `warnings`, `notices`, `exit_code`); the keys it had are unchanged, and standard
  output carries the new ones too. `validate` takes `--report <file>` and `--format yaml|xml` (YAML by default) and
  writes the same tree to the file; the XML is a mechanical mapping of it, its own shape and not JUnit's, which the
  schema's description states, and the schema stays the only normative one (no XSD). `--format` without `--report`, an
  unknown format, or a report file that cannot be written exits 2. It is breaking for a reader of the report: the
  schema is closed (`additionalProperties: false`), so a reader that validates standard output against the old shape
  refuses the three keys until it takes the new schema. Core's seam: none; the CLI and its report are not part of the
  API core calls, and core regenerates nothing.
