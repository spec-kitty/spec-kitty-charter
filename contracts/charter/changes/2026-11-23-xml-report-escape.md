### Changed

- `pack-doctor validate --report <file> --format xml` escapes a string holding a character XML 1.0 cannot carry (a
  control character other than tab, line feed and carriage return, `U+FFFE`, `U+FFFF`, or a lone surrogate), as
  `PackDoctorReport.yaml`'s mapping states (ruling R-ac; ADR-0083 decision 10): the element is `type="escaped"`, each
  such character is `\uXXXX` (the upper-case hex of its UTF-16 code unit) and each backslash `\\`. A string with no
  such character is written as before. A pack whose file name or content holds such a character no longer makes the
  run exit 2. The JSON Schema is unchanged. Core's seam: none; the CLI and its report are not part of the API core
  calls, and core regenerates nothing.
