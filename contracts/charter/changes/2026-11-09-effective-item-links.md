### Added

- The links from the API root to the effective reads, without building a URL (#407, #450 for the served kinds). Every
  member is optional or lies in an open `_links` map, and nothing is renamed or removed.
  - The API root links `activations` (`/api/v1/activations`, `method: POST`), the register a client creates an activation
    in. The `Activation` answer (`createActivation`, `getActivation`, `replaceActivation`) links `artifacts`, the RFC 6570
    template `/api/v1/activations/{activationId}/artifacts{?kind,state}` (`templated: true`) of its effective list, beside
    `self`.
  - `EffectiveArtifact` (the item of the effective list and of the action context) and `EffectiveGraphNode` gain an
    optional `_links`, the open `Links`. For a kind the pack-scoped artifact routes serve, an item links
    `effective-artifact` (`getEffectiveArtifact`, or `getEffectiveAgentProfile` for an `agent_profile`: the artifact's read
    under the activation) and `artifact` (the pack-scoped read of the pack that holds the version in force). A graph
    node names no pack, so it links `effective-artifact` only. `_links` is absent for any other item, a step (`action`)
    and a mission type among them, and an item has no `self`. The mapper asks the service for the served kinds at each
    read, so a kind the packs slice opens later is linked without a contract change.
  - Every entry of an `EffectiveAgentProfileRead.governance` that carries `artifact` also carries `effective-artifact`:
    the read of the entry's winner under the same activation. `EffectiveGovernanceEntry` still holds `_links` as an
    object that is empty for a kind no pack-scoped read serves.
  - `effective-artifact` is a new relation of the hypermedia guide and of the `link-relations-are-known` rule; the
    `activations` relation is now in the contract on the root, and the `artifact` and `up` rows name the effective reads
    they appear on.

  This is additive, not breaking (ADR-0022): a new key of an open `_links` map is a link a client must tolerate
  (`Links`), and the two new `_links` members of closed response schemas are optional and absent where nothing is linked.
  No contract version has been released (`0.1.0-SNAPSHOT` was never cut) and the closed-schema rule for response fields
  freezes at 1.0.0, so core's seam changes only if it regenerates its client and wants to follow the links.
