### Added

- `getPackAgentProfile`, `GET /packs/{packId}/artifacts/agent_profile/{artifactId}` (tag `AgentProfiles`), the pack-scoped
  read of one agent profile (#407, #96 for `agent_profile`). It answers the `Artifact` envelope with `kind: agent_profile`,
  the profile in the new `agentProfile` member and, in the new optional `governance` list, the artifacts the profile's
  governance walk lists over the pack's composed `depends_on` closure. `depth` (0 or 1, default 0) is the number of
  `suggests` hops the walk follows. `AgentProfile` is closed and mirrors `contracts/packs/schemas/agent-profile.schema.json`
  field for field in camelCase; `AgentProfileExcluding` is the closed wire form of `excluding` (`wholeFields` or `fieldValues`,
  the other null). `PackGovernanceEntry` is one governance entry (`kind`, `artifactId`, `urn`, `step`, `relation`, `via`,
  `hops`, `edge`, `packId`, `held`, `_links`), `GovernanceStep` its walk step (`lineage`, `requires`, `suggests`,
  `requires_of_suggested`, `vocabulary`). A parent no pack of the closure holds is listed with `held: false`, a null
  `packId` and no link. `held` describes the edge that listed the entry: a URN listed early by a dangling edge keeps
  `held: false` even when a later step reaches it through a held edge. The walk never goes through an entry with
  `held: false`, so nothing is listed below it, even where another pack of the closure holds the same URN.
  Every entry carries `edge`, the new closed schema `GovernanceEdgeWords` (`when` and `reason`, both optional): the words
  of the edge the walk reached the entry through, whatever its relation (`specializes_from`, `requires`, `suggests` or
  `vocabulary`). It is an empty object where the edge states neither word, and it is always present. Where more than one
  edge could reach an entry, the words are those of the edge of its `via`, the first reach the walk keeps.
  `suggests` entries are listed by this service; core's profile channel treats them as inert. The body references
  (`directiveReferences` and the others) are body fields and are not walked. The answer carries `Charter-Source-Fingerprint`,
  the digest the read verified, and an `ETag` that is the pack revision, as a directive's is. A profile the pack does not
  hold is `404 artifact_not_found`. The `Depth` parameter now speaks of any walk, and the `references` relation is added
  to the relation table for the profile's link to its references read.
  `ArtifactKindToken` gains `agent_profile`, last, so `GET /packs/{packId}/artifacts?kind=agent_profile` lists a pack's
  profiles (a request-side enum widened: a client that never sends the token is unaffected). The pack's artifact list,
  `listArtifactReferences` and `deletePackArtifact` now serve the kind (see the `### Changed` fragment of this change); the `collection`,
  `references` and lineage `artifact` links of a profile read therefore answer 200.
