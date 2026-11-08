### Breaking

- The union floor of rule A1 holds in the writes as it holds in the reads (FR-014, FR-027): the requirement of a
  governing preset id is every copy's must-have items, across levels. An activation write that denies an artifact only
  an earlier level's copy of a governing preset requires, a copy a later level replaced, is refused `409` with
  `artifact_required`, naming the requiring pack, the earlier copy's pack included; it answered `200` before, and the
  next read kept the artifact in force. The `floorAdded` and `floorRemoved` notices of a pack registration follow the
  same union, so replacing a later copy no longer reports an earlier copy's must-have item as removed. No operation or
  schema changes: the sub-resource writes already declare the `409`.
