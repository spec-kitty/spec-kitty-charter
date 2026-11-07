# Work Packages: Graph documents are the sole source of edges

**Inputs**: Design documents from `kitty-specs/graph-documents-sole-edge-source-01M6P9D0/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Required. The charter mandates test-first. The plan-point squad also asked for
non-fakeable pins: a structural no-minting test, and a parameterized refusal for each removed member
and each pack schema.

**Organization**: Subtasks (`Txxx`) roll up into work packages (`WPxx`). Record completion with
`spec-kitty agent tasks mark-status <Txxx> --status done`. There are no checkboxes.

**Why one code work package**: the service change is a single compile unit. Removing a domain field
breaks the generated DTO mapping and the controllers in the same module. Removing a contract member
regenerates the DTOs the controllers call. And closing the pack schemas reds every corpus test until
the open-packs checkout is the migrated one. The pieces cannot land green separately, so WP02 holds
them in one lane, ordered so that each subtask leaves the tree compiling.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Write ADR-0060 (number re-checked at writing): per-field decision, single write path, derived relations, kept-as-data table | WP01 | |
| T002 | Dated amendments in ADR-0024, 0025, 0026, 0027, 0036 d4 and 0053 d22 | WP01 | |
| T003 | Repoint the #189/#167 edge-authority citations in ADR-0037, 0043, 0044, 0050, 0052 d9 (and 0024/0025/0027/0036) | WP01 | |
| T004 | ADR docs checks (check_docs, markdownlint, cspell, frontmatter ≤180) | WP01 | |
| T005 | Pack schemas: remove the fields and `$defs`, add `when` to the org-fragment edge; PackSchemasTest per removed field | WP02 | |
| T006 | Domain: remove the fields and reference types, retire `ArtifactEdges` and `referenceProblem`, add `derivedRelationships()`; structural no-minting test; rewrite the pinned tests | WP02 | |
| T007 | Storage: DSO/Yaml/Binding without the fields; fixtures; the `edges(pack)` port and its adapter | WP02 | |
| T008 | Contract: shapes, paths, examples, orphan schemas, `ReferencedArtifact`, the `### Breaking` fragment | WP02 | |
| T009 | API and application: controllers, `ArtifactDtos`, the registration services' dead write path, the references read on the port | WP02 | |
| T010 | Registration bodies refuse unknown members (422 `validation_failed`, the member named); paradigm guard; tests | WP02 | |
| T011 | Scenarios and BDD steps; `ContractConformanceTest` fixture; parameterized removed-member scenario | WP02 | |
| T012 | Corpus tests on the migrated checkout; retarget `OpenPacksComposeTest`; delete the walk-slice tests | WP02 | |
| T013 | Move `openPacksRef` to the merged part-A commit (blocked on the parent); `verifyAll` on Java 25 | WP02 | |
| T014 | Living docs: compose-packs guide, logical model, descriptors, conceptual model, journeys 06 and 09, scenarios README | WP03 | [P] |
| T015 | Docs changelog entry | WP03 | [P] |
| T016 | Docs checks | WP03 | |
| T017 | open-packs part A: patch file and description (file list, edges, merges, dropped citations, manifest restamp) | WP04 | [P] |
| T018 | open-packs part B: the fragment `when` texts and the validator follow-up, as a draft | WP04 | [P] |
| T019 | Issue drafts: the open-packs migration issue and the GraphWalk retirement follow-up | WP04 | [P] |

---

## Work Package WP01: Decision record (Priority: P1)

**Goal**: One ADR records the per-field decision. The ADRs it changes carry dated amendments, and no ADR still defers edge authority to #189 or #167.
**Independent Test**: `grep -rn "#189\|/189)\|#167\|/167)" docs/adr` shows only historical mentions, each pointing on to ADR-0060. The docs checks pass.
**Prompt**: `tasks/WP01-decision-record.md`
**Requirement Refs**: FR-001, FR-002, FR-013

### Included Subtasks

T001 Write ADR-0060 (number re-checked at writing) (WP01)
T002 Dated amendments in ADR-0024, 0025, 0026, 0027, 0036 d4 and 0053 d22 (WP01)
T003 Repoint the #189/#167 edge-authority citations (WP01)
T004 ADR docs checks (WP01)

**Dependencies**: none. **Parallel**: with WP02 and WP04.

---

## Work Package WP02: Service and contract without edge-minting fields (Priority: P1)

**Goal**: No artifact field mints an edge. Neither the pack schemas, the domain, nor the contract
carry the fields or the `relationships` arrays. `putPackRelationship` is the only edge write. The
references read answers graph edges. Unknown registration members are refused. The gates compare
edges from the graph documents only.
**Independent Test**: `./gradlew verifyAll` on Java 25 passes against the part-A open-packs commit,
and the structural no-minting test fails if any kind's override is restored.
**Prompt**: `tasks/WP02-service-and-contract-without-edge-minting-fields.md`
**Requirement Refs**: FR-003 to FR-011, FR-014, NFR-001, NFR-003, C-001, C-002, C-004

### Included Subtasks

T005 Pack schemas (WP02)
T006 Domain (WP02)
T007 Storage and the edge port (WP02)
T008 Contract (WP02)
T009 API and application (WP02)
T010 Unknown-member refusal (WP02)
T011 Scenarios and BDD (WP02)
T012 Corpus tests on the migrated checkout (WP02)
T013 Pin move and `verifyAll` (WP02)

**Dependencies**: none in code. T013 waits for the parent session to merge open-packs part A (plan
P5). Until then the WP is verified against the local draft commit.
**Risks**: the change is large and cross-module, so the reviewer reads it surface by surface. The
corpus counts may move with the migrated content, and each moved count is justified.

---

## Work Package WP03: Living documentation (Priority: P2)

**Goal**: The guides and model pages describe one home for edges. A changelog entry records the change.
**Independent Test**: The docs checks pass. No page outside history describes `directive_refs`, typed artifact references, or a registration body's `relationships`.
**Prompt**: `tasks/WP03-living-documentation.md`
**Requirement Refs**: NFR-002

### Included Subtasks

T014 Living docs pages (WP03)
T015 Docs changelog entry (WP03)
T016 Docs checks (WP03)

**Dependencies**: WP01 (it cites the ADR) and WP02 (it describes the behavior).

---

## Work Package WP04: open-packs migration and follow-up drafts (Priority: P1)

**Goal**: The parent session can file and merge the open-packs change without re-deriving it. The
GraphWalk follow-up is ready to file.
**Independent Test**: Applying `research/open-packs-part-a.patch` to open-packs at `dad323f` and
running the charter gates against it gives the results recorded in research.md R7.
**Prompt**: `tasks/WP04-open-packs-migration-and-follow-up-drafts.md`
**Requirement Refs**: FR-012

### Included Subtasks

T017 open-packs part A: patch and description (WP04)
T018 open-packs part B draft (WP04)
T019 Issue drafts (WP04)

**Dependencies**: none. Part B names the schema change WP02 makes.
