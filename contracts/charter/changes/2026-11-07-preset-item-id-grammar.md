### Changed

- A preset item whose id is outside the id grammar (`^[A-Za-z0-9][A-Za-z0-9_/.-]*$`, the characters a `policy.yaml`
  key allows) refuses the pack as `500 pack_unreadable`, naming the preset file. Spelled as a URN
  (`directive:DIRECTIVE_001`), with another case on the kind (`DIRECTIVE:directive_001`), with whitespace
  (`DIRECTIVE_001` with a trailing space) or pack-qualified (`alpha-pack::DIRECTIVE_001`), an item matched no held id: a must-have item
  put nothing on the floor and `PUT /activations/{id}` of a deny entry on it answered `201`, and a non-must-have
  `::` item made `GET /offering` answer `500 internal_error`. The pack's ids are unchanged; only the preset item
  spelling is refused. No pack of the pinned open-packs refuses.
