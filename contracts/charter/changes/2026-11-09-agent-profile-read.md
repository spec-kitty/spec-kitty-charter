### Added

- `getPackAgentProfile`, `GET /packs/{packId}/artifacts/agent_profile/{artifactId}` (tag `AgentProfiles`), the pack-scoped
  read of one agent profile (#407, #96 for `agent_profile`). It answers the `Artifact` envelope with `kind: agent_profile`,
  the profile in the new `agentProfile` member and, in the new optional `governance` list, the artifacts the profile's
  governance walk lists over the pack's composed `depends_on` closure. `depth` (0 or 1, default 0) is the number of
  `suggests` hops the walk follows. `AgentProfile` is closed and mirrors `contracts/packs/schemas/agent-profile.schema.json`
  field for field in camelCase; `AgentProfileExcluding` is the closed wire form of `excluding` (`wholeFields` or `fieldValues`,
  the other null). `PackGovernanceEntry` is one governance entry (`kind`, `artifactId`, `urn`, `step`, `relation`, `via`,
  `hops`, `suggestion`, `packId`, `held`, `_links`), `GovernanceStep` its walk step (`lineage`, `requires`, `suggests`,
  `requires_of_suggested`, `vocabulary`). A parent no pack of the closure holds is listed with `held: false`, a null
  `packId` and no link. `held` describes the edge that listed the entry: a URN listed early by a dangling edge keeps
  `held: false` even when a later step reaches it through a held edge, and entries below it name it as `via`.
  `suggests` entries are listed by this service; core's profile channel treats them as inert. The body references
  (`directiveReferences` and the others) are body fields and are not walked. The answer carries `Charter-Source-Fingerprint`,
  the digest the read verified, and an `ETag` that is the pack revision, as a directive's is. A profile the pack does not
  hold is `404 artifact_not_found`. The `Depth` parameter now speaks of any walk, and the `references` relation is added
  to the relation table for the profile's link to its references read.
