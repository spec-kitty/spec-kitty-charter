### Changed

- The composing reads' `pack_unreadable` detail names the first unreadable graph document and its reason, as the edge
  read and a removal already did (#628): `a graph document of <pack> cannot be read: <pack>/<file>: <reason>`, where it
  was `a graph document of <pack> cannot be read`. The edge read names the first by path. pack-check's `skipped` reason
  quotes the longer detail. The code and the status are unchanged.
