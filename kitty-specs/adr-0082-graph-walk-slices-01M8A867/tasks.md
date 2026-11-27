# Tasks: ADR-0082 slices 2 to 4 (adr-0082-graph-walk-slices-01M8A867)

Planning branch and merge target: `feat/adr-0082-graph-walk-slices`. Each slice lands on `main` by its own pull request
(C-002):

| Pull request | Slice | Work packages |
|---|---|---|
| #665 (draft) | 2 (#637) | WP01 |
| new | 3 (#638) | WP02, WP03 |
| new | 4 (#639) | WP04, WP05, WP07 |
| new | #632 item 3 pickup | WP06 |

Every pull request gets its own adversarial review, posted as "Independent adversarial review (agent)", with every
finding folded. Every pull request runs `verifyAll`, `check_docs.py all`, markdownlint and cspell before it is
reported ready. No PIT (C-004).

## Subtask Index

| ID | Description | WP | Parallel |
|---|---|---|---|
| T001 | Move the application layer's walks into `PackGraph` (built) | WP01 | |
| T002 | Walk-containment ArchUnit guard, red first (built) | WP01 | |
| T003 | Fold the 13 review findings of PR #665 (built) | WP01 | |
| T004 | Move the resolver's `depends_on`-only closure and `parent_pack` line into `PackGraph` | WP01 | |
| T005 | Move Kahn's dependency order and the leftover-cycle searches into `PackGraph` | WP01 | |
| T006 | Docs, changelog and PR #665 body for the resolver walks | WP01 | |
| T007 | Generic kernel `GraphWalker<N, E>` and its three users | WP02 | |
| T008 | `PackGraphNode`, `PackNode`, `ArtifactNode`, `EdgeNode`, `EdgeKind` | WP02 | [P] |
| T009 | `Scope` and the walk modes | WP02 | [P] |
| T010 | `PackGraph.of` and the iterative driver `walk(scope, mode, walker)` | WP02 | |
| T011 | `PackGraphWalkTest`, red first | WP02 | |
| T012 | `ValidationWalker` (checks, per-check buckets, VIOLATION gate), `GraphCheck`, `Findings` | WP03 | |
| T013 | Move the three own-closure rules into `packs.domain.rules`; `ProfileReader`; split own pins and pin conflicts | WP03 | |
| T014 | `PackRulesets.ownClosure(..)` and `references(..)` on the walker | WP03 | |
| T015 | Delete `ClosureWalk` and `OwnClosureWalk`; rewire `ClosureImpactCheck` and the tests that build it | WP03 | |
| T016 | Architecture page: built notes and the determinism guard (Proposed); changelog | WP03 | |
| T017 | Relations subject over a composed closure (edges, specialization graph, enhances cycles precomputed) | WP04 | |
| T018 | Relation rules and the specialization cycle as `RelationRules`; `PackRulesets.relations()` | WP04 | |
| T019 | Delete `ImpactRelations` and `SpecializationCycleRule`; rerun their tests on the rules | WP04 | |
| T020 | Edge-index refusals as relations-phase rules, behind the relations gate | WP05 | |
| T021 | One code per edge-index fault on reads and registration (D2; Q-A to Q-C) | WP05 | |
| T022 | Delete `ClosureImpactCheck#relationFaults` and the hand-written gates | WP05 | |
| T023 | Contract, scenarios, `### Breaking` fragment and docs for the wire change | WP05 | |
| T024 | pack-doctor runs the relations phase over each root's composed closure | WP06 | |
| T025 | `FullPassParityTest` rows and the guide | WP06 | |
| T026 | A walk mode and scope selection for the `REFERENCES` members | WP07 | |
| T027 | `references(..)` runs on `PackGraph#walk`; the Interim notes go | WP07 | |
| T028 | `ReferencesWalkTest`, parity rows and the changelog | WP07 | |

## WP01 --- Slice 2: `PackGraph` holds every pack-graph walk (#637, PR #665)

- **Goal**: every walk over the pack graph is in `PackGraph`: the application layer's and the composition resolver's. A guard keeps them there.
- **Priority**: P1. **Independent test**: `PackGraphWalkBoundaryTest` and `PackGraphTest`; the resolver's tests unchanged.
- **Prompt**: [tasks/WP01-pack-graph-walks.md](tasks/WP01-pack-graph-walks.md)

T001 Move the application layer's walks into `PackGraph` (built) (WP01)
T002 Walk-containment ArchUnit guard, red first (built) (WP01)
T003 Fold the 13 review findings of PR #665 (built) (WP01)
T004 Move the resolver's `depends_on`-only closure and `parent_pack` line into `PackGraph` (WP01)
T005 Move Kahn's dependency order and the leftover-cycle searches into `PackGraph` (WP01)
T006 Docs, changelog and PR #665 body for the resolver walks (WP01)

Dependencies: none. Risks: each moved walk's order reaches a refusal detail; pin it before moving.

## WP02 --- Slice 3a: graph nodes, scope and the driver (#638)

- **Goal**: the visitor contract is generic, and `PackGraph` drives it over typed nodes within a scope.
- **Priority**: P1. **Independent test**: `PackGraphWalkTest`.
- **Prompt**: [tasks/WP02-graph-nodes-and-driver.md](tasks/WP02-graph-nodes-and-driver.md)

T007 Generic kernel `GraphWalker<N, E>` and its three users (WP02)
T008 `PackGraphNode`, `PackNode`, `ArtifactNode`, `EdgeNode`, `EdgeKind` (WP02)
T009 `Scope` and the walk modes (WP02)
T010 `PackGraph.of` and the iterative driver `walk(scope, mode, walker)` (WP02)
T011 `PackGraphWalkTest`, red first (WP02)

Dependencies: WP01. Parallel: T008 and T009.

## WP03 --- Slice 3b: the walker, the own-closure rules and REFERENCES (#638)

- **Goal**: one `ValidationWalker` runs the factory's own-closure phase and `REFERENCES`; the judging port is gone.
- **Priority**: P1. **Independent test**: `ValidationWalkerTest`, `OwnClosureRulesTest`, `FullPassParityTest`, `ClosureImpactCheckTest`.
- **Prompt**: [tasks/WP03-walker-and-own-closure-rules.md](tasks/WP03-walker-and-own-closure-rules.md)

T012 `ValidationWalker` (checks, per-check buckets, VIOLATION gate), `GraphCheck`, `Findings` (WP03)
T013 Move the three own-closure rules into `packs.domain.rules`; `ProfileReader`; split own pins and pin conflicts (WP03)
T014 `PackRulesets.ownClosure(..)` and `references(..)` on the walker (WP03)
T015 Delete `ClosureWalk` and `OwnClosureWalk`; rewire `ClosureImpactCheck` and the tests that build it (WP03)
T016 Architecture page: built notes and the determinism guard (Proposed); changelog (WP03)

Dependencies: WP02.

## WP04 --- Slice 4a: the relations phase (#639)

- **Goal**: the relation rules and the specialization cycle come from `PackRulesets.relations()`, judging a domain subject.
- **Priority**: P1. **Independent test**: the `ImpactRelationsTest` cases rerun on the rules, unchanged.
- **Prompt**: [tasks/WP04-relations-phase.md](tasks/WP04-relations-phase.md)

T017 Relations subject over a composed closure (edges, specialization graph, enhances cycles precomputed) (WP04)
T018 Relation rules and the specialization cycle as `RelationRules`; `PackRulesets.relations()` (WP04)
T019 Delete `ImpactRelations` and `SpecializationCycleRule`; rerun their tests on the rules (WP04)

Dependencies: WP03.

## WP05 --- Slice 4b: edge-index rules, one code per fault (#639, #667)

- **Goal**: the edge-index refusals are relations-phase rules; reads and registration answer one code per fault.
- **Priority**: P1. **Independent test**: read and registration scenarios for each fault; the mixed-fault gate fixture.
- **Prompt**: [tasks/WP05-edge-index-rules-one-code.md](tasks/WP05-edge-index-rules-one-code.md)

T020 Edge-index refusals as relations-phase rules, behind the relations gate (WP05)
T021 One code per edge-index fault on reads and registration (D2; Q-A to Q-C) (WP05)
T022 Delete `ClosureImpactCheck#relationFaults` and the hand-written gates (WP05)
T023 Contract, scenarios, `### Breaking` fragment and docs for the wire change (WP05)

Dependencies: WP04; operator answers to Q-A, Q-B and Q-C.

## WP06 --- pack-doctor picks up the cycle and the relations (#632 item 3)

- **Goal**: `pack-doctor validate --full` reports the specialization cycle and the relation faults with registration's codes.
- **Priority**: P2. **Independent test**: `FullPassParityTest` rows.
- **Prompt**: [tasks/WP06-pack-doctor-relations.md](tasks/WP06-pack-doctor-relations.md)

T024 pack-doctor runs the relations phase over each root's composed closure (WP06)
T025 `FullPassParityTest` rows and the guide (WP06)

Dependencies: WP05.

## WP07 --- Slice 4c: REFERENCES on a PackGraph walk (#639)

- **Goal**: `REFERENCES` runs on `PackGraph#walk` with a members mode and the scope's selection; no direct feed.
- **Priority**: P1 (operator ruling 01M8DT4CGSQZBPJCC6K9CN3ZX4). **Independent test**: `ReferencesWalkTest`; parity rows.
- **Prompt**: [tasks/WP07-references-on-a-graph-walk.md](tasks/WP07-references-on-a-graph-walk.md)

T026 A walk mode and scope selection for the `REFERENCES` members (WP07)
T027 `references(..)` runs on `PackGraph#walk`; the Interim notes go (WP07)
T028 `ReferencesWalkTest`, parity rows and the changelog (WP07)

Dependencies: WP05.
