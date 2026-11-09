# Data model: Agent profile reads

## Agent profile body (`AgentProfile` schema, wire)

This mirrors `contracts/packs/schemas/agent-profile.schema.json`, one property per schema field, with camelCase on
the wire.
- `profileId`, `name`, `description`, `schemaVersion`, `roles[]`, `avatarImage`, `capabilities[]`,
  `routingPriority`, `maxConcurrentTasks`, `model`, `effort`, `sentinel`, `purpose`.
- `specialization{primaryFocus, secondaryAwareness, avoidanceBoundary, successDefinition}`.
- `collaboration{handoffTo[], handoffFrom[], worksWith[], outputArtifacts[], operatingProcedures[], canonicalVerbs[]}`.
- `modeDefaults[{mode, description, useCase}]`, `initializationDeclaration`.
- `specializationContext{languages[], frameworks[], filePatterns[], domainKeywords[], writingStyle, complexityPreference}`.
- `appliesToLanguages[]`, `directiveReferences[{code, name, rationale}]`.
- `tacticReferences` / `styleguideReferences` / `toolguideReferences[{id, rationale}]`.
- `selfReviewProtocol{steps[{name, command?, gate}]}`, `excluding`.

The exact set is whatever the domain type `profiles.domain.AgentProfile` holds. A test fails if a field of the
domain type is not mapped.

## Governance entry (`ProfileGovernanceEntry`)

| Field | Type | Rule |
|---|---|---|
| `kind` | ArtifactKindToken or node kind | The target's kind |
| `artifactId` | string | The target's local id |
| `urn` | string | `kind:id` |
| `step` | enum `lineage`, `requires`, `suggests`, `requires_of_suggested`, `vocabulary` | The step that listed it (minimum step) |
| `relation` | enum `specializes_from`, `requires`, `suggests`, `vocabulary` | The step's relation |
| `via` | string (URN) | The parent: the profile for its own edges, otherwise another entry |
| `hops` | integer ≥ 1 | `hops(via) + 1` |
| `when`, `reason` | string, nullable | Only on a `suggests` entry, from the edge |
| `packId` | string, nullable | The holding pack (pack-scoped: the first holder in closure order; activation-scoped: the winner) |
| `held` | boolean | Pack-scoped only. False when the closure holds no such artifact (no link then) |
| `state`, `required`, `policy`, `reason` | as `EffectiveArtifact` | Activation-scoped only |
| `_links` | `artifact` (and `effective-artifact` under an activation) | A link only to a held target of a served kind |

**Invariants:**
- An entry is listed once.
- Entries come in URN order.
- The walk terminates on a lineage cycle.

Activation-scoped `suggestions[]` hold the out-of-force candidates
`{kind, artifactId, via, hops, when, reason}`, as in the action context.

## Effective artifact read (`EffectiveArtifactRead`)

| Field | Rule |
|---|---|
| `activationId`, `revision`, `governanceFingerprint` | As the effective list |
| `kind`, `artifactId`, `packId`, `level` | The layer-merge winner |
| `state`, `required`, `policy`, `reason` | As the effective list item |
| `agentProfile` | Only on the profile route: the winner's body |
| `governance[]`, `suggestions[]` | Only on the profile route |
| `_links` | `self`; `artifact` (the winner's pack-scoped read); `activation` |

## Walk (`packs.application`)

The engine is `ActionContextWalk.Run`, generalised to a start node and a first step.
- **Profile:** `start = agent_profile:<id>`. The steps run in this order:
  1. `LINEAGE` (transitive);
  2. `REQUIRES` from `{start} ∪ lineage`;
  3. `SUGGESTS(depth)` from `{start} ∪ lineage`;
  4. `REQUIRES_OF_SUGGESTED`;
  5. `VOCABULARY` (one hop).
- **Action:** unchanged.
