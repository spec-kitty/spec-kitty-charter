### Changed

- `CharterRefusalCode`: `artifact_referenced` is `raised: true` (ADR-0065 D4, FR-036). The whole-pack registration
  service refuses a replace that removes an artifact which an edge, a preset item or a descriptor `overrides` entry of
  another pack still names, `409`, before any compose, as the `problem` of a `refused` registration status. Its `packs`
  member lists the referring packs (at most 100) and its `violations` name every referrer, by its pack and artifact, in
  the pointer. The artifact `DELETE` raises it too (`2026-11-07-a-removal-served.md`).
  The scenario `errors.feature` lists it among the codes an operation raises.
