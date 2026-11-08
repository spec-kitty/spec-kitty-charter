### Changed

- Wording only, no behaviour change. The `CharterRefusalCode` text names the twelve operations that answer the pack
  composition codes as `422` (it named four); the `getPack` description says that a built-in pack is `404 pack_not_found`;
  the `deletePackRelationship` description states its order of checks (the pack, then `If-Match`, then the pair), which
  differs from `deletePackArtifact`, whose path is judged before `If-Match`.
