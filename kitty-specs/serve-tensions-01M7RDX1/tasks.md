# Tasks: Serve tensions

**Mission**: `serve-tensions-01M7RDX1` · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) (read its
"Revisions after the plan-point squad" section, which wins over the concerns above it) · **ADR**: ADR-0045 (accepted,
with rulings G1 to G3).

Subtask completion is event-sourced: record it with `spec-kitty agent tasks mark-status Txxx --status done`. The rows
below are references, not checkboxes.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | `RelationType.TENSION`, its endpoint row; drop the separate tension table and `bindTension` | WP01 | |
| T002 | Pair-class identity on `Relationship`; `samePair`, `sameSlot`, `tension(...)`; remove `Tension`; `DoctrineGraph` | WP01 | |
| T003 | `GraphBinding` and `RelationshipEndpointRule` bind `tension` as a relation; registered-shard finding removed | WP01 | |
| T004 | Storage: writer, shard, round trip (+ fromPack/toPack), repository, facts builder, salvage | WP01 | |
| T005 | The `tension` spelling in the pack schemas and `Relation.yaml`; `WireTokenParityTest` | WP01 | |
| T006 | The kind taxonomy row `tension` (service and contract) | WP01 | |
| T007 | Fixtures and tests renamed; `in_tension_with` refused as unknown | WP01 | |
| T008 | No walk follows a tension (pin tests); references list a tension edge | WP01 | |
| T009 | `tension_duplicated`, `tension_orientation`, `tension_self` codes | WP02 | [P] |
| T010 | The `GraphRules` tension consistency rule across the pack's graph documents | WP02 | |
| T011 | Load fixtures, validate goldens and planted verdicts | WP02 | |
| T012 | Rule tests with positive controls | WP02 | |
| T013 | Wire: `type: tension`, self-tension refused, stored orientation, echo answer | WP03 | [P] |
| T014 | Merge and registration on the pair class; 201 per class; 422 `tension_duplicated` against another document | WP03 | |
| T015 | Step-enhancement guards and `edgeKey` judged per class | WP03 | |
| T016 | `deletePackRelationship` `relation` parameter, per class; contract path | WP03 | |
| T017 | Artifact removal takes its registered tensions; inbound references do not count a tension | WP03 | |
| T018 | Relationship scenarios | WP03 | |
| T019 | `TensionsInForce`: symmetric resolution, canonical pairs, collapse, reconcilers | WP04 | [P] |
| T020 | `ClosureView.tensions(states)`; `GraphProjection` takes tension edges from it | WP04 | |
| T021 | The `tensions` example pack | WP04 | [P] |
| T022 | Example-pack enumeration tests and the round-trip expectation | WP04 | |
| T023 | `TensionsInForce` unit tests: overrides each side, permutations, self-pair, one-sided reconciler | WP04 | |
| T024 | `TensionSnapshot`, widened `GraphSnapshot.Edge`, `ClosureViewSession`, `PacksSession` | WP05 | |
| T025 | `EffectiveGraphEdge` `oneOf`, the mapper, the graph examples | WP05 | |
| T026 | `EffectiveContext.tensions`, `ContextTension`, the context schema and examples | WP05 | |
| T027 | `EffectiveTag` domain v4, tag tests, operation budget | WP05 | |
| T028 | Unit tests: mapper, context, session | WP05 | |
| T029 | The finding model in `activations.domain`: resolutions and excludable sides | WP06 | [P] |
| T030 | `TensionReadService`, `Read.TENSIONS`, the controller and mapper | WP06 | |
| T031 | Contract: path, schemas, examples, indexes, `Activation` link, headers | WP06 | |
| T032 | Link registries: lint ruleset, hypermedia table, `ActivationMapper` | WP06 | |
| T033 | Operation rows: every-operation, read-refusals, unexpected-failure, activation-links, switch gate | WP06 | |
| T034 | Unit tests for the read, the findings and the links | WP06 | |
| T035 | Scenario step for the tension words on graph edges and context tensions | WP07 | |
| T036 | `tensions.feature`: five activations, three reads each | WP07 | |
| T037 | Follow-through scenarios: exclude and re-read; locked side; registration in reverse order | WP07 | |
| T038 | Divergence register and service-contract page (tension and #101 rows) | WP08 | [P] |
| T039 | Contract change fragments: Breaking, Added, Changed | WP08 | [P] |
| T040 | Glossary, Contextive, packs README, ADR-0027 and ADR-0028 notes | WP08 | [P] |
| T041 | Full gates: `verifyAll`, round trip, docs checks | WP08 | |

## Phase 1: Foundation

### WP01 — Tension is a relation of the model ([tasks/WP01-tension-is-a-relation.md](tasks/WP01-tension-is-a-relation.md))

**Goal**: one model of an edge. **Priority**: P1. **Independent test**: `:kernel:test`, `:packs:test`, the round trip
and the offering scenarios pass with `tension` as a relation and no `Tension` type.

T001 `RelationType.TENSION`, its endpoint row; drop the separate tension table and `bindTension` (WP01)
T002 Pair-class identity on `Relationship`; `samePair`, `sameSlot`, `tension(...)`; remove `Tension`; `DoctrineGraph` (WP01)
T003 `GraphBinding` and `RelationshipEndpointRule` bind `tension` as a relation; registered-shard finding removed (WP01)
T004 Storage: writer, shard, round trip (+ fromPack/toPack), repository, facts builder, salvage (WP01)
T005 The `tension` spelling in the pack schemas and `Relation.yaml`; `WireTokenParityTest` (WP01)
T006 The kind taxonomy row `tension` (service and contract) (WP01)
T007 Fixtures and tests renamed; `in_tension_with` refused as unknown (WP01)
T008 No walk follows a tension (pin tests); references list a tension edge (WP01)

- **Sketch**: kernel first (T001, T002), then the packs that no longer compile (T003, T004, T006), then the
  wire tokens (T005) and tests (T007, T008).
- **Dependencies**: none. **Risks**: the atomic kernel change; tensions newly reach `EdgeIndex` and the references.

## Phase 2: Parallel after WP01

### WP02 — Pack tension consistency ([tasks/WP02-pack-tension-consistency.md](tasks/WP02-pack-tension-consistency.md))

**Goal**: a pack with inconsistent tensions is refused. **Priority**: P1. **Independent test**: the rule tests and the
load fixtures.

T009 `tension_duplicated`, `tension_orientation`, `tension_self` codes (WP02)
T010 The `GraphRules` tension consistency rule across the pack's graph documents (WP02)
T011 Load fixtures, validate goldens and planted verdicts (WP02)
T012 Rule tests with positive controls (WP02)

- **Dependencies**: WP01. **Risks**: ownership is judged on bare URNs against the pack's own artifacts.

### WP03 — Registration carries tensions ([tasks/WP03-registration-carries-tensions.md](tasks/WP03-registration-carries-tensions.md))

**Goal**: register, keep and remove tensions per pair class. **Priority**: P1. **Independent test**: the relationship
and removal scenarios.

T013 Wire: `type: tension`, self-tension refused, stored orientation, echo answer (WP03)
T014 Merge and registration on the pair class; 201 per class; 422 `tension_duplicated` against another document (WP03)
T015 Step-enhancement guards and `edgeKey` judged per class (WP03)
T016 `deletePackRelationship` `relation` parameter, per class; contract path (WP03)
T017 Artifact removal takes its registered tensions; inbound references do not count a tension (WP03)
T018 Relationship scenarios (WP03)

- **Dependencies**: WP01. WP02's codes are named by T014; WP03 depends on WP02 too.

### WP04 — Tensions in force and the example pack ([tasks/WP04-tensions-in-force.md](tasks/WP04-tensions-in-force.md))

**Goal**: one projection every read answers from. **Priority**: P1. **Independent test**: `TensionsInForce` unit tests
and the example-pack tests.

T019 `TensionsInForce`: symmetric resolution, canonical pairs, collapse, reconcilers (WP04)
T020 `ClosureView.tensions(states)`; `GraphProjection` takes tension edges from it (WP04)
T021 The `tensions` example pack (WP04)
T022 Example-pack enumeration tests and the round-trip expectation (WP04)
T023 `TensionsInForce` unit tests: overrides each side, permutations, self-pair, one-sided reconciler (WP04)

- **Dependencies**: WP01, WP02 (the example pack must pass the consistency rules).

## Phase 3: The reads

### WP05 — The effective graph and the action context ([tasks/WP05-graph-and-context.md](tasks/WP05-graph-and-context.md))

T024 `TensionSnapshot`, widened `GraphSnapshot.Edge`, `ClosureViewSession`, `PacksSession` (WP05)
T025 `EffectiveGraphEdge` `oneOf`, the mapper, the graph examples (WP05)
T026 `EffectiveContext.tensions`, `ContextTension`, the context schema and examples (WP05)
T027 `EffectiveTag` domain v4, tag tests, operation budget (WP05)
T028 Unit tests: mapper, context, session (WP05)

- **Dependencies**: WP04.

### WP06 — The activation's tensions read ([tasks/WP06-activation-tensions-read.md](tasks/WP06-activation-tensions-read.md))

T029 The finding model in `activations.domain`: resolutions and excludable sides (WP06)
T030 `TensionReadService`, `Read.TENSIONS`, the controller and mapper (WP06)
T031 Contract: path, schemas, examples, indexes, `Activation` link, headers (WP06)
T032 Link registries: lint ruleset, hypermedia table, `ActivationMapper` (WP06)
T033 Operation rows: every-operation, read-refusals, unexpected-failure, activation-links, switch gate (WP06)
T034 Unit tests for the read, the findings and the links (WP06)

- **Dependencies**: WP05. **Risks**: shared files with #108 (PR #600): append, keep both sides on rebase.

### WP07 — The five example activations ([tasks/WP07-five-activations.md](tasks/WP07-five-activations.md))

T035 Scenario step for the tension words on graph edges and context tensions (WP07)
T036 `tensions.feature`: five activations, three reads each (WP07)
T037 Follow-through scenarios: exclude and re-read; locked side; registration in reverse order (WP07)

- **Dependencies**: WP03, WP05, WP06.

## Phase 4: Records

### WP08 — Records and gates ([tasks/WP08-records-and-gates.md](tasks/WP08-records-and-gates.md))

T038 Divergence register and service-contract page (tension and #101 rows) (WP08)
T039 Contract change fragments: Breaking, Added, Changed (WP08)
T040 Glossary, Contextive, packs README, ADR-0027 and ADR-0028 notes (WP08)
T041 Full gates: `verifyAll`, round trip, docs checks (WP08)

- **Dependencies**: WP07.

## MVP

WP01 to WP04 make the model and the projection right; WP05 to WP07 serve them. The Mission lands as one pull request
(#606), so every WP ships together.
