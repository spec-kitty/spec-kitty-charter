# Tasks: Closure symmetry

Mission: `closure-symmetry-01M7PNFT` · Planning base and merge target: `feat/355-closure-in-pack-ruleset`

## Subtask Index

| ID | Description | WP | Parallel |
|----|-------------|----|----------|
| T001 | Red tests for ReferenceClosure and ReferenceRules | WP01 | |
| T002 | ConfigSchemas port; EmbeddedSchemas implements it | WP01 | |
| T003 | ReferenceClosure in packs.domain.rules | WP01 | |
| T004 | Bulk edit: artifact_schema_error moves to PackCodes | WP01 | |
| T005 | Load and whole-pack write call ReferenceClosure; delete GuardClosure, MissionTemplates | WP01 | |
| T006 | ReferenceFacts on PackContents; ReferenceRules in content() | WP01 | |
| T007 | Store half fills the facts; closure faults leave store-only | WP01 | |
| T008 | Golden snapshot and example packs | WP01 | |
| T009 | Red tests at the commit store's choke point | WP02 | |
| T010 | Commit judge | WP02 | |
| T011 | Pack.without; Removal.after | WP02 | |
| T012 | Remove judge | WP02 | |
| T013 | Symmetry table harness | WP03 | |
| T014 | Closure rows | WP03 | |
| T015 | Invariant rows | WP03 | |
| T016 | Delete row | WP03 | |
| T017 | Fold per-case tests | WP03 | |
| T018 | Measure each write | WP04 | [P] |
| T019 | Fail naming the write and the round | WP04 | [P] |
| T020 | Outer timeout as hang guard | WP04 | [P] |
| T021 | Evidence | WP04 | [P] |
| T022 | ADR-0042 decision 7 amendment | WP05 | |
| T023 | Work breakdown item 8 | WP05 | |

## WP01 - The closure rule in the PACK ruleset

Prompt: `tasks/WP01-closure-rule-in-pack-ruleset.md` · Priority P1 · Dependencies: none.
Goal: one pure rule; load and whole-pack write call it; `content()` runs it, so validate mode states it.
Independent test: `ReferenceClosureTest`, `ReferenceRulesTest`; the load tests unchanged.

T001 Red tests for ReferenceClosure and ReferenceRules (WP01)
T002 ConfigSchemas port; EmbeddedSchemas implements it (WP01)
T003 ReferenceClosure in packs.domain.rules (WP01)
T004 Bulk edit: artifact_schema_error moves to PackCodes (WP01)
T005 Load and whole-pack write call ReferenceClosure; delete the old classes (WP01)
T006 ReferenceFacts on PackContents; ReferenceRules in content() (WP01)
T007 Store half fills the facts; closure faults leave store-only (WP01)
T008 Golden snapshot and example packs (WP01)

Risks: verdict order; ArchUnit. Out-of-map edit: the one-line rename in `GuardAndLifecycleLoadTest` (owned by WP03).

## WP02 - The per-file write judge

Prompt: `tasks/WP02-per-file-write-judge.md` · Priority P1 · Depends on WP01.

T009 Red tests at the commit store's choke point (WP02)
T010 Commit judge (WP02)
T011 Pack.without; Removal.after (WP02)
T012 Remove judge (WP02)

## WP03 - The symmetry table

Prompt: `tasks/WP03-symmetry-table.md` · Priority P1 · Depends on WP01, WP02.

T013 Symmetry table harness (WP03)
T014 Closure rows (WP03)
T015 Invariant rows (WP03)
T016 Delete row (WP03)
T017 Fold per-case tests (WP03)

## WP04 - #593: a per-write wait bound

Prompt: `tasks/WP04-fair-lock-wait-bound.md` · Priority P3 · Dependencies: none (parallel with WP01 to WP03).

T018 Measure each write (WP04)
T019 Fail naming the write and the round (WP04)
T020 Outer timeout as hang guard (WP04)
T021 Evidence (WP04)

## WP05 - Docs

Prompt: `tasks/WP05-docs-adr-0042.md` · Depends on WP03.

T022 ADR-0042 decision 7 amendment (WP05)
T023 Work breakdown item 8 (WP05)
