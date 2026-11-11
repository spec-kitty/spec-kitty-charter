### Breaking

- A sidecar whose asset `path` leaves `assets/` is refused as an escape (#382; ADR-0040 decision 6, amended
  2026-11-11). The path is judged as written: absolute in POSIX or Windows form, or with a `..` part. The detail reads
  `asset path '<path>' escapes the pack's assets/ root`, whether or not a file is at the target, where it read
  `payload assets/<path> does not exist`. A missing payload under `assets/` still reads `does not exist`. The codes do
  not change: a composing read answers `500 pack_unreadable` (`PR-CHARTER-SHARING-004` pins the new detail).
- A refused registration lists every sidecar the store refuses (an escape, a missing payload, a payload another
  sidecar named) once, at its file; it no longer lists it a second time as `unmodelled`, so the violation count
  drops by one per such sidecar (example `PackRegistrationStatus.asset-path-escape`). Core's seam: nothing; a client
  that matched the old detail of an escaping path, or counted the duplicate violation, reads the new answer.
