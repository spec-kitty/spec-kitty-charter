### Added

- `getEffectiveAgentProfile`, `GET /activations/{activationId}/artifacts/agent_profile/{artifactId}` (tag
  `EffectiveAgentProfiles`), the read of one agent profile under an activation (#407, #96 for `agent_profile`), the literal
  path the generic `getEffectiveArtifact` leaves for the kind. It answers `EffectiveAgentProfileRead` (closed): the members
  of `EffectiveArtifactRead` (`activationId`, `revision`, `governanceFingerprint`, `kind`, `artifactId`, the `packId` and
  `level` that hold the version in force, its `state`, `required`, `policy` and `reason`, with `satisfiedBy` and
  `suggestion` where the effective list has them), the winner's profile in `agentProfile` (the same closed `AgentProfile` as
  the pack-scoped read) and `governance`, a list of `EffectiveGovernanceEntry` sorted by `urn`. An entry has the facts of an
  effective artifact (`kind`, `artifactId`, the winner's `packId` and `level`, `state`, `required`, `policy`, `reason`,
  `satisfiedBy`, and a `suggestion` inline, as `EffectiveArtifact` carries it) and the walk's `step`, `relation`, `via` and
  `hops`. It is not `PackGovernanceEntry`: it has no `held` and no `urn`, its `packId` is the winner's pack and never null,
  and its `reason` is the effective reason, not a word of the walk. The walk is judged by the activation: an artifact in
  force is listed with the step that reached it, a suggestion out of force is listed with `state: inactive`,
  `reason: not_selected` and its suggestion, and an ancestor (`specializes_from`) that is not in force is dropped and seeds
  nothing, which diverges from the cascade of the activation writes, which passes an ancestor through (operator ruling W1).
  The profile the walk starts from is exempt: it is answered whatever its state, and the state is on the read. The stored
  winner is answered, and `enhances` is not resolved, so this is not the effective profile of #380. `_links` holds `self`
  (with `depth` when the request sent it), `artifact` (the pack-scoped read of the winner's pack) and `up` (the activation);
  an entry's `_links.artifact` is the pack-scoped read of its winner, for the kinds the generic artifact route serves. The
  refusals are judged in order: `activation_not_found`, then `artifact_not_found` for an id that no pack of the closure
  holds as a profile, before any `If-None-Match`. The answer carries `Charter-Source-Fingerprint` and a strong `ETag` over
  the activation's revision, the packs of its closure and the profile and the `depth`; a matching `If-None-Match` is `304`,
  and `depth` outside 0 to 1 is a `400`. The new tag `EffectiveAgentProfiles` lets the profiles slice implement the
  operation in a controller of its own; no relation is added.
