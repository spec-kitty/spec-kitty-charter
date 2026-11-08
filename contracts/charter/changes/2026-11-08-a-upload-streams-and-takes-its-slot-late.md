### Changed

- `registerPackFromArchive` (`PUT /packs/{packId}`) reads its body as a stream and takes its registration slot when the body
  is spooled, not before it is read (issue #418, A3, A4): a slow upload no longer holds one of the two running and eight
  waiting places while it arrives. A full queue (`503` with `Retry-After`) and a stale `If-Match` are still answered before
  the body is read, and are judged again when it is spooled. The `90` second deadline of a registration starts then. No
  status or code is added.
- `registerPackFromArchive` can answer `503 pack_storage_full` synchronously, before the body is read, when the staging or
  the pack volume lacks the room for the declared length (issue #418, A6). The `503` was declared; the code was a refused
  status's. `pack_storage_full` now means less than the per-job peak is free, about three times the 32 MiB archive bound
  (the archive, the tree it expands to and the copy the commit makes), where it was twice the bound on the pack volume only.
