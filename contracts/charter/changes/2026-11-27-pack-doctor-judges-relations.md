### Changed

- `pack-doctor validate --full` adds the check `RELATIONS` (#632 item 3, FR-012 of mission
  adr-0082-graph-walk-slices). For report consumers: `checks` and `skipped[].check` may hold the new value
  `RELATIONS`, `checks` holds up to 5 items, and a set that passed `--full` before may now exit 1; without `--full`
  the report is unchanged.
  - **What it runs:** per root of the set, over the closure the root composes, the relation rules registration runs
    (`PackRulesets.relationFaults`, the factory's relations phase), each with the code the registration answers
    (`self_override`, `conflicting_relations`, `override_not_permitted`, `override_cycle`, `enhances_cycle` and
    `specialization_cycle`), all 422 at registration. A fault several roots find is reported once. The edge-index
    rules, which judge the layer merge, are not run.
  - **Gating:** `RELATIONS` runs after `REFERENCES`, over each root whose closure `REFERENCES` passed, as
    registration's phases 2 to 4 gate its relations phase; a composition fault stops both. A preset contest, a
    preset-item fault or a case-only answer does not stop it: those stand beside `RELATIONS`' faults, as beside
    `REFERENCES`'.
  - **Report shape:** `PackDoctorReport` gains `RELATIONS` in `checks` (now at most 5 items), listed only when at
    least one root was judged, and in `skipped[].check`.
  - **Core's seam:** none. The CLI and its report are not part of the API core calls, and core regenerates nothing.
