### Changed

- `pack-doctor validate` names a pack by its id everywhere (squad finding B12): a `skipped` reason of a pack whose
  `pack.yaml` cannot be read names the pack id it is checked under (its `=<id>`, or its directory's name), as its
  violations and `packs[].name` do, where it named the directory (`pack.yaml of <dir>`, or `pack <dir> has no pack.yaml`). `PackDoctorReport.yaml` states that
  `packs[].name` is the pack id. Core's seam: none; the CLI and its report are not part of the API core calls, and
  core regenerates nothing.
