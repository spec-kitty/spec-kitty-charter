### Changed

- `pack-doctor validate --full` adds the check `REFERENCES` (#632 item 3, ruling R-s; its name is ruling R-u): per
  pack, within its own closure, the substep delegations, the references and the step enhancements' targets, in the
  registration's order, each with the code the registration answers (`validation_failed` with
  `substep_delegation_unresolved`, `pack_reference_unqualified`, `pack_reference_pack_missing`,
  `pack_reference_artifact_missing`). `PackDoctorReport` gains `REFERENCES` in `checks` (now at most 4 items) and in
  `skipped[].check`. Without `--full` the report is unchanged. Core's seam: none; the CLI and its report are not part
  of the API core calls, and core regenerates nothing.
