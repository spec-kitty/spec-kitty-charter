### Changed

- `pack-doctor validate --full` adds the check `RELATIONS` (#632 item 3, FR-012 of mission
  adr-0082-graph-walk-slices): per root of the set, over the closure the root composes, the relation rules
  registration runs (`PackRulesets.relationFaults`, the factory's relations phase), each with the code the registration
  answers (`self_override`, `conflicting_relations`, `override_not_permitted`, `override_cycle`, `enhances_cycle` and
  `specialization_cycle`), all 422 at registration. A fault several roots find is reported once. `RELATIONS` runs
  whenever `REFERENCES` runs, after it, over each root whose closure `REFERENCES` passed, as registration judges a
  closure's relations only once its references pass; a composition fault stops both. The edge-index rules, which judge
  the layer merge, are not run. `PackDoctorReport` gains `RELATIONS` in `checks` (now at most 5 items) and in
  `skipped[].check`. Without `--full` the report is unchanged. Core's seam: none; the CLI and its report are not part
  of the API core calls, and core regenerates nothing.
