### Breaking

- The union floor of rule A1 holds in the writes as it holds in the reads (FR-014, FR-027): the requirement of a
  governing preset id is the union, across the levels (built-in, org, project), of the must-have items of the copy in
  force at each level. Within one level the copy of the pack last in closure order replaces the others, a child pack's
  copy its parent's, and a replaced copy requires nothing. An activation write that denies an artifact only the copy
  of an earlier level requires, which a later level's copy replaced, is refused `409` with `artifact_required`, naming
  the requiring pack, that earlier level's pack included; it answered `200` before, and the next read kept the
  artifact in force. The `floorAdded` and `floorRemoved` notices of a pack registration follow the same rule, so
  replacing a later level's copy no longer reports an earlier level's must-have item as removed. No operation or
  schema changes: the sub-resource writes already declare the `409`.
