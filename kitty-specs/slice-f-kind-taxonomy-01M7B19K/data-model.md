# Data model: Slice F

## Kernel (`kernel.domain`)

| Type | Shape | Rules |
|---|---|---|
| `NodeKind` | the existing enum with `MISSION_STEP_CONTRACT` removed | token, plural, `authoredInGraphs`, `artifactKind()` |
| `ArtifactKind` | the existing enum with `MISSION_STEP_CONTRACT` removed | token, plural, `activatable` |
| `Kind` markers | a sealed interface per kind (`Directive`, `Tactic`, …, `Action`, `MissionType`, `Template`, `Asset`, `Guard`, …) | one marker for each `NodeKind`, so the parity test checks it |
| Kind groups | sealed interfaces: `Guidance` (directive, tactic, styleguide, toolguide, paradigm, procedure), `Activatable`, and per-relation source and target groups such as `RequiresSource` | equal to the endpoint table's rows, checked by test |
| `NodeRef<K extends Kind>` | a typed URN: optional pack, kind, id | the kind is fixed by `K` |
| `RelationType` | the single relation enum. It absorbs `Relation`, and drops `IMPACTS`, `REPLACES` and `DELEGATES_TO` | `directionality`, `derived`, `endpoints: Set<EndpointPair>` |
| `EndpointPair` | `(NodeKind source, NodeKind target)` | — |
| `Relationship` | the existing class plus static typed factories per relation | the self-loop rule stays. A same-kind relation uses a single type parameter |
| `EndpointRule` | `bind(RelationType, Urn, Urn) → Bound(Relationship) \| Fault(code, relation, sourceKind, targetKind)` | the only untyped path. Its codes are `overrides_source_not_activatable` (checked first) and `relationship_endpoint_not_allowed` |

### The endpoint table (operator, as drafted)

G stands for the six guidance kinds: directive, tactic, styleguide, toolguide, paradigm and procedure.

| Relation | Source → target |
|---|---|
| `requires` | G, agent_profile or skill → G, asset or template; asset → asset; mission_type → action |
| `suggests` | G, agent_profile or skill → G, asset, template or agent_profile |
| `applies` | action → directive or tactic |
| `scope` | mission_type or action → G or agent_profile |
| `vocabulary` | any kind authored in graphs → the glossary kind |
| `instantiates` | action → template |
| `refines` | G → G |
| `specializes_from` | agent_profile → agent_profile |
| `enhances` | step_enhancement → action; X → X for every activatable X except mission_type and step_enhancement |
| `overrides` | X → X, where X is activatable and not step_enhancement |
| `reconciles_tension` | G → G |
| `rejects` | G → anti_pattern |
| `implemented_by` | guard → asset (derived, never authored) |
| tension (`Tension`, `in_tension_with`) | G ↔ G (held beside the relations, ADR-0028) |

## Missions (`missions.domain`)

| Type | Shape | Rules |
|---|---|---|
| `MissionStep` | existing fields plus `List<Substep> substeps` | the list may be empty; when authored it has at least one entry; ids are unique within the step |
| `Substep` | `id`, `description`, `command?`, `List<SubstepInput> inputs`, `SubstepDelegation? delegatesTo`, `guidance?` | `id` follows the kernel identifier grammar; `description` must not be blank |
| `SubstepInput` | `flag`, `source`, `optional` | carried over from the step-contract input |
| `SubstepDelegation` | `ArtifactKind kind`, `List<String> candidates` (at least one) | `kind` is activatable |

### Load rules (a pack that breaks one is refused)

| Code | Rule |
|---|---|
| `substep_delegation_unresolved` | each candidate resolves to an artifact of `kind` in the closure |
| `substep_delegation_unscoped` | `action:<type>/<step>` has a `scope` edge to each candidate |
| `mission_step_unlinked` | every step and on-demand step has an authored `mission_type:<t> -requires-> action:<t>/<s>` edge |
| `relationship_endpoint_not_allowed` | an edge's endpoint kinds are in its relation's table |
| `overrides_source_not_activatable` | the source of an `overrides` edge is activatable (slice C) |

## Removed

- **Packs domain:** `MissionStepContract`, `MissionStepContractIdentifier`, `ContractStep`, `GateBinding`,
  `StepDelegation` and `StepInput`. Their content moves into `Substep`. The `review` gate becomes a lifecycle guard
  entry.
- **Charter schema:** `template_set`, in `charter.schema.json`.
- **Pack schemas:** `mission-step-contract.schema.json`. `file-kinds.yaml` now lists `*.step-contract.yaml` and
  `mission_step_contract.graph.yaml` as retired.

## State and events

There are no new states or events. Every rule is checked when a pack is loaded, read, validated or written. A
refusal writes nothing.
