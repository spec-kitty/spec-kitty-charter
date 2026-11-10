### Added

- `getEffectiveAgentProfile`, `GET /activations/{activationId}/artifacts/agent_profile/{artifactId}` (tag
  `EffectiveAgentProfiles`), the read of one agent profile under an activation (#407, #96 for `agent_profile`), the literal
  path the generic `getEffectiveArtifact` leaves for the kind. It answers `EffectiveAgentProfileRead` (closed): the members
  of `EffectiveArtifactRead` (`activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, the `packId` and
  `level` that hold the version in force, its `state`, `required`, `policy` and `reason`, with `satisfiedBy` and
  `suggestion` where the effective list has them), the winner's profile in `agentProfile` (the same closed `AgentProfile` as
  the pack-scoped read) and `governance`, a list of `EffectiveGovernanceEntry` sorted by `urn`. An entry has the facts of an
  effective artifact (`kind`, `artifactId`, the winner's `packId` and `level`, `state`, `required`, `policy`, `reason`,
  `satisfiedBy`) and the walk's `step`, `relation`, `via`, `hops` and `edge`. It is not `PackGovernanceEntry`: it has no `held` and no `urn`, its `packId` is the winner's pack and never null,
  and its `reason` is the effective reason, not a word of the walk. The walk is judged by the activation: an artifact in
  force is listed with the step that reached it, a suggestion out of force is listed with `state: inactive`,
  `reason: not_selected`, and an ancestor (`specializes_from`) that is not in force is dropped and seeds
  nothing, which diverges from the cascade of the activation writes, which passes an ancestor through (operator ruling W1).
  The profile the walk starts from is exempt: it is answered whatever its state, and the state is on the read. The stored
  winner is answered, and `enhances` is not resolved, so this is not the effective profile of #380. `_links` holds `self`
  (with `depth` when the request sent it), `artifact` (the pack-scoped read of the winner's pack) and `up` (the activation);
  an entry's `_links.artifact` is the pack-scoped read of its winner, for the kinds the generic artifact route serves. The
  refusals are judged in order: `activation_not_found`, then `artifact_not_found` for an id that no pack of the closure
  holds as a profile, before any `If-None-Match`. The answer carries `Charter-Source-Fingerprint` and a strong `ETag` over
  the activation's revision, the packs of its closure and the profile and the `depth`; a matching `If-None-Match` is `304`,
  and `depth` outside 0 to 1 is a `400`. No relation is added.
- Every `EffectiveGovernanceEntry` carries `edge`, the `GovernanceEdgeWords` of the pack-scoped read (the schema is
  introduced with `getPackAgentProfile`): the words of the edge the walk reached the entry through (FR-004), whatever its
  relation, and whether the artifact is in force (`state: active`) or out of force (`inactive`, `not_selected`). It is
  always present and empty where the edge states neither word, so a wordless entry looks the same in force and out of
  force. `edge` is a schema of its own because `EffectiveArtifactSuggestion` is defined only for an artifact out of force
  that a `suggests` edge reaches (#408 ruling 3) and keeps that meaning on every generic effective read.
- The tag `EffectiveAgentProfiles` is separate from `AgentProfiles`, which the pack-scoped profile operation uses, so that
  the profiles slice implements this operation in a controller of its own (`useTags=true` generates one API interface per
  tag). The operation id, the path and the body are unaffected.
