### Breaking

- `AgentProfile.sentinel` (boolean) is replaced by `performer: agent | human` (ruling D4, #512), on the wire and in the
  pack file (`agent-profile.schema.json`), with no reader of the old key: a profile file that carries `sentinel` is now
  refused as invalid content, as any undeclared key of the closed schema is. `performer` is omitted when the file
  declares none, and selection reads an absent performer as `agent`. The role `operator` is reserved for a
  human-performed profile. Core must regenerate its `AgentProfile` DTO and write `performer` in place of `sentinel`
  in the profiles it holds.
- `getEffectiveAgentProfile` now answers the effective profile (R8, #102, #380; rulings D5, D6 and D7 of ADR-0078),
  not the stored winner: `agentProfile` is the winner merged with its in-force enhancers and folded root to leaf under
  its in-force `specializes_from` ancestors, each merged with its own enhancers first. An enhancer's identity fields
  (`profileId`, `name`, `purpose`, `role`, `altRoles`, `performer`, `routingPriority`) are never merged, and identity
  fields are never inherited; every other top-level field a descendant or an enhancer declares replaces the value so
  far, and a field a descendant leaves absent is inherited from its nearest ancestor that declares it. The answer
  gains the required `lineage`, an array of `LineageLink` (`id`, `pack`, `depth`), the in-force ancestors nearest
  first, empty for a root. A profile that enhances another agent profile answers its own stored body and links the
  read of what it enhances as `_links.enhances` (a new relation). `_links.artifact` still reads the stored profile in
  its pack (`getPackAgentProfile`), which is no longer the same body when an enhancer or an ancestor contributes. An
  in-force enhancement stored before the rule that would drop part of a profile of the chain is now refused with
  `500 profile_enhancement_drops_field`, its `packs` naming the enhancing pack first. The `ETag` keeps its inputs,
  which already cover the packs of every enhancer and ancestor of the closure; the tag version stays `v3`, since no
  release served the earlier body under it. Core's seam: core may read a profile's effective form and its lineage
  from this read instead of walking `specializes_from` and `enhances` itself, and must regenerate its
  `EffectiveAgentProfileRead` DTO for `lineage`.
