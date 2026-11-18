# Research: Serve tensions

Findings from two code surveys (2026-11-18) and the operator's rulings. Each entry is Decision / Rationale /
Alternatives.

## R1. Where tensions are dropped today

- **Decision**: Remove the drops; let a typed `tension` edge flow through the ordinary edge path.
- **Rationale**: `CompositionFactsBuilder.typedEdge` drops it because `RelationType.fromToken("in_tension_with")` is
  empty; `DomainRepositoryService.edges` skips it explicitly; `WholePackStore.salvageEdge` drops it. With
  `RelationType.TENSION`, the composition facts, `EdgeIndex` (with its override resolution) and the references listing
  receive it without a special path.
- **Alternatives**: a separate tension channel in `PackCompositionFacts` beside the edges. Rejected: F1 (a) rules one
  model, and the walks already whitelist their relations (R3).

## R2. Identity: pair plus class

- **Decision**: `Relationship.equals`/`hashCode` include `type == TENSION`; a `sameSlot` helper names it; `samePair`
  stays the pair test.
- **Rationale**: `RelationshipMerge.upsert` and `RelationshipRegistrationService` (`created` at :135) use `equals`;
  the step-enhancement guards (:218, :226) and `RelationshipRemovalService.names` use the pair. Each user is
  classified (plan, Engineering Alignment). `EdgeIndex` keys by identity hash, so it is unaffected.
- **Alternatives**: a separate merge for tensions. Rejected: two merges are two models.

## R3. No walk follows a tension

- **Decision**: rely on the explicit relation sets and pin it with one test per walk.
- **Rationale**: `CascadeRules.CASCADE_RELATIONS`, `EffectiveSet.HARD_RELATIONS`, `ActionContextWalk.Step` (and its
  root set at :323), `GovernanceWalk.Step`, `ReachRequest` are all explicit. The only exhaustive switch over
  `RelationType` is `EndpointTable.of` (compile break, gets the row).
- **Alternatives**: none needed.

## R4. Canonical orientation and the pack qualifier

- **Decision**: compare bare URNs. A shard may write a pack-qualified `source` (the schema's pattern allows it).
  The registration wire keeps `from` in the pack; when the canonical `from` is the qualified `to`, it is stored
  qualified.
- **Rationale**: ADR-0036 decision 4 and the pack schema's description (C-002: smaller URN as source); the pack is
  provenance (ADR-0045 decision 1 as amended).
- **Alternatives**: refuse a registration whose canonical `from` is foreign. Rejected: the pair is unordered, so the
  author's orientation is not meaningful.

## R5. The effective graph's tension words

- **Decision**: `GraphProjection` keeps `when` and `reason` for tension edges and adds `reconciledBy` from the merged
  `reconciles_tension` links in force; other edges are unchanged.
- **Rationale**: `EdgeIndex.links()` already carries `when` and `reason` (`Link` record); `GraphProjection` drops them
  at :97. ADR-0045 decision 9's open review item.
- **Alternatives**: carry the words on every edge. Deferred to the operator through the review item; it widens every
  graph answer.

## R6. ETags

- **Decision**: bump `EffectiveTag.DOMAIN` to `charter-effective-read\0v4\n`; the new read uses `Read.TENSIONS` with
  the skipped report's inputs (revision, closure).
- **Rationale**: the graph, context and activation bodies change for the same inputs; EffectiveTag's javadoc requires
  the bump.

## R7. Finding codes

- **Decision**: load codes `tension_duplicated` and `tension_orientation` in `PackCodes.CONTENT`, reported at
  `/edges/<n>` of the file through `GraphRules`; they surface as violations of the existing refusals, so
  `CharterRefusalCode.yaml` is unchanged.
- **Rationale**: the same route as `relationship_endpoint_not_allowed`.

## R8. Core's behaviour (evidence only)

- Core's governance bundle carries `tension_arbiters` and `unarbitrated_tensions`; its consistency check keys a
  `TensionFinding` on the sorted pair and offers two fixed resolution strings. Charter's vocabulary (`reconcile`,
  `deactivate_one_side`), its read shape (graph edge, context member, a separate read) and its word "reconciler" are
  intended differences, recorded in the divergence register (FR-013).

## Plan-point squad

See [research/squad-plan.md](research/squad-plan.md).
