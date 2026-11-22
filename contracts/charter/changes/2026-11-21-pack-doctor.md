### Breaking

- The standalone pack checker is renamed from pack-check to pack-doctor, outright and with no alias (FR-007 of mission
  pack-doctor-coverage-01M7YMQK; ADR-0075 decision 9). The schema `PackCheckReport` is now `PackDoctorReport`
  (`schemas/PackDoctorReport.yaml`), with the same shape. The release asset `pack-check-<version>.jar` is now
  `pack-doctor-<version>.jar`, and the command line takes a command word: `pack-doctor validate` with the options
  pack-check took, and `pack-doctor remediate`, not available yet (exit 2). A command line with no command word is a
  usage fault (exit 2). Core's seam: none; the CLI and its report are not part of the API core calls, and core
  regenerates nothing.
