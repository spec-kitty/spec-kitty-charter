### Changed

- `CharterRefusalCode`: `artifact_referenced` is `raised: true` (ADR-0065 D4, FR-036). The whole-pack registration
  service refuses a replace that removes an artifact which an edge, a preset item or a descriptor `overrides` entry of
  another pack still names, `409`, before any compose. The refusal's `violations` name every referrer, by its pack and
  artifact, in the pointer; its `packs` member is not filled yet, since `CharterRefusal` carries the packs or the
  violations, not both. `registerPackFromArchive` and `registerPackFromSource` stay reserved (`501`) until their
  controller and job are built, so no request of this version provokes it yet, and the `DELETE`s that also raise it
  are not built.
  The scenario `errors.feature` lists it among the codes an operation raises.
