# Data model: Graph documents are the sole source of edges

## Relationship (edge), unchanged

`(source, target, relation)`, with an optional `when` and an optional reason.

- **Identity**: the directed triple, compared as ADR-0036 describes. The structural invariant of
  ADR-0027 and ADR-0028 also applies: at most one structural relationship per unordered pair, and a
  `tension` may sit beside it.
- **Authored in**: a pack's graph documents only, that is a `*.graph.yaml` root shard or
  `drg/fragment.yaml`, or the registered shard that `putPackRelationship` writes (`registered.graph.yaml`).
- **Derived (never authored)**: a guard's `IMPLEMENTED_BY` to its asset. It is refused on write and
  never compared by the round trip.

## Artifacts: fields removed

| Kind | Removed | Kept |
|---|---|---|
| directive | `references` | `procedures` (prose steps, mints nothing) |
| paradigm | `references`, `directive_refs` | — |
| tactic | `references`, `steps[].references` | — |
| procedure | `references` | — |
| mission type | `references`, `steps[].references` | — |
| styleguide | `references` | — |
| toolguide | `references` | — |
| guard | — | `implemented_by` |

## Registration bodies

- `DirectiveRegistration`: loses `references` and `relationships`.
- `ParadigmRegistration`: loses `references`, `directiveRefs` and `relationships`.
- `StyleguideRegistration` and `ToolguideRegistration`: lose `references`.
- Every request shape is closed, and an unknown member is refused with `validation_failed` (400).
- `RelationshipRegistration` (`putPackRelationship`) is the only shape that writes an edge.

## Read shapes

- `Directive`, `Paradigm` and `Styleguide` lose `relationships`.
- `Directive`, `Paradigm`, `Tactic`, `TacticStep`, `Styleguide` and `Toolguide` lose their reference
  fields.
- `ReferencedArtifact` keeps its shape, but each row is now an outgoing graph edge of the artifact,
  and its `relation` is that edge's relation.

## Invariants

1. No artifact field mints an authored edge.
2. The edges the round trip compares are the edges of the graph documents.
3. A pack file or request body that carries a removed field is refused.
