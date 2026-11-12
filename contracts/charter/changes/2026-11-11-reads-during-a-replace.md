### Changed

- A read that meets a pack in the middle of a replace answers the pack's last committed state (issue #490). The
  read takes no lock and does not wait for the replace. Before, it answered `404 pack_not_found` or
  `500 pack_unreadable`, or the pack listing waited up to 10 s for the replace. A read that the pack's directory
  moved under is run again. A read whose pack directory moves under it eight times in a row (one replace moves it
  twice, so about four replaces) answers `500 stale_input`, which the caller may retry. A pack directory that is
  empty, the reservation of a create in flight, is no pack: it is `404 pack_not_found` and is not listed. A replace
  whose second step fails puts the pack back as it was; a read that ran in between is run again over the restored
  pack, so it answers the last committed state, never what it saw while the pack was moved aside. A verified read whose load refuses while any pack is in the middle of a
  replace still answers `500 stale_input`, which the caller may retry, rather than the load's own refusal; whether it
  should is an open call for the operator, recorded in ADR-0073 (Risks).
