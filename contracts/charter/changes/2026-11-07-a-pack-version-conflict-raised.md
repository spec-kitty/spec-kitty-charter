### Changed

- `CharterRefusalCode`: `pack_version_conflict` is `raised: true` (ADR-0065 D2, FR-002). The whole-pack registration
  service refuses a replace that carries the stored `pack_version` with another revision with it, `409`, and the
  registration job will answer it as the `problem` of a `refused` status. `registerPackFromArchive` and
  `registerPackFromSource` stay reserved (`501`) until their controller and job are built, so no request of this
  version provokes it yet.
  The scenario `errors.feature` lists it among the codes an operation raises.
