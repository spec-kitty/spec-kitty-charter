# Changelog

All notable changes to the `charter` contract are recorded here, newest first. The module
version is the `info.version` of `openapi.yaml`.

## 0.1.0-SNAPSHOT

Initial entry, not yet released: an unreleased contract version carries the `-SNAPSHOT`
suffix.

### Added

- `GET /packs`: the Charter Packs of the offering in layer order, with their descriptor
  fields, graph sizes and load diagnostics (`PACK_LEVEL_MISSING`).
- `GET /mission-types/{missionType}/actions/{action}/context`: the first slice of
  action-scoped context (scope edges of the action node, then of the mission type node,
  without activation filtering).
- `CharterRefusal` and `CharterRefusalCode`, a `Problem` with a charter refusal code.
- BDD scenarios in `scenarios/`, the behavior half of the contract.
