### Changed

- `RequiringPack.via` is populated on the `409 artifact_required` and `409 artifact_locked` refusals of an
  activation write: the `parent_pack` chain from the pack the activation names up to the requiring pack, that
  pack excluded. It stays `[]` when the named pack declares the item itself, and for a pack reached over
  `depends_on` only. An activation write also evaluates its preconditions first: a stale `If-Match`, or
  `If-None-Match: *` on a stored id, is a `412` whatever the document holds (RFC 9110 §13.2.2).
