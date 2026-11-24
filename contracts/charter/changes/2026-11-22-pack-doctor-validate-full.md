### Changed

- `pack-doctor validate --full` adds the check `REFERENCES` (#632 item 3, ruling R-s; its name is ruling R-u): per root
  of the set, over the closure the root composes and every pack of it, as a registration of that root judges them
  (ruling R-ae), the substep delegations, the references and the step enhancements' targets, in the registration's order
  and through registration's one body of them (`PackRulesets.references`, ruling R-ab), each with the code the
  registration answers (`validation_failed` with `substep_delegation_unresolved`, `pack_reference_unqualified`,
  `pack_reference_pack_missing`, `pack_reference_artifact_missing`). A pack a reference, a delegation candidate or an
  enhancement target names must be in the pack's `depends_on` closure, its `parent_pack` not counting, as at
  registration. `REFERENCES` runs when `CLOSURE` found no composition fault (ruling R-aa). A fault several roots find is
  reported once; a qualified reference into a pack outside its writer's `depends_on` but inside a root's closure is one
  problem per root. `PackDoctorReport` gains `REFERENCES` in `checks` (now at most 4 items) and in `skipped[].check`.
  Without `--full` the report is unchanged. Core's seam: none; the CLI and its report are not part of the API core
  calls, and core regenerates nothing.
