# Data model: Slice F

## Kernel (`kernel.domain`)

| Type | Shape | Rules |
|---|---|---|
| `NodeKind` | the existing enum with `MISSION_STEP_CONTRACT` removed | token, plural, `authoredInGraphs`, `artifactKind()` |
| `ArtifactKind` | the existing enum with `MISSION_STEP_CONTRACT` removed | token, plural, `activatable` |
| `Kind` markers | a sealed interface per kind (`Directive`, `Tactic`, …, `Action`, `MissionType`, `Template`, `Asset`, `Guard`, …) | one marker for each `NodeKind`, so the parity test checks it |
| Kind groups | sealed interfaces: `Guidance` (directive, tactic, styleguide, toolguide, paradigm, procedure), `Activatable`, and per-relation source and target groups such as `RequiresSource` | equal to the endpoint table's rows, checked by test |
| `KindRef<K extends Kind>` | a typed URN: optional pack, kind, id. Its constructor is private, and only per-kind leaf factories such as `KindRef.directive(id)` build it | the kind is fixed by `K`; no `KindRef` of a group type can exist. Named so that it does not clash with `activations.domain.NodeRef` |
| `RelationType` | the single relation enum. `Relation` is deleted (0 callers in main code), and `IMPACTS`, `REPLACES` and `DELEGATES_TO` are dropped | `directionality`, `derived`, `endpoints: Set<EndpointPair>` |
| `EndpointPair` | `(NodeKind source, NodeKind target)` | — |
| `Relationship` | the existing class. Its raw constructor becomes package-private, and it gains one static typed factory per rectangle of the table (`requires`, `requiresAsset`, `requiresStep`, `enhancesStep`, `enhancesSame`, …) | the self-loop rule stays. A same-kind relation uses a single type parameter. An ArchUnit rule lets only `kernel.domain` call the constructor |
| `EndpointRule` | `bind(RelationType, Urn, Urn) → Bound(Relationship) \| Fault(code, relation, sourceKind, targetKind)`, and `bindTension(Urn, Urn)` | the only untyped path. Checks run in this order: `StepEnhancementRules`, then `overrides_source_not_activatable`, then `relationship_endpoint_not_allowed`. An agreement test reflects over the factories' bounds against the table |

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
| `SubstepDelegation` | `ArtifactKind kind`, `List<PackQualifiedId> candidates` (at least one, written `[<pack>::]<id>`) | `kind` is one of the `scope` targets from `action`: G or agent_profile |

### Load rules (a pack that breaks one is refused)

| Code | Rule |
|---|---|
| `substep_delegation_unresolved` | each candidate resolves to an artifact of `kind`. A bare candidate resolves in the pack, checked at load over `PackContents.MissionTypeFacts`. A qualified candidate resolves in the closure, checked at compose and at registration |
| `substep_delegation_unscoped` | `action:<type>/<step>` has a `scope` edge to each candidate |
| `mission_step_unlinked` | every step and on-demand step has an authored `mission_type:<t> -requires-> action:<t>/<s>` edge. This is checked per pack, and again at compose on the winning type, where an edge to a step the winning type lacks is refused too |
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
