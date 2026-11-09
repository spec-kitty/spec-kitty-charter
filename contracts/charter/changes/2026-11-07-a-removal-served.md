### Changed

- `deletePackArtifact` (`DELETE /packs/{packId}/artifacts/{kind}/{artifactId}`) and `deletePackRelationship`
  (`DELETE /packs/{packId}/relationships`) are built (ADR-0065 D4; FR-005, FR-006, FR-007, FR-036; PR A of the layer merge
  mission): they lose `x-reserved` and their `501`, and the two `501` pins of
  `scenarios/registration/reserved-registration.feature` go with them. With `POST /packs` built as well
  (`2026-11-07-a-pack-source-served.md`), no operation of the `pack-registration` set is reserved, and
  `reserved-registration.feature` is deleted. Both are
  synchronous, run under the registry lock and then the pack's lock, and answer `204` with the pack's new revision as the
  `ETag`; `If-Match` is required (`428` without it, `412` with a stale one, judged under the locks). The artifact
  `DELETE` removes the artifact, its companion files, its outgoing edges from `registered.graph.yaml` and the preset
  items of the pack that name it, and deletes a preset key left empty (every preset list needs an item); it answers `404`
  by code (`pack_not_found`, `kind_not_found`, or the not-found code of the kind), `409 artifact_referenced` while another
  artifact or pack refers to it (judged against the tree after the removal; another pack's preset item and descriptor
  `overrides` entry count, the data-only fields of ADR-0064 D11 do not), `409 governance_policy_violation` (the
  `REMOVE` operation, only when an ancestor of the pack holds and locks the id), and `409 registration_conflict` when a
  source shard or `drg/fragment.yaml` the author wrote declares the artifact's node or an edge from it, since a `DELETE`
  never rewrites an author file. The relationship `DELETE` removes the edge of the unordered pair only from
  `registered.graph.yaml`: `404 relationship_not_found` when no graph document and no derived relation joins the pair,
  `409 relationship_not_registered` when the pair is held outside the registered shard, or in both. Both are strict
  (decision DM-01M6TQ67C9ZTNGZB9CY28JS54F): a removal after which a closure the pack joins no longer composes, or breaks a
  relation rule, is `422` with the composition or relation code, naming the packs, and writes nothing.
- `CharterRefusalCode`: `relationship_not_found` and `relationship_not_registered` are `raised: true`; the `x-refusal`
  text no longer says they stay `raised: false`, and `scenarios/errors.feature` lists them among the codes an operation
  raises.
