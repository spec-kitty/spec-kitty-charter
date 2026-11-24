### Breaking

- A pack whose graph document (a `*.graph.yaml` shard or `drg/fragment.yaml`) declares an `anti_pattern` node that the
  same pack does not hold as a file, `anti_patterns/<id>.anti-pattern.yaml`, is now refused (ADR-0052 decision 14,
  ADR-0053 decision 31; #154; mission pack-doctor-coverage-01M7YMQK, FR-014). Such a pack registered before. The refusal
  is `validation_failed` with one violation `drg_schema_error` per such node, at the node, `<document>#/nodes/<n>`, with
  the detail `<document>: anti_pattern:<id> is declared with no file; an anti-pattern is held by
  anti_patterns/<id>.anti-pattern.yaml in the same pack`; `pack-doctor validate` answers the same code, pointer and
  detail, as registration does. A `rejects` edge to another pack's anti-pattern (`<pack>::anti_pattern:<id>`) is no node
  of the rejecting pack and is judged as before: the target's pack must be in the rejecting pack's `depends_on` closure
  (`pack_reference_pack_missing`). No code is added and no schema of this contract changes, so core regenerates nothing
  (scenarios: `registration/anti-pattern-nodes.feature`).
