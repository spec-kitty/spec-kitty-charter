### Changed

- The composing reads' `pack_unreadable` detail names the first unreadable graph document and its reason, as the edge
  read and a removal already did (#628): `a graph document of <pack> cannot be read: <pack>/<file>: <reason>`, where it
  was `a graph document of <pack> cannot be read`. The edge read names the first by path. pack-doctor's `skipped` reason
  quotes the longer detail. An edge that joins an artifact to itself is refused in the same words, where the detail was
  `the file <file> of <pack> cannot be read: an edge joins '<source>' to itself`. A registration, and pack-doctor, now
  state such an edge written with the pack's own name at its graph document as `drg_schema_error`, as they state its
  bare form; it was `unreadable` at `pack`. The code and the status are unchanged.
