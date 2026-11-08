### Changed

- A composition is refused `500 pack_reference_artifact_missing` when a preset item or a `policy.yaml` lock names an
  artifact the closure holds only under another case (`directive_001` where the closure holds `DIRECTIVE_001`). Ids
  are matched exactly, so the mention governed nothing: a must-have item put nothing on the floor, and a deny entry
  that should have been refused answered `201`. The detail names the mentioning pack, the holding pack and both
  spellings. The existing code is used because it is the nearest: a pack's reference to an artifact the closure does
  not hold as written. A mention of an id no pack of the closure holds is still served, and a lock on it changes no
  write. No pack of the pinned open-packs refuses.
