### Changed

- The detail of a `pack_unreadable` for a `pack.yaml` that cannot be read says why: its parse failure, or each schema
  violation, an undeclared key named (issue #431). It was `pack.yaml of <pack> cannot be read` alone, so a descriptor
  that still declares `active_charter` was refused without naming the key. A registration's `pack_unreadable`
  violation at `pack` carries the same detail. The codes and statuses do not change.
