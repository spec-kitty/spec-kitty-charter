### Breaking

- A per-file write or removal is judged on the whole pack it leaves (#605, operator ruling of 2026-11-18). The `PUT`,
  `POST` and `DELETE` routes under `/packs/{packId}/artifacts` and `/packs/{packId}/relationships` judged a write only
  on the rules each route states. For example, a `DELETE` of a step enhancement's one registered `enhances` edge
  answered `204` and left a pack that every read then refused. Now:
  - The commit store reads the copy it would swap in with the registration's full read. A write that would add a
    violation the stored pack does not already hold is refused `422 validation_failed`, one violation per new fault at
    its file, in the registration's words, and nothing is written. Each of these routes already declares `422
    validation_failed` for a write that breaks a pack invariant or schema, or whose resulting pack does not revalidate.
  - A fault the stored pack already held does not block a write: a fault of the same code at the same file is held,
    whatever its words, one for one, so only a fault the write adds is refused.
  - `deletePackRelationship`'s description no longer says that removing a step enhancement's only registered
    `enhances` edge answers `500 pack_unreadable`: it is the `422` above, at the enhancement's file. A violation's
    `pointer` (`RefusalViolation`, `Problem.violations`) is a pack file on this refusal, as on a registration.
  - No refusal code is added.
- Core's seam: a client that wrote or removed an artifact or relationship that left the pack invalid, and read `200`,
  `201` or `204`, now reads the `422`; fix the pack, or register it whole.
