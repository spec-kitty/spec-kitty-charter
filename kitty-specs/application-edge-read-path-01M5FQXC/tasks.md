# Tasks: Application-edge read path on the Pack model

**Mission**: `application-edge-read-path-01M5FQXC` · **Branch**: `feat/pack-repositories-validation`
**Inputs**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md), [research.md](./research.md), [contracts/openapi-delta.md](./contracts/openapi-delta.md)

Ownership is disjoint by layer directory. Each edge layer owns its own `@Configuration`; the `deployable` aggregate (WP02) `@ComponentScan`s the layer config packages (no compile coupling). Scaffolding removal is split: each layer WP deletes its own scaffolding (WP04 storage, WP05 application, WP06 api), and the shared **domain** ports are deleted last in WP07 once every consumer is gone — so the branch converges without a stranded reference.

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Revise ADR-0006: application is an outer edge ring | WP01 | [P] |
| T002 | Update hexagonal-service.md to the edge model | WP01 | [P] |
| T003 | Update architectural-patterns-applied.md + regenerate C4/class diagrams | WP01 | [P] |
| T004 | Add the changelog entry | WP01 | [P] |
| T005 | Reconcile ddd-patterns glossary wording | WP01 | [P] |
| T006 | Rename `bootstrap` → `deployable` (package, module, settings) | WP02 | |
| T007 | Revise the ArchUnit gate: `APPLICATION_IS_EDGE` + biting control; retarget BOOTSTRAP→deployable | WP02 | |
| T008 | `DeployableConfiguration`: aggregate `@ComponentScan` of the layer config packages | WP02 | |
| T009 | Drop the explicit `DomainRepositoryService` `@Bean`; keep `Locations`/guard beans | WP02 | |
| T010 | Adapt the moved deployable tests to the rename + scan | WP02 | |
| T011 | `Pack` is the single owner of read violations | WP03 | |
| T012 | `TacticRepository`: upsert `save` contract (javadoc + signature) | WP03 | |
| T013 | Read ports for tactics (all + one-by-id) on the domain | WP03 | |
| T014 | Domain tests for the single-owner + upsert contract | WP03 | |
| T015 | Keep `PackInspectionQuery` compiling (its removal is WP07) | WP03 | |
| T016 | `StorageConfiguration`: `@ComponentScan` storage + `SerializationStrategy` bean | WP04 | |
| T017 | Scan-driven `DomainRepositoryService` (no explicit bean); inject `SerializationStrategy` | WP04 | |
| T018 | Implement upsert `save` (create arm + replace arm), atomic + pack-root-contained | WP04 | |
| T019 | Point-read identity resolution (descriptor-only; no full-pack fan-out) | WP04 | |
| T020 | Collapse `partialReads` into the aggregate owner | WP04 | |
| T021 | Delete `FileSystemCharterPackRepository` (storage scaffolding) | WP04 | |
| T022 | Storage tests: upsert both-arm contract test, point-read, round-trip preserved | WP04 | |
| T023 | `ApplicationConfiguration`: `@ComponentScan` of the application package | WP05 | |
| T024 | `TacticReadService` (`@Service`): list tactics + one-by-id + refusal | WP05 | |
| T025 | Delete `ConsistentLoad`, `PackListingQuery`, `ActionContextQuery` | WP05 | |
| T026 | Modular wired `@SpringBootTest` (storage + application slice) | WP05 | |
| T027 | Application tests: read happy-path + absent-pack refusal (non-vacuous) | WP05 | |
| T028 | Keep `ServiceInfoQuery` intact | WP05 | |
| T029 | `ApiConfiguration`: `@ComponentScan` of the api package | WP06 | |
| T030 | `TacticController`: GET tactics collection + single-by-id (thin) | WP06 | |
| T031 | OpenAPI: add tactic ops, remove listing/action-context ops; regenerate | WP06 | |
| T032 | Delete `PacksController` + `ContextController` | WP06 | |
| T033 | `contracts/charter/CHANGELOG.md` for the contract change | WP06 | |
| T034 | API tests: tactic endpoints 200 + 404 (non-vacuous) | WP06 | |
| T035 | Delete `CharterPack`, `CharterOffering` | WP07 | |
| T036 | Delete `CharterPackRepository` port | WP07 | |
| T037 | Delete `PackInspectionQuery` port | WP07 | |
| T038 | Final purity/gate + single-model absence check | WP07 | |

---

## WP01 — Doctrine docs: ADR-0006 revision + architecture docs + changelog

- **Goal**: Make the written architecture say application is an outer edge ring and record the change. Priority: High.
- **Independent test**: ADR-0006 reads application-as-edge; docs/diagrams regenerate clean; the docs-freshness + diagram gates pass.
- **Subtasks**: T001, T002, T003, T004, T005
- **Prompt**: [WP01-doctrine-docs.md](./tasks/WP01-doctrine-docs.md)
- **Dependencies**: none
- **Requirements**: FR-012, SC-004

## WP02 — Composition root: deployable rename + aggregate config + revised gate

- **Goal**: Rename `bootstrap`→`deployable`, add the aggregate `DeployableConfiguration`, and revise the ArchUnit gate to `APPLICATION_IS_EDGE`. Priority: High (foundational).
- **Independent test**: `:deployable:test` runs the revised gate (controls still bite); the module builds under the new name.
- **Subtasks**: T006, T007, T008, T009, T010
- **Prompt**: [WP02-composition-deployable.md](./tasks/WP02-composition-deployable.md)
- **Dependencies**: none
- **Requirements**: FR-006, FR-007, FR-011, NFR-003, SC-003

## WP03 — Domain: single violations owner + upsert contract + read ports

- **Goal**: Make `Pack` the one owner of read violations, pin `save` as upsert, and expose the tactic read ports — additively (no deletions). Priority: High.
- **Independent test**: `:packs:test` domain tests pass; `Pack.violations()` is canonical; `TacticRepository` javadoc states upsert.
- **Subtasks**: T011, T012, T013, T014, T015
- **Prompt**: [WP03-domain-ports.md](./tasks/WP03-domain-ports.md)
- **Dependencies**: none
- **Requirements**: FR-009, FR-010, C-005

## WP04 — Persistence edge: storage config + upsert + point-read + scan-driven

- **Goal**: `StorageConfiguration` (scan + injected `SerializationStrategy`), scan-driven `DomainRepositoryService`, upsert `save` (atomic create + replace), point-read id resolution, collapse `partialReads`, delete the storage scaffolding. Priority: High.
- **Independent test**: `:packs:test` storage tests pass incl. the save both-arm contract test and preserved round-trip.
- **Subtasks**: T016, T017, T018, T019, T020, T021, T022
- **Prompt**: [WP04-persistence-edge.md](./tasks/WP04-persistence-edge.md)
- **Dependencies**: WP02, WP03
- **Requirements**: FR-008, FR-009, FR-010, NFR-001, NFR-003

## WP05 — Application edge: TacticReadService + config + scaffolding removal

- **Goal**: `ApplicationConfiguration` + `TacticReadService` over the ports, a modular wired test, and deletion of the application scaffolding. Priority: High.
- **Independent test**: wired `@SpringBootTest` reads a fixture pack's tactics through the production path; absent pack → refusal.
- **Subtasks**: T023, T024, T025, T026, T027, T028
- **Prompt**: [WP05-application-edge.md](./tasks/WP05-application-edge.md)
- **Dependencies**: WP02, WP03, WP04
- **Requirements**: FR-001, FR-002, FR-003, FR-007, NFR-004, SC-001, SC-003

## WP06 — Inbound API + OpenAPI contract change

- **Goal**: Thin `TacticController` + `ApiConfiguration`, add the tactic operations and remove listing/action-context from the OpenAPI contract (regenerate), delete `PacksController`/`ContextController`. Priority: High.
- **Independent test**: API tests exercise 200 + 404 on the tactic routes; the removed routes are gone from the contract; generated API matches.
- **Subtasks**: T029, T030, T031, T032, T033, T034
- **Prompt**: [WP06-inbound-api-contract.md](./tasks/WP06-inbound-api-contract.md)
- **Dependencies**: WP02, WP05
- **Requirements**: FR-001, FR-002, FR-005, C-004

## WP07 — Cutover: delete the shared domain scaffolding

- **Goal**: With every consumer gone, delete `CharterPack`/`CharterOffering`/`CharterPackRepository`/`PackInspectionQuery` and confirm one model. Priority: High.
- **Independent test**: repo-wide search finds zero references to the removed types; build + full gate green on the branch tip.
- **Subtasks**: T035, T036, T037, T038
- **Prompt**: [WP07-cutover-delete-scaffolding.md](./tasks/WP07-cutover-delete-scaffolding.md)
- **Dependencies**: WP04, WP05, WP06
- **Requirements**: FR-004, FR-005, C-001, SC-002

## Dependency graph

```
WP01 (docs) ─ independent
WP02 (composition) ─┬─ WP04 ─┬─ WP05 ─┬─ WP06 ─┐
WP03 (domain)      ─┴────────┘        │        ├─ WP07 (cutover)
                                      └────────┘
```

## MVP

WP02 + WP03 + WP04 + WP05 deliver the wired tactic read path (SC-001) end-to-end over the canonical model; WP06 exposes it over HTTP and WP07 removes the last scaffolding.
