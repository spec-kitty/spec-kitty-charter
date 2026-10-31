# Tasks: DRG tension edges and node fields round-trip

**Mission**: `drg-tension-and-node-fields-01M68A0T` · **Branch**: `issue-235-236-drg-round-trip`
(planning base and merge target) · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

The slicing is by layer, so `owned_files` never overlap:

- **WP01**: the kernel model.
- **WP02**: the packs read, policy, write and gate path. It turns both expected-red entries green.
- **WP03**: the records.

Every behavior change is red first (C-005). The plan-point squad's findings S1–S20 are folded into the
subtasks below.

## Subtask Index

| ID | Description | WP | Parallel |
| --- | --- | --- | --- |
| T001 | `DrgNode` gains `provenance`, ordered `tags`, `bodyPath`; 2-arg constructor kept | WP01 | |
| T002 | New kernel `Tension(from, to, when, rationale)` | WP01 | [P] |
| T003 | `DoctrineGraph` holds `tensions()`; 3-arg constructor; merge, equals and hashCode | WP01 | |
| T004 | `NodeKind.plural()` (inverse of `fromPlural`) with a property test | WP01 | [P] |
| T005 | Kernel javadoc: `Relation`/`RelationType` IMPACTS and RECONCILES_TENSION stop stating the −1 reading | WP01 | [P] |
| T006 | `RawNode` + reader carry provenance, tags, body_path (`YamlFields.strings`) | WP02 | |
| T007 | `PackPolicy` binds node fields, binds `in_tension_with` to `Tension`, removes the −1 mapping; `registeredGraph` reports a tension | WP02 | |
| T008 | `GraphShardWriter`: node fields, tension edges, shape refusals, `writeFragment` | WP02 | |
| T009 | `RelationshipShard.read` uses `registeredGraph` (no silent drop) | WP02 | |
| T010 | Gate adapter re-binds the written shard; production-path fixture with provenance, tags and a tension `when` | WP02 | |
| T011 | Rewrite the tests and fixtures that pin −1 (`PackPolicyGraphTest`, `VocabularyEqualityTest`, load fixtures) | WP02 | |
| T012 | Run the gate; remove both expected-red entries after the ratchet reports them green | WP02 | |
| T013 | ADR-0045: tension beside the relationships; nodes keep every declared field | WP03 | |
| T014 | DIV-018, `logical-model.md`, `Relation.yaml` description, contract CHANGELOG, docs changelog, ADR index | WP03 | |
| T015 | Follow-up draft: restore the load-fixture runner (63 orphaned cases) | WP03 | [P] |

## Phase 1 — Foundation

### WP01 — Kernel: tension and node fields in the domain model

- **Goal**: the kernel can hold a tension beside the structural relationships, and a node can hold
  every declared field.
- **Priority**: P1. **Prompt**: [tasks/WP01-kernel-tension-and-node-fields.md](tasks/WP01-kernel-tension-and-node-fields.md)
- **Independent test**: `./gradlew :kernel:test`.

T001 `DrgNode` gains `provenance`, ordered `tags`, `bodyPath`; 2-arg constructor kept (WP01)
T002 New kernel `Tension(from, to, when, rationale)` (WP01)
T003 `DoctrineGraph` holds `tensions()`; 3-arg constructor; merge, equals and hashCode (WP01)
T004 `NodeKind.plural()` with a property test (WP01)
T005 Kernel javadoc for IMPACTS and RECONCILES_TENSION (WP01)

**Sketch.**

1. Write the red tests first:
   - `TensionTest`;
   - `DoctrineGraphTest` equality guards (S4);
   - `DrgNode` field tests;
   - the `NodeKind` plural property test.
2. Then the records and the graph.
3. Then the javadoc.

**Dependencies**: none. **Risks**: doclint on record components; `Relationship.equals` ignores type
(S4). Tests must not lean on graph equality alone.

## Phase 2 — The round trip

### WP02 — Packs: read, bind and write tension edges and node fields; retire both expected-red entries

- **Goal**: `in_tension_with` and the node fields survive the domain round trip, and the gate proves it.
- **Priority**: P1. **Prompt**: [tasks/WP02-packs-round-trip-and-gate.md](tasks/WP02-packs-round-trip-and-gate.md)
- **Independent test**: `./gradlew :packs:test :packs:wholePackRoundTrip`.

T006 `RawNode` + reader carry provenance, tags, body_path (WP02)
T007 `PackPolicy` binds node fields and tension; `registeredGraph` (WP02)
T008 `GraphShardWriter`: node fields, tension edges, shape refusals, `writeFragment` (WP02)
T009 `RelationshipShard.read` uses `registeredGraph` (WP02)
T010 Gate adapter re-bind + production-path fixture (WP02)
T011 Rewrite −1 tests and fixtures (WP02)
T012 Gate run; remove both expected-red entries via the ratchet (WP02)

**Sketch.**

1. Red tests on the production path, reader → policy → writer.
2. Then the reader, the policy, the writer and `RelationshipShard`.
3. Then the gate adapter.
4. Run the gate. Remove each entry only on `UNEXPECTEDLY_GREEN`.

**Dependencies**: WP01. **Risks**:

- `PackPolicy.edge` complexity (S13).
- The fragment schema refuses `when` and a root header (S3).
- PR-B rebase on `PackPolicy` ~L560-580 and on `expected-red.yaml`'s `kinds:` entry. Neither is
  touched here.

## Phase 3 — Records

### WP03 — Records: ADR-0045 and the documents that state the −1 reading

- **Goal**: no record still states that a tension reads as `impacts` −1, and the representation
  ADR-0028 D5 deferred is decided in an ADR.
- **Priority**: P2. **Prompt**: [tasks/WP03-records-adr-and-docs.md](tasks/WP03-records-adr-and-docs.md)
- **Independent test**: `python3 scripts/docs/check_docs.py all`; `./gradlew verifyAll` (contract
  checks); the grep in SC-004.

T013 ADR-0045 (WP03)
T014 DIV-018, logical model, `Relation.yaml` description, CHANGELOGs, ADR index (WP03)
T015 Follow-up draft: the load-fixture runner (WP03)

**Dependencies**: WP02. **Risks**:

- The `Relation.yaml` edit is description only (C-001) and is an open review item.
- ADR number collision: re-check `main` and the open PRs when writing. 0044 is taken on the #194 PR-B
  branch.
