# Contract delta: ETag on every pack read of one thing

| Operation | Path | Before | After |
|---|---|---|---|
| getPackArtifact (tactic) | `/packs/{packId}/artifacts/tactic/{artifactId}` | no ETag | `ETag`: the pack revision |
| getPackArtifact (mission_step_contract) | `/packs/{packId}/artifacts/mission_step_contract/{artifactId}` | no ETag | `ETag`: the pack revision |
| getPackMissionType | `/packs/{packId}/artifacts/mission_type/{missionTypeId}` | no ETag | `ETag`: the pack revision, a concurrency token, not a cache validator for content from other packs |
| getPackMissionStep | `.../mission_type/{missionTypeId}/steps/{stepId}` | no ETag | as getPackMissionType |
| getPackMissionSkeleton | `.../mission_type/{missionTypeId}/skeletons/{skeletonName}` | no ETag | as getPackMissionType |

Unchanged: no pack route takes `If-None-Match` or answers 304; activation routes keep their own tags; refusals carry no
ETag. The header stays `required: false` where shared with refusals. Change note: `### Added`.
